package com.malrota.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record BookingCreateRequest(
        @NotBlank @Size(max = 100) String ownerId,
        @NotBlank @Size(max = 100) String routeId,
        @NotBlank @Size(max = 80) String grade,
        @NotBlank @Size(max = 100) String departure,
        @NotBlank @Size(max = 100) String arrival,
        @NotBlank @Size(max = 20) String departureTime,
        @NotBlank @Size(max = 20) String arrivalTime,
        @Positive int charge,
        @NotBlank @Size(max = 100) String seatNo,
        @Positive int passengers,
        @Positive int totalFare
) {
}
