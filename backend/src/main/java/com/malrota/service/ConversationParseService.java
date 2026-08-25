package com.malrota.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.malrota.client.PythonNluClient;
import com.malrota.client.WatsonxClient;
import com.malrota.domain.ConversationSession;
import com.malrota.dto.request.ConversationParseRequest;
import com.malrota.dto.response.ConversationParseResponse;
import com.malrota.service.nlu.ConversationRuleExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.malrota.client.PythonNluClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ConversationParseService {

    private final WatsonxClient watsonxClient;
    private final ConversationRuleExtractor ruleExtractor;
    private final PythonNluClient pythonNluClient;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public ConversationParseService(WatsonxClient watsonxClient, ConversationRuleExtractor ruleExtractor, PythonNluClient pythonNluClient) {
        this.watsonxClient = watsonxClient;
        this.ruleExtractor = ruleExtractor;
        this.pythonNluClient = pythonNluClient;
    }

    public ConversationParseResponse parse(ConversationParseRequest request) {
        return parse(request, null);
    }

    public ConversationParseResponse parse(ConversationParseRequest request, ConversationSession session) {
        // 1. 파이썬 NLU 서버 호출 (우선)
        ConversationParseResponse pythonResult = pythonNluClient.parse(request.text(), session);
        if (pythonResult != null) {
            log.info("[ConversationParseService] 파이썬 NLU 결과 사용");
            return pythonResult;
        }

        // 2. 파이썬 실패 시 기존 방식 (룰베이스 + watsonx)
        log.warn("[ConversationParseService] 파이썬 NLU 실패, 기존 방식으로 대체");
        LocalDateTime now = LocalDateTime.now();
        String isoDateTime = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "+09:00";

        ConversationRuleExtractor.RuleParse rules = ruleExtractor.extract(request.text(), now);
        ConversationParseResponse llmResult = null;

        if (watsonxClient != null && watsonxClient.isConfigured()) {
            try {
                String prompt = buildPrompt(request.text(), isoDateTime, session);
                String rawAnswer = watsonxClient.generate(prompt);
                llmResult = objectMapper.readValue(extractJson(rawAnswer), ConversationParseResponse.class);
            } catch (Exception e) {
                log.warn("[ConversationParseService] LLM 호출 실패, 룰베이스 결과로 대체: {}", e.getMessage());
            }
        }

        return normalize(llmResult, rules, session);
    }

    private ConversationParseResponse normalize(ConversationParseResponse llm,
                                                ConversationRuleExtractor.RuleParse rules,
                                                ConversationSession session) {
        String intent = firstNonBlank(rules.intent(), value(llm, ConversationParseResponse::intent), "BUS_SEARCH");
        String departure = firstNonBlank(rules.departure(), value(llm, ConversationParseResponse::departure), sessionValue(session, ConversationSession::getDeparture));
        String arrival = firstNonBlank(rules.arrival(), value(llm, ConversationParseResponse::arrival), sessionValue(session, ConversationSession::getArrival));
        String date = firstNonBlank(rules.date() == null ? null : rules.date().toString(), value(llm, ConversationParseResponse::date), sessionValue(session, ConversationSession::getDate));
        String departureTime = firstNonBlank(rules.departureTime() == null ? null : rules.departureTime().toString(), value(llm, ConversationParseResponse::departureTime), sessionValue(session, ConversationSession::getDepartureTime));
        String timePreference = firstNonBlank(rules.timePreference(), value(llm, ConversationParseResponse::timePreference), sessionValue(session, ConversationSession::getTimePreference), "ANY");
        String servicePreference = firstNonBlank(rules.servicePreference(), value(llm, ConversationParseResponse::servicePreference), sessionValue(session, ConversationSession::getServicePreference), "ANY");
        String busGradePreference = firstNonBlank(rules.busGradePreference(), value(llm, ConversationParseResponse::busGradePreference), sessionValue(session, ConversationSession::getBusGradePreference), "ANY");
        
        int passengers = rules.passengers() > 1 ? rules.passengers()
                : llm != null && llm.passengers() > 1 ? llm.passengers()
                : session != null && session.getPassengers() > 1 ? session.getPassengers() : 1;

        List<String> seatPreferences = mergePreferences(session == null ? List.of() : session.getSeatPreferences(),
                llm == null ? null : llm.seatPreferences(), rules.seatPreferences(), rules.seatPreferenceMentioned());
        List<String> accessibilityNeeds = mergePreferences(session == null ? List.of() : session.getAccessibilityNeeds(),
                llm == null ? null : llm.accessibilityNeeds(), rules.accessibilityNeeds(), rules.accessibilityMentioned());

        List<String> missing = missingRequired(departure, arrival, date, departureTime, timePreference);
        
        // ⭐ 깔끔하게 정리된 반문 생성 호출
        String prompt = clarificationPrompt(missing, departure, arrival, seatPreferences, accessibilityNeeds);

        return new ConversationParseResponse(
                intent, nullIfBlank(departure), nullIfBlank(arrival), nullIfBlank(date),
                nullIfBlank(departureTime), timePreference, servicePreference, busGradePreference, passengers,
                seatPreferences, accessibilityNeeds, missing, prompt
        );
    }

    private List<String> mergePreferences(List<String> existing, List<String> llmValues, List<String> ruleValues, boolean explicitlyMentioned) {
        Set<String> result = new LinkedHashSet<>();
        if (!explicitlyMentioned) {
            addAll(result, existing);
            addAll(result, llmValues);
        } else {
            addAll(result, ruleValues);
            if (result.isEmpty() && llmValues != null) addAll(result, llmValues);
        }
        return new ArrayList<>(result);
    }

    private void addAll(Set<String> target, List<String> values) {
        if (values != null) values.stream().filter(v -> v != null && !v.isBlank() && !"null".equalsIgnoreCase(v)).forEach(target::add);
    }

    private List<String> missingRequired(String departure, String arrival, String date, String depTime, String timePref) {
        List<String> missing = new ArrayList<>();
        if (isBlank(departure)) missing.add("departure");
        if (isBlank(arrival)) missing.add("arrival");
        if (isBlank(date)) missing.add("date");
        if (isBlank(depTime) && (isBlank(timePref) || "ANY".equalsIgnoreCase(timePref))) {
            missing.add("timePreference");
        }
        return missing;
    }

    private String clarificationPrompt(List<String> missing, String departure, String arrival, 
                                       List<String> seatPrefs, List<String> accessNeeds) {
        // 필수값(출발/도착/날짜/시간) 누락 시 질문
        if (!missing.isEmpty()) {
            if (missing.contains("departure") && missing.contains("arrival")) {
                return "어디에서 출발해서 어디로 가시나요? 출발지와 도착지를 말씀해 주세요.";
            }
            if (missing.contains("departure")) {
                return (arrival != null && !arrival.isBlank() ? arrival + "행 " : "") + "버스를 탈 출발 터미널을 말씀해 주세요. (강남/동서울 등)";
            }
            if (missing.contains("arrival")) {
                return (departure != null && !departure.isBlank() ? departure + "에서 " : "") + "어디로 가시나요?";
            }
            if (missing.contains("date") && missing.contains("timePreference")) {
                return "언제 출발하시나요? '내일 아침', '이번 주말 오후'처럼 날짜와 시간대를 편하게 말씀해 주세요.";
            }
            if (missing.contains("date")) {
                return "출발하시는 날짜를 말씀해 주세요. '오늘', '내일', '이번 주 토요일'처럼 말씀하셔도 됩니다.";
            }
            if (missing.contains("timePreference")) {
                return "몇 시쯤 출발하는 버스를 원하시나요? '오전 9시', '오후 3시', '첫차', '막차'처럼 말씀해 주세요.";
            }
        }

        // 필수값 4개가 모두 찼지만, 약자/좌석 조건이 비어있는 경우 -> 배려 질문 생성!
        boolean hasNoPreferences = (seatPrefs == null || seatPrefs.isEmpty()) && (accessNeeds == null || accessNeeds.isEmpty());
        if (hasNoPreferences) {
            String depStr = (departure != null && !departure.isBlank()) ? departure + "에서 " : "";
            String arrStr = (arrival != null && !arrival.isBlank()) ? arrival + " 가는 " : "";
            return depStr + arrStr + "표를 찾을게요. 혹시 다리가 불편하시거나 창가/통로 등 더 편하신 자리가 있으신가요?";
        }

        return null;
    }

    private String buildPrompt(String text, String isoDateTime, ConversationSession session) {
        String currentStateJson = session == null ? "{}" : """
                {"departure":"%s","arrival":"%s","date":"%s","departureTime":"%s","timePreference":"%s","servicePreference":"%s","busGradePreference":"%s","passengers":%d,"seatPreferences":%s,"accessibilityNeeds":%s}
                """.formatted(jsonValue(session.getDeparture()), jsonValue(session.getArrival()), jsonValue(session.getDate()),
                jsonValue(session.getDepartureTime()), jsonValue(session.getTimePreference()), jsonValue(session.getServicePreference()),
                jsonValue(session.getBusGradePreference()), session.getPassengers(), jsonArray(session.getSeatPreferences()), jsonArray(session.getAccessibilityNeeds()));

        return """
            당신은 고령자(디지털 소외계층) 및 교통약자를 위한 고속버스 예매 서비스의 자연어 조건 추출(NLU) 인공지능입니다.
            공손하고 차분한 어투로 차근차근 설명해줘야 하고, 사용자 음성에서 추출한 조건을 절대 넘겨 짚지 않아야 합니다.
            사용자 발화와 기존 수집 정보를 해석하여, 아래에 정의된 JSON 객체만 반환하세요.
            설명, Markdown(백틱), 추가 문장, 질문을 절대 출력하지 마세요.

            [입력 정보]
            - 기준 시각: %s (Asia/Seoul)
            - 기존 수집 정보: %s

            [핵심 추출 규칙]
            1. 지명/터미널: '~행'(부산행 등)은 arrival, '~발'(서울발 등)은 departure에 지명만 저장
            2. 날짜/시간: 기준시각 참고하여 절대날짜(YYYY-MM-DD) 변환. "첫차/시방/빨리"->servicePreference:"FIRST", "막차"->"LAST"
            3. 탑승 인원: 가족/동행(할머니, 손주, 영감, 바깥양반 등)과 '함께/둘이/데리고' 타면 -> passengers: 2 & accessibilityNeeds에 "ELDERLY_CARE" 추가
            4. 신체/좌석 배려:
               - 다리/무릎 통증, 도가니, 시큰거림, 삭신, 계단 힘듦 -> accessibilityNeeds에 "WALKING_DIFFICULTY" & seatPreferences에 "FRONT"
               - 멀미, 속 울렁거림, 메스꺼움 -> accessibilityNeeds에 "MOTION_SICKNESS" & seatPreferences에 "MIDDLE"
            5. 등급 선호: "우등"->EXCELLENT, "프리미엄/편한 거"->PREMIUM, "일반/싼 거/싼 놈"->GENERAL, "아무거나"->ANY
            6. 상태 병합: 새로 언급된 조건은 갱신하고, 언급되지 않은 기존 조건은 유지

            [반환 JSON 스키마]
            {
              "intent": "BUS_SEARCH | CANCEL | INQUIRY",
              "departure": "string | null",
              "arrival": "string | null",
              "date": "YYYY-MM-DD | null",
              "departureTime": "HH:MM | null",
              "timePreference": "MORNING | AFTERNOON | EVENING | NIGHT | ANY",
              "servicePreference": "FIRST | LAST | ANY",
              "busGradePreference": "GENERAL | EXCELLENT | PREMIUM | ANY",
              "passengers": 1,
              "seatPreferences": [],
              "accessibilityNeeds": []
            }

            [예시 1 - 표준 발화 및 보행 배려]
            기준 시각: 2026-08-24T10:00:00+09:00
            기존 수집 정보: {}
            사용자: "내일 오전 서울에서 대전 가는데 우등으로, 다리가 불편해서 앞쪽 창가로 줘"
            결과:
            {"intent":"BUS_SEARCH","departure":"서울","arrival":"대전","date":"2026-08-25","departureTime":null,"timePreference":"MORNING","servicePreference":"ANY","busGradePreference":"EXCELLENT","passengers":1,"seatPreferences":["FRONT","WINDOW"],"accessibilityNeeds":["WALKING_DIFFICULTY"]}

            [예시 2 - 사투리 발화 및 손주 동행]
            기준 시각: 2026-08-24T10:00:00+09:00
            기존 수집 정보: {}
            사용자: "손주 아 데꼬 부산행 젤 빠른 거 둘이 탈 건데 계단 타기 하영 힘들어"
            결과:
            {"intent":"BUS_SEARCH","departure":null,"arrival":"부산","date":null,"departureTime":null,"timePreference":"ANY","servicePreference":"FIRST","busGradePreference":"ANY","passengers":2,"seatPreferences":["FRONT"],"accessibilityNeeds":["WALKING_DIFFICULTY","ELDERLY_CARE"]}

            [예시 3 - 멀티턴 상태 수정]
            기준 시각: 2026-08-24T10:00:00+09:00
            기존 수집 정보: {"intent":"BUS_SEARCH","departure":"서울","arrival":"대전","date":"2026-08-25","departureTime":null,"timePreference":"MORNING","servicePreference":"ANY","busGradePreference":"EXCELLENT","passengers":1,"seatPreferences":["FRONT"],"accessibilityNeeds":["WALKING_DIFFICULTY"]}
            사용자: "우등 말고 젤 싼 일반으로 바꿔줘"
            결과:
            {"intent":"BUS_SEARCH","departure":"서울","arrival":"대전","date":"2026-08-25","departureTime":null,"timePreference":"MORNING","servicePreference":"ANY","busGradePreference":"GENERAL","passengers":1,"seatPreferences":["FRONT"],"accessibilityNeeds":["WALKING_DIFFICULTY"]}

            [실제 입력]
            기준 시각: %s
            기존 수집 정보: %s
            사용자: "%s"
            결과:
            """.formatted(isoDateTime, currentStateJson, isoDateTime, currentStateJson, text);
    }

    private String extractJson(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        return start >= 0 && end > start ? raw.substring(start, end + 1) : raw;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (!isBlank(value) && !"null".equalsIgnoreCase(value)) return value;
        return null;
    }

    private String nullIfBlank(String value) {
        return isBlank(value) || "null".equalsIgnoreCase(value) ? null : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String jsonValue(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }

    private String jsonArray(List<String> values) {
        if (values == null) return "[]";
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> "\"" + jsonValue(value) + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private String sessionValue(ConversationSession session, SessionStringGetter getter) {
        return session == null ? null : getter.get(session);
    }

    private String value(ConversationParseResponse response, ResponseStringGetter getter) {
        return response == null ? null : getter.get(response);
    }

    @FunctionalInterface
    private interface SessionStringGetter { String get(ConversationSession session); }

    @FunctionalInterface
    private interface ResponseStringGetter { String get(ConversationParseResponse response); }
}