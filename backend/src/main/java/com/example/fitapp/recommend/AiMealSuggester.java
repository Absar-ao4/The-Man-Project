package com.example.fitapp.recommend;

import com.example.fitapp.ai.AiGateway;
import com.example.fitapp.ai.AiLogService;
import com.example.fitapp.ai.AiModelClient.AiReply;
import com.example.fitapp.ai.AiModelClient.AiRequest;
import com.example.fitapp.common.ApiException;
import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.config.AnthropicProperties;
import com.example.fitapp.diet.DietFood;
import com.example.fitapp.nutrition.MealType;
import com.example.fitapp.recommend.RulesMealSuggester.Line;
import com.example.fitapp.recommend.RulesMealSuggester.Remaining;
import com.example.fitapp.recommend.RulesMealSuggester.Suggestion;
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
 * Asks the AI model for meal ideas that fit what is left of today, through the shared gateway (same daily budget as
 * every AI call). The model only picks foods from our own list and quantities; the calories and macros are always
 * worked out here from the list, never taken from the model. An idea that uses an unknown food, a silly quantity or
 * more calories than are left is dropped, and if nothing usable is left the whole answer is refused.
 *
 * <p>Nothing about the user goes into the prompt: only the numbers that are left, a meal type and food names from our list.
 */
@Component
public class AiMealSuggester {

    private static final String TOOL = "suggest_meals";
    private static final int MAX_TITLE = 60;
    private static final int MAX_DESCRIPTION = 300;
    private static final int MAX_FOODS = 6;
    private static final double MAX_GRAMS = 600;
    private static final double MIN_MEAL_KCAL = 40;
    /** A meal may go this far over what is left (rounding, a spoon of oil) but no further. */
    private static final double OVER_FACTOR = 1.15;

    private static final String SYSTEM_PROMPT =
            "You are a practical nutritionist. You are given what is left of someone's calories and macros for today, the kind of meal, "
                    + "and a numbered list of foods. Suggest the number of different meals asked for. Use only foods from the list, refer to them by their number, "
                    + "and give grams for each. Each meal has two to five foods and stays within the remaining calories. Prefer meals that cover the remaining protein. "
                    + "Give each meal a short title and one plain sentence describing it. Weights are raw or uncooked. Do not give medical advice. "
                    + "Always answer by calling the suggest_meals tool.";

    private final AiGateway gateway;
    private final AiLogService logs;
    private final AnthropicProperties props;
    private final ObjectMapper mapper;

    public AiMealSuggester(AiGateway gateway, AiLogService logs, AnthropicProperties props, ObjectMapper mapper) {
        this.gateway = gateway;
        this.logs = logs;
        this.props = props;
        this.mapper = mapper;
    }

