package com.example.fitapp.diet;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.common.ErrorCode;
import com.google.ortools.Loader;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/**
 * Picks quantities of foods that hit a day's targets for the least money, with Google OR-Tools (GLOP, a
 * linear programming solver, so quantities are continuous grams that are rounded afterwards).
 *
 * <p>The rules: calories within 5 percent of the target, protein at least the target, fat 20 to 35 percent
 * of calories (carbs are whatever fills the rest), no food above its variety limit, total cost within the
 * budget. If that is not possible within the budget the result is the plan that gets the most protein
 * within the budget, and says how far short it falls and what budget would have been enough.
 *
 * <p>Each LP is solved slightly inside its limits (a small margin), because rounding grams to whole
 * steps moves the totals a little. The rounded plan is then checked against the real limits, and a finer
 * rounding is used if it does not pass.
 */
@Component
public class DietOptimizer {

    /** The part of a day's targets the optimiser uses. Carbohydrate is not a target: it fills the rest. */
    public record Targets(double calories, double protein) {}

    public record Line(DietFood food, double grams) {
        public double cost() {
            return grams * food.costPerGram();
        }

        public double calories() {
            return grams * food.kcal() / 100.0;
        }

        public double protein() {
            return grams * food.protein() / 100.0;
        }

        public double carbs() {
            return grams * food.carbs() / 100.0;
        }

        public double fat() {
            return grams * food.fat() / 100.0;
        }
    }

    /**
     * FEASIBLE: every rule is met within the budget. BUDGET_SHORT: the rules can be met, but not for this
     * budget ([minimumBudgetNeeded] says what it would take). UNREACHABLE: the rules cannot be met with these
     * foods at any price (for example a vegan with several allergies).
     */
    public enum Status {
        FEASIBLE,
        BUDGET_SHORT,
        UNREACHABLE
    }

    public record Result(Status status, List<Line> lines, Double minimumBudgetNeeded) {}

    static final double CALORIE_BAND = 0.05;
    static final double FAT_MIN_SHARE = 0.20;
    static final double FAT_MAX_SHARE = 0.35;
    static final double PRODUCE_MIN_GRAMS = 200;

    private static final double MARGIN = 0.005; // solve inside the limits so rounding does not step over them
    private static final double PROTEIN_MARGIN = 0.01;
    private static final double FAT_SHARE_MARGIN = 0.005;
    private static final double BUDGET_MARGIN = 0.998;
    private static final double KCAL_TIE_BREAK = 0.005; // in the best effort plan, 200 kcal is worth 1 g of protein
    private static final double EPS = 1e-6;
    private static final double[] ROUNDING_STEPS = {0, 1.0, 0.1}; // 0 means "each food's own step"
    private static boolean nativeLoaded;

    public Result optimize(List<DietFood> foods, Targets targets, double budget) {
        if (foods.isEmpty()) {
            return new Result(Status.UNREACHABLE, List.of(), null);
        }
        // 1) the full plan within the budget, with a vegetable and fruit minimum if that still fits
        for (boolean produce : new boolean[] {true, false}) {
            double[] grams = solveMinCost(foods, targets, budget * BUDGET_MARGIN, produce);
            if (grams != null) {
                List<Line> lines = roundAndCheck(foods, grams, l -> meetsAll(l, targets, budget), false);
                if (lines != null) {
                    return new Result(Status.FEASIBLE, lines, null);
                }
            }
        }
        // 2) why it did not fit: would any budget do?
        double[] unlimited = solveMinCost(foods, targets, Double.POSITIVE_INFINITY, false);
        Status status = unlimited == null ? Status.UNREACHABLE : Status.BUDGET_SHORT;
        Double minimum = null;
        if (unlimited != null) {
            double cost = 0;
            for (int i = 0; i < foods.size(); i++) {
                cost += unlimited[i] * foods.get(i).costPerGram();
            }
            minimum = Math.ceil(cost);
        }
        // 3) the most protein the budget buys
        double[] best = solveMaxProtein(foods, targets, budget * BUDGET_MARGIN);
        List<Line> lines = roundAndCheck(foods, best, l -> meetsCaps(l, targets, budget), true);
        return new Result(status, lines, minimum);
    }

