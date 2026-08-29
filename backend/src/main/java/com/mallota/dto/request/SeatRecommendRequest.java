package com.mallota.dto.request;

import java.util.List;

public record SeatRecommendRequest(
        String busGrade,                  // 버스 등급 (우등, 고속 등 — 나중에 등급별 좌석에 쓸 것)
        List<String> seatPreferences,     // 좌석 선호 (WINDOW, FRONT 등)
        List<String> accessibilityNeeds,   // 접근성 요구 (WALKING_DIFFICULTY 등)
        Integer passengers
) {
        public SeatRecommendRequest(String busGrade, List<String> seatPreferences, List<String> accessibilityNeeds){
                this(busGrade, seatPreferences, accessibilityNeeds, 1);
        }        
}