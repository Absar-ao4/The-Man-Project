package com.example.fitapp.diet;

import com.example.fitapp.diet.DietOptimizer.Line;
import com.example.fitapp.diet.MealArranger.ArrangedMeal;
import com.example.fitapp.diet.MealArranger.MealLine;
import java.util.ArrayList;
import java.util.List;

/**
 * The arrangement that needs no AI: each food goes to the meals that suit it (eggs and oats to breakfast, powders and
 * fruit to snacks, the rest to the main meals) and is split evenly between them. Deterministic.
 */
public final class RulesMealArranger {

    private enum Kind {
        BREAKFAST,
        MAIN,
        SNACK
    }

    private record Slot(String title, Kind kind) {}

    private RulesMealArranger() {}

    private static List<Slot> slots(int meals) {
        return switch (meals) {
            case 2 -> List.of(new Slot("Lunch", Kind.MAIN), new Slot("Dinner", Kind.MAIN));
            case 3 -> List.of(new Slot("Breakfast", Kind.BREAKFAST), new Slot("Lunch", Kind.MAIN), new Slot("Dinner", Kind.MAIN));
            case 4 ->
                    List.of(
                            new Slot("Breakfast", Kind.BREAKFAST),
                            new Slot("Lunch", Kind.MAIN),
                            new Slot("Snack", Kind.SNACK),
                            new Slot("Dinner", Kind.MAIN));
            case 5 ->
                    List.of(
                            new Slot("Breakfast", Kind.BREAKFAST),
                            new Slot("Morning snack", Kind.SNACK),
                            new Slot("Lunch", Kind.MAIN),
                            new Slot("Evening snack", Kind.SNACK),
                            new Slot("Dinner", Kind.MAIN));
            default ->
                    List.of(
                            new Slot("Breakfast", Kind.BREAKFAST),
                            new Slot("Morning snack", Kind.SNACK),
                            new Slot("Lunch", Kind.MAIN),
                            new Slot("Evening snack", Kind.SNACK),
                            new Slot("Dinner", Kind.MAIN),
                            new Slot("Late snack", Kind.SNACK));
        };
    }

    public static List<ArrangedMeal> arrange(List<Line> plan, int meals) {
        List<Slot> slots = slots(Math.max(2, Math.min(6, meals)));
        List<List<MealLine>> bySlot = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            bySlot.add(new ArrayList<>());
        }
        for (Line line : plan) {
            List<Integer> targets = targetSlots(line.food().mealHint(), slots);
            double share = line.grams() / targets.size();
            for (int s : targets) {
                bySlot.get(s).add(new MealLine(line.food(), share));
            }
        }
        fillEmptySlots(bySlot);
        List<ArrangedMeal> result = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            result.add(new ArrangedMeal(slots.get(i).title(), null, bySlot.get(i)));
        }
        return result;
    }

    private static List<Integer> targetSlots(String hint, List<Slot> slots) {
        List<Integer> targets = new ArrayList<>();
        Kind wanted =
                switch (hint) {
                    case "BREAKFAST" -> Kind.BREAKFAST;
                    case "SNACK" -> Kind.SNACK;
                    case "MAIN" -> Kind.MAIN;
                    default -> null;
                };
        for (int i = 0; i < slots.size(); i++) {
            if (wanted == null || slots.get(i).kind() == wanted) {
                targets.add(i);
            }
        }
        if (targets.isEmpty()) {
            // No such slot (no breakfast with two meals, no snack with three): breakfast foods open the day, snacks close it.
            targets.add("SNACK".equals(hint) ? slots.size() - 1 : 0);
        }
        return targets;
    }

    /** A meal with nothing in it is not a meal: move half of the biggest portion across until none is empty. */
    private static void fillEmptySlots(List<List<MealLine>> bySlot) {
        for (int guard = 0; guard < 20; guard++) {
            int empty = -1;
            for (int i = 0; i < bySlot.size(); i++) {
                if (bySlot.get(i).isEmpty()) {
                    empty = i;
                    break;
                }
            }
            if (empty < 0) {
                return;
            }
            int fromMeal = -1;
            int fromLine = -1;
            double biggest = -1;
            for (int m = 0; m < bySlot.size(); m++) {
                List<MealLine> lines = bySlot.get(m);
                for (int l = 0; l < lines.size(); l++) {
                    double weight = lines.get(l).grams() * lines.get(l).food().kcal();
                    if (weight > biggest) {
                        biggest = weight;
                        fromMeal = m;
                        fromLine = l;
                    }
                }
            }
            if (fromMeal < 0) {
                return; // no foods at all
            }
            MealLine source = bySlot.get(fromMeal).get(fromLine);
            double half = source.grams() / 2.0;
            bySlot.get(fromMeal).set(fromLine, new MealLine(source.food(), half));
            bySlot.get(empty).add(new MealLine(source.food(), half));
        }
    }
}
