package com.malrota.dto.request;

import jakarta.validation.constraints.NotBlank;

public record BusSearchRequest(
        @NotBlank(message = "출발지를 입력해 주세요.")
        String departure,
        @NotBlank(message = "도착지를 입력해 주세요.")
        String arrival,
        @NotBlank(message = "출발일을 입력해 주세요.")
        String date
) {
}