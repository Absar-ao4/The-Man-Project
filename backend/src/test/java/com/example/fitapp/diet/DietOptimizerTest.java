package com.example.fitapp.diet;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fitapp.diet.DietOptimizer.Line;
import com.example.fitapp.diet.DietOptimizer.Result;
import com.example.fitapp.diet.DietOptimizer.Status;
import com.example.fitapp.diet.DietOptimizer.Targets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Runs the real OR-Tools solver (the jar bundles its native library) on a small price list. */
class DietOptimizerTest {

    private final DietOptimizer optimizer = new DietOptimizer();

    /** Price is per kg, nutrition per 100 g. */
    private static DietFood food(String slug, String category, double kcal, double p, double c, double f, double pricePerKg, double servingG, int maxServings) {
        return new DietFood(
                UUID.randomUUID(),
                slug,
                slug,
                category,
                List.of("VEGAN", "VEGETARIAN", "EGGETARIAN", "NON_VEGETARIAN"),
                List.of(),
                kcal,
                p,
                c,
                f,
                "kg",
                1000,
                pricePerKg,
                pricePerKg,
                false,
                "INR",
                servingG,
                maxServings,
                5,
                "MAIN");
    }

    private final DietFood rice = food("rice", "GRAIN", 360, 6.7, 80, 0.6, 60, 75, 6);
    private final DietFood dal = food("dal", "PULSE", 340, 22, 62, 1.5, 160, 60, 4);
    private final DietFood soya = food("soya", "SOY", 345, 52, 33, 0.5, 140, 40, 3);
    private final DietFood oil = food("oil", "FAT", 884, 0, 0, 100, 152, 10, 3);
    private final DietFood peanuts = food("peanuts", "NUT", 567, 26, 16, 49, 160, 30, 2);
    private final DietFood spinach = food("spinach", "VEGETABLE", 23, 2.9, 3.6, 0.4, 40, 100, 3);
    private final List<DietFood> pantry = List.of(rice, dal, soya, oil, peanuts, spinach);

    private static double sum(List<Line> lines, java.util.function.ToDoubleFunction<Line> f) {
        return lines.stream().mapToDouble(f).sum();
    }

    @Test
    void aGenerousBudgetGivesAFullPlanThatMeetsEveryRule() {
        Targets targets = new Targets(2000, 90);

        Result result = optimizer.optimize(pantry, targets, 200);

        assertThat(result.status()).isEqualTo(Status.FEASIBLE);
        assertThat(result.minimumBudgetNeeded()).isNull();
        List<Line> lines = result.lines();
        double calories = sum(lines, Line::calories);
        assertThat(calories).isBetween(2000 * 0.95, 2000 * 1.05);
        assertThat(sum(lines, Line::protein)).isGreaterThanOrEqualTo(90);
        double fatShare = sum(lines, l -> 9 * l.fat()) / calories;
        assertThat(fatShare).isBetween(0.20, 0.35);
        assertThat(DietOptimizer.shownCost(lines)).isLessThanOrEqualTo(200);
        assertThat(DietOptimizer.meetsAll(lines, targets, 200)).isTrue();
    }

    @Test
    void noFoodGoesAboveItsVarietyLimit() {
        Result result = optimizer.optimize(pantry, new Targets(2400, 110), 300);

        assertThat(result.status()).isEqualTo(Status.FEASIBLE);
        for (Line line : result.lines()) {
            assertThat(line.grams()).isLessThanOrEqualTo(line.food().maxGrams() + 1e-6);
        }
    }

    @Test
    void vegetablesAreIncludedWhenTheBudgetAllowsIt() {
        Result result = optimizer.optimize(pantry, new Targets(2000, 90), 300);

        double produce = result.lines().stream().filter(l -> l.food().isProduce()).mapToDouble(Line::grams).sum();
        assertThat(produce).isGreaterThanOrEqualTo(DietOptimizer.PRODUCE_MIN_GRAMS - 1e-6);
    }

