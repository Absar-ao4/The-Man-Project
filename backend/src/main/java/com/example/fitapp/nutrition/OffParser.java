package com.example.fitapp.nutrition;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Maps an Open Food Facts product response (v2) to {@link FoodData} (per 100 g). */
public final class OffParser {

    private static final double KJ_PER_KCAL = 4.184;
    private static final double MAX_KCAL_PER_100G = 950;
    private static final double MAX_MACRO_PER_100G = 100;

    private OffParser() {}

    public static Optional<FoodData> parse(String barcode, JsonNode root) {
        if (root == null || root.path("status").asInt(0) != 1) {
            return Optional.empty();
        }
        JsonNode product = root.path("product");
        JsonNode nutriments = product.path("nutriments");
        if (product.isMissingNode() || nutriments.isMissingNode()) {
            return Optional.empty();
        }

        Double protein = number(nutriments, "proteins_100g");
        Double carbs = number(nutriments, "carbohydrates_100g");
        Double fat = number(nutriments, "fat_100g");
        Double kcal = number(nutriments, "energy-kcal_100g");
        if (kcal == null) {
            Double kj = number(nutriments, "energy_100g");
            if (kj != null) {
                kcal = kj / KJ_PER_KCAL;
            }
        }
        if (kcal == null && (protein != null || carbs != null || fat != null)) {
            // Atwater factors when only macros are listed.
            kcal = 4 * orZero(protein) + 4 * orZero(carbs) + 9 * orZero(fat);
        }
        if (kcal == null) {
            return Optional.empty(); // nothing usable on the label
        }
        double p = orZero(protein);
        double c = orZero(carbs);
        double f = orZero(fat);
        if (kcal < 0 || kcal > MAX_KCAL_PER_100G || p < 0 || p > MAX_MACRO_PER_100G || c < 0
                || c > MAX_MACRO_PER_100G || f < 0 || f > MAX_MACRO_PER_100G) {
            return Optional.empty();
        }

        String name = firstText(product, "product_name", "product_name_en", "generic_name");
        if (name == null) {
            name = "Product " + barcode;
        }
        String brands = firstText(product, "brands");
        String brand = brands == null ? null : brands.split(",")[0].trim();

        return Optional.of(
                new FoodData(
                        "OFF",
                        barcode,
                        truncate(name, 250),
                        brand == null || brand.isEmpty() ? null : truncate(brand, 150),
                        round2(kcal),
                        round2(p),
                        round2(c),
                        round2(f),
                        servingUnits(product)));
    }

    private static List<ServingUnit> servingUnits(JsonNode product) {
        List<ServingUnit> units = new ArrayList<>();
        units.add(new ServingUnit("g", 1.0));
        double grams = product.path("serving_quantity").asDouble(0);
        if (grams <= 0) {
            String text = product.path("serving_quantity").asText("");
            try {
                grams = text.isEmpty() ? 0 : Double.parseDouble(text);
            } catch (NumberFormatException e) {
                grams = 0;
            }
        }
        if (grams > 0 && grams <= 5000) {
            String label = firstText(product, "serving_size");
            units.add(new ServingUnit(label == null ? "1 serving" : truncate(label, 40), round2(grams)));
        }
        return List.copyOf(units);
    }

    /** OFF sometimes sends numbers as strings. */
    private static Double number(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.isNumber()) {
            return v.asDouble();
        }
        try {
            return Double.parseDouble(v.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String text = node.path(field).asText("").trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return null;
    }

    private static double orZero(Double d) {
        return d == null ? 0 : d;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
