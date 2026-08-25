package com.malrota.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "watsonx")
public record WatsonxProperties(
        boolean enabled,
        String apiKey,
        String projectId,
        String url,
        String modelId
) {
}