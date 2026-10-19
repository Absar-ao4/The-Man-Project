package com.example.fitapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.usda")
public record UsdaProperties(String apiKey, String baseUrl) {

    public String baseUrlOrDefault() {
        return baseUrl == null || baseUrl.isBlank() ? "https://api.nal.usda.gov/fdc/v1" : baseUrl;
    }
}
