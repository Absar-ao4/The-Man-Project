package com.example.fitapp.config;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * What an AI call costs, for the daily budget. Prices are USD per million tokens and live in
 * application.yml so they can be corrected without a release.
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiPricingProperties(Map<String, Price> prices, Integer maxImageBytes, Map<String, Integer> dailyLimits) {

    public record Price(BigDecimal input, BigDecimal output) {}

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);
    private static final int DEFAULT_DAILY_LIMIT = 10;

    /** Per user per day uses of an AI feature (app.ai.daily-limits.&lt;feature&gt;). A feature with no entry gets 10. */
    public int dailyLimitFor(String feature) {
        Integer configured = dailyLimits == null ? null : dailyLimits.get(feature);
        return configured == null ? DEFAULT_DAILY_LIMIT : configured;
    }

    public int maxImageBytesOrDefault() {
        return maxImageBytes == null ? 4 * 1024 * 1024 : maxImageBytes;
    }

    /**
     * Cost of one call. A model that is not in the table is charged at the most expensive known price, so a
     * newly configured model can never make the budget look smaller than it is.
     */
    public BigDecimal costUsd(String model, int inputTokens, int outputTokens) {
        Price price = prices == null ? null : prices.get(model);
        if (price == null && prices != null && !prices.isEmpty()) {
            price =
                    prices.values().stream()
                            .max((a, b) -> a.output().compareTo(b.output()))
                            .orElse(null);
        }
        if (price == null) {
            return BigDecimal.ZERO;
        }
        return price.input()
                .multiply(BigDecimal.valueOf(inputTokens))
                .add(price.output().multiply(BigDecimal.valueOf(outputTokens)))
                .divide(MILLION, 6, java.math.RoundingMode.HALF_UP);
    }
}
