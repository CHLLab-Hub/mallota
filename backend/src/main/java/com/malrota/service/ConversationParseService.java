package com.malrota.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.malrota.client.WatsonxClient;
import com.malrota.domain.ConversationSession;
import com.malrota.dto.request.ConversationParseRequest;
import com.malrota.dto.response.ConversationParseResponse;
import com.malrota.service.nlu.ConversationRuleExtractor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** watsonx는 추출 보조 역할만 하고, 필수값 판정과 상대 날짜 보정은 서버가 수행한다. */
@Service
public class ConversationParseService {

    private final WatsonxClient watsonxClient;
    private final ConversationRuleExtractor ruleExtractor;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public ConversationParseService(WatsonxClient watsonxClient, ConversationRuleExtractor ruleExtractor) {
        this.watsonxClient = watsonxClient;
        this.ruleExtractor = ruleExtractor;
    }

    /** 세션을 사용하지 않는 기존 /search 호환용 진입점. */
    public ConversationParseResponse parse(ConversationParseRequest request) {
        return parse(request, null);
    }

    /** 현재 서버 세션을 포함해 한 턴의 조건을 추출하고 완성 상태를 반환한다. */
    public ConversationParseResponse parse(ConversationParseRequest request, ConversationSession session) {
        LocalDateTime now = LocalDateTime.now();
        ConversationRuleExtractor.RuleParse rules = ruleExtractor.extract(request.text(), now);
        ConversationParseResponse llmResult = null;

        if (watsonxClient.isConfigured()) {
            try {
                String rawAnswer = watsonxClient.ask(buildPrompt(request.text(), now.toLocalDate(), session));
                llmResult = objectMapper.readValue(extractJson(rawAnswer), ConversationParseResponse.class);
            } catch (Exception ignored) {
                // 키·네트워크·LLM JSON 오류가 있어도 기본 대화 흐름은 계속 동작한다.
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
        int passengers = rules.passengers() > 0 ? rules.passengers()
                : llm != null && llm.passengers() > 0 ? llm.passengers()
                : session != null && session.getPassengers() > 0 ? session.getPassengers() : 1;

        List<String> seatPreferences = mergePreferences(session == null ? List.of() : session.getSeatPreferences(),
                llm == null ? null : llm.seatPreferences(), rules.seatPreferences(), rules.seatPreferenceMentioned());
        List<String> accessibilityNeeds = mergePreferences(session == null ? List.of() : session.getAccessibilityNeeds(),
                llm == null ? null : llm.accessibilityNeeds(), rules.accessibilityMentioned() ? rules.accessibilityNeeds() : List.of(),
                rules.accessibilityMentioned());

        List<String> missing = missingRequired(departure, arrival, date);
        return new ConversationParseResponse(intent, nullIfBlank(departure), nullIfBlank(arrival), nullIfBlank(date),
                nullIfBlank(departureTime), timePreference, servicePreference, busGradePreference, passengers,
                seatPreferences, accessibilityNeeds, missing, clarificationPrompt(missing, departure, arrival));
    }

    /** 빈 배열을 반환한 LLM 때문에 기존 세션 선호가 사라지지 않도록 병합한다. */
    private List<String> mergePreferences(List<String> existing, List<String> llmValues,
                                          List<String> ruleValues, boolean explicitlyMentioned) {
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
        if (values != null) values.stream().filter(value -> value != null && !value.isBlank()).forEach(target::add);
    }

    private List<String> missingRequired(String departure, String arrival, String date) {
        List<String> missing = new ArrayList<>();
        if (isBlank(departure)) missing.add("departure");
        if (isBlank(arrival)) missing.add("arrival");
        if (isBlank(date)) missing.add("date");
        return missing;
    }

    private String clarificationPrompt(List<String> missing, String departure, String arrival) {
        if (missing.isEmpty()) return null;
        if (missing.contains("departure") && missing.contains("arrival")) return "어디에서 출발해서 어디로 가시나요? 출발지와 도착지를 말씀해 주세요.";
        if (missing.contains("departure")) return "%s행 버스를 찾을 출발지를 말씀해 주세요.".formatted(arrival);
        if (missing.contains("arrival")) return "%s에서 어디로 가시나요?".formatted(departure);
        return "언제 출발하시나요? 오늘, 내일, 이번 주 토요일처럼 말씀해 주세요.";
    }

    private String buildPrompt(String text, LocalDate today, ConversationSession session) {
        String currentState = session == null ? "{}" : """
                {"departure":"%s","arrival":"%s","date":"%s","departureTime":"%s","timePreference":"%s","servicePreference":"%s","busGradePreference":"%s","passengers":%d,"seatPreferences":%s,"accessibilityNeeds":%s}
                """.formatted(jsonValue(session.getDeparture()), jsonValue(session.getArrival()), jsonValue(session.getDate()),
                jsonValue(session.getDepartureTime()), jsonValue(session.getTimePreference()), jsonValue(session.getServicePreference()),
                jsonValue(session.getBusGradePreference()), session.getPassengers(), jsonArray(session.getSeatPreferences()), jsonArray(session.getAccessibilityNeeds()));
        return """
                당신은 고속버스 예매 서비스의 자연어 조건 추출기다. 사용자 발화에서 말한 정보만 JSON 하나로 반환하라.
                설명, Markdown, 터미널 ID, 운행편, 요금, 좌석 재고, 예약 결과는 절대 출력하지 마라.
                오늘 날짜는 %s이며 내일·모레·요일 같은 상대 날짜는 YYYY-MM-DD로 변환한다.
                기존 수집 상태: %s
                반환 키: intent(BUS_SEARCH/CANCEL/INQUIRY), departure, arrival, date, departureTime(HH:MM 또는 null),
                timePreference(MORNING/AFTERNOON/EVENING/NIGHT/ANY), servicePreference(FIRST/LAST/ANY),
                busGradePreference(GENERAL/EXCELLENT/PREMIUM/ANY), passengers, seatPreferences, accessibilityNeeds.
                출발지·도착지·날짜가 모두 있을 때에만 서버가 버스 API를 호출한다. 없는 값은 추측하지 말고 null로 둔다.
                "창가 말고 통로"처럼 수정한 선호는 새 값으로 교체하고, 현재 발화에 없는 기존 조건은 유지한다.
                사용자 발화: %s
                """.formatted(today, currentState, text);
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
