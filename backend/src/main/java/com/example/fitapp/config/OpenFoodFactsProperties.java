package com.example.fitapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.openfoodfacts")
public record OpenFoodFactsProperties(String baseUrl, String userAgent) {

    public String baseUrlOrDefault() {
        return baseUrl == null || baseUrl.isBlank() ? "https://world.openfoodfacts.org" : baseUrl;
    }

    /** Open Food Facts asks every client to identify itself. */
    public String userAgentOrDefault() {
        return userAgent == null || userAgent.isBlank() ? "TheManProject/0.1 (fitness app backend)" : userAgent;
    }
}
