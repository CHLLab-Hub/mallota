package com.malrota.dto.request;

import jakarta.validation.constraints.NotBlank;

public record BusSearchRequest(
        @NotBlank(message = "출발지를 입력해 주세요.")
        String departure,
        @NotBlank(message = "도착지를 입력해 주세요.")
        String arrival,
        @NotBlank(message = "출발일을 입력해 주세요.")
        String date,
        String departureTime,
        String timePreference,
        String servicePreference,
        String busGradePreference
) {
    /** 기존 호출부 및 단순 조회 요청과의 호환용 생성자. */
    public BusSearchRequest(String departure, String arrival, String date) {
        this(departure, arrival, date, null, null, null, null);
    }
}
