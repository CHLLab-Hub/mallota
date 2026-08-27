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

        // 룰베이스 추출기 1차 실행 (시간 정규화 & 안전망). 세션에 이미 확정된 시간대(오전/오후 등)를
        // 함께 넘겨서, "오후"라고 말해둔 뒤 "8시"라고만 답해도 오전/오후를 다시 안 물어보게 한다.
        ConversationRuleExtractor.RuleParse rules = ruleExtractor.extract(userText, now,
                sessionValue(session, ConversationSession::getTimePreference));
        ConversationParseResponse llmResult = null;

        // watsonx.ai LLM 호출
        if (watsonxClient != null && watsonxClient.isConfigured()) {
            try {
                String prompt = buildPrompt(userText, isoDateTime, session, rules);
                String rawAnswer = watsonxClient.ask(prompt);
                llmResult = objectMapper.readValue(extractJson(rawAnswer), ConversationParseResponse.class);
            } catch (Exception e) {
                log.warn("[ConversationParseService] LLM 호출 실패, 룰베이스 결과로 대체: {}", e.getMessage());
            }
        }

        // LLM + 룰베이스 + 세션 상태 병합 및 반문 생성
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

        // [문맥 기반 단독 터미널명 매핑] "강남", "노포동" 등이 단독으로 들어왔을 때의 방향 결정.
        // 출발/도착이 둘 다 복수 터미널 도시라 동시에 애매한 경우(예: 서울→서울), 실제로 "지금
        // 되묻고 있는" 쪽은 출발지가 우선이다. 이미 구체적인 터미널로 확정된 슬롯은 isMultiTerminalCity가
        // false를 반환하므로, 단순히 도시가 같다는 이유만으로 이미 확정된 슬롯이 다시 덮어써지지 않는다
        // — belongsToCity만으로 판단하면 "서울경부"(이미 확정)도 도시가 "서울"이라 아직 애매한 반대편
        // 슬롯("서울") 대신 잘못 매칭돼버리는 사고가 났었다.
        String standalone = rules.standaloneTerminal();
        boolean standaloneConsumed = false;
        if (standalone != null) {
            String city = TagoClient.cityOf(standalone);
            String sessionDep = sessionValue(session, ConversationSession::getDeparture);
            String sessionArr = sessionValue(session, ConversationSession::getArrival);
            String currentlyAskedDirection = TagoClient.isMultiTerminalCity(sessionDep) ? "departure"
                    : TagoClient.isMultiTerminalCity(sessionArr) ? "arrival" : null;

            if (city != null && city.equals(sessionDep) && "departure".equals(currentlyAskedDirection)) {
                departure = standalone;
                standaloneConsumed = true;
            } else if (city != null && city.equals(sessionArr) && "arrival".equals(currentlyAskedDirection)) {
                arrival = standalone;
                standaloneConsumed = true;
            } else if (currentlyAskedDirection == null && belongsToCity(sessionDep, city)) {
                // 되묻는 중인 애매한 도시가 더 이상 없다면(둘 다 이미 구체적인 터미널로 확정됨),
                // 같은 도시의 다른 터미널을 말한 건 되물음에 대한 답이 아니라 암묵적인 정정이다.
                departure = standalone;
                standaloneConsumed = true;
            } else if (currentlyAskedDirection == null && belongsToCity(sessionArr, city)) {
                arrival = standalone;
                standaloneConsumed = true;
            } else if (sessionDep != null && sessionArr == null) {
                arrival = standalone;
                standaloneConsumed = true;
            } else if (sessionArr != null && sessionDep == null) {
                departure = standalone;
                standaloneConsumed = true;
            } else if (departure == null) {
                departure = standalone;
                standaloneConsumed = true;
            }
        }

        // [터미널 정정] "대전청사 말고 대전종합으로", "서대구 아니라 동대구로", "광주종합 말고
        // 동대구로"(아예 다른 도시로 통째로 바꾸는 경우)처럼 이미 확정된 출발/도착 터미널을 다른
        // 터미널로 바꿔달라는 표현. 어느 쪽(출발/도착)을 바꿀지는 2단계로 판단한다: 1) "말고" 앞쪽
        // (정정 대상)이 등록된 터미널로 알아들어졌으면, 그 터미널이 속한 도시가 출발/도착 어느 쪽과
        // 같은지로 확실하게 판단한다 — 새 터미널이 완전히 다른 도시여도(예: 광주→대구) 정확하다.
        // 2) "말고" 앞쪽이 STT 오인식으로 못 알아들어졌으면(예: "서대구"를 "선대 후"로), 대신 "말고"
        // 뒤쪽(새 터미널)의 도시가 출발/도착 어느 쪽과 "같은 도시"인지로 판단한다.
        //
        // 정정이 적용돼도, 뒤이어 나올 질문(예: 아직 날짜/시간이 안 정해졌으면 그 질문)은 정정 전과
        // 똑같은 문구일 수 있다 — 그러면 사용자는 정정이 실제로 반영됐는지 전혀 알 수 없다. 그래서
        // 정정을 확인해 주는 문구를 뒤이은 질문 앞에 붙여, 조용히 반영되고 넘어가는 일이 없게 한다.
        String correction = rules.correctionTerminal();
        String correctionAck = null;
        if (correction != null) {
            String rejected = rules.rejectedTerminal();
            String rejectedCity = rejected != null ? TagoClient.cityOf(rejected) : null;
            String correctionCity = TagoClient.cityOf(correction);

            String targetDirection = null;
            if (rejectedCity != null && belongsToCity(departure, rejectedCity)) {
                targetDirection = "departure";
            } else if (rejectedCity != null && belongsToCity(arrival, rejectedCity)) {
                targetDirection = "arrival";
            } else if (correctionCity != null && belongsToCity(departure, correctionCity)) {
                targetDirection = "departure";
            } else if (correctionCity != null && belongsToCity(arrival, correctionCity)) {
                targetDirection = "arrival";
            }

            if ("departure".equals(targetDirection)) {
                departure = correction;
                correctionAck = String.format("네, 출발지를 %s%s 바꿔드릴게요.", correction, euro(correction));
            } else if ("arrival".equals(targetDirection)) {
                arrival = correction;
                correctionAck = String.format("네, 도착지를 %s%s 바꿔드릴게요.", correction, euro(correction));
            }
        }

        String date = firstNonBlank(rules.date() == null ? null : rules.date().toString(), sessionValue(session, ConversationSession::getDate), value(llm, ConversationParseResponse::date));

        // 정확한 시각과 관련된 두 가지 배타 규칙을 모두 적용한다:
        // 1) 오전/오후 없는 "12시"/"8시"처럼 모호한 시각(ambiguousMeridiem)은 추측해서 확정하지 않는다.
        // 2) 정확한 시각과 servicePreference("첫차"/"막차")는 서로 배타적인 개념이다. 이번 턴에
        //    servicePreference가 새로 명시됐는데 새 정확한 시각은 없다면, 세션에 남아있던 옛 정확한
        //    시각을 다시 끌어오지 않는다 — 안 그러면 "말고 첫차로"라고 정정해도 옛 시각이 계속
        //    함께 살아남는다. 반대로 새 정확한 시각이 주어졌다면 옛 FIRST/LAST를 밀어낸다.
        boolean freshServicePreference = rules.servicePreference() != null;
        boolean freshDepartureTime = rules.departureTime() != null;

        String departureTime = rules.ambiguousMeridiem() ? null
                : (freshServicePreference && !freshDepartureTime) ? null
                : firstNonBlank(rules.departureTime() == null ? null : rules.departureTime().toString(), sessionValue(session, ConversationSession::getDepartureTime), value(llm, ConversationParseResponse::departureTime));
        // 첫차/막차는 "그 날의 가장 이르거나 늦은 버스"라는 절대적인 의미라, 세션에 남아있던 옛
        // 시간대 선호(예: 오전/오후)와 함께 살아남으면 검색 단계에서 옛 시간대 안에서만 가장
        // 늦은 버스를 찾아버려 진짜 막차가 아닌 엉뚱한 시각이 나온다. 이번 턴에 시간대를 새로
        // 언급하지 않았다면(순수하게 첫차/막차만 말한 경우) 옛 시간대 선호를 밀어내고 "ANY"로 둔다.
        String timePreference = (freshServicePreference && rules.timePreference() == null) ? "ANY"
                : firstNonBlank(rules.timePreference(), sessionValue(session, ConversationSession::getTimePreference), value(llm, ConversationParseResponse::timePreference), "ANY");
        String servicePreference = (freshDepartureTime && !freshServicePreference) ? "ANY"
                : firstNonBlank(rules.servicePreference(), sessionValue(session, ConversationSession::getServicePreference), value(llm, ConversationParseResponse::servicePreference), "ANY");
        String busGradePreference = firstNonBlank(rules.busGradePreference(), sessionValue(session, ConversationSession::getBusGradePreference), value(llm, ConversationParseResponse::busGradePreference), "ANY");

        int passengers = rules.passengers() > 0 ? rules.passengers()
                : session != null && session.getPassengers() > 0 ? session.getPassengers()
                : llm != null && llm.passengers() > 0 ? llm.passengers() : 1;

        boolean passengerMentionedThisTurn = hasPassengerMention(rawText) || rules.passengerMentioned();
        boolean passengerMentioned = passengerMentionedThisTurn
                || (session != null && session.isPassengerCountConfirmed());

        String previousPrompt = sessionValue(session, ConversationSession::getClarificationPrompt);
        boolean seatPreferenceQuestionPending = previousPrompt != null && previousPrompt.contains(SEAT_PREFERENCE_QUESTION_MARKER);
        boolean noSeatPreferenceThisTurn = seatPreferenceQuestionPending && isNoSeatPreferenceResponse(rawText);
        // 이 질문은 "다리가 불편하시거나 창가/통로 등"처럼 좌석 위치 선호와 배려 사유를 함께 묻는다.
        // "멀미가 심해서"처럼 위치 키워드 없이 배려 사유만 답해도 이 질문에 답한 것으로 쳐야
        // 같은 질문이 "잘 못 알아들었어요"와 함께 끝없이 반복되지 않는다.
        boolean seatPreferenceMentionedThisTurn = rules.seatPreferenceMentioned() || rules.accessibilityMentioned() || noSeatPreferenceThisTurn;
        boolean seatPreferenceMentioned = seatPreferenceMentionedThisTurn
                || (session != null && session.isSeatPreferenceConfirmed());

        // "없어요", "아무렇게나"는 선호를 비워 두되, 선호 질문에 답했다는 사실은 확정한다.
        // 그래야 같은 질문을 반복하지 않고 좌석 추천기는 기본 규칙으로 자리를 고른다.
        List<String> seatPreferences = noSeatPreferenceThisTurn ? List.of()
                : mergePreferences(session == null ? List.of() : session.getSeatPreferences(),
                llm == null ? null : llm.seatPreferences(), rules.seatPreferences(), rules.seatPreferenceMentioned());
        List<String> accessibilityNeeds = mergePreferences(session == null ? List.of() : session.getAccessibilityNeeds(),
                llm == null ? null : llm.accessibilityNeeds(), rules.accessibilityNeeds(), rules.accessibilityMentioned());

        List<String> missing = missingRequired(departure, arrival, date, departureTime, servicePreference);
        String prompt = clarificationPrompt(missing, departure, arrival, passengers, passengerMentioned,
                seatPreferenceMentioned, timePreference, rules.ambiguousMeridiem());

        // 단독 터미널 답변("센트럴시티" 등)이 들어왔지만 지금 되묻고 있는 도시(예: 대전)와 다른
        // 도시(예: 서울) 터미널이라 어디에도 반영되지 못한 경우, "잘 못 알아들었어요"라는 애매한
        // 문구 대신 어느 도시 터미널인지 구체적으로 알려주고 다시 골라달라고 안내한다.
        if (standalone != null && !standaloneConsumed) {
            String standaloneCity = TagoClient.cityOf(standalone);
            String ambiguousCity = TagoClient.isMultiTerminalCity(departure) ? departure
                    : TagoClient.isMultiTerminalCity(arrival) ? arrival : null;
            if (standaloneCity != null && ambiguousCity != null && !standaloneCity.equals(ambiguousCity)) {
                String options = String.join(", ", TagoClient.terminalsInCity(ambiguousCity));
                prompt = String.format("%s%s %s 터미널이에요. %s 터미널 중에서 골라주세요: %s.",
                        standalone, eunNeun(standalone), standaloneCity, ambiguousCity, options);
            }
        }

        // 이번 발화에서 뭔가는 실제로 알아들었는지 판단한다. "아니다 막차로 할래"처럼 다른 질문이
        // 밀려 있는 도중에 서비스 선호/날짜/인원 등 별개의 조건을 성공적으로 바꿨을 때도, 그 조건과
        // 무관한 질문(예: 터미널 되묻기)이 우연히 똑같이 다시 나가면 "죄송해요, 잘 못 알아들었어요"가
        // 붙어버려 실제로는 알아들은 것도 못 알아들은 것처럼 보이는 사고가 있었다.
        boolean understoodSomethingThisTurn = standaloneConsumed
                || rules.departure() != null || rules.arrival() != null || rules.date() != null
                || freshDepartureTime || freshServicePreference || rules.busGradePreference() != null
                || passengerMentionedThisTurn || seatPreferenceMentionedThisTurn
                || rules.wantsEarlierBus() || rules.wantsLaterBus();

        // 터미널 정정 외에도, 첫차/막차나 버스 등급처럼 이번 턴에 새로 확정된 조건은 다른(무관한)
        // 질문에 가려 아무 확인 문구 없이 조용히 바뀌면 사용자가 실제로 반영됐는지 알 수 없다.
        String changeAck = correctionAck;
        if (changeAck == null && freshServicePreference) {
            if ("FIRST".equalsIgnoreCase(servicePreference)) changeAck = "네, 첫차로 준비할게요.";
            else if ("LAST".equalsIgnoreCase(servicePreference)) changeAck = "네, 막차로 준비할게요.";
        }
        if (changeAck == null && rules.busGradePreference() != null) {
            changeAck = switch (rules.busGradePreference().toUpperCase()) {
                case "PREMIUM" -> "네, 프리미엄 등급으로 준비할게요.";
                case "EXCELLENT" -> "네, 우등 등급으로 준비할게요.";
                case "GENERAL" -> "네, 일반 등급으로 준비할게요.";
                default -> null;
            };
        }

        if (changeAck != null) {
            // 뭔가 확정됐다는 게 확실하니, 뒤이은 질문이 우연히 직전과 같은 문구여도
            // "죄송해요, 잘 못 알아들었어요"를 붙이지 않는다 — 실제로는 알아들었기 때문이다.
            prompt = prompt == null ? changeAck : changeAck + " " + prompt;
        } else if (prompt != null && !isBlank(rawText) && !understoodSomethingThisTurn && prompt.equals(previousPrompt)) {
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
                passengerMentioned, seatPreferences, seatPreferenceMentioned, accessibilityNeeds,
                missing, prompt, wantsEarlierBus, wantsLaterBus
        );
    }

    /**
     * value가 city 소속인지 확인한다. value가 이미 구체적인 터미널명(예: "대전청사")이면
     * TagoClient.cityOf로 소속 도시를 찾고, 아직 도시명 그 자체(예: "대전", 멀티터미널 미확정)이면
     * city와 직접 비교한다 — 두 경우 모두 지원해야 정정/단독 응답 표현이 "이미 확정된 터미널"이든
     * "아직 세부 터미널을 안 고른 도시"든 상관없이 올바른 방향(출발/도착)을 찾을 수 있다.
     */
    private boolean belongsToCity(String terminalOrCity, String city) {
        if (isBlank(terminalOrCity) || isBlank(city)) return false;
        return city.equals(terminalOrCity) || city.equals(TagoClient.cityOf(terminalOrCity));
    }

    private boolean hasPassengerMention(String text) {
        if (text == null || text.isBlank()) return false;
        return Pattern.compile("(\\d+|[한두세네다섯여섯]+)\\s*(?:명|장|인|자리|좌석|표|사람|식구|분(?!\\s*(?:뒤|후)))").matcher(text).find()
                || List.of("혼자", "둘이", "셋이", "넷이", "다섯이", "부부", "데리고", "모시고", "고치", "같이").stream().anyMatch(text::contains);
    }

    private boolean isNoSeatPreferenceResponse(String text) {
        if (text == null || text.isBlank()) return false;
        return List.of("상관없", "아무거나", "아무렇게나", "아무 데나", "아무데나", "아무 자리", "아무자리",
                "선호 없", "없어요", "없습니다", "없어", "없다", "괜찮", "마음대로", "알아서", "편한 데로", "아니요")
                .stream().anyMatch(text::contains);
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

    private List<String> missingRequired(String departure, String arrival, String date, String depTime, String servicePref) {
        List<String> missing = new ArrayList<>();
        if (isBlank(departure)) missing.add("departure");
        if (isBlank(arrival)) missing.add("arrival");
        if (isBlank(date)) missing.add("date");
        // "오전"/"오후"만으로는 부족하다 — 시간대만 받으면 실제 버스 시각이 중구난방으로 흩어져서
        // (추천 시간/최저가/다른 시간 3개뿐인) 추천 결과가 사용자 의도와 동떨어질 수 있다. 정확한
        // 시각(departureTime)이나 "첫차"/"막차"(그 자체로 시각이 하나로 정해짐)만 통과시킨다.
        boolean hasServicePreference = "FIRST".equalsIgnoreCase(servicePref) || "LAST".equalsIgnoreCase(servicePref);
        if (isBlank(depTime) && !hasServicePreference) {
            missing.add("departureTime");
        }
        return missing;
    }

    // 배려/좌석 선호 질문에 포함되는 고유 문구. 세션에 저장된 "직전 반문"에 이 문구가 있었는지로
    // "이미 한 번 물어봤는지"를 판단한다 (아래 seatPreferenceQuestionPending 참고).
    private static final String SEAT_PREFERENCE_QUESTION_MARKER = "더 편하신 좌석이 있으신가요";

    private String clarificationPrompt(List<String> missing, String departure, String arrival,
                                       int passengers, boolean passengerMentioned,
                                       boolean seatPreferenceMentioned, String timePreference,
                                       boolean ambiguousMeridiem) {
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
            if (missing.contains("date") && missing.contains("departureTime")) {
                return "언제 출발하시나요? '내일 아침', '이번 주말 오후'처럼 날짜와 시간대를 편하게 말씀해 주세요.";
            }
            if (missing.contains("date")) {
                return "출발하시는 날짜를 말씀해 주세요. '오늘', '내일', '이번 주 토요일'처럼 말씀하셔도 됩니다.";
            }
            if (missing.contains("departureTime")) {
                // 오전/오후 없이 "12시"처럼만 말해 자정인지 정오인지 알 수 없는 경우를 최우선으로 짚어준다.
                if (ambiguousMeridiem) {
                    return "입력하신 시간이 오전인지 오후인지 확인이 필요해요. '오전 8시', '오후 3시'처럼 오전 또는 오후를 붙여 말씀해 주세요.";
                }
                // 이미 "오전"/"오후" 같은 시간대는 말씀하셨다면, 그걸 무시하고 처음부터 다시 묻지 않고
                // 그 시간대 안에서 정확히 몇 시인지만 좁혀서 묻는다.
                String timeOfDayKorean = timeOfDayKorean(timePreference);
                if (timeOfDayKorean != null) {
                    return String.format("%s 중 정확히 몇 시쯤이 좋으실까요? '%s 9시'처럼 편하게 말씀해 주세요.", timeOfDayKorean, timeOfDayKorean);
                }
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
        if (!seatPreferenceMentioned) {
            String countStr = passengers > 1 ? passengers + "명" : "한 명";
            return String.format("네, %s 자리로 알아볼게요. 혹시 다리가 불편하시거나 창가/통로 등 " + SEAT_PREFERENCE_QUESTION_MARKER + "?", countStr);
        }

        return null;
    }

    /** 받침 유무에 따라 "은"/"는" 조사를 고른다 (한글 음절이 아니면 "는"으로 무난하게 처리) */
    private String eunNeun(String word) {
        if (word == null || word.isBlank()) return "는";
        char last = word.charAt(word.length() - 1);
        if (last < 0xAC00 || last > 0xD7A3) return "는";
        boolean hasBatchim = (last - 0xAC00) % 28 != 0;
        return hasBatchim ? "은" : "는";
    }

    /** 받침 유무에 따라 "으로"/"로" 조사를 고른다 (ㄹ 받침은 예외적으로 "로") */
    private String euro(String word) {
        if (word == null || word.isBlank()) return "로";
        char last = word.charAt(word.length() - 1);
        if (last < 0xAC00 || last > 0xD7A3) return "로";
        int finalConsonant = (last - 0xAC00) % 28;
        if (finalConsonant == 0 || finalConsonant == 8) return "로"; // 받침 없음, 또는 ㄹ 받침 예외
        return "으로";
    }

    /** timePreference 값을 되물을 때 쓸 한국어 시간대 표현 (없거나 ANY면 null) */
    private String timeOfDayKorean(String timePreference) {
        if (timePreference == null) return null;
        return switch (timePreference.toUpperCase()) {
            case "MORNING" -> "오전";
            case "AFTERNOON" -> "오후";
            case "EVENING" -> "저녁";
            case "NIGHT" -> "심야";
            default -> null;
        };
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

    private String buildPrompt(String text, String isoDateTime, ConversationSession session, ConversationRuleExtractor.RuleParse rules) {
        String currentStateJson = session == null ? "{}" : """
                {"departure":"%s","arrival":"%s","date":"%s","departureTime":"%s","timePreference":"%s","servicePreference":"%s","busGradePreference":"%s","passengers":%d,"seatPreferences":%s,"accessibilityNeeds":%s}
                """.formatted(jsonValue(session.getDeparture()), jsonValue(session.getArrival()), jsonValue(session.getDate()),
                jsonValue(session.getDepartureTime()), jsonValue(session.getTimePreference()), jsonValue(session.getServicePreference()),
                jsonValue(session.getBusGradePreference()), session.getPassengers(), jsonArray(session.getSeatPreferences()), jsonArray(session.getAccessibilityNeeds()));
        String ruleHintsJson = ruleHintsJson(rules);

        return """
        당신은 고령자(디지털 소외계층) 및 교통약자를 위한 고속버스 예매 NLU 인공지능입니다.
        공손하고 차분한 어투로 차근차근 설명해줘야 하고, 사용자 음성에서 추출한 조건을 절대 넘겨 짚지 않아야 합니다.
        사용자 발화와 기존 수집 정보를 해석하여, 아래에 정의된 JSON 객체만 반환하세요.
        설명, Markdown(백틱), 추가 문장, 질문을 절대 출력하지 마세요.

        [입력 정보]
        - 기준 시각: %s (Asia/Seoul)
        - 기존 수집 정보: %s
        - 이번 발화에서 규칙 기반으로 이미 정확히 인식된 값(참고용): %s

        [핵심 추출 규칙]
        0. "이번 발화에서 규칙 기반으로 이미 정확히 인식된 값"에 들어있는 필드는 이미 확실하니 그 값을 그대로 반환하세요.
           그 값을 무시하거나 다르게 바꾸면 안 됩니다. 이 JSON에 없는 필드만 아래 규칙에 따라 직접 판단하세요.
        1. 지명/터미널: '~행'(부산행 등)은 arrival, '~발'(서울발 등)은 departure에 지명만 저장
        2. 날짜/시간: 기준시각 참고하여 절대날짜(YYYY-MM-DD) 변환. "첫차/시방/빨리"->servicePreference:"FIRST", "막차"->"LAST".
           이번 발화에 관련 언급이 전혀 없으면 기존 수집 정보의 값을 그대로 유지하고, 기존 정보에도 없으면 "ANY"를 반환하세요.
           ("ANY"는 사용자가 명시적으로 "아무거나 상관없다"고 말했거나, 정말 아무 정보도 없을 때만 사용합니다.)
        3. 탑승 인원: 가족/동행(할머니, 손주, 영감, 바깥양반 등)과 '함께/둘이/데리고' 타면 -> passengers: 2 & accessibilityNeeds에 "ELDERLY_CARE" 추가.
           숫자/인원 표현이 전혀 없으면 기존 수집 정보의 passengers 값을 그대로 유지하고, 기존 정보도 없으면 1을 반환하세요.
        4. 신체/좌석 배려:
           - 다리/무릎 통증, 도가니, 시큰거림, 삭신, 계단 힘듦 -> accessibilityNeeds에 "WALKING_DIFFICULTY" & seatPreferences에 "FRONT"
           - 멀미, 속 울렁거림, 메스꺼움 -> accessibilityNeeds에 "MOTION_SICKNESS" & seatPreferences에 "MIDDLE"
           - 임산부, 임신, 만삭 -> accessibilityNeeds에 "PREGNANCY" & seatPreferences에 "AISLE"
           - 시각장애, 안내견 동반 -> accessibilityNeeds에 "VISUAL_IMPAIRMENT" & seatPreferences에 "FRONT"
        5. 등급 선호: "우등"->EXCELLENT, "프리미엄"/"비싼 버스"/"고급 버스"/"누워서 가는 거"->PREMIUM, "일반/싼 거/싼 놈"->GENERAL, "아무거나"->ANY.
           주의: "편안한 자리였으면 좋겠다", "편한 좌석으로" 처럼 좌석 자체의 편안함을 말한 것은 버스 등급(busGradePreference)이 아니라
           seatPreferences/accessibilityNeeds에 해당하는 표현입니다. "버스가 편하다"는 말이 아니라면 PREMIUM으로 단정하지 마세요.
           언급이 없으면 기존 수집 정보의 값을 유지하고, 기존 정보도 없으면 "ANY"를 반환하세요.
        6. 상태 병합(가장 중요): 이번 발화에서 새로 언급된 조건만 갱신하고, 언급되지 않은 나머지 필드는 반드시 [입력 정보]의 "기존 수집 정보" 값을 그대로 복사해서 반환하세요.
           특히 servicePreference, busGradePreference, timePreference, passengers는 이번 발화에 언급 없다고 해서 임의로 "ANY"나 1로 초기화하면 안 됩니다 — 사용자가 이전에 말했던 조건을 잃어버리게 됩니다.
        7. 정정 표현("OO 말고 XX로", "OO 아니라 XX로", "OO 아니고 XX로"): 이미 확정된 값을 다른 값으로 바꾸는 표현입니다.
           "말고"/"아니라"/"아니고" 앞의 값은 완전히 버리고, 뒤의 새 값만 반영하세요 — 앞뒤 값이 둘 다 결과에 남으면 안 됩니다.
           어느 필드인지는 값의 종류로 판단합니다: 지명이면 출발/도착 중 그 지명이 있던 자리(도시가 통째로 바뀌어도 마찬가지), 좌석 위치/등급 표현이면 해당 선호 필드.
           특히 정확한 시각(예: "저녁 7시")과 servicePreference(FIRST/LAST, 예: "첫차")는 절대 동시에 존재할 수 없는 값입니다.
           "저녁 7시 말고 첫차로"라고 하면 departureTime과 timePreference는 반드시 null로 비우고 servicePreference만 "FIRST"로 반환하세요 — 거부된 시각을 servicePreference와 함께 남기면 안 됩니다.

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
        {"intent":"BUS_SEARCH","departure":"대구","arrival":"대전","date":"2026-08-25","departureTime":null,"timePreference":"MORNING","servicePreference":"ANY","busGradePreference":"EXCELLENT","passengers":1,"seatPreferences":["FRONT","WINDOW"],"accessibilityNeeds":["WALKING_DIFFICULTY"]}

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

        [예시 4 - 정확한 시각과 servicePreference는 공존 불가 (규칙 7 핵심 예시)]
        기준 시각: 2026-08-24T10:00:00+09:00
        기존 수집 정보: {"intent":"BUS_SEARCH","departure":"서울","arrival":"대전","date":"2026-08-25","departureTime":"19:00","timePreference":"EVENING","servicePreference":"ANY","busGradePreference":"ANY","passengers":1,"seatPreferences":[],"accessibilityNeeds":[]}
        사용자: "저녁 일곱시 말고 첫차로 부탁해"
        결과:
        {"intent":"BUS_SEARCH","departure":"서울","arrival":"대전","date":"2026-08-25","departureTime":null,"timePreference":"ANY","servicePreference":"FIRST","busGradePreference":"ANY","passengers":1,"seatPreferences":[],"accessibilityNeeds":[]}

        [예시 5 - 조건이 여러 턴에 걸쳐 나뉘어 들어올 때 (상태 유지 핵심 예시)]
        기준 시각: 2026-08-24T10:00:00+09:00
        기존 수집 정보: {"intent":"BUS_SEARCH","departure":null,"arrival":null,"date":null,"departureTime":null,"timePreference":"ANY","servicePreference":"FIRST","busGradePreference":"ANY","passengers":1,"seatPreferences":[],"accessibilityNeeds":[]}
        사용자: "대전에서 서울 가요"
        결과:
        {"intent":"BUS_SEARCH","departure":"서울","arrival":"대전","date":null,"departureTime":null,"timePreference":"ANY","servicePreference":"FIRST","busGradePreference":"ANY","passengers":1,"seatPreferences":[],"accessibilityNeeds":[]}

        [실제 입력]
        기준 시각: %s
        기존 수집 정보: %s
        이번 발화에서 규칙 기반으로 이미 정확히 인식된 값: %s
        사용자: "%s"
        결과:
        """.formatted(isoDateTime, currentStateJson, ruleHintsJson, isoDateTime, currentStateJson, ruleHintsJson, text);
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

    /**
     * 룰베이스가 이번 발화에서 이미 확실히 잡아낸 필드만 담은 JSON 힌트. LLM이 룰베이스와
     * 별개로 맨땅에서 다시 판단하며 값이 갈리는 것을 막기 위해, 확실한 필드는 이 힌트로
     * 그대로 신뢰하게 하고 LLM은 힌트에 없는(=룰베이스가 못 잡은) 필드만 직접 판단하게 한다.
     * 언급 안 된 필드는 아예 키 자체를 넣지 않는다 — null/빈 값과 "안 물어봄"을 구분하기 위해서다.
     */
    private String ruleHintsJson(ConversationRuleExtractor.RuleParse rules) {
        if (rules == null) return "{}";
        List<String> fields = new ArrayList<>();
        if (!isBlank(rules.departure())) fields.add("\"departure\":\"" + jsonValue(rules.departure()) + "\"");
        if (!isBlank(rules.arrival())) fields.add("\"arrival\":\"" + jsonValue(rules.arrival()) + "\"");
        if (rules.date() != null) fields.add("\"date\":\"" + rules.date() + "\"");
        if (rules.departureTime() != null) fields.add("\"departureTime\":\"" + rules.departureTime() + "\"");
        if (!isBlank(rules.timePreference())) fields.add("\"timePreference\":\"" + rules.timePreference() + "\"");
        if (!isBlank(rules.servicePreference())) fields.add("\"servicePreference\":\"" + rules.servicePreference() + "\"");
        if (!isBlank(rules.busGradePreference())) fields.add("\"busGradePreference\":\"" + rules.busGradePreference() + "\"");
        if (rules.passengers() > 0) fields.add("\"passengers\":" + rules.passengers());
        if (!rules.seatPreferences().isEmpty()) fields.add("\"seatPreferences\":" + jsonArray(rules.seatPreferences()));
        if (!rules.accessibilityNeeds().isEmpty()) fields.add("\"accessibilityNeeds\":" + jsonArray(rules.accessibilityNeeds()));
        return fields.isEmpty() ? "{}" : "{" + String.join(",", fields) + "}";
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
