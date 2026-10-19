package com.example.fitapp.diet;

import com.example.fitapp.diet.DietDtos.MacroDto;
import com.example.fitapp.diet.DietDtos.PlanDto;
import com.example.fitapp.diet.DietDtos.PlanItemDto;
import com.example.fitapp.diet.DietDtos.PlanMealDto;
import com.example.fitapp.diet.DietDtos.PlanSummaryDto;
import com.example.fitapp.diet.DietDtos.ShortfallDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Saved diet plans. Plans belong to one user: every query is scoped by the user id, and deleted plans are hidden. */
@Repository
public class DietPlanRepository {

    public record NewItem(String slug, String name, double grams, double cost, double calories, double protein, double carbs, double fat) {}

    public record NewMeal(int index, String title, String recipe, List<NewItem> items) {}

    public record NewPlan(
            UUID id,
            UUID userId,
            BigDecimal budget,
            String currency,
            DietType dietType,
            int mealsPerDay,
            Cuisine cuisine,
            List<String> allergies,
            List<String> excludedSlugs,
            int targetCalories,
            int targetProtein,
            int targetCarbs,
            int targetFat,
            double totalCost,
            double totalCalories,
            double totalProtein,
            double totalCarbs,
            double totalFat,
            boolean feasible,
            ShortfallDto shortfall,
            String arrangedBy,
            List<String> notes,
            List<NewMeal> meals) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public DietPlanRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public void insert(NewPlan p) {
        jdbc.update(
                "insert into diet_plans (id, user_id, budget, currency, diet_type, meals_per_day, cuisine, allergies, excluded_foods,"
                        + " target_calories, target_protein_g, target_carbs_g, target_fat_g, total_cost, total_calories, total_protein_g,"
                        + " total_carbs_g, total_fat_g, feasible, shortfall, arranged_by, notes)"
                        + " values (?, ?, ?, ?, ?, ?, ?, string_to_array(?, ','), string_to_array(?, ','), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?::jsonb)",
                p.id(),
                p.userId(),
                p.budget(),
                p.currency(),
                p.dietType().name(),
                p.mealsPerDay(),
                p.cuisine().name(),
                String.join(",", p.allergies()),
                String.join(",", p.excludedSlugs()),
                p.targetCalories(),
                p.targetProtein(),
                p.targetCarbs(),
                p.targetFat(),
                round2(p.totalCost()),
                round1(p.totalCalories()),
                round1(p.totalProtein()),
                round1(p.totalCarbs()),
                round1(p.totalFat()),
                p.feasible(),
                json(p.shortfall()),
                p.arrangedBy(),
                json(p.notes()));
        List<Object[]> items = new ArrayList<>();
        for (NewMeal meal : p.meals()) {
            jdbc.update(
                    "insert into diet_plan_meals (plan_id, meal_index, title, recipe_markdown) values (?, ?, ?, ?)",
                    p.id(),
                    meal.index(),
                    meal.title(),
                    meal.recipe());
            int n = 0;
            for (NewItem item : meal.items()) {
                items.add(
                        new Object[] {
                            UUID.randomUUID(),
                            p.id(),
                            meal.index(),
                            n++,
                            item.slug(),
                            item.name(),
                            round1(item.grams()),
                            round2(item.cost()),
                            round1(item.calories()),
                            round1(item.protein()),
                            round1(item.carbs()),
                            round1(item.fat())
                        });
            }
        }
        jdbc.batchUpdate(
                "insert into diet_plan_items (id, plan_id, meal_index, item_index, food_slug, food_name, grams, cost, calories,"
                        + " protein_g, carbs_g, fat_g) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                items);
    }

    public List<PlanSummaryDto> list(UUID userId) {
        return jdbc.query(
                "select id, created_at, budget, currency, diet_type, meals_per_day, total_cost, feasible, total_calories, total_protein_g"
                        + " from diet_plans where user_id = ? and deleted_at is null order by created_at desc limit 50",
                (rs, i) ->
                        new PlanSummaryDto(
                                rs.getObject("id", UUID.class),
                                rs.getTimestamp("created_at").toInstant(),
                                rs.getBigDecimal("budget").doubleValue(),
                                rs.getString("currency").trim(),
                                DietType.valueOf(rs.getString("diet_type")),
                                rs.getInt("meals_per_day"),
                                rs.getBigDecimal("total_cost").doubleValue(),
                                rs.getBoolean("feasible"),
                                rs.getBigDecimal("total_calories").doubleValue(),
                                rs.getBigDecimal("total_protein_g").doubleValue()),
                userId);
    }

    public Optional<PlanDto> find(UUID userId, UUID planId) {
        List<PlanDto> rows =
                jdbc.query(
                        "select id, created_at, budget, currency, diet_type, meals_per_day, cuisine, target_calories, target_protein_g,"
                                + " target_carbs_g, target_fat_g, total_cost, total_calories, total_protein_g, total_carbs_g, total_fat_g,"
                                + " feasible, shortfall::text as shortfall, arranged_by, notes::text as notes"
                                + " from diet_plans where id = ? and user_id = ? and deleted_at is null",
                        (rs, i) ->
                                new PlanDto(
                                        rs.getObject("id", UUID.class),
                                        rs.getTimestamp("created_at").toInstant(),
                                        rs.getBigDecimal("budget").doubleValue(),
                                        rs.getString("currency").trim(),
                                        DietType.valueOf(rs.getString("diet_type")),
                                        rs.getInt("meals_per_day"),
                                        Cuisine.valueOf(rs.getString("cuisine")),
                                        rs.getBigDecimal("total_cost").doubleValue(),
                                        rs.getBoolean("feasible"),
                                        readShortfall(rs.getString("shortfall")),
                                        new MacroDto(rs.getInt("target_calories"), rs.getInt("target_protein_g"), rs.getInt("target_carbs_g"), rs.getInt("target_fat_g")),
                                        new MacroDto(
                                                rs.getBigDecimal("total_calories").doubleValue(),
                                                rs.getBigDecimal("total_protein_g").doubleValue(),
                                                rs.getBigDecimal("total_carbs_g").doubleValue(),
                                                rs.getBigDecimal("total_fat_g").doubleValue()),
                                        rs.getString("arranged_by"),
                                        readNotes(rs.getString("notes")),
                                        List.of()),
                        planId,
                        userId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        PlanDto base = rows.get(0);
        return Optional.of(
                new PlanDto(
                        base.id(),
                        base.createdAt(),
                        base.budget(),
                        base.currency(),
                        base.dietType(),
                        base.mealsPerDay(),
                        base.cuisine(),
                        base.totalCost(),
                        base.feasible(),
                        base.shortfall(),
                        base.targets(),
                        base.totals(),
                        base.arrangedBy(),
                        base.notes(),
                        meals(planId)));
    }

    /** True if a plan was hidden. */
    public boolean softDelete(UUID userId, UUID planId) {
        return jdbc.update("update diet_plans set deleted_at = ? where id = ? and user_id = ? and deleted_at is null", java.sql.Timestamp.from(Instant.now()), planId, userId) == 1;
    }

    // ------------------------------------------------------------------ reading meals

    private List<PlanMealDto> meals(UUID planId) {
        record MealRow(int index, String title, String recipe) {}
        List<MealRow> rows =
                jdbc.query(
                        "select meal_index, title, recipe_markdown from diet_plan_meals where plan_id = ? order by meal_index",
                        (rs, i) -> new MealRow(rs.getInt("meal_index"), rs.getString("title"), rs.getString("recipe_markdown")),
                        planId);
        TreeMap<Integer, List<PlanItemDto>> items = new TreeMap<>();
        jdbc.query(
                "select meal_index, food_slug, food_name, grams, cost, calories, protein_g, carbs_g, fat_g from diet_plan_items"
                        + " where plan_id = ? order by meal_index, item_index",
                rs -> {
                    items.computeIfAbsent(rs.getInt("meal_index"), k -> new ArrayList<>())
                            .add(
                                    new PlanItemDto(
                                            rs.getString("food_name"),
                                            rs.getString("food_slug"),
                                            rs.getBigDecimal("grams").doubleValue(),
                                            rs.getBigDecimal("cost").doubleValue(),
                                            new MacroDto(
                                                    rs.getBigDecimal("calories").doubleValue(),
                                                    rs.getBigDecimal("protein_g").doubleValue(),
                                                    rs.getBigDecimal("carbs_g").doubleValue(),
                                                    rs.getBigDecimal("fat_g").doubleValue())));
                },
                planId);
        List<PlanMealDto> meals = new ArrayList<>();
        for (MealRow row : rows) {
            List<PlanItemDto> mealItems = items.getOrDefault(row.index(), List.of());
            double cost = mealItems.stream().mapToDouble(PlanItemDto::cost).sum();
            meals.add(
                    new PlanMealDto(
                            row.index(),
                            row.title(),
                            row.recipe(),
                            round2(cost),
                            new MacroDto(
                                    round1(mealItems.stream().mapToDouble(i -> i.macros().calories()).sum()),
                                    round1(mealItems.stream().mapToDouble(i -> i.macros().proteinG()).sum()),
                                    round1(mealItems.stream().mapToDouble(i -> i.macros().carbsG()).sum()),
                                    round1(mealItems.stream().mapToDouble(i -> i.macros().fatG()).sum())),
                            mealItems));
        }
        return meals;
    }

    // ------------------------------------------------------------------ json

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not store the plan details", e);
        }
    }

    private ShortfallDto readShortfall(String text) {
        if (text == null) {
            return null;
        }
        try {
            return mapper.readValue(text, ShortfallDto.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private List<String> readNotes(String text) {
        if (text == null) {
            return List.of();
        }
        try {
            return mapper.readValue(text, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
