package com.example.fitapp.recommend;

import com.example.fitapp.ai.AiGateway;
import com.example.fitapp.ai.AiLogService;
import com.example.fitapp.ai.AiModelClient.AiReply;
import com.example.fitapp.ai.AiModelClient.AiRequest;
import com.example.fitapp.common.ApiException;
import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.config.AnthropicProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * A short written read on how balanced the week's training was, from the AI model, through the shared gateway. The prompt
 * holds only the number of counted sets per muscle: no names, no weights, nothing about the person.
 */
@Component
public class AiWeeklySummarizer {

    private static final String TOOL = "summarize_week";
    static final int MAX_SUMMARY = 600;
    static final int MAX_TIP = 160;
    static final int MAX_TIPS = 3;

    private static final String SYSTEM_PROMPT =
            "You are a thoughtful strength coach. You are given how many hard sets someone did for each muscle group in the last seven days. "
                    + "Write a short, friendly summary (two or three sentences) of how balanced the week was between pushing, pulling, legs and core, "
                    + "and give up to three short, specific tips for next week. Only talk about the muscle groups and counts you were given. "
                    + "Do not give medical advice and do not mention injuries. Always answer by calling the summarize_week tool.";

    /** The summary and its tips. */
    public record Summary(String text, List<String> tips) {}

    private final AiGateway gateway;
    private final AiLogService logs;
    private final AnthropicProperties props;
    private final ObjectMapper mapper;

    public AiWeeklySummarizer(AiGateway gateway, AiLogService logs, AnthropicProperties props, ObjectMapper mapper) {
        this.gateway = gateway;
        this.logs = logs;
        this.props = props;
        this.mapper = mapper;
    }

    public Summary summarize(Map<String, Integer> setsByMuscle) {
        if (!props.configured()) {
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "The AI weekly summary is not set up on this server");
        }
        long started = System.currentTimeMillis();
        try {
            AiReply reply = gateway.call(request(setsByMuscle));
            Summary summary = parse(reply.toolInput());
            logs.log(null, "SUMMARY", reply.model(), reply.inputTokens(), reply.outputTokens(), reply.costUsd(), summary.tips().size(), null, false, "OK", System.currentTimeMillis() - started);
            return summary;
        } catch (ApiException e) {
            logs.log(null, "SUMMARY", props.explainModelOrDefault(), 0, 0, BigDecimal.ZERO, 0, null, false, "ERROR", System.currentTimeMillis() - started);
            throw e;
        }
    }

    private AiRequest request(Map<String, Integer> setsByMuscle) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("summary").put("type", "string");
        properties.putObject("tips").put("type", "array").putObject("items").put("type", "string");
        schema.putArray("required").add("summary").add("tips");

        StringBuilder text = new StringBuilder("Hard sets in the last 7 days:\n");
        setsByMuscle.forEach((muscle, sets) -> text.append(muscle.toLowerCase()).append(": ").append(sets).append('\n'));
        ObjectNode content = mapper.createObjectNode();
        content.put("type", "text");
        content.put("text", text.toString());
        return new AiRequest(props.explainModelOrDefault(), SYSTEM_PROMPT, List.of(content), TOOL, "Report the summary and tips.", schema, 600);
    }

    /** Checks and cleans the answer; a summary without text is refused. */
    static Summary parse(JsonNode input) {
        String text = input == null ? "" : AiText.clean(input.path("summary").asText(""), MAX_SUMMARY);
        if (text.isEmpty()) {
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "The AI service gave a summary that could not be used");
        }
        List<String> tips = new ArrayList<>();
        if (input.path("tips").isArray()) {
            for (JsonNode tip : input.path("tips")) {
                String clean = AiText.clean(tip.asText(""), MAX_TIP);
                if (!clean.isEmpty() && tips.size() < MAX_TIPS) {
                    tips.add(clean);
                }
            }
        }
        return new Summary(text, tips);
    }
}
