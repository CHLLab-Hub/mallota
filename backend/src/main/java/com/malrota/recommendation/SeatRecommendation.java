package com.malrota.recommendation;

import java.util.List;

public record SeatRecommendation(
        Seat bestSeat,
        int score,
        List<String> reasons,
        List<Seat> alternatives,
        boolean adjacentPair,           // alternatives가 bestSeat과 함께 배정된 그룹 좌석인지 (2/3/4인 배치, 동률 대안이 아님)
        List<Seat> allSeats,            // 전체 좌석 (배치도용)
        List<Seat> tiedAlternativeSeats // 추천(그룹 포함)과 점수가 동률인 다른 좌석/그룹 — "같은 조건 좌석" 표시용
) {
}
