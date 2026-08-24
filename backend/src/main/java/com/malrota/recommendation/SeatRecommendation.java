package com.malrota.recommendation;

import java.util.List;

public record SeatRecommendation(
        Seat bestSeat,              // 대표 추천 좌석 (동률이면 앞줄·왼쪽 우선)
        int score,                  // 점수
        List<String> reasons,       // 추천 이유
        List<Seat> alternatives     // 같은 점수의 다른 좌석들 (UI에서 함께 표시)
) {
}