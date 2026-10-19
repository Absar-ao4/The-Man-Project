package com.example.fitapp.diet;

import com.example.fitapp.ai.AiQuotaService;
import com.example.fitapp.common.ApiException;
import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.config.AnthropicProperties;
import com.example.fitapp.diet.DietDtos.CheapestProteinDto;
import com.example.fitapp.diet.DietDtos.CheapestProteinListDto;
import com.example.fitapp.diet.DietDtos.FoodDto;
import com.example.fitapp.diet.DietDtos.FoodListDto;
import com.example.fitapp.diet.DietDtos.LogMealRequest;
import com.example.fitapp.diet.DietDtos.LogMealResponse;
import com.example.fitapp.diet.DietDtos.PlanDto;
import com.example.fitapp.diet.DietDtos.PlanItemDto;
import com.example.fitapp.diet.DietDtos.PlanMealDto;
import com.example.fitapp.diet.DietDtos.PlanRequest;
import com.example.fitapp.diet.DietDtos.PlanSummaryDto;
import com.example.fitapp.diet.DietDtos.ShortfallDto;
import com.example.fitapp.diet.DietOptimizer.Line;
import com.example.fitapp.diet.DietOptimizer.Result;
import com.example.fitapp.diet.DietOptimizer.Status;
import com.example.fitapp.diet.DietOptimizer.Targets;
import com.example.fitapp.diet.DietPlanRepository.NewItem;
import com.example.fitapp.diet.DietPlanRepository.NewMeal;
import com.example.fitapp.diet.DietPlanRepository.NewPlan;
import com.example.fitapp.diet.MealArranger.ArrangedMeal;
import com.example.fitapp.diet.MealArranger.MealLine;
import com.example.fitapp.nutrition.MealService;
import com.example.fitapp.nutrition.MealSource;
import com.example.fitapp.nutrition.dto.NutritionDtos.MealItemRequest;
import com.example.fitapp.profile.DailyTarget;
import com.example.fitapp.profile.DailyTargetRepository;
import com.example.fitapp.profile.ProfileRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The budget diet planner: prices, plans, the cheapest protein list. A plan is built from the user's latest daily
 * targets and the chosen budget, diet type and exclusions; the optimiser picks quantities, then foods are put into meals
 * (by the AI model if it is set up and the user has uses left today, otherwise by simple rules). Creating a plan
 * saves it.
 */
@Service
public class DietPlanService {

    private static final Logger log = LoggerFactory.getLogger(DietPlanService.class);
    private static final List<String> CATEGORY_ORDER =
            List.of("GRAIN", "PULSE", "SOY", "MEAT", "FISH", "SHELLFISH", "EGG", "DAIRY", "VEGETABLE", "FRUIT", "NUT", "FAT", "SUPPLEMENT");
    private static final double MIN_PROTEIN_PER_100G_FOR_RANKING = 5.0;

    private final DietFoodRepository foods;
    private final DietPlanRepository plans;
    private final DietOptimizer optimizer;
    private final MealArranger arranger;
    private final ProfileRepository profiles;
    private final DailyTargetRepository targets;
    private final AiQuotaService quota;
    private final AnthropicProperties anthropic;
    private final MealService meals;
    private final Clock clock;

