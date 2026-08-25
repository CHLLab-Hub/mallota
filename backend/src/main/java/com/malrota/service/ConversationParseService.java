package com.malrota.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.malrota.client.WatsonxClient;
import com.malrota.dto.request.ConversationParseRequest;
import com.malrota.dto.response.ConversationParseResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class ConversationParseService {


    private final WatsonxClient watsonxClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ConversationParseService(WatsonxClient watsonxClient) {
        this.watsonxClient = watsonxClient;
    }

        public ConversationParseResponse parse(ConversationParseRequest request) {
        String prompt = buildPrompt(request.text());
        String rawAnswer = watsonxClient.ask(prompt);

        try {
            String json = extractJson(rawAnswer);
            ConversationParseResponse parsed = objectMapper.readValue(json, ConversationParseResponse.class);
            return cleanNullStrings(parsed); // "null" 문자열 정리
        } catch (Exception e) {
            throw new RuntimeException("watsonx 응답을 해석하지 못했습니다: " + rawAnswer, e);
        }
    }

    // "null", "", 공백 문자열을 진짜 null로 정리
    private ConversationParseResponse cleanNullStrings(ConversationParseResponse r) {
        return new ConversationParseResponse(
                r.intent(),
                clean(r.departure()),
                clean(r.arrival()),
                clean(r.date()),
                clean(r.timePreference()),
                r.passengers(),
                r.seatPreferences(),
                r.accessibilityNeeds(),
                r.missingFields()
        );
    }

    private String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("null")) return null;
        return trimmed;
    }

    private String buildPrompt(String text) {
        LocalDate today = LocalDate.now();
        return """
                너는 고속버스 예매 도우미다. 아래 사용자 발화에서 정보를 추출해 JSON으로만 답하라.
                설명, 인사, 코드블록 표시 없이 순수 JSON 객체 하나만 출력하라.

                오늘 날짜는 %s 이다. "내일", "모레" 같은 표현은 이 날짜 기준으로 YYYY-MM-DD로 계산하라.

                JSON 형식:
                {
                  "intent": "BUS_SEARCH",
                  "departure": "출발지 또는 null",
                  "arrival": "도착지 또는 null",
                  "date": "YYYY-MM-DD 또는 null",
                  "timePreference": "MORNING/AFTERNOON/EVENING 또는 null",
                  "passengers": 1,
                  "seatPreferences": ["WINDOW", "FRONT" 등 배열],
                  "accessibilityNeeds": ["WALKING_DIFFICULTY" 등 배열],
                  "missingFields": ["departure/arrival/date 중 누락된 것"]
                }

                사용자 발화: %s
                """.formatted(today, text);
    }

    private String extractJson(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
    }
}