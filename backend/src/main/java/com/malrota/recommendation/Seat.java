package com.malrota.recommendation;

public record Seat(
        String seatNo,
        String position,
        String side,
        boolean available
) {
}