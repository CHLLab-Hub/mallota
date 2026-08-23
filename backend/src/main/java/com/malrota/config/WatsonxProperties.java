package com.malrota.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "watsonx")
public record WatsonxProperties(
        String apiKey,
        String projectId,
        String url,
        String modelId
) {
}