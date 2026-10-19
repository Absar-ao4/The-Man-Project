package com.example.fitapp.recommend;

import com.example.fitapp.ai.AiQuotaService;
import com.example.fitapp.common.ApiException;
import com.example.fitapp.config.AnthropicProperties;
import com.example.fitapp.diet.Allergen;
import com.example.fitapp.diet.DietDtos.MacroDto;
import com.example.fitapp.diet.DietFood;
import com.example.fitapp.diet.DietFoodRepository;
import com.example.fitapp.diet.DietType;
import com.example.fitapp.nutrition.DayTotals;
import com.example.fitapp.nutrition.MealItemRepository;
import com.example.fitapp.nutrition.MealType;
import com.example.fitapp.profile.DailyTarget;
import com.example.fitapp.profile.DailyTargetRepository;
import com.example.fitapp.profile.Profile;
import com.example.fitapp.profile.ProfileRepository;
import com.example.fitapp.recommend.RecommendDtos.MealSuggestionRequest;
import com.example.fitapp.recommend.RecommendDtos.MealSuggestionsDto;
import com.example.fitapp.recommend.RecommendDtos.SuggestionDto;
import com.example.fitapp.recommend.RecommendDtos.SuggestionItemDto;
import com.example.fitapp.recommend.RulesMealSuggester.Line;
import com.example.fitapp.recommend.RulesMealSuggester.Remaining;
import com.example.fitapp.recommend.RulesMealSuggester.Suggestion;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Meal ideas from what is left of today's targets. The numbers come from the user's own targets and food log; the AI model
 * (when configured and the user has uses left today) picks foods and quantities from our own food list, otherwise simple
 * rules do. Either way the calories and macros shown are worked out from the list.
 */
