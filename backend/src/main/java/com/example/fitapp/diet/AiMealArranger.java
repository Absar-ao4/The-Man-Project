package com.example.fitapp.diet;

import com.example.fitapp.ai.AiGateway;
import com.example.fitapp.ai.AiLogService;
import com.example.fitapp.ai.AiModelClient.AiReply;
import com.example.fitapp.ai.AiModelClient.AiRequest;
import com.example.fitapp.common.ApiException;
import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.config.AnthropicProperties;
import com.example.fitapp.diet.DietOptimizer.Line;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Asks the AI model to arrange a day's foods into meals with simple recipes, through the shared gateway
 * (same daily budget as every AI call). The model only decides which meal each food goes in and how much, and writes
 * the titles and recipes. The foods and their daily totals are fixed by the optimiser: the answer is checked, and
 * quantities are scaled to add up exactly, or the whole answer is refused and the rules based arrangement is used.
 *
 * <p>Nothing about the user goes into the prompt: only food names from our own price list, quantities, a meal count
 * and a cuisine from a fixed list.
 */
@Component
public class AiMealArranger implements MealArranger {

    private static final String TOOL = "arrange_meals";
    private static final double MIN_FACTOR = 0.8;
    private static final double MAX_FACTOR = 1.25;
    private static final int MAX_TITLE = 60;
    private static final int MAX_RECIPE = 1200;

    private static final String SYSTEM_PROMPT =
            "You are a practical home cook and nutritionist. You are given the foods and quantities for one day. "
                    + "Arrange them into exactly the number of meals asked for. Use every food. For each food, the grams across all meals must add up to the total given. "
                    + "Give each meal a short title and a simple recipe of three to six short lines that uses only the listed foods plus water, salt and spices. "
                    + "Match the cooking style that was asked for. Weights are uncooked or raw unless the name says otherwise. "
                    + "Do not add other foods and do not give medical advice. Always answer by calling the arrange_meals tool.";

    private final AiGateway gateway;
    private final AiLogService logs;
    private final AnthropicProperties props;
    private final ObjectMapper mapper;

    public AiMealArranger(AiGateway gateway, AiLogService logs, AnthropicProperties props, ObjectMapper mapper) {
        this.gateway = gateway;
        this.logs = logs;
        this.props = props;
        this.mapper = mapper;
    }

