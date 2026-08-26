package com.malrota.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.malrota.client.WatsonxClient;
import com.malrota.domain.ConversationSession;
import com.malrota.dto.request.ConversationParseRequest;
import com.malrota.dto.response.ConversationParseResponse;
import com.malrota.service.nlu.ConversationRuleExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.malrota.client.TagoClient;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
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

    /** 세션 없는 단일 요청용 파싱 진입점 */
    public ConversationParseResponse parse(ConversationParseRequest request) {
        return parse(request, null);
    }

    /** 세션 기반 멀티턴 파싱 메인 진입점 */
    public ConversationParseResponse parse(ConversationParseRequest request, ConversationSession session) {
        LocalDateTime now = LocalDateTime.now();
        String isoDateTime = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "+09:00";
        String userText = extractRequestText(request);

        // 룰베이스 추출기 1차 실행 (시간 정규화 & 안전망)
        ConversationRuleExtractor.RuleParse rules = ruleExtractor.extract(userText, now);
        ConversationParseResponse llmResult = null;

        // watsonx.ai LLM 호출
        if (watsonxClient != null && watsonxClient.isConfigured()) {
            try {
                String prompt = buildPrompt(userText, isoDateTime, session);
                String rawAnswer = watsonxClient.ask(prompt);
                llmResult = objectMapper.readValue(extractJson(rawAnswer), ConversationParseResponse.class);
            } catch (Exception e) {
                log.warn("[ConversationParseService] LLM 호출 실패, 룰베이스 결과로 대체: {}", e.getMessage());
            }
        }

        // 3. LLM + 룰베이스 + 세션 상태 병합 및 반문 생성
        return normalize(llmResult, rules, session, userText);
    }

    private String extractRequestText(ConversationParseRequest request) {
        if (request == null) return "";
        try {
            return request.text() != null ? request.text() : "";
        } catch (NoSuchMethodError e) {
            return "";
        }
    }

    private ConversationParseResponse normalize(ConversationParseResponse llm,
                                                ConversationRuleExtractor.RuleParse rules,
                                                ConversationSession session,
                                                String rawText) {
        // 우선순위는 항상 "룰베이스(이번 발화에서 확실히 잡힘) → 세션(이미 확정된 사실) → LLM(추측)" 순이다.
        // LLM에게 "언급 없으면 기존 값을 그대로 복사하라"고 프롬프트에 명시했지만, 실제로는 지시를
        // 놓치고 자기 나름의 기본값(예: 특정 터미널 "서울경부"를 도시명 "서울"로, 인원 2명을 1명으로)을
        // 되돌려버리는 경우가 있었다. 그래서 LLM 결과를 세션보다 먼저 신뢰하면 이미 확정된 조건이
        // 아무 말도 안 했는데 갑자기 초기화되는 사고가 난다. LLM은 "룰베이스도 세션도 모르는" 첫 언급을
        // 보완하는 최후의 수단으로만 쓴다.
        String intent = firstNonBlank(rules.intent(), value(llm, ConversationParseResponse::intent), "BUS_SEARCH");
        String departure = firstNonBlank(rules.departure(), sessionValue(session, ConversationSession::getDeparture), value(llm, ConversationParseResponse::departure));
        String arrival = firstNonBlank(rules.arrival(), sessionValue(session, ConversationSession::getArrival), value(llm, ConversationParseResponse::arrival));
        
        // [문맥 기반 단독 터미널명 매핑] "강남", "노포동" 등이 단독으로 들어왔을 때의 방향 결정!
        String standalone = rules.standaloneTerminal();
        if (standalone != null) {
            String city = TagoClient.cityOf(standalone);
            String sessionDep = sessionValue(session, ConversationSession::getDeparture);
            String sessionArr = sessionValue(session, ConversationSession::getArrival);

            // 기존 출발지 도시를 세부화하는 답변인 경우 (예: 출발지가 "서울"인데 "강남" 입력)
            if (city != null && city.equals(sessionDep)) {
                departure = standalone;
            }
            // 기존 도착지 도시를 세부화하는 답변인 경우 (예: 도착지가 "서울"인데 "강남" 입력)
            else if (city != null && city.equals(sessionArr)) {
                arrival = standalone;
            }
            // 출발지만 있고 도착지가 비어있을 때 단독 입력 -> 도착지로 배정! (예: 부산에서 출발인데 "강남" 입력 -> 도착지: 서울경부)
            else if (sessionDep != null && sessionArr == null) {
                arrival = standalone;
            }
            // 도착지만 있고 출발지가 비어있을 때 단독 입력 -> 출발지로 배정!
            else if (sessionArr != null && sessionDep == null) {
                departure = standalone;
            }
            // 둘 다 비어있을 때 -> 기본 출발지로 설정
            else if (departure == null) {
                arrival = standalone;
            }
        }

        String date = firstNonBlank(rules.date() == null ? null : rules.date().toString(), sessionValue(session, ConversationSession::getDate), value(llm, ConversationParseResponse::date));
        String departureTime = firstNonBlank(rules.departureTime() == null ? null : rules.departureTime().toString(), sessionValue(session, ConversationSession::getDepartureTime), value(llm, ConversationParseResponse::departureTime));
        String timePreference = firstNonBlank(rules.timePreference(), sessionValue(session, ConversationSession::getTimePreference), value(llm, ConversationParseResponse::timePreference), "ANY");
        String servicePreference = firstNonBlank(rules.servicePreference(), sessionValue(session, ConversationSession::getServicePreference), value(llm, ConversationParseResponse::servicePreference), "ANY");
        String busGradePreference = firstNonBlank(rules.busGradePreference(), sessionValue(session, ConversationSession::getBusGradePreference), value(llm, ConversationParseResponse::busGradePreference), "ANY");

        int passengers = rules.passengers() > 0 ? rules.passengers()
                : session != null && session.getPassengers() > 0 ? session.getPassengers()
                : llm != null && llm.passengers() > 0 ? llm.passengers() : 1;

        boolean passengerMentioned = hasPassengerMention(rawText) 
                || (rules.passengers() > 1) 
                || (llm != null && llm.passengers() > 1) 
                || (session != null && session.getPassengers() > 1);

        List<String> seatPreferences = mergePreferences(session == null ? List.of() : session.getSeatPreferences(),
                llm == null ? null : llm.seatPreferences(), rules.seatPreferences(), rules.seatPreferenceMentioned());
        List<String> accessibilityNeeds = mergePreferences(session == null ? List.of() : session.getAccessibilityNeeds(),
                llm == null ? null : llm.accessibilityNeeds(), rules.accessibilityNeeds(), rules.accessibilityMentioned());

        List<String> missing = missingRequired(departure, arrival, date, departureTime, timePreference, servicePreference);
        String previousPrompt = sessionValue(session, ConversationSession::getClarificationPrompt);
        boolean seatPreferenceAlreadyAsked = previousPrompt != null && previousPrompt.contains(SEAT_PREFERENCE_QUESTION_MARKER);
        String prompt = clarificationPrompt(missing, departure, arrival, passengers, passengerMentioned, seatPreferenceAlreadyAsked);

        if (prompt != null && !isBlank(rawText) && prompt.equals(previousPrompt)) {
            prompt = "죄송해요, 잘 못 알아들었어요. " + prompt;
        }

        // "더 빠른/더 늦은 거 없어?"는 세션에 계속 남는 조건이 아니라 이번 발화 한 번에 대한 요청이다.
        // 이번 턴에 구체적인 시각(예: "8시로 바꿔줘")을 새로 말한 경우는 그 절대 시각 자체가
        // 요청이므로 상대적 "더 이르게/늦게" 신호와 겹치지 않게 둔다.
        boolean wantsEarlierBus = rules.wantsEarlierBus() && rules.departureTime() == null;
        boolean wantsLaterBus = rules.wantsLaterBus() && rules.departureTime() == null;

        return new ConversationParseResponse(
                intent, nullIfBlank(departure), nullIfBlank(arrival), nullIfBlank(date),
                nullIfBlank(departureTime), timePreference, servicePreference, busGradePreference, passengers,
                seatPreferences, accessibilityNeeds, missing, prompt, wantsEarlierBus, wantsLaterBus
        );
    }

    private boolean hasPassengerMention(String text) {
        if (text == null || text.isBlank()) return false;
        return Pattern.compile("(\\d+|[한두세네다섯여섯]+)\\s*(?:명|장|인|자리|좌석|표|사람|분|식구)").matcher(text).find()
                || List.of("혼자", "둘이", "셋이", "넷이", "다섯이", "부부", "데리고", "모시고", "고치", "같이").stream().anyMatch(text::contains);
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

    private List<String> missingRequired(String departure, String arrival, String date, String depTime, String timePref, String servicePref) {
        List<String> missing = new ArrayList<>();
        if (isBlank(departure)) missing.add("departure");
        if (isBlank(arrival)) missing.add("arrival");
        if (isBlank(date)) missing.add("date");
        boolean hasServicePreference = "FIRST".equalsIgnoreCase(servicePref) || "LAST".equalsIgnoreCase(servicePref);
        if (isBlank(depTime) && (isBlank(timePref) || "ANY".equalsIgnoreCase(timePref)) && !hasServicePreference) {
            missing.add("timePreference");
        }
        return missing;
    }

    // ConversationParseService.java 내부

    // 배려/좌석 선호 질문에 포함되는 고유 문구. 세션에 저장된 "직전 반문"에 이 문구가 있었는지로
    // "이미 한 번 물어봤는지"를 판단한다 (아래 seatPreferenceAlreadyAsked 참고).
    private static final String SEAT_PREFERENCE_QUESTION_MARKER = "더 편하신 좌석이 있으신가요";

    private String clarificationPrompt(List<String> missing, String departure, String arrival,
                                       int passengers, boolean passengerMentioned,
                                       boolean seatPreferenceAlreadyAsked) {
        // 필수 이동 정보(출발/도착/날짜/시간) 누락 시 질문
        if (!missing.isEmpty()) {
            if (missing.contains("departure") && missing.contains("arrival")) {
                return "어디에서 출발해서 어디로 가시나요? 출발지와 도착지를 말씀해 주세요.";
            }
            if (missing.contains("departure")) {
                return (arrival != null && !arrival.isBlank() ? arrival + "행 " : "") + "버스를 탈 출발 터미널을 말씀해 주세요.";
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

        // [전국 복수 터미널 세부 질문] 세부 터미널이 명시되지 않고 큰 지역명만 있는 경우 구체적 안내!
        String terminalDisambiguation = checkMultiTerminalCity(departure, arrival);
        if (terminalDisambiguation != null) {
            return terminalDisambiguation;
        }

        // 인원수 미언급 시 질문 (표 몇 장)
        if (!passengerMentioned) {
            String depStr = (departure != null && !departure.isBlank()) ? departure + "에서 " : "";
            String arrStr = (arrival != null && !arrival.isBlank()) ? arrival + " 가는 " : "";
            return depStr + arrStr + "표를 찾을게요. 탑승하시는 인원은 총 몇 분이신가요? 표 몇 장 예매해 드릴까요? (혼자이시면 '한 장'이라고 말씀해 주세요.)";
        }

        // 배려/좌석 선호 질문. "할머니 모시고" 같은 말에서 접근성 배려(ELDERLY_CARE 등)가 자동으로
        // 추론되어 accessNeeds가 이미 채워져 있더라도, 그건 어디까지나 추론일 뿐 창가/통로 같은
        // 좌석 자체 선호를 실제로 물어본 적은 없다. seatPrefs/accessNeeds가 비어있는지가 아니라
        // "이 질문을 이미 한 번 했는지"로 판단해야, 추론 때문에 이 질문 자체가 통째로 생략되어
        // "좌석 선호를 안 물어봤다"는 사고가 나지 않는다.
        if (!seatPreferenceAlreadyAsked) {
            String countStr = passengers > 1 ? passengers + "분" : "1분";
            return String.format("네, %s 자리로 알아볼게요. 혹시 다리가 불편하시거나 창가/통로 등 " + SEAT_PREFERENCE_QUESTION_MARKER + "?", countStr);
        }

        return null;
    }

    /**
     * 전국 주요 복수 터미널 도시 세부 분기 질문 생성기
     */
    private String checkMultiTerminalCity(String departure, String arrival) {
        String city = TagoClient.isMultiTerminalCity(departure) ? departure
                : TagoClient.isMultiTerminalCity(arrival) ? arrival : null;
        if (city == null) return null;

        String options = String.join(", ", TagoClient.terminalsInCity(city));
        return city + " 어느 터미널로 원하시나요? " + options + " 중 편하신 곳을 말씀해 주세요.";
    }

    private String buildPrompt(String text, String isoDateTime, ConversationSession session) {
        String currentStateJson = session == null ? "{}" : """
                {"departure":"%s","arrival":"%s","date":"%s","departureTime":"%s","timePreference":"%s","servicePreference":"%s","busGradePreference":"%s","passengers":%d,"seatPreferences":%s,"accessibilityNeeds":%s}
                """.formatted(jsonValue(session.getDeparture()), jsonValue(session.getArrival()), jsonValue(session.getDate()),
                jsonValue(session.getDepartureTime()), jsonValue(session.getTimePreference()), jsonValue(session.getServicePreference()),
                jsonValue(session.getBusGradePreference()), session.getPassengers(), jsonArray(session.getSeatPreferences()), jsonArray(session.getAccessibilityNeeds()));

        return """
        당신은 고령자(디지털 소외계층) 및 교통약자를 위한 고속버스 예매 NLU 인공지능입니다.
        공손하고 차분한 어투로 차근차근 설명해줘야 하고, 사용자 음성에서 추출한 조건을 절대 넘겨 짚지 않아야 합니다.
        사용자 발화와 기존 수집 정보를 해석하여, 아래에 정의된 JSON 객체만 반환하세요.
        설명, Markdown(백틱), 추가 문장, 질문을 절대 출력하지 마세요.

        [입력 정보]
        - 기준 시각: %s (Asia/Seoul)
        - 기존 수집 정보: %s

        [핵심 추출 규칙]
        1. 지명/터미널: '~행'(부산행 등)은 arrival, '~발'(서울발 등)은 departure에 지명만 저장
        2. 날짜/시간: 기준시각 참고하여 절대날짜(YYYY-MM-DD) 변환. "첫차/시방/빨리"->servicePreference:"FIRST", "막차"->"LAST".
           이번 발화에 관련 언급이 전혀 없으면 기존 수집 정보의 값을 그대로 유지하고, 기존 정보에도 없으면 "ANY"를 반환하세요.
           ("ANY"는 사용자가 명시적으로 "아무거나 상관없다"고 말했거나, 정말 아무 정보도 없을 때만 사용합니다.)
        3. 탑승 인원: 가족/동행(할머니, 손주, 영감, 바깥양반 등)과 '함께/둘이/데리고' 타면 -> passengers: 2 & accessibilityNeeds에 "ELDERLY_CARE" 추가.
           숫자/인원 표현이 전혀 없으면 기존 수집 정보의 passengers 값을 그대로 유지하고, 기존 정보도 없으면 1을 반환하세요.
        4. 신체/좌석 배려:
           - 다리/무릎 통증, 도가니, 시큰거림, 삭신, 계단 힘듦 -> accessibilityNeeds에 "WALKING_DIFFICULTY" & seatPreferences에 "FRONT"
           - 멀미, 속 울렁거림, 메스꺼움 -> accessibilityNeeds에 "MOTION_SICKNESS" & seatPreferences에 "MIDDLE"
        5. 등급 선호: "우등"->EXCELLENT, "프리미엄/편한 거"->PREMIUM, "일반/싼 거/싼 놈"->GENERAL, "아무거나"->ANY.
           언급이 없으면 기존 수집 정보의 값을 유지하고, 기존 정보도 없으면 "ANY"를 반환하세요.
        6. 상태 병합(가장 중요): 이번 발화에서 새로 언급된 조건만 갱신하고, 언급되지 않은 나머지 필드는 반드시 [입력 정보]의 "기존 수집 정보" 값을 그대로 복사해서 반환하세요.
           특히 servicePreference, busGradePreference, timePreference, passengers는 이번 발화에 언급 없다고 해서 임의로 "ANY"나 1로 초기화하면 안 됩니다 — 사용자가 이전에 말했던 조건을 잃어버리게 됩니다.

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
        사용자: "내일 오전 대구에서 대전 가는데 우등으로, 다리가 불편해서 앞쪽 창가로 줘"
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

        [예시 4 - 조건이 여러 턴에 걸쳐 나뉘어 들어올 때 (상태 유지 핵심 예시)]
        기준 시각: 2026-08-24T10:00:00+09:00
        기존 수집 정보: {"intent":"BUS_SEARCH","departure":null,"arrival":null,"date":null,"departureTime":null,"timePreference":"ANY","servicePreference":"FIRST","busGradePreference":"ANY","passengers":1,"seatPreferences":[],"accessibilityNeeds":[]}
        사용자: "대전에서 서울 가요"
        결과:
        {"intent":"BUS_SEARCH","departure":"서울","arrival":"대전","date":null,"departureTime":null,"timePreference":"ANY","servicePreference":"FIRST","busGradePreference":"ANY","passengers":1,"seatPreferences":[],"accessibilityNeeds":[]}

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