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
    List<String> seatPreferences,
    List<String> accessibilityNeeds,
    List<String> missingFields,
    String clarificationPrompt
) {
}
