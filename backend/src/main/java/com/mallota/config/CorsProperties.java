package com.mallota.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "mallota.cors")
public record CorsProperties(List<String> allowedOrigins) {

    private static final List<String> DEFAULT_ORIGINS = List.of("http://localhost:3000");

    public CorsProperties {
        if (allowedOrigins == null) {
            allowedOrigins = DEFAULT_ORIGINS;
        } else {
            allowedOrigins = allowedOrigins.stream()
                    .map(String::trim)
                    .filter(origin -> !origin.isEmpty())
                    .toList();

            if (allowedOrigins.isEmpty()) {
                allowedOrigins = DEFAULT_ORIGINS;
            }
        }
    }
}
