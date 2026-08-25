package com.malrota.dto.request;

import java.util.List;

public record SeatRecommendRequest(
    List<String> seatPreferences,
    List<String> accessibilityNeeds,
    String busGrade,
    Integer passengers // 인원수 추가
) {
    // 3개 필드만 받는 기존 호출도 호환
    public SeatRecommendRequest(List<String> seatPreferences, List<String> accessibilityNeeds, String busGrade) {
        this(seatPreferences, accessibilityNeeds, busGrade, 1);
    }
}