    @Override
    public List<ArrangedMeal> arrange(List<Line> plan, int meals, Cuisine cuisine) {
        if (!props.configured()) {
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "AI meal arrangement is not set up on this server");
        }
        long started = System.currentTimeMillis();
        try {
            AiReply reply = gateway.call(request(plan, meals, cuisine));
            List<ArrangedMeal> arranged = parse(reply.toolInput(), plan, meals);
            logs.log(null, "DIET", reply.model(), reply.inputTokens(), reply.outputTokens(), reply.costUsd(), plan.size(), null, false, "OK", System.currentTimeMillis() - started);
            return arranged;
        } catch (ApiException e) {
            logs.log(null, "DIET", props.explainModelOrDefault(), 0, 0, BigDecimal.ZERO, 0, null, false, "ERROR", System.currentTimeMillis() - started);
            throw e;
        }
    }

    // ------------------------------------------------------------------ request

    private AiRequest request(List<Line> plan, int meals, Cuisine cuisine) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode mealsNode = schema.putObject("properties").putObject("meals");
        mealsNode.put("type", "array");
        ObjectNode meal = mealsNode.putObject("items");
        meal.put("type", "object");
        ObjectNode mealProps = meal.putObject("properties");
        mealProps.putObject("title").put("type", "string");
        mealProps.putObject("recipe").put("type", "string");
        ObjectNode item = mealProps.putObject("items");
        item.put("type", "array");
        ObjectNode itemSchema = item.putObject("items");
        itemSchema.put("type", "object");
        ObjectNode itemProps = itemSchema.putObject("properties");
        itemProps.putObject("ref").put("type", "string");
        itemProps.putObject("grams").put("type", "number");
        itemSchema.putArray("required").add("ref").add("grams");
        meal.putArray("required").add("title").add("recipe").add("items");
        schema.putArray("required").add("meals");

        StringBuilder text = new StringBuilder();
        text.append("Meals: ").append(meals).append('\n');
        text.append("Cooking style: ").append(cuisine).append('\n');
        text.append("Foods for the day:\n");
        for (int i = 0; i < plan.size(); i++) {
            text.append('f').append(i + 1).append(": ").append(plan.get(i).food().name()).append(": ").append(Math.round(plan.get(i).grams())).append(" g\n");
        }
        ObjectNode content = mapper.createObjectNode();
        content.put("type", "text");
        content.put("text", text.toString());
        return new AiRequest(
                props.explainModelOrDefault(),
                SYSTEM_PROMPT,
                List.of(content),
                TOOL,
                "Report the meals with their foods and recipes.",
                schema,
                2000);
    }

    // ------------------------------------------------------------------ checking the answer

    /** Turns the tool input into meals, or throws if it is not a usable arrangement of exactly these foods. */
    static List<ArrangedMeal> parse(JsonNode input, List<Line> plan, int meals) {
        if (input == null || !input.path("meals").isArray() || input.path("meals").size() != meals) {
            throw unusable();
        }
        List<Map<Integer, Double>> perMeal = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        List<String> recipes = new ArrayList<>();
        double[] totals = new double[plan.size()];
        int mealNumber = 0;
        for (JsonNode meal : input.path("meals")) {
            mealNumber++;
            Map<Integer, Double> lines = new LinkedHashMap<>();
            if (!meal.path("items").isArray()) {
                throw unusable();
            }
            for (JsonNode item : meal.path("items")) {
                int index = indexOf(item.path("ref").asText(""), plan.size());
                double grams = item.path("grams").asDouble(0);
                if (index < 0 || !(grams > 0) || Double.isInfinite(grams)) {
                    throw unusable();
                }
                lines.merge(index, grams, Double::sum);
                totals[index] += grams;
            }
            if (lines.isEmpty()) {
                throw unusable();
            }
            perMeal.add(lines);
            String title = clean(meal.path("title").asText(""), MAX_TITLE).replace('\n', ' ');
            titles.add(title.isEmpty() ? "Meal " + mealNumber : title);
            String recipe = clean(meal.path("recipe").asText(""), MAX_RECIPE);
            recipes.add(recipe.isEmpty() ? null : recipe);
        }
        double[] factor = new double[plan.size()];
        for (int i = 0; i < plan.size(); i++) {
            if (totals[i] <= 0) {
                throw unusable(); // a food was left out
            }
            factor[i] = plan.get(i).grams() / totals[i];
            if (factor[i] < MIN_FACTOR || factor[i] > MAX_FACTOR) {
                throw unusable(); // the quantities were changed too much to trust
            }
        }
        // Scale to the exact totals, in 0.1 g steps; the last meal that holds a food takes the rounding remainder.
        int[] lastMealOf = new int[plan.size()];
        double[] assigned = new double[plan.size()];
        List<List<MealLine>> built = new ArrayList<>();
        for (int m = 0; m < perMeal.size(); m++) {
            List<MealLine> lines = new ArrayList<>();
            for (Map.Entry<Integer, Double> e : perMeal.get(m).entrySet()) {
                double grams = Math.round(e.getValue() * factor[e.getKey()] * 10.0) / 10.0;
                assigned[e.getKey()] += grams;
                lastMealOf[e.getKey()] = m;
                lines.add(new MealLine(plan.get(e.getKey()).food(), grams));
            }
            built.add(lines);
        }
        for (int i = 0; i < plan.size(); i++) {
            double remainder = Math.round((plan.get(i).grams() - assigned[i]) * 10.0) / 10.0;
            if (remainder != 0) {
                List<MealLine> lines = built.get(lastMealOf[i]);
                for (int k = 0; k < lines.size(); k++) {
                    if (lines.get(k).food().equals(plan.get(i).food())) {
                        double fixed = Math.round((lines.get(k).grams() + remainder) * 10.0) / 10.0;
                        if (fixed <= 0) {
                            throw unusable();
                        }
                        lines.set(k, new MealLine(lines.get(k).food(), fixed));
                    }
                }
            }
        }
        List<ArrangedMeal> result = new ArrayList<>();
        for (int m = 0; m < built.size(); m++) {
            result.add(new ArrangedMeal(titles.get(m), recipes.get(m), built.get(m)));
        }
        return result;
    }

    private static int indexOf(String ref, int size) {
        if (ref.length() < 2 || ref.charAt(0) != 'f') {
            return -1;
        }
        try {
            int n = Integer.parseInt(ref.substring(1));
            return n >= 1 && n <= size ? n - 1 : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Plain text only: no markup, no links, no control characters, bounded length. */
    static String clean(String text, int max) {
        String t = text.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1").replaceAll("[<>]", "").replaceAll("[\\p{Cntrl}&&[^\\n]]", " ").trim();
        return t.length() > max ? t.substring(0, max).trim() : t;
    }

    private static ApiException unusable() {
        return new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "The AI service gave an arrangement that could not be used");
    }
}
