package com.example.fitapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Requests per minute per client address. [expensivePerMinute] covers the endpoints that cost money or do heavy work (the
 * AI calls, building a diet plan, the data export); a value that is not set falls back to 30.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(int authPerMinute, int searchPerMinute, int expensivePerMinute) {

    static final int DEFAULT_EXPENSIVE_PER_MINUTE = 30;

    public RateLimitProperties {
        if (expensivePerMinute <= 0) {
            expensivePerMinute = DEFAULT_EXPENSIVE_PER_MINUTE;
        }
    }
}