    // ------------------------------------------------------------------ the two programs

    /** The cheapest quantities that meet every rule, or null if there are none (within [budgetLimit]). */
    double[] solveMinCost(List<DietFood> foods, Targets t, double budgetLimit, boolean produceMinimum) {
        ensureLoaded();
        MPSolver solver = newSolver();
        try {
            MPVariable[] x = variables(solver, foods);
            double inf = MPSolver.infinity();

            MPConstraint calories =
                    solver.makeConstraint(t.calories() * (1 - CALORIE_BAND + MARGIN), t.calories() * (1 + CALORIE_BAND - MARGIN));
            MPConstraint protein = solver.makeConstraint(t.protein() * (1 + PROTEIN_MARGIN), inf);
            MPConstraint fatLow = solver.makeConstraint(0, inf);
            MPConstraint fatHigh = solver.makeConstraint(-inf, 0);
            MPConstraint cost = solver.makeConstraint(-inf, Double.isInfinite(budgetLimit) ? inf : budgetLimit);
            MPConstraint produce = solver.makeConstraint(produceMinimum ? PRODUCE_MIN_GRAMS : 0, inf);
            MPObjective objective = solver.objective();

            for (int i = 0; i < foods.size(); i++) {
                DietFood f = foods.get(i);
                double kcal = f.kcal() / 100.0;
                double fatKcal = 9.0 * f.fat() / 100.0;
                calories.setCoefficient(x[i], kcal);
                protein.setCoefficient(x[i], f.protein() / 100.0);
                fatLow.setCoefficient(x[i], fatKcal - (FAT_MIN_SHARE + FAT_SHARE_MARGIN) * kcal);
                fatHigh.setCoefficient(x[i], fatKcal - (FAT_MAX_SHARE - FAT_SHARE_MARGIN) * kcal);
                cost.setCoefficient(x[i], f.costPerGram());
                produce.setCoefficient(x[i], f.isProduce() ? 1.0 : 0.0);
                objective.setCoefficient(x[i], f.costPerGram());
            }
            objective.setMinimization();
            return solve(solver, x);
        } finally {
            solver.delete();
        }
    }

    /** The quantities that buy the most protein within the budget without going over the calorie and fat limits. Always exists (all zero). */
    double[] solveMaxProtein(List<DietFood> foods, Targets t, double budgetLimit) {
        ensureLoaded();
        MPSolver solver = newSolver();
        try {
            MPVariable[] x = variables(solver, foods);
            double inf = MPSolver.infinity();

            MPConstraint calories = solver.makeConstraint(-inf, t.calories() * (1 + CALORIE_BAND - MARGIN));
            MPConstraint fatHigh = solver.makeConstraint(-inf, 0);
            MPConstraint cost = solver.makeConstraint(-inf, budgetLimit);
            MPObjective objective = solver.objective();

            for (int i = 0; i < foods.size(); i++) {
                DietFood f = foods.get(i);
                double kcal = f.kcal() / 100.0;
                calories.setCoefficient(x[i], kcal);
                fatHigh.setCoefficient(x[i], 9.0 * f.fat() / 100.0 - (FAT_MAX_SHARE - FAT_SHARE_MARGIN) * kcal);
                cost.setCoefficient(x[i], f.costPerGram());
                objective.setCoefficient(x[i], f.protein() / 100.0 + KCAL_TIE_BREAK * kcal);
            }
            objective.setMaximization();
            double[] solution = solve(solver, x);
            return solution == null ? new double[foods.size()] : solution;
        } finally {
            solver.delete();
        }
    }

