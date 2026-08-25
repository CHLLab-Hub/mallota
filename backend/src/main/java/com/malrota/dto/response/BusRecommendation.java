package com.malrota.dto.response;

public record BusRecommendation(
        BusSchedule bus,     // 추천 버스
        String reason,       // 추천 이유 (예: "가장 저렴한 버스입니다")
        String label         // 짧은 표시용 (예: "최저가", "추천시간", "다른시간")
) {
}