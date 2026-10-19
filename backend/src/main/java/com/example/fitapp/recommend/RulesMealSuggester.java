package com.example.fitapp.recommend;

import com.example.fitapp.diet.DietFood;
import com.example.fitapp.nutrition.MealType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Meal ideas without the AI: a protein food, a carbohydrate food and some vegetables or fruit, in quantities that fit what
 * is left of the day. Deterministic, so the same day gives the same ideas.
 */
public final class RulesMealSuggester {

    /** One food and how much of it. */
    public record Line(DietFood food, double grams) {}

    /** A suggested meal. */
    public record Suggestion(String title, String description, List<Line> lines) {}

    /** What is left of the day. */
    public record Remaining(double calories, double protein, double carbs, double fat) {}

    static final double PRODUCE_GRAMS = 100;
    static final double MIN_PROTEIN_PER_100G = 12;
    /** Oats are 13 percent protein but they are the carbohydrate of a meal, and powders are not a meal. */
    static final Set<String> NOT_A_MAIN_PROTEIN = Set.of("GRAIN", "SUPPLEMENT");

    private RulesMealSuggester() {}

    /** About how many kcal a meal of this kind is. */
    static double typicalKcal(MealType type) {
        if (type == null) {
            return 600;
        }
        return switch (type) {
            case BREAKFAST -> 500;
            case LUNCH, DINNER -> 700;
            case SNACK -> 250;
        };
    }

    static boolean fits(DietFood food, MealType type) {
        String hint = food.mealHint();
        if (type == null || type == MealType.LUNCH || type == MealType.DINNER) {
            return "ANY".equals(hint) || "MAIN".equals(hint);
        }
        if (type == MealType.BREAKFAST) {
            return "ANY".equals(hint) || "BREAKFAST".equals(hint);
        }
        return "ANY".equals(hint) || "SNACK".equals(hint) || "BREAKFAST".equals(hint);
    }

    public static List<Suggestion> suggest(List<DietFood> pool, Remaining remaining, MealType type, int count) {
        if (remaining.calories() < 1) {
            return List.of();
        }
        double mealKcal = Math.min(remaining.calories(), typicalKcal(type));
        double mealProtein = remaining.protein() * (mealKcal / remaining.calories());

        List<DietFood> proteins =
                pool.stream()
                        .filter(f -> fits(f, type))
                        .filter(f -> f.protein() >= MIN_PROTEIN_PER_100G && f.kcal() < 500 && !NOT_A_MAIN_PROTEIN.contains(f.category()))
                        .sorted(Comparator.comparingDouble((DietFood f) -> -f.protein() / Math.max(1, f.kcal())).thenComparing(DietFood::slug))
                        .toList();
        List<DietFood> carbs =
                pool.stream()
                        .filter(f -> fits(f, type) && "GRAIN".equals(f.category()))
                        .sorted(Comparator.comparing(DietFood::slug))
                        .toList();
        List<DietFood> produce = pool.stream().filter(DietFood::isProduce).sorted(Comparator.comparing(DietFood::slug)).toList();

        List<Suggestion> result = new ArrayList<>();
        Set<Set<String>> seen = new HashSet<>();
        if (proteins.isEmpty()) {
            return result;
        }
        for (int v = 0; v < proteins.size() && result.size() < count; v++) {
            DietFood protein = proteins.get(v);
            DietFood carb = carbs.isEmpty() ? null : carbs.get(v % carbs.size());
            DietFood veg = produce.isEmpty() ? null : produce.get(v % produce.size());

            List<Line> lines = new ArrayList<>();
            double kcal = 0;
            double gramsProtein = round(clamp(mealProtein * 0.7 / (protein.protein() / 100.0), 30, protein.maxGrams()), protein.stepGrams());
            lines.add(new Line(protein, gramsProtein));
            kcal += gramsProtein * protein.kcal() / 100.0;
            if (veg != null) {
                double gramsVeg = round(Math.min(PRODUCE_GRAMS, veg.maxGrams()), veg.stepGrams());
                lines.add(new Line(veg, gramsVeg));
                kcal += gramsVeg * veg.kcal() / 100.0;
            }
            if (carb != null && mealKcal - kcal > 40) {
                double gramsCarb = round(clamp((mealKcal - kcal) / (carb.kcal() / 100.0), 0, carb.maxGrams()), carb.stepGrams());
                if (gramsCarb > 0) {
                    lines.add(new Line(carb, gramsCarb));
                }
            }
            Set<String> key = new HashSet<>();
            lines.forEach(l -> key.add(l.food().slug()));
            if (!seen.add(key)) {
                continue;
            }
            result.add(new Suggestion(title(protein, carb), "Weights are raw or uncooked. Cook it the way you like it.", lines));
        }
        return result;
    }

    private static String title(DietFood protein, DietFood carb) {
        String main = shortName(protein.name());
        return carb == null ? main : main + " with " + shortName(carb.name()).toLowerCase();
    }

    /** "Chicken breast, raw" becomes "Chicken breast". */
    static String shortName(String name) {
        int comma = name.indexOf(',');
        int paren = name.indexOf('(');
        int cut = name.length();
        if (comma > 0) {
            cut = Math.min(cut, comma);
        }
        if (paren > 0) {
            cut = Math.min(cut, paren);
        }
        return name.substring(0, cut).trim();
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** To the nearest buyable step, at least one step. */
    static double round(double grams, double step) {
        double s = step <= 0 ? 5 : step;
        return Math.max(s, Math.round(grams / s) * s);
    }
}
