package com.example.fitapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for calls to the Anthropic API. The key only ever lives in the backend environment
 * (ANTHROPIC_API_KEY); the app never sees it.
 */
@ConfigurationProperties(prefix = "app.anthropic")
public record AnthropicProperties(
        String apiKey, String baseUrl, String explainModel, Integer explainDailyLimit) {

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String baseUrlOrDefault() {
        return baseUrl == null || baseUrl.isBlank() ? "https://api.anthropic.com" : baseUrl;
    }

    public String explainModelOrDefault() {
        return explainModel == null || explainModel.isBlank() ? "claude-haiku-5-5" : explainModel;
    }

    public int explainDailyLimitOrDefault() {
        return explainDailyLimit == null ? 10 : explainDailyLimit;
    }
}
