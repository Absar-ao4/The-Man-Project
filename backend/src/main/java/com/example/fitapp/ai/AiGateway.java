package com.example.fitapp.ai;

import com.example.fitapp.ai.AiModelClient.AiReply;
import com.example.fitapp.ai.AiModelClient.AiRequest;
import com.example.fitapp.ai.AiModelClient.RawReply;
import com.example.fitapp.common.ApiException;
import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.config.AiPricingProperties;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/**
 * The single door to the AI provider. Every feature calls through here, so every call is refused
 * once the daily budget is spent and every call's cost is added to it.
 */
@Service
public class AiGateway {

    private final AiModelClient client;
    private final AiBudgetService budget;
    private final AiPricingProperties pricing;
    private final PhotoScanConfigService config;

    public AiGateway(
            AiModelClient client, AiBudgetService budget, AiPricingProperties pricing, PhotoScanConfigService config) {
        this.client = client;
        this.budget = budget;
        this.pricing = pricing;
        this.config = config;
    }

    public AiReply call(AiRequest request) {
        if (!config.get().enabled()) {
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "AI features are switched off");
        }
        if (budget.exhausted()) {
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "AI features are resting for today, please log manually");
        }
        RawReply raw = client.call(request);
        BigDecimal cost = pricing.costUsd(request.model(), raw.inputTokens(), raw.outputTokens());
        budget.add(cost);
        return new AiReply(raw.toolInput(), raw.inputTokens(), raw.outputTokens(), cost, request.model());
    }
}
