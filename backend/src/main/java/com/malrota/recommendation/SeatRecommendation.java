package com.malrota.recommendation;

import java.util.List;

public record SeatRecommendation(
        Seat bestSeat,
        int score,
        List<String> reasons,
        List<Seat> alternatives,
        List<Seat> allSeats      // 전체 좌석 (배치도용)
) {
}