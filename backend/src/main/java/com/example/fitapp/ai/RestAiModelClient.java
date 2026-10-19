package com.example.fitapp.ai;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.config.AnthropicProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * The Anthropic Messages API over RestClient. The key only exists in the backend environment and is
 * never logged: on failure only the HTTP status is, because exception messages can carry request
 * details. Images and prompts are never stored or logged.
 */
@Component
public class RestAiModelClient implements AiModelClient {

    private static final Logger log = LoggerFactory.getLogger(RestAiModelClient.class);
    private static final String VERSION = "2023-06-01";

    private final RestClient client;
    private final AnthropicProperties props;
    private final ObjectMapper mapper;

    public RestAiModelClient(AnthropicProperties props, ObjectMapper mapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(60_000);
        this.client = RestClient.builder().baseUrl(props.baseUrlOrDefault()).requestFactory(factory).build();
        this.props = props;
        this.mapper = mapper;
    }

    @Override
    public RawReply call(AiRequest request) {
        if (!props.configured()) {
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "AI features are not set up on this server");
        }
        try {
            JsonNode reply =
                    client.post()
                            .uri("/v1/messages")
                            .header("x-api-key", props.apiKey())
                            .header("anthropic-version", VERSION)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body(request))
                            .retrieve()
                            .body(JsonNode.class);
            return parse(reply, request.toolName());
        } catch (RestClientResponseException e) {
            log.warn("Anthropic call failed with HTTP {}", e.getStatusCode().value());
            if (e.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
                throw new ApiException(ErrorCode.LIMIT_REACHED, "The AI service is busy, try again in a minute");
            }
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "The AI service is unavailable");
        } catch (RestClientException e) {
            log.warn("Anthropic call failed: {}", e.getClass().getSimpleName());
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "The AI service is unavailable");
        }
    }

    private ObjectNode body(AiRequest request) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", request.model());
        body.put("max_tokens", request.maxTokens());
        body.put("system", request.system());

        ObjectNode tool = body.putArray("tools").addObject();
        tool.put("name", request.toolName());
        tool.put("description", request.toolDescription());
        tool.set("input_schema", request.inputSchema());
        body.putObject("tool_choice").put("type", "tool").put("name", request.toolName());

        ObjectNode message = body.putArray("messages").addObject();
        message.put("role", "user");
        var content = message.putArray("content");
        request.content().forEach(content::add);
        return body;
    }

    /** Package private so the parsing can be tested without a network. */
    static RawReply parse(JsonNode reply, String toolName) {
        if (reply != null && reply.path("content").isArray()) {
            for (JsonNode block : reply.get("content")) {
                if ("tool_use".equals(block.path("type").asText()) && toolName.equals(block.path("name").asText())) {
                    JsonNode usage = reply.path("usage");
                    return new RawReply(block.path("input"), usage.path("input_tokens").asInt(0), usage.path("output_tokens").asInt(0));
                }
            }
        }
        throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "The AI service gave an answer that could not be used");
    }
}
