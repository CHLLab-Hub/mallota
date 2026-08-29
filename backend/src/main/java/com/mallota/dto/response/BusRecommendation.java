package com.mallota.dto.response;

import java.util.List;

public record BusRecommendation(
        BusSchedule bus,       // 추천 버스
        String reason,         // 추천 이유 (예: "가장 저렴한 버스입니다")
        List<String> labels    // 짧은 표시용 뱃지들. 같은 버스가 두 조건을 모두 만족하면 뱃지가 2개 붙는다.
) {
}