    private static MPVariable[] variables(MPSolver solver, List<DietFood> foods) {
        MPVariable[] x = new MPVariable[foods.size()];
        for (int i = 0; i < x.length; i++) {
            x[i] = solver.makeNumVar(0.0, foods.get(i).maxGrams(), foods.get(i).slug());
        }
        return x;
    }

    private static double[] solve(MPSolver solver, MPVariable[] x) {
        MPSolver.ResultStatus status = solver.solve();
        if (status != MPSolver.ResultStatus.OPTIMAL) {
            return null;
        }
        double[] grams = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            grams[i] = Math.max(0.0, x[i].solutionValue());
        }
        return grams;
    }

    private static MPSolver newSolver() {
        MPSolver solver = MPSolver.createSolver("GLOP");
        if (solver == null) {
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "The diet optimiser is not available on this server");
        }
        return solver;
    }

    private static synchronized void ensureLoaded() {
        if (nativeLoaded) {
            return;
        }
        try {
            Loader.loadNativeLibraries();
            nativeLoaded = true;
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "The diet optimiser is not available on this server");
        }
    }

    // ------------------------------------------------------------------ rounding and checking

    /**
     * Rounds the quantities to buyable steps and returns the first rounding that passes [check]. With
     * [alwaysReturn] the finest rounding comes back even if it does not pass; otherwise null.
     */
    private static List<Line> roundAndCheck(List<DietFood> foods, double[] grams, Predicate<List<Line>> check, boolean alwaysReturn) {
        List<Line> last = null;
        for (double step : ROUNDING_STEPS) {
            List<Line> lines = new ArrayList<>();
            for (int i = 0; i < foods.size(); i++) {
                DietFood f = foods.get(i);
                double s = step == 0 ? f.stepGrams() : step;
                double rounded = Math.round(grams[i] / s) * s;
                rounded = Math.min(rounded, f.maxGrams());
                if (rounded > EPS) {
                    lines.add(new Line(f, Math.round(rounded * 10.0) / 10.0));
                }
            }
            last = lines;
            if (check.test(lines)) {
                return lines;
            }
        }
        return alwaysReturn ? last : null;
    }

    /** The cost the plan will show: each line to the cent, added up. */
    static double shownCost(List<Line> lines) {
        double total = 0;
        for (Line l : lines) {
            total += Math.round(l.cost() * 100.0) / 100.0;
        }
        return total;
    }

    /** True if the plan meets every rule of the full plan. */
    static boolean meetsAll(List<Line> lines, Targets t, double budget) {
        double calories = lines.stream().mapToDouble(Line::calories).sum();
        double protein = lines.stream().mapToDouble(Line::protein).sum();
        double fatKcal = lines.stream().mapToDouble(l -> 9.0 * l.fat()).sum();
        if (calories < t.calories() * (1 - CALORIE_BAND) - EPS || calories > t.calories() * (1 + CALORIE_BAND) + EPS) {
            return false;
        }
        if (protein < t.protein() - EPS) {
            return false;
        }
        double share = fatKcal / calories;
        if (share < FAT_MIN_SHARE - EPS || share > FAT_MAX_SHARE + EPS) {
            return false;
        }
        return shownCost(lines) <= budget + EPS && withinVarietyLimits(lines);
    }

    /** True if a best effort plan keeps to the limits that still apply when the targets cannot be met. */
    static boolean meetsCaps(List<Line> lines, Targets t, double budget) {
        double calories = lines.stream().mapToDouble(Line::calories).sum();
        double fatKcal = lines.stream().mapToDouble(l -> 9.0 * l.fat()).sum();
        if (calories > t.calories() * (1 + CALORIE_BAND) + EPS) {
            return false;
        }
        if (calories > 0 && fatKcal / calories > FAT_MAX_SHARE + EPS) {
            return false;
        }
        return shownCost(lines) <= budget + EPS && withinVarietyLimits(lines);
    }

    private static boolean withinVarietyLimits(List<Line> lines) {
        return lines.stream().allMatch(l -> l.grams() <= l.food().maxGrams() + EPS);
    }
}
