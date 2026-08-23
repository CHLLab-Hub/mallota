package com.malrota.dto.response;

public record BusSchedule(
        String routeId,        // 노선ID
        String grade,          // 버스등급 (우등, 고속 등)
        String departure,      // 출발지
        String arrival,        // 도착지
        String departureTime,  // 출발시간
        String arrivalTime,    // 도착시간
        int charge             // 운임(요금)
) {
}