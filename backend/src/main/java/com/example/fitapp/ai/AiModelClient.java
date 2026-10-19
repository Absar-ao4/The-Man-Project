package com.example.fitapp.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.List;

/**
 * One call to an AI model that must answer through a single tool, so the reply is structured data
 * and never free text. A seam: tests replace it, the real one talks to the Anthropic Messages API.
 */
public interface AiModelClient {

    /** [content] are Messages API content blocks (text, image). */
    record AiRequest(
            String model,
            String system,
            List<ObjectNode> content,
            String toolName,
            String toolDescription,
            ObjectNode inputSchema,
            int maxTokens) {}

    record RawReply(JsonNode toolInput, int inputTokens, int outputTokens) {}

    /** What the app gets back: the tool input, the tokens used and what the call cost. */
    record AiReply(JsonNode toolInput, int inputTokens, int outputTokens, BigDecimal costUsd, String model) {}

    /**
     * Throws ApiException: LIMIT_REACHED (HTTP 429) if the provider is rate limiting, otherwise
     * UPSTREAM_UNAVAILABLE.
     */
    RawReply call(AiRequest request);
}
