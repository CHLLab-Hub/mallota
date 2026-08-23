package com.malrota.dto.response;

import java.util.List;

public record NluExtractResponse(
    String intent,                      // 의도: BOOKING, SEARCH, CANCEL, INQUIRY
    String departure,                   // 출발지 터미널명
    String arrival,                     // 도착지 터미널명
    String date,                        // 출발 날짜 (YYYY-MM-DD)
    String timePreference,              // 시간대 선호 (09:00, 오전, 막차 등)
    String busGrade,                    // 버스 등급 (우등, 프리미엄, 일반 등)
    String pricePreference,             // "CHEAPEST"(최저가 선호), "NORMAL", "PREMIUM"
    List<String> seatPreferences,       // 좌석 선호 (FRONT, AISLE, WINDOW 등)
    List<String> accessibilityNeeds,    // 약자/접근성 요구 (ELDERLY_CARE, LEG_PAIN 등)
    List<String> missingFields,         // 누락된 필수 필드 (["departure", "date"])
    String rawUtterance                 // 원본 발화
) {
}