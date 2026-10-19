package com.example.fitapp.nutrition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OffParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Optional<FoodData> parse(String text) throws Exception {
        JsonNode root = MAPPER.readTree(text);
        return OffParser.parse("3017620422003", root);
    }

    @Test
    void parsesAFullProduct() throws Exception {
        FoodData food =
                parse(
                                """
                                {"status": 1, "product": {
                                  "product_name": "Hazelnut spread", "brands": "Acme, Other",
                                  "serving_size": "15 g", "serving_quantity": 15,
                                  "nutriments": {"energy-kcal_100g": 539, "proteins_100g": 6.3,
                                                 "carbohydrates_100g": 57.5, "fat_100g": 30.9}}}
                                """)
                        .orElseThrow();

        assertThat(food.source()).isEqualTo("OFF");
        assertThat(food.foodRef()).isEqualTo("OFF:3017620422003");
        assertThat(food.name()).isEqualTo("Hazelnut spread");
        assertThat(food.brand()).isEqualTo("Acme");
        assertThat(food.kcalPer100g()).isEqualTo(539.0);
        assertThat(food.proteinPer100g()).isEqualTo(6.3);
        assertThat(food.servingUnits())
                .containsExactly(new ServingUnit("g", 1.0), new ServingUnit("15 g", 15.0));
    }

    @Test
    void unknownProductIsEmpty() throws Exception {
        assertThat(parse("{\"status\": 0, \"status_verbose\": \"product not found\"}")).isEmpty();
        assertThat(OffParser.parse("1", null)).isEmpty();
    }

    @Test
    void convertsKilojoulesWhenKcalIsMissing() throws Exception {
        FoodData food =
                parse(
                                """
                                {"status": 1, "product": {"product_name": "X",
                                  "nutriments": {"energy_100g": 1000, "proteins_100g": 5}}}
                                """)
                        .orElseThrow();
        assertThat(food.kcalPer100g()).isCloseTo(239.01, within(0.01));
    }

    @Test
    void estimatesEnergyFromMacrosWhenNoEnergyIsListed() throws Exception {
        FoodData food =
                parse(
                                """
                                {"status": 1, "product": {"product_name": "X",
                                  "nutriments": {"proteins_100g": 10, "carbohydrates_100g": 20, "fat_100g": 5}}}
                                """)
                        .orElseThrow();
        assertThat(food.kcalPer100g()).isEqualTo(4 * 10 + 4 * 20 + 9 * 5.0);
    }

    @Test
    void acceptsNumbersSentAsStrings() throws Exception {
        FoodData food =
                parse(
                                """
                                {"status": 1, "product": {"product_name": "X", "serving_quantity": "30",
                                  "nutriments": {"energy-kcal_100g": "250", "proteins_100g": "8.5"}}}
                                """)
                        .orElseThrow();
        assertThat(food.kcalPer100g()).isEqualTo(250.0);
        assertThat(food.proteinPer100g()).isEqualTo(8.5);
        assertThat(food.servingUnits()).contains(new ServingUnit("1 serving", 30.0));
    }

    @Test
    void fallsBackToABarcodeNameAndSkipsProductsWithNoNutrition() throws Exception {
        FoodData unnamed =
                parse("{\"status\": 1, \"product\": {\"nutriments\": {\"energy-kcal_100g\": 100}}}")
                        .orElseThrow();
        assertThat(unnamed.name()).isEqualTo("Product 3017620422003");
        assertThat(unnamed.brand()).isNull();

        assertThat(parse("{\"status\": 1, \"product\": {\"product_name\": \"Empty\", \"nutriments\": {}}}"))
                .isEmpty();
    }

    @Test
    void rejectsImplausibleLabels() throws Exception {
        assertThat(
                        parse(
                                "{\"status\": 1, \"product\": {\"product_name\": \"Bad\","
                                        + " \"nutriments\": {\"energy-kcal_100g\": 9000}}}"))
                .isEmpty();
    }
}
