package com.example.fitapp.diet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.diet.DietOptimizer.Line;
import com.example.fitapp.diet.MealArranger.ArrangedMeal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MealArrangersTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static DietFood food(String slug, String hint, double kcal) {
        return new DietFood(
                UUID.randomUUID(), slug, slug, "GRAIN", List.of("VEGAN"), List.of(), kcal, 5, 50, 2, "kg", 1000, 50, 50, false, "INR", 50, 6, 5, hint);
    }

    private final DietFood oats = food("oats", "BREAKFAST", 380);
    private final DietFood rice = food("rice", "MAIN", 360);
    private final DietFood whey = food("whey", "SNACK", 400);
    private final DietFood oil = food("oil", "ANY", 884);
    private final List<Line> plan = List.of(new Line(oats, 60), new Line(rice, 300), new Line(whey, 30), new Line(oil, 30));

    private static Map<String, Double> totals(List<ArrangedMeal> meals) {
        return meals.stream()
                .flatMap(m -> m.lines().stream())
                .collect(Collectors.groupingBy(l -> l.food().slug(), Collectors.summingDouble(l -> l.grams())));
    }

    // ------------------------------------------------------------ rules

    @Test
    void theRulesArrangementKeepsEveryFoodsTotalForAnyMealCount() {
        for (int meals = 2; meals <= 6; meals++) {
            List<ArrangedMeal> arranged = RulesMealArranger.arrange(plan, meals);

            assertThat(arranged).hasSize(meals);
            assertThat(arranged).allSatisfy(m -> assertThat(m.lines()).isNotEmpty());
            Map<String, Double> t = totals(arranged);
            assertThat(t.get("oats")).isCloseTo(60, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(t.get("rice")).isCloseTo(300, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(t.get("whey")).isCloseTo(30, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(t.get("oil")).isCloseTo(30, org.assertj.core.data.Offset.offset(1e-9));
        }
    }

    @Test
    void foodsGoToTheMealsThatSuitThem() {
        List<ArrangedMeal> arranged = RulesMealArranger.arrange(plan, 4); // Breakfast, Lunch, Snack, Dinner

        assertThat(arranged.stream().map(ArrangedMeal::title).toList()).containsExactly("Breakfast", "Lunch", "Snack", "Dinner");
        assertThat(slugs(arranged.get(0))).contains("oats").doesNotContain("rice", "whey");
        assertThat(slugs(arranged.get(1))).contains("rice");
        assertThat(slugs(arranged.get(2))).contains("whey");
        assertThat(slugs(arranged.get(3))).contains("rice");
        // The oil suits any meal, so it is spread over all four.
        assertThat(arranged).allSatisfy(m -> assertThat(slugs(m)).contains("oil"));
    }

    @Test
    void withTwoMealsBreakfastFoodsOpenTheDayAndSnacksCloseIt() {
        List<ArrangedMeal> arranged = RulesMealArranger.arrange(plan, 2);

        assertThat(slugs(arranged.get(0))).contains("oats");
        assertThat(slugs(arranged.get(1))).contains("whey");
    }

    @Test
    void fewFoodsAndManyMealsStillGiveNoEmptyMeal() {
        List<ArrangedMeal> arranged = RulesMealArranger.arrange(List.of(new Line(rice, 400)), 6);

        assertThat(arranged).hasSize(6).allSatisfy(m -> assertThat(m.lines()).isNotEmpty());
        assertThat(totals(arranged).get("rice")).isCloseTo(400, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void anOutOfRangeMealCountIsClamped() {
        assertThat(RulesMealArranger.arrange(plan, 1)).hasSize(2);
        assertThat(RulesMealArranger.arrange(plan, 9)).hasSize(6);
    }

    private static List<String> slugs(ArrangedMeal meal) {
        return meal.lines().stream().map(l -> l.food().slug()).toList();
    }

    // ------------------------------------------------------------ the AI answer

    private List<ArrangedMeal> parse(String json, int meals) throws Exception {
        return AiMealArranger.parse(mapper.readTree(json), plan, meals);
    }

    private static final String GOOD =
            """
            {"meals":[
              {"title":"Breakfast","recipe":"Cook the oats with water.","items":[{"ref":"f1","grams":60},{"ref":"f4","grams":10}]},
              {"title":"Lunch","recipe":"Boil the rice.","items":[{"ref":"f2","grams":150},{"ref":"f4","grams":10}]},
              {"title":"Dinner","recipe":"Boil the rice and add a shake.","items":[{"ref":"f2","grams":150},{"ref":"f3","grams":30},{"ref":"f4","grams":10}]}
            ]}
            """;

    @Test
    void aGoodAnswerIsAccepted() throws Exception {
        List<ArrangedMeal> meals = parse(GOOD, 3);

        assertThat(meals).hasSize(3);
        assertThat(meals.get(0).title()).isEqualTo("Breakfast");
        assertThat(meals.get(0).recipe()).isEqualTo("Cook the oats with water.");
        assertThat(totals(meals).get("rice")).isCloseTo(300, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(totals(meals).get("oil")).isCloseTo(30, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void quantitiesAreScaledToTheExactTotals() throws Exception {
        // The model's rice adds up to 330 g instead of 300 g (a 10 percent drift): scaled back to exactly 300 g.
        String json = GOOD.replace("\"grams\":150", "\"grams\":165");

        List<ArrangedMeal> meals = parse(json, 3);

        assertThat(totals(meals).get("rice")).isCloseTo(300, org.assertj.core.data.Offset.offset(0.05));
    }

    @Test
    void anAnswerThatChangesQuantitiesTooMuchIsRefused() {
        String json = GOOD.replace("\"grams\":150", "\"grams\":300"); // double the rice

        assertThatThrownBy(() -> parse(json, 3)).isInstanceOf(ApiException.class);
    }

    @Test
    void aWrongMealCountIsRefused() {
        assertThatThrownBy(() -> parse(GOOD, 4)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> parse(GOOD, 2)).isInstanceOf(ApiException.class);
    }

    @Test
    void aMissingFoodIsRefused() {
        String json = GOOD.replace("{\"ref\":\"f3\",\"grams\":30},", "");

        assertThatThrownBy(() -> parse(json, 3)).isInstanceOf(ApiException.class);
    }

    @Test
    void anUnknownRefOrABadQuantityIsRefused() {
        assertThatThrownBy(() -> parse(GOOD.replace("\"f3\"", "\"f9\""), 3)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> parse(GOOD.replace("\"f3\"", "\"banana\""), 3)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> parse(GOOD.replace("\"grams\":30", "\"grams\":-30"), 3)).isInstanceOf(ApiException.class);
    }

    @Test
    void anEmptyMealOrAMissingListIsRefused() {
        assertThatThrownBy(() -> parse("{\"meals\":[{\"title\":\"x\",\"recipe\":\"y\",\"items\":[]},{\"title\":\"x\",\"recipe\":\"y\",\"items\":[]},{\"title\":\"x\",\"recipe\":\"y\",\"items\":[]}]}", 3))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> parse("{}", 3)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> AiMealArranger.parse(null, plan, 3)).isInstanceOf(ApiException.class);
    }

    @Test
    void aBlankTitleGetsADefaultAndARecipeCanBeMissing() throws Exception {
        String json = GOOD.replace("\"title\":\"Lunch\"", "\"title\":\"  \"").replace("\"recipe\":\"Boil the rice.\"", "\"recipe\":\"\"");

        List<ArrangedMeal> meals = parse(json, 3);

        assertThat(meals.get(1).title()).isEqualTo("Meal 2");
        assertThat(meals.get(1).recipe()).isNull();
    }

    @Test
    void recipeTextIsPlainTextOnly() {
        String cleaned = AiMealArranger.clean("Cook <b>well</b>. See [this link](http://evil.example) now.\u0007", 1200);

        assertThat(cleaned).doesNotContain("<", ">", "http", "evil", "\u0007");
        assertThat(cleaned).contains("this link");
        assertThat(AiMealArranger.clean("x".repeat(2000), 100)).hasSize(100);
    }

    @Test
    void theRoundingRemainderLandsInTheLastMealSoTotalsAreExact() throws Exception {
        // Three equal thirds of 300 g would be 100.0 each; use awkward numbers that do not divide evenly.
        String json =
                """
                {"meals":[
                  {"title":"A","recipe":"","items":[{"ref":"f1","grams":60},{"ref":"f2","grams":100.03},{"ref":"f4","grams":10}]},
                  {"title":"B","recipe":"","items":[{"ref":"f2","grams":100.03},{"ref":"f4","grams":10}]},
                  {"title":"C","recipe":"","items":[{"ref":"f2","grams":100.03},{"ref":"f3","grams":30},{"ref":"f4","grams":10}]}
                ]}
                """;

        Map<String, Double> t = totals(parse(json, 3));

        assertThat(t.get("rice")).isCloseTo(300.0, org.assertj.core.data.Offset.offset(1e-9));
    }
}
