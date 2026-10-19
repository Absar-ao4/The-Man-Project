package com.example.fitapp.nutrition;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class UsdaParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(String text) throws Exception {
        return MAPPER.readTree(text);
    }

    @Test
    void parsesAStandardSearchResult() throws Exception {
        List<FoodData> foods =
                UsdaParser.parseSearch(
                        json(
                                """
                                {"foods":[{
                                  "fdcId": 171077, "description": "Chicken, broilers or fryers, breast, cooked",
                                  "dataType": "SR Legacy",
                                  "foodNutrients": [
                                    {"nutrientId": 1003, "value": 31.02},
                                    {"nutrientId": 1004, "value": 3.57},
                                    {"nutrientId": 1005, "value": 0},
                                    {"nutrientId": 1008, "value": 165}
                                  ]}]}
                                """));

        assertThat(foods).hasSize(1);
        FoodData f = foods.get(0);
        assertThat(f.source()).isEqualTo("USDA");
        assertThat(f.externalId()).isEqualTo("171077");
        assertThat(f.foodRef()).isEqualTo("USDA:171077");
        assertThat(f.kcalPer100g()).isEqualTo(165.0);
        assertThat(f.proteinPer100g()).isEqualTo(31.02);
        assertThat(f.fatPer100g()).isEqualTo(3.57);
        assertThat(f.carbsPer100g()).isEqualTo(0.0);
        assertThat(f.servingUnits()).containsExactly(new ServingUnit("g", 1.0));
    }

    @Test
    void fallsBackToAtwaterEnergyWhenKcalIsMissing() throws Exception {
        List<FoodData> specific =
                UsdaParser.parseSearch(
                        json(
                                """
                                {"foods":[{"fdcId": 1, "description": "A", "foodNutrients": [
                                  {"nutrientId": 2048, "value": 120}, {"nutrientId": 2047, "value": 130}]}]}
                                """));
        assertThat(specific.get(0).kcalPer100g()).isEqualTo(120.0);

        List<FoodData> general =
                UsdaParser.parseSearch(
                        json(
                                """
                                {"foods":[{"fdcId": 2, "description": "B", "foodNutrients": [
                                  {"nutrientId": 2047, "value": 130}]}]}
                                """));
        assertThat(general.get(0).kcalPer100g()).isEqualTo(130.0);
    }

    @Test
    void prefersPlainEnergyOverAtwater() throws Exception {
        List<FoodData> foods =
                UsdaParser.parseSearch(
                        json(
                                """
                                {"foods":[{"fdcId": 3, "description": "C", "foodNutrients": [
                                  {"nutrientId": 2047, "value": 999}, {"nutrientId": 1008, "value": 100}]}]}
                                """));
        assertThat(foods.get(0).kcalPer100g()).isEqualTo(100.0);
    }

    @Test
    void understandsTheDetailStyleNutrientShape() throws Exception {
        List<FoodData> foods =
                UsdaParser.parseSearch(
                        json(
                                """
                                {"foods":[{"fdcId": 4, "description": "D", "foodNutrients": [
                                  {"nutrient": {"id": 1008}, "amount": 52},
                                  {"nutrient": {"id": 1003}, "amount": 0.26}]}]}
                                """));
        assertThat(foods).hasSize(1);
        assertThat(foods.get(0).kcalPer100g()).isEqualTo(52.0);
        assertThat(foods.get(0).proteinPer100g()).isEqualTo(0.26);
    }

    @Test
    void skipsFoodsWithoutEnergyOrIdentity() throws Exception {
        List<FoodData> foods =
                UsdaParser.parseSearch(
                        json(
                                """
                                {"foods":[
                                  {"fdcId": 5, "description": "No energy", "foodNutrients": [{"nutrientId": 1003, "value": 5}]},
                                  {"description": "No id", "foodNutrients": [{"nutrientId": 1008, "value": 50}]},
                                  {"fdcId": 6, "description": "  ", "foodNutrients": [{"nutrientId": 1008, "value": 50}]}
                                ]}
                                """));
        assertThat(foods).isEmpty();
    }

    @Test
    void rejectsImplausibleValues() throws Exception {
        List<FoodData> foods =
                UsdaParser.parseSearch(
                        json(
                                """
                                {"foods":[
                                  {"fdcId": 7, "description": "Too many kcal", "foodNutrients": [{"nutrientId": 1008, "value": 5000}]},
                                  {"fdcId": 8, "description": "Too much protein", "foodNutrients": [
                                    {"nutrientId": 1008, "value": 400}, {"nutrientId": 1003, "value": 400}]},
                                  {"fdcId": 9, "description": "Negative", "foodNutrients": [{"nutrientId": 1008, "value": -5}]}
                                ]}
                                """));
        assertThat(foods).isEmpty();
    }

    @Test
    void collectsServingUnitsFromMeasuresAndPortions() throws Exception {
        List<FoodData> foods =
                UsdaParser.parseSearch(
                        json(
                                """
                                {"foods":[{"fdcId": 10, "description": "Rice", "foodNutrients": [{"nutrientId": 1008, "value": 130}],
                                  "foodMeasures": [
                                    {"disseminationText": "1 cup", "gramWeight": 158},
                                    {"disseminationText": "Quantity not specified", "gramWeight": 0}],
                                  "foodPortions": [
                                    {"amount": 1, "modifier": "tbsp", "gramWeight": 10},
                                    {"amount": 2, "measureUnit": {"name": "slice"}, "gramWeight": 56},
                                    {"amount": 1, "measureUnit": {"name": "undetermined"}, "gramWeight": 5}]}]}
                                """));
        assertThat(foods.get(0).servingUnits())
                .containsExactly(
                        new ServingUnit("g", 1.0),
                        new ServingUnit("1 cup", 158.0),
                        new ServingUnit("1 tbsp", 10.0),
                        new ServingUnit("2 slice", 56.0));
    }

    @Test
    void readsTheBrandWhenPresent() throws Exception {
        List<FoodData> foods =
                UsdaParser.parseSearch(
                        json(
                                """
                                {"foods":[{"fdcId": 11, "description": "Cereal", "brandOwner": "Acme Foods",
                                  "foodNutrients": [{"nutrientId": 1008, "value": 380}]}]}
                                """));
        assertThat(foods.get(0).brand()).isEqualTo("Acme Foods");
    }

    @Test
    void toleratesMissingOrEmptyInput() throws Exception {
        assertThat(UsdaParser.parseSearch(null)).isEmpty();
        assertThat(UsdaParser.parseSearch(json("{}"))).isEmpty();
        assertThat(UsdaParser.parseSearch(json("{\"foods\": []}"))).isEmpty();
        assertThat(UsdaParser.parseSearch(json("{\"foods\": \"nope\"}"))).isEmpty();
    }
}