    public List<Suggestion> suggest(List<DietFood> pool, Remaining remaining, MealType type, int count) {
        if (!props.configured()) {
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "AI meal suggestions are not set up on this server");
        }
        long started = System.currentTimeMillis();
        try {
            AiReply reply = gateway.call(request(pool, remaining, type, count));
            List<Suggestion> suggestions = parse(reply.toolInput(), pool, remaining.calories(), count);
            logs.log(null, "SUGGEST", reply.model(), reply.inputTokens(), reply.outputTokens(), reply.costUsd(), suggestions.size(), null, false, "OK", System.currentTimeMillis() - started);
            return suggestions;
        } catch (ApiException e) {
            logs.log(null, "SUGGEST", props.explainModelOrDefault(), 0, 0, BigDecimal.ZERO, 0, null, false, "ERROR", System.currentTimeMillis() - started);
            throw e;
        }
    }

    private AiRequest request(List<DietFood> pool, Remaining remaining, MealType type, int count) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode list = schema.putObject("properties").putObject("suggestions");
        list.put("type", "array");
        ObjectNode meal = list.putObject("items");
        meal.put("type", "object");
        ObjectNode mealProps = meal.putObject("properties");
        mealProps.putObject("title").put("type", "string");
        mealProps.putObject("description").put("type", "string");
        ObjectNode items = mealProps.putObject("items");
        items.put("type", "array");
        ObjectNode item = items.putObject("items");
        item.put("type", "object");
        ObjectNode itemProps = item.putObject("properties");
        itemProps.putObject("ref").put("type", "string");
        itemProps.putObject("grams").put("type", "number");
        item.putArray("required").add("ref").add("grams");
        meal.putArray("required").add("title").add("description").add("items");
        schema.putArray("required").add("suggestions");

        StringBuilder text = new StringBuilder();
        text.append("Meal: ").append(type == null ? "any" : type.name().toLowerCase()).append('\n');
        text.append("Remaining today: ")
                .append(Math.round(remaining.calories())).append(" kcal, ")
                .append(Math.round(remaining.protein())).append(" g protein, ")
                .append(Math.round(remaining.carbs())).append(" g carbs, ")
                .append(Math.round(remaining.fat())).append(" g fat\n");
        text.append("Meals wanted: ").append(count).append('\n');
        text.append("Foods:\n");
        for (int i = 0; i < pool.size(); i++) {
            text.append('f').append(i + 1).append(": ").append(pool.get(i).name()).append('\n');
        }
        ObjectNode content = mapper.createObjectNode();
        content.put("type", "text");
        content.put("text", text.toString());
        return new AiRequest(
                props.explainModelOrDefault(),
                SYSTEM_PROMPT,
                List.of(content),
                TOOL,
                "Report the meal ideas with their foods.",
                schema,
                1500);
    }

    /** Turns the tool input into checked suggestions, or throws when none of them can be used. */
    static List<Suggestion> parse(JsonNode input, List<DietFood> pool, double remainingKcal, int count) {
        if (input == null || !input.path("suggestions").isArray()) {
            throw unusable();
        }
        List<Suggestion> result = new ArrayList<>();
        for (JsonNode node : input.path("suggestions")) {
            if (result.size() >= count) {
                break;
            }
            Suggestion s = one(node, pool, remainingKcal);
            if (s != null) {
                result.add(s);
            }
        }
        if (result.isEmpty()) {
            throw unusable();
        }
        return result;
    }

    private static Suggestion one(JsonNode node, List<DietFood> pool, double remainingKcal) {
        if (!node.path("items").isArray()) {
            return null;
        }
        Map<Integer, Double> grams = new LinkedHashMap<>();
        for (JsonNode item : node.path("items")) {
            int index = indexOf(item.path("ref").asText(""), pool.size());
            double g = item.path("grams").asDouble(0);
            if (index < 0 || !(g > 0) || Double.isInfinite(g)) {
                return null;
            }
            grams.merge(index, g, Double::sum);
        }
        if (grams.isEmpty() || grams.size() > MAX_FOODS) {
            return null;
        }
        List<Line> lines = new ArrayList<>();
        double kcal = 0;
        for (Map.Entry<Integer, Double> e : grams.entrySet()) {
            DietFood food = pool.get(e.getKey());
            if (e.getValue() > Math.min(MAX_GRAMS, food.maxGrams())) {
                return null;
            }
            double rounded = RulesMealSuggester.round(e.getValue(), food.stepGrams());
            lines.add(new Line(food, rounded));
            kcal += rounded * food.kcal() / 100.0;
        }
        if (kcal < MIN_MEAL_KCAL || kcal > remainingKcal * OVER_FACTOR + 30) {
            return null;
        }
        String title = AiText.clean(node.path("title").asText(""), MAX_TITLE);
        String description = AiText.clean(node.path("description").asText(""), MAX_DESCRIPTION);
        return new Suggestion(title.isEmpty() ? RulesMealSuggester.shortName(lines.get(0).food().name()) : title, description, lines);
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

    private static ApiException unusable() {
        return new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "The AI service gave meal ideas that could not be used");
    }
}
