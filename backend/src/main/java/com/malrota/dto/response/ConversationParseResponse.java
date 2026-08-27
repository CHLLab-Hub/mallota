package com.malrota.dto.response;

import java.util.List;

public record ConversationParseResponse(
    String intent,
    String departure,
    String arrival,
    String date,
    String departureTime,
    String timePreference,
    String servicePreference,
    String busGradePreference,
    int passengers,
    boolean passengerMentioned,
    List<String> seatPreferences,
    boolean seatPreferenceMentioned,
    List<String> accessibilityNeeds,
    List<String> missingFields,
    String clarificationPrompt,
    boolean wantsEarlierBus,
    boolean wantsLaterBus,
    boolean routeNotFound // true면 조건이 아니라 출발지-도착지 사이에 직행 노선 자체가 없다는 뜻
) {
}
