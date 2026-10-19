package com.example.fitapp.nutrition;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.nutrition.dto.NutritionDtos.DaySummary;
import com.example.fitapp.nutrition.dto.NutritionDtos.NutritionSummary;
import com.example.fitapp.profile.ProfileService;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NutritionSummaryService {

    static final int MAX_RANGE_DAYS = 366;

    private final MealItemRepository meals;
    private final WaterLogRepository water;
    private final ProfileService profiles;

    public NutritionSummaryService(
            MealItemRepository meals, WaterLogRepository water, ProfileService profiles) {
        this.meals = meals;
        this.water = water;
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public NutritionSummary summarize(UUID userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw ApiException.validation("to", "must not be before from");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw ApiException.validation("to", "range is limited to " + MAX_RANGE_DAYS + " days");
        }

        Map<LocalDate, DayTotals> foodByDay = new TreeMap<>();
        meals.totalsByDay(userId, from, to).forEach(t -> foodByDay.put(t.date(), t));
        Map<LocalDate, Integer> waterByDay = new TreeMap<>();
        water.findByUserIdAndDateBetweenAndDeletedAtIsNull(userId, from, to)
                .forEach(w -> waterByDay.put(w.getDate(), w.getGlasses()));

        List<LocalDate> dates = new ArrayList<>(foodByDay.keySet());
        waterByDay.keySet().stream().filter(d -> !foodByDay.containsKey(d)).forEach(dates::add);
        dates.sort(LocalDate::compareTo);

        List<DaySummary> days = new ArrayList<>();
        for (LocalDate date : dates) {
            DayTotals t = foodByDay.get(date);
            days.add(
                    new DaySummary(
                            date,
                            t == null ? 0 : round2(t.calories()),
                            t == null ? 0 : round2(t.proteinG()),
                            t == null ? 0 : round2(t.carbsG()),
                            t == null ? 0 : round2(t.fatG()),
                            waterByDay.getOrDefault(date, 0),
                            t == null ? 0 : t.items().intValue()));
        }

        List<DaySummary> withFood = days.stream().filter(d -> d.itemCount() > 0).toList();
        return new NutritionSummary(
                from,
                to,
                profiles.findCurrentTargets(userId),
                days,
                average(withFood, DaySummary::calories),
                average(withFood, DaySummary::proteinG),
                average(withFood, DaySummary::carbsG),
                average(withFood, DaySummary::fatG));
    }

    private static Double average(
            List<DaySummary> days, java.util.function.ToDoubleFunction<DaySummary> field) {
        if (days.isEmpty()) {
            return null;
        }
        return round2(days.stream().mapToDouble(field).average().orElse(0));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