    @Test
    void theCheapestWayIsChosen() {
        // Only two protein sources: soya is far cheaper per gram of protein than dal, so dal should not be needed for protein.
        DietFood expensiveProtein = food("whey", "SUPPLEMENT", 400, 80, 8, 6, 2000, 30, 2);
        Result withExpensive = optimizer.optimize(List.of(rice, soya, oil, peanuts, expensiveProtein), new Targets(2000, 90), 500);
        Result without = optimizer.optimize(List.of(rice, soya, oil, peanuts), new Targets(2000, 90), 500);

        assertThat(withExpensive.status()).isEqualTo(Status.FEASIBLE);
        assertThat(without.status()).isEqualTo(Status.FEASIBLE);
        // Adding an option can never make the cheapest plan more expensive (allowing a cent of rounding).
        assertThat(DietOptimizer.shownCost(withExpensive.lines())).isLessThanOrEqualTo(DietOptimizer.shownCost(without.lines()) + 1.0);
        assertThat(withExpensive.lines().stream().noneMatch(l -> l.food().slug().equals("whey")))
                .as("the expensive powder is not needed")
                .isTrue();
    }

    @Test
    void aBudgetThatIsTooSmallGivesTheMostProteinItBuysAndSaysWhatWouldWork() {
        Targets targets = new Targets(2000, 90);

        Result result = optimizer.optimize(pantry, targets, 20);

        assertThat(result.status()).isEqualTo(Status.BUDGET_SHORT);
        assertThat(result.minimumBudgetNeeded()).isNotNull().isGreaterThan(20);
        assertThat(DietOptimizer.shownCost(result.lines())).isLessThanOrEqualTo(20);
        assertThat(result.lines()).isNotEmpty();
        assertThat(sum(result.lines(), Line::calories)).isLessThanOrEqualTo(2000 * 1.05);
        // And that minimum really works.
        Result atMinimum = optimizer.optimize(pantry, targets, result.minimumBudgetNeeded() + 1);
        assertThat(atMinimum.status()).isEqualTo(Status.FEASIBLE);
    }

    @Test
    void theBestEffortPlanBuysProteinFirst() {
        Result result = optimizer.optimize(pantry, new Targets(2000, 90), 6);

        // With 6 rupees the cheapest protein is soya: it must be in the plan, and it must be the main protein source.
        assertThat(result.lines().stream().anyMatch(l -> l.food().slug().equals("soya"))).isTrue();
        double protein = sum(result.lines(), Line::protein);
        double soyaProtein = result.lines().stream().filter(l -> l.food().slug().equals("soya")).mapToDouble(Line::protein).sum();
        assertThat(soyaProtein).isGreaterThan(protein * 0.5);
    }

    @Test
    void targetsThatNoBudgetCanReachAreReportedAsUnreachable() {
        // Everything in this pantry, at its variety limit, is well under 6000 kcal.
        Result result = optimizer.optimize(pantry, new Targets(6000, 90), 100000);

        assertThat(result.status()).isEqualTo(Status.UNREACHABLE);
        assertThat(result.minimumBudgetNeeded()).isNull();
        assertThat(result.lines()).isNotEmpty();
    }

    @Test
    void noFoodsMeansUnreachableAndNoPlan() {
        Result result = optimizer.optimize(List.of(), new Targets(2000, 90), 100);

        assertThat(result.status()).isEqualTo(Status.UNREACHABLE);
        assertThat(result.lines()).isEmpty();
    }

    @Test
    void theSameInputGivesTheSamePlan() {
        Result a = optimizer.optimize(pantry, new Targets(2200, 100), 250);
        Result b = optimizer.optimize(pantry, new Targets(2200, 100), 250);

        assertThat(a.lines().stream().map(l -> l.food().slug() + ":" + l.grams()).toList())
                .isEqualTo(b.lines().stream().map(l -> l.food().slug() + ":" + l.grams()).toList());
    }

    // ------------------------------------------------------------ the checks

    @Test
    void theChecksRejectPlansThatBreakARule() {
        Targets t = new Targets(2000, 90);
        // 300 g rice only: far too few calories, no fat.
        List<Line> thin = List.of(new Line(rice, 300));
        assertThat(DietOptimizer.meetsAll(thin, t, 1000)).isFalse();
        assertThat(DietOptimizer.meetsCaps(thin, t, 1000)).isTrue();
        // Over the calorie cap.
        List<Line> huge = List.of(new Line(rice, 450), new Line(dal, 240), new Line(oil, 30));
        assertThat(DietOptimizer.meetsCaps(huge, t, 1000)).isFalse();
        // Over the budget.
        assertThat(DietOptimizer.meetsCaps(thin, t, 1)).isFalse();
    }
}