@Service
public class MealSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(MealSuggestionService.class);

    static final double MIN_REMAINING_KCAL = 80;
    static final int DEFAULT_COUNT = 3;

    private final ProfileRepository profiles;
    private final DailyTargetRepository targets;
    private final MealItemRepository meals;
    private final DietFoodRepository foods;
    private final AiQuotaService quota;
    private final AnthropicProperties anthropic;
    private final AiMealSuggester ai;
    private final Clock clock;

    public MealSuggestionService(
            ProfileRepository profiles,
            DailyTargetRepository targets,
            MealItemRepository meals,
            DietFoodRepository foods,
            AiQuotaService quota,
            AnthropicProperties anthropic,
            AiMealSuggester ai,
            Clock clock) {
        this.profiles = profiles;
        this.targets = targets;
        this.meals = meals;
        this.foods = foods;
        this.quota = quota;
        this.anthropic = anthropic;
        this.ai = ai;
        this.clock = clock;
    }

    @Transactional
    public MealSuggestionsDto suggest(UUID userId, MealSuggestionRequest req) {
        Profile profile = profiles.findByUserIdAndDeletedAtIsNull(userId).orElse(null);
        LocalDate today = LocalDate.now(clock.withZone(zoneOf(profile)));
        LocalDate date = req == null || req.date() == null ? today : req.date();
        if (date.isBefore(today.minusDays(1)) || date.isAfter(today.plusDays(1))) {
            throw ApiException.validation("date", "must be yesterday, today or tomorrow");
        }
        DailyTarget target =
                targets.findFirstByUserIdAndDeletedAtIsNullAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(userId, date)
                        .orElseThrow(() -> ApiException.validation("targets", "Set your daily targets first, then ask for ideas"));
        DayTotals day = meals.totalsByDay(userId, date, date).stream().findFirst().orElse(null);
        double eatenCalories = value(day == null ? null : day.calories());
        double eatenProtein = value(day == null ? null : day.proteinG());
        double eatenCarbs = value(day == null ? null : day.carbsG());
        double eatenFat = value(day == null ? null : day.fatG());
        Remaining remaining =
                new Remaining(
                        Math.max(0, target.getCalories() - eatenCalories),
                        Math.max(0, target.getProteinG() - eatenProtein),
                        Math.max(0, target.getCarbsG() - eatenCarbs),
                        Math.max(0, target.getFatG() - eatenFat));

        MealType type = req == null ? null : req.mealType();
        int count = req == null || req.count() == null ? DEFAULT_COUNT : req.count();
        DietType diet = req == null || req.dietType() == null ? DietType.NON_VEGETARIAN : req.dietType();
        Set<Allergen> allergies = req == null || req.allergies() == null || req.allergies().isEmpty() ? EnumSet.noneOf(Allergen.class) : EnumSet.copyOf(req.allergies());
        Set<String> allergyNames = allergies.stream().map(Enum::name).collect(Collectors.toSet());
        List<DietFood> pool =
                foods.foodsFor(userId, currency(userId, profile)).stream()
                        .filter(f -> f.dietTags().contains(diet.name()))
                        .filter(f -> f.allergens().stream().noneMatch(allergyNames::contains))
                        .toList();

        List<String> notes = new ArrayList<>();
        List<Suggestion> suggestions = List.of();
        String source = "RULES";
        if (remaining.calories() < MIN_REMAINING_KCAL) {
            notes.add("You have already reached today's calories, so there is nothing left to suggest.");
        } else if (pool.isEmpty()) {
            notes.add("No foods are left after your diet and allergy choices.");
        } else {
            List<Suggestion> fromAi = null;
            if (anthropic.configured()) {
                if (quota.tryConsume(userId, AiQuotaService.MEAL_SUGGESTIONS)) {
                    try {
                        fromAi = ai.suggest(pool, remaining, type, count);
                    } catch (RuntimeException e) {
                        quota.refund(userId, AiQuotaService.MEAL_SUGGESTIONS); // nothing usable came back, so it must not use up a try
                        log.info("AI meal suggestions not used: {}", e.getClass().getSimpleName());
                        notes.add("The AI was not available, so these ideas come from simple rules.");
                    }
                } else {
                    notes.add("You have used today's AI meal ideas, so these come from simple rules.");
                }
            }
            if (fromAi != null && !fromAi.isEmpty()) {
                suggestions = fromAi;
                source = "AI";
            } else {
                suggestions = RulesMealSuggester.suggest(pool, remaining, type, count);
                if (suggestions.isEmpty()) {
                    notes.add("There was not enough to build a meal from. Try another diet type.");
                }
            }
        }
        int usesLeft = Math.max(0, quota.limit(AiQuotaService.MEAL_SUGGESTIONS) - quota.used(userId, AiQuotaService.MEAL_SUGGESTIONS));
        return new MealSuggestionsDto(
                date,
                new MacroDto(target.getCalories(), target.getProteinG(), target.getCarbsG(), target.getFatG()),
                new MacroDto(round1(eatenCalories), round1(eatenProtein), round1(eatenCarbs), round1(eatenFat)),
                new MacroDto(round1(remaining.calories()), round1(remaining.protein()), round1(remaining.carbs()), round1(remaining.fat())),
                source,
                notes,
                suggestions.stream().map(MealSuggestionService::toDto).toList(),
                anthropic.configured() ? usesLeft : 0);
    }

    static SuggestionDto toDto(Suggestion s) {
        double calories = 0;
        double protein = 0;
        double carbs = 0;
        double fat = 0;
        List<SuggestionItemDto> items = new ArrayList<>();
        for (Line line : s.lines()) {
            DietFood f = line.food();
            double factor = line.grams() / 100.0;
            MacroDto macros = new MacroDto(round1(f.kcal() * factor), round1(f.protein() * factor), round1(f.carbs() * factor), round1(f.fat() * factor));
            calories += f.kcal() * factor;
            protein += f.protein() * factor;
            carbs += f.carbs() * factor;
            fat += f.fat() * factor;
            items.add(new SuggestionItemDto(f.slug(), f.name(), line.grams(), macros));
        }
        return new SuggestionDto(s.title(), s.description(), new MacroDto(round1(calories), round1(protein), round1(carbs), round1(fat)), items);
    }

    /** The profile currency if there is a price list for it, otherwise USD, otherwise whatever list exists. */
    private String currency(UUID userId, Profile profile) {
        List<String> supported = foods.supportedCurrencies();
        String wanted = profile == null ? null : profile.getCurrency();
        if (wanted != null && supported.contains(wanted)) {
            return wanted;
        }
        return supported.contains("USD") || supported.isEmpty() ? "USD" : supported.get(0);
    }

    private static ZoneId zoneOf(Profile profile) {
        try {
            return ZoneId.of(profile == null ? "UTC" : profile.getTimezone());
        } catch (DateTimeException e) {
            return ZoneId.of("UTC");
        }
    }

    private static double value(Double d) {
        return d == null ? 0 : d;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
