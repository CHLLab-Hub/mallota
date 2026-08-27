package com.malrota.dto.response;

import java.util.List;

public record BusRecommendResponse(
        List<BusRecommendation> recommendations,
        boolean routeExists    // false면 조건이 아니라 이 출발지-도착지 사이에 직행 노선 자체가 없다는 뜻
) {
}
