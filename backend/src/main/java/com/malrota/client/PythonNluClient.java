package com.malrota.client;

import com.malrota.domain.ConversationSession;
import com.malrota.dto.response.ConversationParseResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

@Component
public class PythonNluClient {

    private final RestClient restClient = RestClient.create();
    private static final String PYTHON_URL = "http://localhost:8000/api/conversation/parse";

    public ConversationParseResponse parse(String text, ConversationSession session) {
        try {
            Map<String, Object> currentState = buildCurrentState(session);
            return restClient.post()
                    .uri(PYTHON_URL)
                    .body(new PythonRequest(text, currentState))
                    .retrieve()
                    .body(ConversationParseResponse.class);
        } catch (Exception e) {
            return null;
        }
    }

    // 세션의 기존 조건을 파이썬이 받는 형식으로 변환
    private Map<String, Object> buildCurrentState(ConversationSession session) {
        Map<String, Object> state = new HashMap<>();
        if (session == null) return state;
        putIfNotBlank(state, "departure", session.getDeparture());
        putIfNotBlank(state, "arrival", session.getArrival());
        putIfNotBlank(state, "date", session.getDate());
        putIfNotBlank(state, "departureTime", session.getDepartureTime());
        putIfNotBlank(state, "timePreference", session.getTimePreference());
        putIfNotBlank(state, "servicePreference", session.getServicePreference());
        putIfNotBlank(state, "busGradePreference", session.getBusGradePreference());
        state.put("passengers", session.getPassengers());
        state.put("seatPreferences", session.getSeatPreferences());
        state.put("accessibilityNeeds", session.getAccessibilityNeeds());
        return state;
    }

    private void putIfNotBlank(Map<String, Object> state, String key, String value) {
        if (value != null && !value.isBlank()) state.put(key, value);
    }

    // 파이썬 서버가 받는 요청 형식 (text + currentState)
    private record PythonRequest(String text, Map<String, Object> currentState) {}
}