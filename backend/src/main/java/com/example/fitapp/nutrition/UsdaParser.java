package com.example.fitapp.nutrition;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps a USDA FoodData Central search response to {@link FoodData} (per 100 g).
 *
 * <p>Nutrient ids: energy 1008 (kcal), with 2048 and 2047 (Atwater energy) as fallbacks for foods
 * that only report those; protein 1003, fat 1004, carbohydrate 1005.
 */
public final class UsdaParser {

    static final int ENERGY_KCAL = 1008;
    static final int ENERGY_ATWATER_SPECIFIC = 2048;
    static final int ENERGY_ATWATER_GENERAL = 2047;
    static final int PROTEIN = 1003;
    static final int FAT = 1004;
    static final int CARBS = 1005;

    private static final double MAX_KCAL_PER_100G = 950; // pure fat is about 900
    private static final double MAX_MACRO_PER_100G = 100;
    private static final int MAX_SERVING_UNITS = 8;
    private static final int MAX_NAME = 250;

    private UsdaParser() {}

    public static List<FoodData> parseSearch(JsonNode root) {
        List<FoodData> result = new ArrayList<>();
        JsonNode foods = root == null ? null : root.path("foods");
        if (foods == null || !foods.isArray()) {
            return result;
        }
        for (JsonNode food : foods) {
            FoodData parsed = parseFood(food);
            if (parsed != null) {
                result.add(parsed);
            }
        }
        return result;
    }

    static FoodData parseFood(JsonNode food) {
        long fdcId = food.path("fdcId").asLong(0);
        String description = food.path("description").asText("").trim();
        if (fdcId <= 0 || description.isEmpty()) {
            return null;
        }

        Map<Integer, Double> nutrients = new LinkedHashMap<>();
        for (JsonNode n : food.path("foodNutrients")) {
            int id = n.has("nutrientId") ? n.path("nutrientId").asInt(0) : n.path("nutrient").path("id").asInt(0);
            JsonNode amount = n.has("value") ? n.get("value") : n.get("amount");
            if (id != 0 && amount != null && amount.isNumber()) {
                nutrients.putIfAbsent(id, amount.asDouble());
            }
        }

        Double kcal = firstPresent(nutrients, ENERGY_KCAL, ENERGY_ATWATER_SPECIFIC, ENERGY_ATWATER_GENERAL);
        if (kcal == null) {
            return null; // without energy a food cannot be logged sensibly
        }
        double protein = nutrients.getOrDefault(PROTEIN, 0.0);
        double carbs = nutrients.getOrDefault(CARBS, 0.0);
        double fat = nutrients.getOrDefault(FAT, 0.0);
        if (!plausible(kcal, protein, carbs, fat)) {
            return null;
        }

        String brand = textOrNull(food.path("brandOwner"));
        if (brand == null) {
            brand = textOrNull(food.path("brandName"));
        }
        return new FoodData(
                "USDA",
                Long.toString(fdcId),
                truncate(description, MAX_NAME),
                brand,
                round2(kcal),
                round2(protein),
                round2(carbs),
                round2(fat),
                servingUnits(food));
    }

    private static List<ServingUnit> servingUnits(JsonNode food) {
        Map<String, ServingUnit> units = new LinkedHashMap<>();
        units.put("g", new ServingUnit("g", 1.0));

        for (JsonNode m : food.path("foodMeasures")) {
            double grams = m.path("gramWeight").asDouble(0);
            String label = textOrNull(m.path("disseminationText"));
            addUnit(units, label, grams);
        }
        for (JsonNode p : food.path("foodPortions")) {
            double grams = p.path("gramWeight").asDouble(0);
            double amount = p.path("amount").asDouble(1);
            String name = textOrNull(p.path("modifier"));
            if (name == null) {
                name = textOrNull(p.path("measureUnit").path("name"));
            }
            if (name != null && !"undetermined".equalsIgnoreCase(name)) {
                addUnit(units, trimAmount(amount) + " " + name, grams);
            }
        }
        return List.copyOf(units.values());
    }

    private static void addUnit(Map<String, ServingUnit> units, String label, double grams) {
        if (label == null || grams <= 0 || grams > 5000 || units.size() >= MAX_SERVING_UNITS) {
            return;
        }
        units.putIfAbsent(label.toLowerCase(), new ServingUnit(truncate(label, 40), round2(grams)));
    }

    private static String trimAmount(double amount) {
        return amount == Math.rint(amount) ? Long.toString((long) amount) : Double.toString(amount);
    }

    private static boolean plausible(double kcal, double protein, double carbs, double fat) {
        return kcal >= 0
                && kcal <= MAX_KCAL_PER_100G
                && protein >= 0
                && protein <= MAX_MACRO_PER_100G
                && carbs >= 0
                && carbs <= MAX_MACRO_PER_100G
                && fat >= 0
                && fat <= MAX_MACRO_PER_100G;
    }

    private static Double firstPresent(Map<Integer, Double> values, int... ids) {
        for (int id : ids) {
            if (values.containsKey(id)) {
                return values.get(id);
            }
        }
        return null;
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText("").trim();
        return text.isEmpty() ? null : text;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
