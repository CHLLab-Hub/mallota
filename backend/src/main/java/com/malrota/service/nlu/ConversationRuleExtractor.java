package com.malrota.service.nlu;

import com.malrota.client.TagoClient;
import com.malrota.util.KoreanVowelFold;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class ConversationRuleExtractor {

    // TagoClient의 모든 터미널명과 별칭을 긴 순서대로 정렬하여 정규식 생성
    private static final String TERMINALS = TagoClient.allNamesAndAliases().stream()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .map(Pattern::quote)
            .collect(Collectors.joining("|"));
    // 복수 터미널 도시(서울/대구/대전/광주 등)는 도시명 자체가 어느 터미널의 별칭도 아니라서
    // TERMINALS에 안 잡힌다. "서울 말고 대구로"처럼 세부 터미널 없이 도시명만으로 정정하는
    // 표현도 잡으려면 CORRECTION/REJECTED 패턴에서만 도시명까지 별도로 인정해야 한다.
    private static final String CITIES = TagoClient.allCities().stream()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .map(Pattern::quote)
            .collect(Collectors.joining("|"));

    private static final Pattern DEPARTURE_PATTERN = Pattern.compile("(?:출발(?:지)?[:\\s]*)?(" + TERMINALS + ")\\s*(?:에서|서|발)");
    // "으로"/"로" 조사는 받침 유무에 따라 형태가 다르다("천안고속으로", "동대구로") — "로"만 인정하면
    // 받침 있는 터미널명(예: "-고속"으로 끝나는 이름들) 뒤에 "으로"가 붙었을 때 매칭이 실패한다.
    // "까지"도 목적지를 가리키는 흔한 조사라 함께 인정한다("대전까지 부탁드려요").
    //
    // "가줘"/"가주세요"/"데려다줘"/"내려주세요"처럼 부탁하는 말투는 실제로 아주 흔한데(특히
    // 고령자), 원래 "가(?:요|는|자|고|려고|는데)"만으로는 "줘"/"주세요"류 어미를 전혀 못 잡았다
    // (실사용 보고 사례). "가"뿐 아니라 "데려다"/"내려"/"태워다"까지 어간으로 함께 인정해
    // "~에 내려주세요", "~로 데려다줘" 같은 표현도 도착지로 인식하게 한다.
    private static final String ARRIVAL_ENDING =
            "(?:행|(?:으로|로|에|까지)?\\s*(?:가(?:요|는|자|고|려고|려는|는데|야)|갈(?:려고|려는)?|"
            + "(?:데려다|내려|태워다|가)\\s*(?:주세요|줘요|줘)|도착|부탁))";
    private static final Pattern ARRIVAL_PATTERN = Pattern.compile("(" + TERMINALS + ")\\s*" + ARRIVAL_ENDING);
    // "서울에서 대전으로 가요"처럼 한 문장에 출발지와 도착지가 함께 있을 때는 각각 따로 잡는 것보다
    // 이 문장 구조를 우선한다. 두 도시가 같은 값으로 덮이는 사고를 막는다.
    private static final Pattern ROUTE_PATTERN = Pattern.compile(
            "([가-힣]{2,}?)에서\\s*([가-힣]{2,}?)(?:으로|로|에)(?=\\s|$|[0-9가-힣])");
    // "대전청사 말고 대전종합으로", "서대구 아니라 동대구로"처럼 이미 확정한 터미널을 다른 터미널로
    // 바꿔달라는 정정 표현. "말고" 앞쪽(정정 대상)은 STT가 못 알아듣게 받아써도(예: "서대구"를
    // "선대 후"로) 상관없이, "말고" 뒤에 오는 원하는 터미널명만 정확히 잡으면 된다.
    private static final Pattern CORRECTION_PATTERN = Pattern.compile("(?:말고|아니라|아니고)\\s*(" + TERMINALS + "|" + CITIES + ")");
    // "말고" 앞쪽(정정 대상)이 등록된 터미널명으로 알아들어졌을 때는 그 터미널이 속한 도시로 출발/
    // 도착 중 어느 쪽을 바꿀지 확실하게 판단할 수 있다 — "광주종합 말고 동대구로"처럼 아예 다른
    // 도시로 통째로 바꾸는 경우(새 터미널의 도시가 기존 출발/도착 어느 쪽과도 같은 도시가 아님)에도
    // 정확히 도착지를 찾아낼 수 있다. 인식이 안 되면(예: "선대 후") null로 두고, 호출한 쪽이 "말고"
    // 뒤쪽 터미널의 도시를 기존 출발/도착과 비교하는 방식으로 대신 판단한다.
    private static final Pattern REJECTED_PATTERN = Pattern.compile("(" + TERMINALS + "|" + CITIES + ")\\s*(?:말고|아니라|아니고)");
    private static final Pattern GENERIC_DEP_PATTERN = Pattern.compile("([가-힣]{2,})(?:\\s*에서|(?<!에)서|발)(?![가-힣])");
    // 도착지 캡처는 반드시 예약 수량자({2,}?)로 써야 한다 — "서울로 가는"처럼 조사 "로"/"에"가 지명에
    // 바로 붙으면, 뒤쪽 (?:로|에)? 가 있어도 없어도 되는 선택 그룹이라 탐욕적(greedy) 캡처가 "로"까지
    // 통째로 삼켜버린 채로도(=지명이 "서울로") 나머지 패턴("가는")이 그대로 매칭돼 버려서, 등록되지
    // 않은 도시(서울/대구/대전/부산/광주 등 터미널이 여럿이라 별칭에 없는 도시)의 도착지를 "-(으)로
    // 가는" 형태로 말하면 지명에 조사가 섞여 들어가 아예 인식이 실패했다(실사용 보고 사례).
    //
    // "가" 뒤의 어미(요/는/자/고/려고/는데)는 반드시 하나가 있어야 한다(선택 사항이면 안 됨) — 실제로
    // 보고된 사고: "친구가 다리를 다쳤어"에서 "친구가"의 "가"가 주격 조사일 뿐인데 어미 없는 맨 "가"도
    // 매칭돼 버려서 "친구"가 도착지로 오인됐다. "명사+가"(주격 조사)는 극히 흔한 문형이라, "가다"
    // 동사 어미로 확실히 해석되는 경우(요/는/자/고/려고/는데가 뒤따르는 경우)만 인정한다.
    //
    // "부탁": 실제로 보고된 사고: "서울경부로 부탁해요"처럼 "가다" 동사 없이 "-로 부탁해요"라고만
    // 말하면 도착지 어미로 전혀 인식되지 않았다. "가다"/"도착"과 대등한, 목적지를 말하는 흔한
    // 종결 표현이라 함께 인정한다.
    private static final Pattern GENERIC_ARR_PATTERN = Pattern.compile(
            "([가-힣]{2,}?)\\s*" + ARRIVAL_ENDING + "(?![가-힣])");

    // "혼자서"처럼 출발 문형("-서")과 우연히 겹치는 흔한 비지명 단어. GENERIC_DEP_PATTERN이 이런
    // 단어를 출발지 후보로 잡아버리면 "혼자서 서울 갈려고"에서 "혼자"가, "여기서 대전 가는 버스
    // 있어?"에서 "여기"가, "그래서 서울 갈게요"에서 "그래"가 미지원 지역으로 오인된다(실제 보고 +
    // 같은 정규식 구조로 재현되는 사례들).
    private static final Set<String> NON_TERMINAL_DEPARTURE_WORDS = Set.of(
            "혼자", "같이", "함께", "저희", "우리", "여기", "거기", "저기", "그래", "따라");

    // "돌아가고"/"내려가고"처럼 도착 문형("-가다" 계열 동사)과 우연히 겹치는 흔한 비지명 단어.
    // GENERIC_ARR_PATTERN이 이런 동사 어간을 도착지 후보로 잡아버리는 걸 막는다.
    private static final Set<String> NON_TERMINAL_ARRIVAL_WORDS = Set.of(
            "돌아", "내려", "올라", "넘어", "지나", "다녀");

    private static final Pattern MONTH_DAY_PATTERN = Pattern.compile("(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일");
    private static final Pattern NEXT_MONTH_DAY_PATTERN = Pattern.compile("다음\\s*달\\s*(\\d{1,2})\\s*일");
    private static final Pattern DAY_OF_MONTH_PATTERN = Pattern.compile("(?:돌아오는|다가오는|이번\\s*달)?\\s*(\\d{1,2})\\s*일");
    private static final Pattern DAY_AFTER_PATTERN = Pattern.compile("(\\d+)\\s*일\\s*(?:뒤|후)");
    private static final Pattern HOUR_AFTER_PATTERN = Pattern.compile("(\\d+)\\s*시간\\s*(?:뒤|후)");
    private static final Pattern MINUTE_AFTER_PATTERN = Pattern.compile("(\\d+)\\s*분\\s*(?:뒤|후)");

    private static final Pattern THIS_WEEKDAY_PATTERN = Pattern.compile("이번\\s*주\\s*([월화수목금토일])(?:요일)?");
    private static final Pattern NEXT_WEEKDAY_PATTERN = Pattern.compile("다음\\s*주\\s*([월화수목금토일])(?:요일)?");
    private static final Pattern WEEKDAY_PATTERN = Pattern.compile("(?:돌아오는|다가오는)?\\s*([월화수목금토일])요일");

    // 시각의 시(hour)는 "8시"처럼 숫자로도, "여덟 시"/"한 시"처럼 순우리말 수사로도 말함
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "(새벽|아침|낮|점심|저녁|밤|심야|오전|오후)?\\s*(\\d{1,2}|열두|열한|다섯|여섯|일곱|여덟|아홉|한|두|세|네|열)\\s*시\\s*(?:(\\d{1,2})\\s*분|반)?");
    // "30분 뒤"의 "분"은 시간 단위이지 탑승 인원이 아니다 — 부정형 전방탐색으로 뒤/후가 붙은 "분"은 제외한다.
    // 앞에 한글 음절이 이어지면(예: "편안한") 순우리말 수사가 아니라 다른 단어의 끝 글자일 뿐이다 —
    // "편안한 자리"가 "한 자리"(1명)로 오인되던 실제 보고 사례. 부정 전방탐색으로 그 경우를 막는다.
    private static final Pattern PASSENGER_PATTERN = Pattern.compile("(?<![가-힣])(\\d+|[한두세네다섯여섯]+)\\s*(?:명|장|인|자리|좌석|표|사람|식구|분(?!\\s*(?:뒤|후)))");

    public RuleParse extract(String text, LocalDateTime baseDateTime) {
        return extract(text, baseDateTime, null);
    }

    /**
     * knownTimePreference: 세션에 이미 확정된 시간대(MORNING/AFTERNOON/EVENING/NIGHT)가 있으면
     * 전달한다. "내일 오후"라고 말해둔 뒤 되묻는 질문에 "8시"라고만 답해도(오전/오후를 다시 안
     * 붙여도), 이 힌트로 오후 8시임을 판단해 매번 되묻는 일이 없게 하기 위해서다.
     */
    public RuleParse extract(String text, LocalDateTime baseDateTime, String knownTimePreference) {
        String input = text == null ? "" : text.trim();
        // 실제로 보고된 사고: "서울 경부로 부탁해요"처럼 터미널명 음절 사이에 공백이 잘못 끼어들면
        // (STT든 LLM 교정이든) DEPARTURE_PATTERN/ARRIVAL_PATTERN이 "서울경부"와 정확히 일치하는
        // 연속된 문자열을 요구하기 때문에 아예 매칭되지 않았다. 등록된 터미널명이 공백만 끼운 채로
        // 나타나면 그 부분만 공백을 제거해, 문장의 나머지 구조(단어 사이 정상 공백)는 그대로 둔다.
        input = collapseSpacesInsideTerminalNames(input);
        // 이 앱 도메인에서 거의 항상 STT 오인식인 흔한 단어는 애초에 텍스트 자체를 교정해 둔다 —
        // 의미만 속으로 해석하고 화면 표시(사용자 말풍선)는 원문 그대로 두면, 실제로는 "내일"로
        // 이해했으면서 사용자에게는 "매일"이라고 말한 것처럼 보여 혼란스럽다. 텍스트 자체를 고치면
        // 이후 모든 로직(날짜/서비스 선호 판단 포함)과 화면 표시가 자연히 같은 값을 쓰게 된다.
        input = input.replace("매일", "내일").replace("초청", "첫차");

        // 발화 전체가 등록된 터미널명/별칭 그 자체와 완전히 일치하는 경우("부산서부" 등 반문에 대한 단답)를 최우선으로 식별한다.
        // 완전 일치를 먼저 확인해 이 오인식을 원천 차단하고, 방향 배정은 세션 문맥을 아는 ConversationParseService에 맡긴다.
        String wholeInputAsTerminal = findStandaloneTerminal(input);
        String compactInput = input.replaceAll("\\s+", "");
        boolean isStandaloneTerminalToken = wholeInputAsTerminal != null
                && TagoClient.allNamesAndAliases().contains(compactInput);
        // 실제로 보고된 사고: "어디서 출발하시나요?"라는 질문에 세부 터미널 없이 도시명만
        // ("서울", "대구") 단독으로 답하면 아무 반응도 없었다. "서울"/"대구"처럼 세부 터미널이
        // 여럿인 도시명은 일부러 allNamesAndAliases()에 등록돼 있지 않아(세부 터미널을 반드시
        // 되묻게 하려고 — TagoClient 참고) wholeInputAsTerminal이 항상 null이 되므로, 터미널명과는
        // 별도로 도시명 목록에서 완전 일치를 확인해야 한다.
        boolean isStandaloneCityToken = wholeInputAsTerminal == null && TagoClient.allCities().contains(compactInput);

        String arrival = null;
        String departure = null;
        String standalone = null;
        // 등록되지 않은 지명(예: "완도")을 "-(으)로 가는"/"-에서" 같은 도착/출발 문형으로 말했을 때,
        // isPlausibleTerminal이 조용히 걸러내 버리면 사용자는 아무 반응이 없거나 매번 같은 질문만
        // 반복되는 걸 보게 된다. 여기 담아 두면 ConversationParseService가 "그 지역은 아직 지원하지
        // 않는다"고 정직하게 안내할 수 있다.
        String unrecognizedArrival = null;
        String unrecognizedDeparture = null;

        if (isStandaloneTerminalToken) {
            standalone = wholeInputAsTerminal;
        } else if (isStandaloneCityToken) {
            standalone = compactInput;
        } else {
            // "서울에서 대전으로" 같은 한 문장 출발+도착 구조를 개별 패턴보다 먼저 확인한다.
            Matcher routeMatcher = ROUTE_PATTERN.matcher(input);
            if (routeMatcher.find()) {
                String routeDeparture = routeMatcher.group(1);
                String routeArrival = routeMatcher.group(2);
                // 등록되지 않은 지명(예: "완도")이 "-(으)로" 조사와 함께 이 패턴으로 잡혀도 그냥
                // 통과시키면 안 된다 — 아래 개별 패턴들과 똑같이 검증해서, 미지원 지역이면
                // unrecognized*로 넘겨 정직하게 안내할 수 있게 한다.
                if (isPlausibleTerminal(routeDeparture)) departure = routeDeparture;
                else unrecognizedDeparture = routeDeparture;
                if (isPlausibleTerminal(routeArrival)) arrival = routeArrival;
                else unrecognizedArrival = routeArrival;
            } else {
                arrival = find(ARRIVAL_PATTERN, input);
                if (arrival == null) {
                    String genericArrival = findGenericArrival(input);
                    if (genericArrival != null) {
                        if (isPlausibleTerminal(genericArrival)) arrival = genericArrival;
                        else unrecognizedArrival = genericArrival;
                    }
                }

                departure = find(DEPARTURE_PATTERN, input);
                if (departure == null) {
                    String genericDeparture = findGenericDeparture(input);
                    if (genericDeparture != null) {
                        if (isPlausibleTerminal(genericDeparture)) departure = genericDeparture;
                        else unrecognizedDeparture = genericDeparture;
                    }
                }
            }

            // 단독 단어 입력(조사 없는 "강남", "사상")은 특정 방향으로 단정짓지 않고 식별만 수행
            if (departure == null && arrival == null && input.length() <= 10) {
                standalone = findStandaloneTerminal(input);
            }
        }

        // 지명 표준명으로 정규화 (단, "서울"처럼 터미널이 여러 개인 도시명은 임의로 하나를 골라버리면
        // 세부 터미널을 되묻는 흐름(TagoClient.isMultiTerminalCity)이 깨지므로 그대로 둔다)
        if (arrival != null) {
            arrival = canonicalizeTerminal(arrival);
        }
        if (departure != null) {
            departure = canonicalizeTerminal(departure);
        }
        // "OO 말고 XX로" 정정 표현: 별칭이면 정식 명칭으로 통일해 ConversationParseService가
        // 세션의 출발/도착 중 어느 쪽과 같은 도시인지 비교해서 그 자리를 갈아끼울 수 있게 한다.
        // 등록된 터미널명/별칭은 모두 공백이 없는데, STT가 "대전 종합"처럼 음절 사이에 공백을
        // 잘못 끼워 넣는 경우가 있어 공백을 제거한 텍스트로 매칭한다.
        String compactForCorrection = input.replaceAll("\\s+", "");
        String correctionTerminal = find(CORRECTION_PATTERN, compactForCorrection);
        if (correctionTerminal != null) {
            correctionTerminal = canonicalizeTerminal(correctionTerminal);
        }
        // "말고" 앞쪽(정정 대상)도 등록된 터미널명으로 알아들어졌으면 함께 넘긴다 — "광주종합 말고
        // 동대구로"처럼 아예 다른 도시로 통째로 바꾸는 정정도 정확히 판단할 수 있게 하기 위해서다.
        String rejectedTerminal = find(REJECTED_PATTERN, compactForCorrection);
        if (rejectedTerminal != null) {
            rejectedTerminal = canonicalizeTerminal(rejectedTerminal);
        }
        // 실제로 보고된 사고: "대전청사 말고 대전터미널로 부탁해"에서 "대전터미널로 부탁"이 정정
        // 문구(correctionTerminal)이자 동시에 일반 ARRIVAL_PATTERN(부탁 어미)에도 걸려서, 같은
        // 지명이 "정정 대상"과 "새로 언급된 도착지" 양쪽으로 중복 인식돼 충돌했다. correctionTerminal과
        // 같은 지명이면 일반 도착/출발 슬롯에서는 제거해, 정정 로직에만 온전히 맡긴다.
        if (correctionTerminal != null && correctionTerminal.equals(arrival)) arrival = null;
        if (correctionTerminal != null && correctionTerminal.equals(departure)) departure = null;

        // 실제 보고된 사례: "저녁 일곱시 말고 첫차로 부탁해"처럼 날짜/시간/승차 방식을 정정하는
        // 문장에서, 거부된 옛 값("저녁 일곱시")이 "말고" 뒤의 새 값("첫차")과 함께 그대로 다시
        // 추출되면 정확한 시각과 "첫차"가 동시에 세션에 반영되는 모순이 생긴다. "말고"/"아니라"/
        // "아니고" 뒤쪽 텍스트만으로 날짜/시간/승차 방식을 추출해서, 정정 이전에 언급된 값이 다시
        // 끼어들지 않게 한다 (그런 표현이 없으면 원문 그대로 사용하므로 기존 동작은 그대로다).
        String textForTimeExtraction = afterLastCorrectionKeyword(input);

        // 날짜, 시간, 좌석, 약자, 인원 추출
        DateTimeResolution resolution = resolveDateTime(textForTimeExtraction, baseDateTime, knownTimePreference);
        List<String> seats = extractSeatPreferences(input);
        List<String> needs = extractAccessibilityNeeds(input);
        int passengerCount = extractPassengers(input);
        boolean passengerMentioned = hasPassengerExpression(input);

        // "8시", "12시", "한 시"처럼 오전/오후가 없으면 같은 시각이 두 개 존재한다.
        // 예매 시간은 추측하지 않고 반드시 되묻게 한다.
        boolean ambiguousMeridiem = hasAmbiguousMeridiem(textForTimeExtraction) && resolution.departureTime() == null;
        // "이번 주말"/"주말"은 토요일/일요일 중 어느 쪽인지 알 수 없다. 날짜가 이미 다른 표현
        // (요일 명시, "내일" 등)으로 확정됐다면 애매하지 않다.
        boolean ambiguousWeekend = hasAmbiguousWeekend(textForTimeExtraction) && resolution.date() == null;

        return new RuleParse(
                input.contains("취소") ? "CANCEL" : (input.contains("문의") || input.contains("얼마") ? "INQUIRY" : "BUS_SEARCH"),
                departure,
                arrival,
                resolution.date(),
                resolution.departureTime(),
                timePreference(textForTimeExtraction, resolution.departureTime()),
                servicePreference(textForTimeExtraction),
                busGradePreference(input),
                passengerCount,
                passengerMentioned,
                seats,
                needs,
                hasSeatPreferenceExpression(input),
                hasAccessibilityExpression(input),
                standalone,
                correctionTerminal,
                rejectedTerminal,
                wantsEarlierBus(input),
                wantsLaterBus(input),
                ambiguousMeridiem,
                ambiguousWeekend,
                unrecognizedDeparture,
                unrecognizedArrival
        );
    }

    /**
     * "더 빠른 거 없어?", "더 이른 시간대로" 처럼 방금 안내한 버스보다 더 이른 시간을 요청하는
     * 상대적 표현인지 판별한다. "첫차"/"젤 빠른"(servicePreference=FIRST)과 달리 세션에 계속
     * 남는 값이 아니라, 이번 발화 한 번에 대해서만 "이전에 보여준 버스보다 이르게"를 의미한다.
     */
    private boolean wantsEarlierBus(String text) {
        return List.of("더 빠른", "더빠른", "더 이른", "더이른", "더 일찍", "더일찍", "조금 더 일찍", "좀 더 일찍", "당겨서", "더 당겨")
                .stream().anyMatch(text::contains);
    }

    /**
     * "더 늦은 거 없어?", "더 나중 시간대로" 처럼 방금 안내한 버스보다 더 늦은 시간을 요청하는
     * 상대적 표현인지 판별한다. wantsEarlierBus와 대칭이며 마찬가지로 세션에 남지 않는 1회성 신호다.
     */
    private boolean wantsLaterBus(String text) {
        return List.of("더 늦은", "더늦은", "더 나중", "더나중", "조금 더 늦게", "좀 더 늦게", "미뤄서", "더 미뤄", "뒤로 미뤄")
                .stream().anyMatch(text::contains);
    }

    /**
     * 등록된 터미널명/별칭이 음절 사이에 공백만 끼운 채로 나타나면(예: "서울 경부") 그 부분의
     * 공백만 제거해 "서울경부"로 되돌린다. 문장의 다른 부분(단어 사이 정상 공백)은 건드리지 않는다
     * — 이름 전체를 문자 단위로 공백 허용 정규식으로 바꿔서 찾아내므로, 실제로 그 이름이 공백과
     * 함께 나타난 경우만 정확히 잡아낸다.
     */
    private static String collapseSpacesInsideTerminalNames(String text) {
        if (text == null || text.isBlank()) return text;
        String compact = text.replaceAll("\\s+", "");
        String result = text;
        for (String name : TagoClient.allNamesAndAliases()) {
            if (name.length() < 2 || !compact.contains(name)) continue;
            StringBuilder spaced = new StringBuilder();
            for (int i = 0; i < name.length(); i++) {
                if (i > 0) spaced.append("\\s*");
                spaced.append(Pattern.quote(String.valueOf(name.charAt(i))));
            }
            // 실제로 보고된 사고: "천안에서 부산가는"에서 "에[서] 부산"이 "서부산"(부산서부의 별칭)과
            // 우연히 겹쳐서, 단어 경계를 넘어 "에서"의 "서"와 "부산"을 하나로 붙여버렸다. 매칭 시작
            // 지점 바로 앞이 한글이면(=이미 다른 단어 중간이면) 그 단어의 일부일 뿐 진짜 터미널명의
            // 시작이 아니므로 제외한다 — 실제 터미널명은 항상 공백/문장 시작 직후에 나온다.
            result = result.replaceAll("(?<![가-힣])" + spaced, name);
        }
        return result;
    }

    /**
     * 지명 표준명 정규화. "서울", "대전"처럼 터미널이 여럿인 도시명 그 자체는 그대로 두어
     * ConversationParseService가 세부 터미널을 되묻도록 한다. "강남", "동대구"처럼 특정
     * 터미널(별칭)을 콕 집은 경우에만 정식 명칭으로 치환한다.
     */
    private String canonicalizeTerminal(String raw) {
        if (TagoClient.isMultiTerminalCity(raw)) return raw;
        String canon = TagoClient.resolveCanonicalName(raw);
        return canon != null ? canon : raw;
    }

    private boolean isPlausibleTerminal(String candidate) {
        return TagoClient.isKnownRegion(candidate);
    }

    /** 단독 지명 입력 처리 헬퍼 (TagoClient 연동) */
    private String findStandaloneTerminal(String text) {
        if (text == null || text.isBlank()) return null;
        String clean = text.trim().replaceAll("\\s+", "");
        return TagoClient.resolveCanonicalName(clean);
    }

    private DateTimeResolution resolveDateTime(String text, LocalDateTime base, String knownTimePreference) {
        LocalDate date = null;
        LocalTime time = null;

        Matcher hourAfter = HOUR_AFTER_PATTERN.matcher(text);
        if (hourAfter.find()) {
            LocalDateTime target = base.plusHours(Long.parseLong(hourAfter.group(1)));
            return new DateTimeResolution(target.toLocalDate(), target.toLocalTime().withSecond(0).withNano(0));
        }
        Matcher minAfter = MINUTE_AFTER_PATTERN.matcher(text);
        if (minAfter.find()) {
            LocalDateTime target = base.plusMinutes(Long.parseLong(minAfter.group(1)));
            return new DateTimeResolution(target.toLocalDate(), target.toLocalTime().withSecond(0).withNano(0));
        }

        Matcher dayAfter = DAY_AFTER_PATTERN.matcher(text);
        if (dayAfter.find()) date = base.toLocalDate().plusDays(Long.parseLong(dayAfter.group(1)));

        Matcher monthDay = MONTH_DAY_PATTERN.matcher(text);
        if (monthDay.find()) {
            int month = Integer.parseInt(monthDay.group(1));
            int day = Integer.parseInt(monthDay.group(2));
            int year = base.getYear() + (month < base.getMonthValue() ? 1 : 0);
            date = safeDate(year, month, day);
        } else {
            Matcher nextMonth = NEXT_MONTH_DAY_PATTERN.matcher(text);
            if (nextMonth.find()) {
                YearMonth next = YearMonth.from(base).plusMonths(1);
                date = safeDate(next.getYear(), next.getMonthValue(), Integer.parseInt(nextMonth.group(1)));
            } else {
                date = resolveWeekdayOrRelativeDay(text, base, date);
            }
        }

        Matcher timeMatcher = TIME_PATTERN.matcher(text);
        while (timeMatcher.find()) {
            String ampm = timeMatcher.group(1);
            int hour = koreanHourToNumber(timeMatcher.group(2));
            // "일반"처럼 발화의 다른 단어에 들어 있는 '반'이 시각에 영향을 주면 안 된다 — 반드시
            // 이번 매치 구간(group(0)) 안의 '반'인지로 판단한다.
            int minute = timeMatcher.group(0).contains("반") ? 30 : (timeMatcher.group(3) != null ? Integer.parseInt(timeMatcher.group(3)) : 0);

            // ampm은 "8시"처럼 오전/오후 표현 없이 시각만 말한 경우 null일 수 있다 (List.of(...).contains(null)은
            // NullPointerException을 던지므로 반드시 null 체크 후에 검사해야 한다).
            if (ampm != null) {
                if (List.of("오후", "저녁", "밤", "심야").contains(ampm) && hour < 12) hour += 12;
                else if (List.of("낮", "점심").contains(ampm) && hour <= 6) hour += 12;
                else if (List.of("오전", "새벽", "아침").contains(ampm) && hour == 12) hour = 0;
            } else if (knownTimePreference != null && !knownTimePreference.isBlank()
                    && !"ANY".equalsIgnoreCase(knownTimePreference)) {
                // 실제로 보고된 사례: "내일 오후"라고 이미 말해둔 상태에서 되묻는 질문에 "8시"라고만
                // 답해도, 이번 발화에 오전/오후가 없다는 이유로 매번 다시 되물었다. 세션에 이미
                // 확정된 시간대가 있으면 그걸로 오전/오후를 판단해 바로 확정한다.
                if ("MORNING".equalsIgnoreCase(knownTimePreference)) {
                    if (hour == 12) hour = 0;
                } else if (hour < 12) {
                    hour += 12;
                }
            } else {
                // 오전/오후 없는 12시는 자정과 정오 중 어느 쪽인지 알 수 없다. 세션에도 확정된
                // 시간대가 없으면 24시간제인지 12시간제인지도 알 수 없으므로 추측하지 않는다
                // (hasAmbiguousMeridiem이 이 경우를 감지해 사용자에게 다시 물어보게 한다).
                continue;
            }
            if (hour < 24 && minute < 60) time = LocalTime.of(hour, minute);
        }

        return new DateTimeResolution(date, time);
    }

    /** "여덟 시"처럼 순우리말 수사로 말한 시각을 숫자로 변환 (이미 숫자면 그대로 파싱) */
    private int koreanHourToNumber(String value) {
        return switch (value) {
            case "한" -> 1;
            case "두" -> 2;
            case "세" -> 3;
            case "네" -> 4;
            case "다섯" -> 5;
            case "여섯" -> 6;
            case "일곱" -> 7;
            case "여덟" -> 8;
            case "아홉" -> 9;
            case "열" -> 10;
            case "열한" -> 11;
            case "열두" -> 12;
            default -> Integer.parseInt(value);
        };
    }

    /** 오전/오후 표현 없이 시각만 말한 매치가 하나라도 있는지 (자정/정오 등 모호한 시각 판별용) */
    private boolean hasAmbiguousMeridiem(String text) {
        Matcher matcher = TIME_PATTERN.matcher(text);
        while (matcher.find()) {
            if (matcher.group(1) == null) return true;
        }
        return false;
    }

    /**
     * "주말"이 토요일/일요일 중 어느 쪽인지 명시 없이 언급됐는지. 실제로 보고된 사고: "이번 주말
     * 오후"를 임의로 토요일로 조용히 확정해버려서 사용자가 일요일을 의도했어도 확인 없이 넘어갔다.
     */
    private boolean hasAmbiguousWeekend(String text) {
        return text.contains("주말") && !text.contains("토요일") && !text.contains("일요일");
    }

    private LocalDate resolveWeekdayOrRelativeDay(String text, LocalDateTime base, LocalDate current) {
        LocalDate baseDate = base.toLocalDate();
        // 실제 보고된 사례: 음성 인식이 "모레"를 "모래"로 받아쓴다 (ㅔ/ㅐ 혼동). "내일"의 "내"도
        // 같은 모음이라 같은 문제가 생길 수 있어, 상대 날짜 키워드 전체를 모음 혼동 보정 비교로 맞춘다.
        if (KoreanVowelFold.contains(text, "그글피")) return baseDate.plusDays(4);
        if (KoreanVowelFold.contains(text, "글피")) return baseDate.plusDays(3);
        if (KoreanVowelFold.contains(text, "모레")) return baseDate.plusDays(2);
        if (KoreanVowelFold.contains(text, "내일")) return baseDate.plusDays(1);
        if (text.contains("오늘")) return baseDate;

        // "이번 주말"은 토요일과 일요일 중 어느 쪽인지 알 수 없어 여기서 날짜를 확정하지 않는다
        // (hasAmbiguousWeekend가 이 경우를 감지해 되묻게 한다) — "이번 주말 토요일"처럼 요일까지
        // 함께 말하면 아래 WEEKDAY_PATTERN이 그 요일을 그대로 잡아낸다.

        Matcher thisWeekday = THIS_WEEKDAY_PATTERN.matcher(text);
        if (thisWeekday.find()) return baseDate.plusDays(weekday(thisWeekday.group(1)) - baseDate.getDayOfWeek().getValue());
        Matcher nextWeekday = NEXT_WEEKDAY_PATTERN.matcher(text);
        if (nextWeekday.find()) return baseDate.plusDays(7 - baseDate.getDayOfWeek().getValue() + weekday(nextWeekday.group(1)));
        Matcher weekday = WEEKDAY_PATTERN.matcher(text);
        if (weekday.find()) {
            int difference = Math.floorMod(weekday(weekday.group(1)) - baseDate.getDayOfWeek().getValue(), 7);
            if (difference == 0 && (text.contains("돌아오는") || text.contains("다가오는"))) difference = 7;
            return baseDate.plusDays(difference);
        }

        Matcher dayOfMonth = DAY_OF_MONTH_PATTERN.matcher(text);
        if (dayOfMonth.find()) {
            int day = Integer.parseInt(dayOfMonth.group(1));
            YearMonth month = YearMonth.from(base);
            LocalDate candidate = safeDate(month.getYear(), month.getMonthValue(), day);
            if (candidate != null && !candidate.isAfter(baseDate)) {
                month = month.plusMonths(1);
                candidate = safeDate(month.getYear(), month.getMonthValue(), day);
            }
            return candidate;
        }
        return current;
    }

    private boolean hasPassengerExpression(String text) {
        if (text == null || text.isBlank()) return false;
        return PASSENGER_PATTERN.matcher(text).find()
                || List.of("혼자", "둘이", "셋이", "넷이", "다섯이", "부부", "데리고", "모시고", "고치", "같이", "친구", "일행").stream().anyMatch(text::contains);
    }

    private int extractPassengers(String text) {
        if (text == null || text.isBlank()) return 0;

        // "3명 말고 2명이요"처럼 인원수를 정정하는 문장에서는 가장 마지막(=최종 확정) 값을 써야
        // 한다. find()는 첫 번째 일치만 주므로, 일치하는 것 전부를 훑어 마지막 것을 남긴다.
        Matcher digitMatcher = PASSENGER_PATTERN.matcher(text);
        String lastVal = null;
        while (digitMatcher.find()) {
            lastVal = digitMatcher.group(1);
        }
        if (lastVal != null) {
            try {
                String val = lastVal;
                return switch (val) {
                    case "한", "하나" -> 1;
                    case "두", "둘" -> 2;
                    case "세", "셋" -> 3;
                    case "네", "넷" -> 4;
                    case "다섯" -> 5;
                    case "여섯" -> 6;
                    default -> {
                        int c = Integer.parseInt(val);
                        yield (c > 0 && c <= 45) ? c : 1;
                    }
                };
            } catch (Exception ignored) {}
        }

        if (List.of("여섯이", "여섯 명", "여섯 장").stream().anyMatch(text::contains)) return 6;
        if (List.of("다섯이", "다섯 명", "다섯 장").stream().anyMatch(text::contains)) return 5;
        if (List.of("네 명", "넷이서", "넷이", "네 장", "네 식구").stream().anyMatch(text::contains)) return 4;
        if (List.of("세 명", "셋이서", "셋이", "세 장", "세 식구").stream().anyMatch(text::contains)) return 3;
        if (List.of("두 명", "둘이서", "둘이", "두 장", "두 식구", "부부").stream().anyMatch(text::contains)) return 2;
        if (List.of("한 명", "혼자서", "혼자", "한 장").stream().anyMatch(text::contains)) return 1;

        // "친구랑"처럼 가족이 아닌 동행(친구/일행 등)도 "혼자"가 아니라는 뜻이므로 인원 추론에
        // 포함해야 한다 — 실제로 보고된 사고: "친구랑 대전에서 부산으로 갈려고"라고 말해도 인원이
        // 기본값 1에서 바뀌지 않았다.
        List<String> companionNouns = List.of("할머니", "할아버지", "할망", "하르방", "손주", "손자", "손녀", "손지",
                "영감", "바깥양반", "안사람", "집사람", "딸래미", "아들래미", "친구", "일행", "동생", "언니", "오빠", "형");
        boolean hasFamily = companionNouns.stream().anyMatch(text::contains);
        // "-와/과/랑/이랑/하고"("함께"란 뜻의 조사)는 동행 명사 바로 뒤에 붙어 있을 때만 인정한다.
        // "랑"/"와"/"과" 한 글자만 아무 데나 있으면(예: "출발과 도착", "사랑") 오탐이 나기 쉽다.
        // STT가 명사와 조사 사이에 공백을 잘못 끼워 넣을 수 있어 공백을 제거하고 비교한다.
        String compact = text.replaceAll("\\s+", "");
        List<String> companionParticles = List.of("랑", "이랑", "와", "과", "하고");
        boolean hasCompanionParticle = companionNouns.stream()
                .anyMatch(noun -> companionParticles.stream().anyMatch(particle -> compact.contains(noun + particle)));
        boolean hasTogether = hasCompanionParticle
                || List.of("데리고", "데꼬", "모시고", "고치", "같이", "탈 건데", "갈 건데").stream().anyMatch(text::contains);
        if (hasFamily && hasTogether) return 2;

        return 0;
    }

    private List<String> extractSeatPreferences(String text) {
        List<String> result = new ArrayList<>();
        // 실제 보고된 사례: "뒷 쪽 통로"처럼 음절 사이에 공백이 끼어들면(직접 입력이든 STT든) 일치하지
        // 않았다 (mentionedAndNotRejected가 공백을 접어서 비교하므로 여기선 그대로 넘기면 된다).
        // "뒷쪽"도 표준 표기 "뒤쪽"의 흔한 오기(사이시옷을 뒷자리/뒷좌석에서 유추)라 함께 받는다.
        if (mentionedAndNotRejected(text, "창가")) result.add("WINDOW");
        if (mentionedAndNotRejected(text, "통로")) result.add("AISLE");
        // "햇빛이 안 들어오는/없는" 자리는 창가를 피하고 싶다는 뜻이라 통로로, 반대로 "햇빛 잘 드는/
        // 볕 좋은" 자리는 창가로 이어진다. "햇빛"/"볕" 단어 자체는 방향이 없어 부정 표현 유무로 가른다.
        boolean dislikesSun = List.of("햇빛이 안", "햇빛 안", "햇빛이 없는", "햇빛 없는", "햇빛이 싫어", "햇빛 싫어",
                "볕이 안", "볕 안", "볕이 싫어", "볕 싫어", "그늘").stream().anyMatch(text::contains);
        boolean likesSun = !dislikesSun && List.of("햇빛", "볕", "양지").stream().anyMatch(text::contains);
        if (dislikesSun && !result.contains("AISLE")) result.add("AISLE");
        if (likesSun && !result.contains("WINDOW")) result.add("WINDOW");
        if (List.of("앞쪽", "앞자리", "앞좌석").stream().anyMatch(k -> mentionedAndNotRejected(text, k))) result.add("FRONT");
        if (mentionedAndNotRejected(text, "중간")) result.add("MIDDLE");
        if (List.of("뒤쪽", "뒷쪽", "뒷자리", "뒷좌석").stream().anyMatch(k -> mentionedAndNotRejected(text, k))) result.add("BACK");
        if (List.of("혼자", "단독").stream().anyMatch(k -> mentionedAndNotRejected(text, k))) result.add("SINGLE");
        return result;
    }

    private List<String> extractAccessibilityNeeds(String text) {
        List<String> result = new ArrayList<>();
        if (List.of("다리", "무릎", "허리", "관절", "시큰", "삭신", "도가니", "지팡이", "계단", "하영 힘들", "절임").stream().anyMatch(text::contains)) {
            result.add("WALKING_DIFFICULTY");
        }
        if (List.of("어르신", "할머니", "할아버지", "할망", "하르방", "손주", "손자", "손녀", "손지", "영감", "바깥양반").stream().anyMatch(text::contains)) {
            result.add("ELDERLY_CARE");
        }
        if (List.of("멀미", "속이 메스", "울렁", "토해", "옴팡지게").stream().anyMatch(text::contains)) {
            result.add("MOTION_SICKNESS");
        }
        if (List.of("임산부", "임신", "만삭", "배가 불러").stream().anyMatch(text::contains)) {
            result.add("PREGNANCY");
        }
        if (List.of("시각장애", "안내견", "앞이 안 보", "앞이 잘 안 보").stream().anyMatch(text::contains)) {
            result.add("VISUAL_IMPAIRMENT");
        }
        return result;
    }

    private String timePreference(String text, LocalTime departureTime) {
        if (departureTime != null) {
            int h = departureTime.getHour();
            if (h < 6) return "NIGHT";
            if (h < 10) return "MORNING";
            if (h < 17) return "AFTERNOON";
            if (h < 21) return "EVENING";
            return null;
        }
        if (text.contains("오전") || text.contains("아침") || text.contains("새벽")) return "MORNING";
        if (text.contains("오후") || text.contains("낮") || text.contains("점심")) return "AFTERNOON";
        if (text.contains("저녁")) return "EVENING";
        if (text.contains("밤") || text.contains("야간") || text.contains("심야")) return "NIGHT";
        return null;
    }

    private String servicePreference(String text) {
        // "저차", "쳐차"는 음성 인식이 "첫차"를 잘못 받아적은 흔한 오인식 표기다 (실제 사용자 보고
        // 사례). "초청"도 같은 부류지만 extract() 시작부에서 이미 "첫차"로 텍스트 자체를 교정해
        // 두므로 여기까지 오지 않는다. "첫차"/"막차"는 STT가 "첫 차"/"막 차"처럼 중간에 공백을
        // 잘못 끼워 넣는 경우가 있어, 공백을 제거한 텍스트로 비교한다 ("젤 빠른"은 원래 띄어 쓰는
        // 두 단어라 그대로 둔다).
        String compact = text.replaceAll("\\s+", "");
        if (List.of("첫차", "저차", "쳐차", "시방", "싸게싸게", "일찍이").stream().anyMatch(compact::contains)
                || List.of("젤 빠른", "가장 빠른", "제일 빠른").stream().anyMatch(text::contains)) {
            return "FIRST";
        }
        if (compact.contains("막차")) return "LAST";
        return null;
    }

    private String busGradePreference(String text) {
        if (mentionedAndNotRejected(text, "우등")) return "EXCELLENT";
        if (List.of("프리미엄", "비싼 놈", "제일 좋은", "누워서", "억수로 편한").stream().anyMatch(k -> mentionedAndNotRejected(text, k))) return "PREMIUM";
        if (List.of("일반", "고속", "싼 놈", "싼 거", "젤 싼", "제일 싼", "저렴한", "가성비").stream().anyMatch(k -> mentionedAndNotRejected(text, k))) return "GENERAL";
        return null;
    }

    // "말고"/"아니라"/"아니고" 뒤쪽만 골라내는 용도 (CORRECTION_PATTERN과 달리 특정 터미널명을
    // 요구하지 않고, 그 키워드 뒤의 나머지 텍스트 전부가 필요할 때 쓴다)
    private static final Pattern CORRECTION_KEYWORD_PATTERN = Pattern.compile("말고|아니라|아니고");
    // keyword 바로 뒤(공백 허용)에 정정 키워드가 붙어 있으면 그 keyword 자체가 거부된 것으로 본다
    private static final Pattern IMMEDIATE_REJECTION_PATTERN = Pattern.compile("^\\s*(말고|아니라|아니고)");

    /** 마지막 "말고"/"아니라"/"아니고" 뒤쪽 텍스트만 반환 (없으면 원문 그대로) */
    private String afterLastCorrectionKeyword(String text) {
        Matcher matcher = CORRECTION_KEYWORD_PATTERN.matcher(text);
        int lastEnd = -1;
        while (matcher.find()) {
            lastEnd = matcher.end();
        }
        return lastEnd < 0 ? text : text.substring(lastEnd);
    }

    /**
     * text에 keyword가 있고, "keyword 말고"/"아니라"/"아니고"처럼 keyword 자신이 거부된 게 아닌지
     * 확인한다. 좌석 위치(앞쪽/중간/뒤쪽/창가/통로)와 버스 등급(우등/프리미엄/일반) 선호처럼, 이미
     * 확정된 값을 "OO 말고 XX로"로 정정하는 문장에서 거부된 옛 값이 새 값과 함께 잡히는 걸 막는다
     * (예: "프리미엄 말고 일반으로"에서 PREMIUM이 잡히면 안 됨).
     *
     * text와 keyword 양쪽 모두 공백을 제거하고 비교한다 — "뒷 쪽 통로"처럼 직접 입력이든 STT든
     * 음절 사이에 공백이 끼어드는 경우가 흔해서다 (keyword까지 함께 접으면 "비싼 놈"처럼 원래
     * 띄어 쓰는 키워드도 그대로 안전하게 매칭된다).
     */
    private boolean mentionedAndNotRejected(String text, String keyword) {
        String compactText = text.replaceAll("\\s+", "");
        String compactKeyword = keyword.replaceAll("\\s+", "");
        int idx = compactText.indexOf(compactKeyword);
        if (idx < 0) return false;
        String after = compactText.substring(idx + compactKeyword.length());
        return !IMMEDIATE_REJECTION_PATTERN.matcher(after).find();
    }

    private String find(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * GENERIC_DEP_PATTERN 매치 중 NON_TERMINAL_DEPARTURE_WORDS에 해당하는 흔한 비지명 단어("혼자"
     * 등)는 건너뛰고, 그 뒤에 실제 지명 후보가 더 있으면 그것을 대신 반환한다. 없으면 null을 반환해
     * 출발지가 "미지원 지역"이 아니라 그냥 "아직 안 말한 것"으로 정상 처리되게 한다.
     */
    private String findGenericDeparture(String text) {
        Matcher matcher = GENERIC_DEP_PATTERN.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (!NON_TERMINAL_DEPARTURE_WORDS.contains(candidate)) return candidate;
        }
        return null;
    }

    /** findGenericDeparture와 대칭 — GENERIC_ARR_PATTERN 매치 중 흔한 비지명 동사 어간은 건너뛴다. */
    private String findGenericArrival(String text) {
        Matcher matcher = GENERIC_ARR_PATTERN.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (!NON_TERMINAL_ARRIVAL_WORDS.contains(candidate)) return candidate;
        }
        return null;
    }

    private LocalDate safeDate(int year, int month, int day) {
        try { return LocalDate.of(year, month, day); } catch (Exception e) { return null; }
    }

    private int weekday(String koreanDay) {
        return switch (koreanDay) {
            case "월" -> 1; case "화" -> 2; case "수" -> 3; case "목" -> 4;
            case "금" -> 5; case "토" -> 6; case "일" -> 7;
            default -> 1;
        };
    }

    private boolean hasSeatPreferenceExpression(String text) {
        // "혼자"는 여기 넣지 않는다 — 인원수를 밝히는 표현("혼자서 서울 갈려고")일 뿐인데, 여기 있으면
        // 좌석/배려 선호 질문 자체를 이미 답한 것으로 세션에 영구 확정(seatPreferenceConfirmed)돼
        // 버려서 "멀미 심해요" 같은 실제 배려사항을 물어볼 기회가 다시는 오지 않는 사고가 났다.
        // extractSeatPreferences()가 "혼자"에서 SINGLE 좌석 선호를 뽑아내는 것과는 별개다.
        return List.of("창가", "통로", "앞쪽", "앞자리", "앞좌석", "중간", "뒤쪽", "뒷쪽", "뒷자리", "뒷좌석",
                "햇빛", "볕", "그늘", "양지").stream().anyMatch(text::contains);
    }

    private boolean hasAccessibilityExpression(String text) {
        return List.of("다리", "무릎", "허리", "어르신", "할머니", "할아버지", "손주", "영감", "멀미", "도가니", "시큰", "삭신",
                "임산부", "임신", "만삭", "배가 불러", "아기", "유아", "젖먹이", "신생아", "돌쟁이",
                "시각장애", "안내견", "앞이 안 보", "앞이 잘 안 보").stream().anyMatch(text::contains);
    }

    public record RuleParse(
        String intent,
        String departure,
        String arrival,
        LocalDate date,
        LocalTime departureTime,
        String timePreference,
        String servicePreference,
        String busGradePreference,
        int passengers,
        boolean passengerMentioned,
        List<String> seatPreferences,
        List<String> accessibilityNeeds,
        boolean seatPreferenceMentioned,
        boolean accessibilityMentioned,
        String standaloneTerminal,
        String correctionTerminal,
        String rejectedTerminal,
        boolean wantsEarlierBus,
        boolean wantsLaterBus,
        boolean ambiguousMeridiem,
        // "이번 주말"/"주말"은 토요일과 일요일 둘 다 가리킬 수 있어 애매하다 — 실제로 보고된 사고:
        // "이번 주말 오후"를 임의로 토요일로 조용히 확정해버려서, 사용자가 일요일을 의도했어도
        // 확인 없이 넘어갔다. 오전/오후가 모호한 시각(ambiguousMeridiem)과 같은 방식으로, 요일을
        // 추측하지 않고 반드시 되묻는다.
        boolean ambiguousWeekend,
        // 등록되지 않은 지명을 "-에서"/"-(으)로 가는" 문형으로 말했을 때의 원문 그대로 (지원하지
        // 않는 지역이라고 정직하게 안내하기 위한 용도 — departure/arrival과 달리 등록 여부와
        // 무관하게 항상 채워진다는 보장이 없는 1회성 신호다).
        String unrecognizedDeparture,
        String unrecognizedArrival
    ) {}

    private record DateTimeResolution(LocalDate date, LocalTime departureTime) {}
}
