package com.mallota.dto.response;

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
    boolean routeNotFound, // true면 조건이 아니라 출발지-도착지 사이에 직행 노선 자체가 없다는 뜻
    // STT 오인식을 LLM이 문맥으로 교정한 원문(예: "참가죽" -> "창가 쪽", "두잠" -> "두 장"). LLM
    // 호출 결과에만 채워지며, 내부에서 직접 만드는 응답(노선 없음 등)에는 null이다.
    String correctedText
) {
}
