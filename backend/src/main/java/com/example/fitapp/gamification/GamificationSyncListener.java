package com.example.fitapp.gamification;

import com.example.fitapp.nutrition.MealItemRepository;
import com.example.fitapp.nutrition.WaterLogRepository;
import com.example.fitapp.sync.SyncListener;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * After a push, pays XP for what was just stored: finished workouts, and nutrition or water targets
 * for the days the pushed rows belong to. The result goes back to the app in the push response so it
 * can celebrate.
 */
@Component
public class GamificationSyncListener implements SyncListener {

    private final GamificationService gamification;
    private final MealItemRepository meals;
    private final WaterLogRepository water;

    public GamificationSyncListener(
            GamificationService gamification, MealItemRepository meals, WaterLogRepository water) {
        this.gamification = gamification;
        this.meals = meals;
        this.water = water;
    }

    @Override
    public void onPushed(UUID userId, String entity, List<UUID> acceptedIds, Collector out) {
        switch (entity) {
            case "workout_sessions" -> gamification.awardSessions(userId, acceptedIds, out);
            case "meal_items" -> {
                Set<LocalDate> days = new HashSet<>();
                meals.findAllById(acceptedIds).stream()
                        .filter(m -> userId.equals(m.getUserId()))
                        .forEach(m -> days.add(m.getDate()));
                gamification.awardNutritionDays(userId, days, out);
            }
            case "water_logs" -> {
                Set<LocalDate> days = new HashSet<>();
                water.findAllById(acceptedIds).stream()
                        .filter(w -> userId.equals(w.getUserId()))
                        .forEach(w -> days.add(w.getDate()));
                gamification.awardNutritionDays(userId, days, out);
            }
            case "weight_entries" -> gamification.awardWeightLogs(userId, acceptedIds, out);
            default -> {
                // nothing else earns XP
            }
        }
    }
}
