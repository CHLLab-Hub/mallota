package com.malrota.dto.response;

import java.util.List;

public record ConversationParseResponse(
    String intent,
    String departure,
    String arrival,
    String date,
    String timePreference,
    int passengers,
    List<String> seatPreferences,
    List<String> accessibilityNeeds,
    List<String> missingFields
) {
}