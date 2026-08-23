package com.malrota.dto.response;

import java.time.LocalDateTime;

public record HealthResponse(
    String status,          
    String service,         
    LocalDateTime timestamp 
) {
    public static HealthResponse ok() {
        return new HealthResponse("UP", "malrota-backend", LocalDateTime.now());
    }
}
