package com.malrota.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tago")
public record TagoProperties(
        String serviceKey,
        String baseUrl
) {
}