package com.mallota.controller;

import com.mallota.dto.response.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public HealthResponse health() {
    return new HealthResponse("UP", "mallota-backend", java.time.LocalDateTime.now());
}
}