    public DietPlanService(
            DietFoodRepository foods,
            DietPlanRepository plans,
            DietOptimizer optimizer,
            MealArranger arranger,
            ProfileRepository profiles,
            DailyTargetRepository targets,
            AiQuotaService quota,
            AnthropicProperties anthropic,
            MealService meals,
            Clock clock) {
        this.foods = foods;
        this.plans = plans;
        this.optimizer = optimizer;
        this.arranger = arranger;
        this.profiles = profiles;
        this.targets = targets;
        this.quota = quota;
        this.anthropic = anthropic;
        this.meals = meals;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ foods and prices

    public FoodListDto listFoods(UUID userId, DietType dietType, String requestedCurrency) {
        String currency = resolveCurrency(userId, requestedCurrency);
        List<FoodDto> list =
                foods.foodsFor(userId, currency).stream()
                        .filter(f -> dietType == null || f.dietTags().contains(dietType.name()))
                        .map(DietPlanService::toDto)
                        .toList();
        return new FoodListDto(currency, foods.supportedCurrencies(), list);
    }

    public FoodDto setPrice(UUID userId, UUID foodId, BigDecimal price) {
        DietFood food = foods.find(userId, foodId).orElseThrow(() -> ApiException.notFound("Food not found"));
        foods.setOverride(userId, food.slug(), food.currency(), price.setScale(2, java.math.RoundingMode.HALF_UP));
        return toDto(foods.find(userId, foodId).orElseThrow());
    }

    public FoodDto clearPrice(UUID userId, UUID foodId) {
        DietFood food = foods.find(userId, foodId).orElseThrow(() -> ApiException.notFound("Food not found"));
        foods.clearOverride(userId, food.slug(), food.currency());
        return toDto(foods.find(userId, foodId).orElseThrow());
    }

    /** Foods ranked by grams of protein per unit of currency, best first. Foods with little protein are left out. */
    public CheapestProteinListDto cheapestProtein(UUID userId, DietType dietType, String requestedCurrency) {
        String currency = resolveCurrency(userId, requestedCurrency);
        List<CheapestProteinDto> ranked =
                foods.foodsFor(userId, currency).stream()
                        .filter(f -> dietType == null || f.dietTags().contains(dietType.name()))
                        .filter(f -> f.protein() >= MIN_PROTEIN_PER_100G_FOR_RANKING)
                        .map(
                                f -> {
                                    double proteinPerUnit = f.protein() / 100.0 * f.unitGrams();
                                    double perCurrency = proteinPerUnit / f.price();
                                    return new CheapestProteinDto(
                                            f.id(),
                                            f.name(),
                                            f.protein(),
                                            f.price(),
                                            f.unit(),
                                            round2(perCurrency),
                                            round2(100.0 / perCurrency),
                                            f.overridden());
                                })
                        .sorted(Comparator.comparingDouble(CheapestProteinDto::proteinGramsPerCurrency).reversed())
                        .toList();
        return new CheapestProteinListDto(currency, dietType, ranked);
    }

    // ------------------------------------------------------------------ plans

    public PlanDto create(UUID userId, PlanRequest req) {
        String currency = resolveCurrency(userId, req.currency());
        DailyTarget target = latestTargets(userId);
        double budget = req.budget().doubleValue();
        Cuisine cuisine = req.cuisine() == null ? Cuisine.ANY : req.cuisine();
        Set<Allergen> allergies =
                req.allergies() == null || req.allergies().isEmpty() ? EnumSet.noneOf(Allergen.class) : EnumSet.copyOf(req.allergies());
        Set<String> allergyNames = new HashSet<>();
        allergies.forEach(a -> allergyNames.add(a.name()));
        Set<String> excludedSlugs = new HashSet<>();
        if (req.excludedFoodIds() != null) {
            foods.keysFor(req.excludedFoodIds()).values().forEach(k -> excludedSlugs.add(k.slug()));
        }

        List<DietFood> all = foods.foodsFor(userId, currency);
        List<DietFood> usable =
                all.stream()
                        .filter(f -> f.dietTags().contains(req.dietType().name()))
                        .filter(f -> f.allergens().stream().noneMatch(allergyNames::contains))
                        .filter(f -> !excludedSlugs.contains(f.slug()))
                        .toList();
        if (usable.isEmpty()) {
            throw new ApiException(ErrorCode.INFEASIBLE_PLAN, "No foods match your diet type, allergies and exclusions. Remove some exclusions and try again.");
        }

        Result result = optimizer.optimize(usable, new Targets(target.getCalories(), target.getProteinG()), budget);
        List<Line> lines = sorted(result.lines());
        if (lines.isEmpty()) {
            String hint = result.minimumBudgetNeeded() == null ? "" : " A budget of at least " + Math.round(result.minimumBudgetNeeded()) + " " + currency + " would work.";
            throw new ApiException(ErrorCode.INFEASIBLE_PLAN, "That budget is too small to buy food that fits your targets." + hint);
        }

        double calories = lines.stream().mapToDouble(Line::calories).sum();
        double protein = lines.stream().mapToDouble(Line::protein).sum();
        double carbs = lines.stream().mapToDouble(Line::carbs).sum();
        double fat = lines.stream().mapToDouble(Line::fat).sum();
        boolean feasible = result.status() == Status.FEASIBLE;
        ShortfallDto shortfall =
                feasible
                        ? null
                        : new ShortfallDto(
                                result.status() == Status.BUDGET_SHORT ? "BUDGET_TOO_LOW" : "TARGETS_UNREACHABLE",
                                round1(Math.max(0, target.getCalories() - calories)),
                                round1(Math.max(0, target.getProteinG() - protein)),
                                result.minimumBudgetNeeded());

        List<String> notes = new ArrayList<>();
        if (feasible) {
            double produce = lines.stream().filter(l -> l.food().isProduce()).mapToDouble(Line::grams).sum();
            if (produce < DietOptimizer.PRODUCE_MIN_GRAMS) {
                notes.add("To stay within your budget this plan has little or no vegetables and fruit. Add some if you can.");
            }
        } else if (result.status() == Status.BUDGET_SHORT) {
            notes.add("This is the most protein your budget buys. It does not reach your targets.");
        } else {
            notes.add("Your targets cannot be reached with the foods that fit your diet and exclusions, at any budget. This is the most protein those foods give within your budget.");
        }
        long own = lines.stream().filter(l -> l.food().overridden()).count();
        if (own > 0) {
            notes.add("Your own prices were used for " + own + (own == 1 ? " food." : " foods."));
        }

        List<ArrangedMeal> arranged = null;
        String arrangedBy = "RULES";
        if (anthropic.configured()) {
            if (quota.tryConsume(userId, AiQuotaService.DIET_ARRANGE)) {
                try {
                    List<ArrangedMeal> attempt = arranger.arrange(lines, req.mealsPerDay(), cuisine);
                    if (attempt == null || attempt.size() != req.mealsPerDay()) {
                        throw new IllegalStateException("The arrangement does not have the right number of meals");
                    }
                    arranged = attempt;
                    arrangedBy = "AI";
                } catch (RuntimeException e) {
                    quota.refund(userId, AiQuotaService.DIET_ARRANGE); // nothing usable came back, so it must not use up a try
                    log.info("AI meal arrangement not used: {}", e.getClass().getSimpleName());
                    notes.add("The AI meal arrangement was not available, so the foods were split into meals by simple rules.");
                }
            } else {
                notes.add("You have used today's AI meal arrangements, so the foods were split into meals by simple rules.");
            }
        }
        if (arranged == null) {
            arranged = RulesMealArranger.arrange(lines, req.mealsPerDay());
        }

        List<NewMeal> newMeals = new ArrayList<>();
        double totalCost = 0;
        for (int m = 0; m < arranged.size(); m++) {
            List<NewItem> items = new ArrayList<>();
            for (MealLine line : arranged.get(m).lines()) {
                DietFood f = line.food();
                double factor = line.grams() / 100.0;
                double cost = round2(line.grams() * f.costPerGram());
                totalCost += cost;
                items.add(new NewItem(f.slug(), f.name(), line.grams(), cost, f.kcal() * factor, f.protein() * factor, f.carbs() * factor, f.fat() * factor));
            }
            newMeals.add(new NewMeal(m, arranged.get(m).title(), arranged.get(m).recipe(), items));
        }

        UUID id = UUID.randomUUID();
        plans.insert(
                new NewPlan(
                        id,
                        userId,
                        req.budget(),
                        currency,
                        req.dietType(),
                        req.mealsPerDay(),
                        cuisine,
                        allergyNames.stream().sorted().toList(),
                        excludedSlugs.stream().sorted().toList(),
                        target.getCalories(),
                        target.getProteinG(),
                        target.getCarbsG(),
                        target.getFatG(),
                        totalCost,
                        calories,
                        protein,
                        carbs,
                        fat,
                        feasible,
                        shortfall,
                        arrangedBy,
                        notes,
                        newMeals));
        return plans.find(userId, id).orElseThrow();
    }

    public List<PlanSummaryDto> listPlans(UUID userId) {
        return plans.list(userId);
    }

    public PlanDto getPlan(UUID userId, UUID planId) {
        return plans.find(userId, planId).orElseThrow(() -> ApiException.notFound("Plan not found"));
    }

    public void deletePlan(UUID userId, UUID planId) {
        if (!plans.softDelete(userId, planId)) {
            throw ApiException.notFound("Plan not found");
        }
    }

    /** Logs one planned meal into the food log, one entry per food, tagged as coming from the diet plan. */
    public LogMealResponse logMeal(UUID userId, UUID planId, LogMealRequest req) {
        PlanDto plan = getPlan(userId, planId);
        PlanMealDto meal =
                plan.meals().stream().filter(m -> m.index() == req.mealIndex()).findFirst().orElseThrow(() -> ApiException.notFound("That meal is not in this plan"));
        double calories = 0;
        for (PlanItemDto item : meal.items()) {
            meals.create(
                    userId,
                    new MealItemRequest(
                            null,
                            req.date(),
                            req.mealType(),
                            item.foodName(),
                            null,
                            Math.max(0.1, item.grams()),
                            item.macros().calories(),
                            item.macros().proteinG(),
                            item.macros().carbsG(),
                            item.macros().fatG(),
                            MealSource.DIET_PLAN));
            calories += item.macros().calories();
        }
        return new LogMealResponse(meal.items().size(), round1(calories));
    }

    // ------------------------------------------------------------------ helpers

    private String resolveCurrency(UUID userId, String requested) {
        String currency = requested == null || requested.isBlank() ? null : requested.trim().toUpperCase(Locale.ROOT);
        List<String> supported = foods.supportedCurrencies();
        if (currency == null) {
            // Nothing asked for: the profile currency, or USD when we have no price list for it (the list says which one it used).
            currency = profiles.findByUserIdAndDeletedAtIsNull(userId).map(p -> p.getCurrency()).orElse("USD").toUpperCase(Locale.ROOT);
            if (!supported.contains(currency) && !supported.isEmpty()) {
                currency = supported.contains("USD") ? "USD" : supported.get(0);
            }
        }
        if (!supported.contains(currency)) {
            throw ApiException.validation("currency", "There is no price list for " + currency + " yet. Choose one of: " + String.join(", ", supported) + ".");
        }
        return currency;
    }

    private DailyTarget latestTargets(UUID userId) {
        // One day ahead of UTC, so a target that starts "today" for someone far east of UTC is already visible.
        LocalDate asOf = LocalDate.now(clock.withZone(ZoneOffset.UTC)).plusDays(1);
        return targets.findFirstByUserIdAndDeletedAtIsNullAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(userId, asOf)
                .orElseThrow(() -> ApiException.validation("targets", "Set your daily calorie and protein targets first."));
    }

    private static List<Line> sorted(List<Line> lines) {
        return lines.stream()
                .sorted(
                        Comparator.comparingInt((Line l) -> categoryRank(l.food().category()))
                                .thenComparing(l -> l.food().name()))
                .toList();
    }

    private static int categoryRank(String category) {
        int i = CATEGORY_ORDER.indexOf(category);
        return i < 0 ? CATEGORY_ORDER.size() : i;
    }

    static FoodDto toDto(DietFood f) {
        return new FoodDto(
                f.id(),
                f.slug(),
                f.name(),
                f.category(),
                f.dietTags(),
                f.allergens(),
                f.kcal(),
                f.protein(),
                f.carbs(),
                f.fat(),
                f.unit(),
                f.unitGrams(),
                f.price(),
                f.defaultPrice(),
                f.overridden(),
                f.currency());
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
