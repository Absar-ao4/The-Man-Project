package com.example.fitapp.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.diet.DietFood;
import com.example.fitapp.nutrition.MealType;
import com.example.fitapp.recommend.AiWeeklySummarizer.Summary;
import com.example.fitapp.recommend.RulesMealSuggester.Line;
import com.example.fitapp.recommend.RulesMealSuggester.Remaining;
import com.example.fitapp.recommend.RulesMealSuggester.Suggestion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MealSuggestionLogicTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static DietFood food(String slug, String name, String category, double kcal, double p, double c, double f, String hint, double serving, int max, double step) {
        return new DietFood(UUID.randomUUID(), slug, name, category, List.of("NON_VEGETARIAN"), List.of(), kcal, p, c, f, "kg", 1000, 10, 10, false, "USD", serving, max, step, hint);
    }

    private final DietFood chicken = food("chicken", "Chicken breast, raw", "MEAT", 165, 31, 0, 3.6, "MAIN", 150, 4, 5);
    private final DietFood egg = food("egg", "Egg, whole", "EGG", 143, 12.6, 0.7, 9.5, "BREAKFAST", 50, 6, 50);
    private final DietFood rice = food("rice", "White rice, uncooked", "GRAIN", 360, 6.7, 80, 0.6, "MAIN", 75, 6, 5);
    private final DietFood oats = food("oats", "Rolled oats", "GRAIN", 380, 13, 67, 7, "BREAKFAST", 50, 3, 5);
    private final DietFood spinach = food("spinach", "Spinach", "VEGETABLE", 23, 2.9, 3.6, 0.4, "ANY", 100, 3, 5);
    private final DietFood whey = food("whey", "Whey protein powder", "SUPPLEMENT", 400, 80, 8, 6, "SNACK", 30, 2, 5);
    private final List<DietFood> pool = List.of(chicken, egg, rice, oats, spinach, whey);

    private static double kcal(Suggestion s) {
        return s.lines().stream().mapToDouble(l -> l.grams() * l.food().kcal() / 100.0).sum();
    }

    // ------------------------------------------------------------------ the rules

    @Test
    void aLunchFitsWhatIsLeftAndUsesProteinCarbsAndVegetables() {
        List<Suggestion> s = RulesMealSuggester.suggest(pool, new Remaining(800, 60, 90, 25), MealType.LUNCH, 3);

        assertThat(s).isNotEmpty();
        Suggestion first = s.get(0);
        assertThat(first.lines()).extracting(l -> l.food().slug()).containsExactly("chicken", "spinach", "rice");
        assertThat(kcal(first)).isBetween(640.0, 760.0); // a lunch is about 700 kcal, never more than is left
        assertThat(first.title()).isEqualTo("Chicken breast with white rice");
    }

    @Test
    void aBreakfastUsesBreakfastFoods() {
        List<Suggestion> s = RulesMealSuggester.suggest(pool, new Remaining(600, 40, 70, 20), MealType.BREAKFAST, 3);

        assertThat(s).isNotEmpty();
        assertThat(s.get(0).lines()).extracting(l -> l.food().slug()).contains("egg", "oats").doesNotContain("chicken", "rice", "whey");
    }

    @Test
    void quantitiesAreInBuyableSteps() {
        List<Suggestion> s = RulesMealSuggester.suggest(pool, new Remaining(600, 40, 70, 20), MealType.BREAKFAST, 1);

        for (Line line : s.get(0).lines()) {
            assertThat(line.grams() % line.food().stepGrams()).as(line.food().slug()).isZero();
            assertThat(line.grams()).isPositive();
        }
    }

    @Test
    void aMealNeverMoreThanWhatIsLeft() {
        List<Suggestion> s = RulesMealSuggester.suggest(pool, new Remaining(300, 25, 30, 10), MealType.DINNER, 3);

        s.forEach(x -> assertThat(kcal(x)).isLessThan(300 * 1.25));
    }

    @Test
    void nothingLeftMeansNoSuggestions() {
        assertThat(RulesMealSuggester.suggest(pool, new Remaining(0, 0, 0, 0), MealType.LUNCH, 3)).isEmpty();
    }

    @Test
    void withoutAProteinFoodThereAreNoSuggestions() {
        assertThat(RulesMealSuggester.suggest(List.of(rice, spinach), new Remaining(600, 40, 70, 20), MealType.LUNCH, 3)).isEmpty();
    }

    @Test
    void theSameDayGivesTheSameIdeasAndTheCountIsHonoured() {
        Remaining r = new Remaining(800, 60, 90, 25);

        assertThat(RulesMealSuggester.suggest(pool, r, MealType.LUNCH, 3)).isEqualTo(RulesMealSuggester.suggest(pool, r, MealType.LUNCH, 3));
        assertThat(RulesMealSuggester.suggest(pool, r, MealType.LUNCH, 1)).hasSize(1);
    }

    @Test
    void shortNamesDropTheDetail() {
        assertThat(RulesMealSuggester.shortName("Chicken breast, raw")).isEqualTo("Chicken breast");
        assertThat(RulesMealSuggester.shortName("Curd (plain yogurt)")).isEqualTo("Curd");
        assertThat(RulesMealSuggester.shortName("Egg")).isEqualTo("Egg");
    }

    // ------------------------------------------------------------------ checking the AI answer

    private static JsonNode json(String text) throws Exception {
        return JSON.readTree(text);
    }

    @Test
    void aGoodAiAnswerBecomesCheckedSuggestions() throws Exception {
        JsonNode input =
                json("{\"suggestions\":[{\"title\":\"Chicken rice bowl\",\"description\":\"Simple and filling.\",\"items\":[{\"ref\":\"f1\",\"grams\":150},{\"ref\":\"f3\",\"grams\":80},{\"ref\":\"f5\",\"grams\":100}]}]}");

        List<Suggestion> s = AiMealSuggester.parse(input, pool, 700, 3);

        assertThat(s).hasSize(1);
        assertThat(s.get(0).title()).isEqualTo("Chicken rice bowl");
        assertThat(s.get(0).lines()).extracting(l -> l.food().slug()).containsExactly("chicken", "rice", "spinach");
        assertThat(s.get(0).lines().get(0).grams()).isEqualTo(150.0);
    }

    @Test
    void badIdeasAreDroppedAndGoodOnesKept() throws Exception {
        JsonNode input =
                json(
                        "{\"suggestions\":["
                                + "{\"title\":\"Unknown\",\"description\":\"\",\"items\":[{\"ref\":\"f99\",\"grams\":100}]},"
                                + "{\"title\":\"Zero\",\"description\":\"\",\"items\":[{\"ref\":\"f1\",\"grams\":0}]},"
                                + "{\"title\":\"Too much\",\"description\":\"\",\"items\":[{\"ref\":\"f1\",\"grams\":600},{\"ref\":\"f3\",\"grams\":450}]},"
                                + "{\"title\":\"Fine\",\"description\":\"\",\"items\":[{\"ref\":\"f1\",\"grams\":150}]}]}");

        List<Suggestion> s = AiMealSuggester.parse(input, pool, 700, 3);

        assertThat(s).extracting(Suggestion::title).containsExactly("Fine");
    }

    @Test
    void anIdeaOverTheCaloriesThatAreLeftIsDropped() throws Exception {
        JsonNode input = json("{\"suggestions\":[{\"title\":\"Big\",\"description\":\"\",\"items\":[{\"ref\":\"f3\",\"grams\":300}]}]}"); // 1080 kcal of rice

        assertThatThrownBy(() -> AiMealSuggester.parse(input, pool, 500, 3)).isInstanceOf(ApiException.class);
    }

    @Test
    void quantitiesOverTheDailyLimitOfAFoodAreRefused() throws Exception {
        JsonNode input = json("{\"suggestions\":[{\"title\":\"Eggs\",\"description\":\"\",\"items\":[{\"ref\":\"f2\",\"grams\":350}]}]}"); // the limit for eggs is 300 g

        assertThatThrownBy(() -> AiMealSuggester.parse(input, pool, 2000, 3)).isInstanceOf(ApiException.class);
    }

    @Test
    void repeatedFoodsAreMergedAndQuantitiesRoundedToTheStep() throws Exception {
        JsonNode input = json("{\"suggestions\":[{\"title\":\"Eggs\",\"description\":\"\",\"items\":[{\"ref\":\"f2\",\"grams\":60},{\"ref\":\"f2\",\"grams\":60}]}]}");

        List<Suggestion> s = AiMealSuggester.parse(input, pool, 700, 3);

        assertThat(s.get(0).lines()).hasSize(1);
        assertThat(s.get(0).lines().get(0).grams()).isEqualTo(100.0); // 120 g rounded to the 50 g step
    }

    @Test
    void thereAreNeverMoreThanSixFoodsInAnIdea() throws Exception {
        List<DietFood> many = List.of(chicken, egg, rice, oats, spinach, whey, food("x", "X", "GRAIN", 100, 1, 20, 0, "ANY", 100, 3, 5));
        JsonNode input = json("{\"suggestions\":[{\"title\":\"All\",\"description\":\"\",\"items\":["
                + "{\"ref\":\"f1\",\"grams\":50},{\"ref\":\"f2\",\"grams\":50},{\"ref\":\"f3\",\"grams\":50},{\"ref\":\"f4\",\"grams\":50},{\"ref\":\"f5\",\"grams\":50},{\"ref\":\"f6\",\"grams\":10},{\"ref\":\"f7\",\"grams\":50}]}]}");

        assertThatThrownBy(() -> AiMealSuggester.parse(input, many, 2000, 3)).isInstanceOf(ApiException.class);
    }

    @Test
    void textFromTheModelIsCleaned() throws Exception {
        JsonNode input =
                json("{\"suggestions\":[{\"title\":\"<b>Bowl</b> [link](http://evil.example)\",\"description\":\"Line one\\nline two\",\"items\":[{\"ref\":\"f1\",\"grams\":150}]},"
                        + "{\"title\":\"\",\"description\":\"\",\"items\":[{\"ref\":\"f1\",\"grams\":100}]}]}");

        List<Suggestion> s = AiMealSuggester.parse(input, pool, 700, 3);

        assertThat(s.get(0).title()).isEqualTo("bBowl/b link");
        assertThat(s.get(0).description()).isEqualTo("Line one line two");
        assertThat(s.get(1).title()).as("an empty title falls back to the main food").isEqualTo("Chicken breast");
    }

    @Test
    void theNumberOfSuggestionsIsCappedAndAnAnswerWithNoneUsableIsRefused() throws Exception {
        String one = "{\"title\":\"A\",\"description\":\"\",\"items\":[{\"ref\":\"f1\",\"grams\":150}]}";
        JsonNode five = json("{\"suggestions\":[" + one + "," + one + "," + one + "," + one + "," + one + "]}");

        assertThat(AiMealSuggester.parse(five, pool, 700, 2)).hasSize(2);
        assertThatThrownBy(() -> AiMealSuggester.parse(json("{\"suggestions\":[]}"), pool, 700, 3)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> AiMealSuggester.parse(json("{}"), pool, 700, 3)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> AiMealSuggester.parse(null, pool, 700, 3)).isInstanceOf(ApiException.class);
    }

    @Test
    void aWeeklySummaryIsCheckedAndTipsAreCapped() throws Exception {
        Summary ok = AiWeeklySummarizer.parse(json("{\"summary\":\"  A good week. \",\"tips\":[\"One\",\"Two\",\"<i>Three</i>\",\"Four\",\"\"]}"));

        assertThat(ok.text()).isEqualTo("A good week.");
        assertThat(ok.tips()).containsExactly("One", "Two", "iThree/i");
        assertThatThrownBy(() -> AiWeeklySummarizer.parse(json("{\"summary\":\"   \",\"tips\":[]}"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> AiWeeklySummarizer.parse(null)).isInstanceOf(ApiException.class);
        assertThat(AiWeeklySummarizer.parse(json("{\"summary\":\"Fine.\"}")).tips()).isEmpty();
        assertThat(AiWeeklySummarizer.parse(json("{\"summary\":\"" + "x".repeat(900) + "\",\"tips\":[]}")).text()).hasSize(600);
    }
}
