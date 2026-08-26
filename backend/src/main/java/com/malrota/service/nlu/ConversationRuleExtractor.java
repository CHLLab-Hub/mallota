package com.malrota.service.nlu;

import com.malrota.client.TagoClient;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
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

    private static final Pattern DEPARTURE_PATTERN = Pattern.compile("(?:출발(?:지)?[:\\s]*)?(" + TERMINALS + ")\\s*(?:에서|서|발)");
    private static final Pattern ARRIVAL_PATTERN = Pattern.compile("(" + TERMINALS + ")\\s*(?:행|(?:로|에)?\\s*(?:가(?:요|는|자|고|려고|는데)?|갈|도착))");
    // "서"/"발"은 지명에 곧바로 붙는 축약 조사라 사이에 공백이 있으면 안 됨(그렇지 않으면 "오전 서울에서"의
    // "오전"이 "서울"의 "서"를 조사로 잘못 삼켜버림). "(?<!에)서"는 "에서"의 "서"만 따로 매치되는 것도 방지.
    private static final Pattern GENERIC_DEP_PATTERN = Pattern.compile("([가-힣]{2,})(?:\\s*에서|(?<!에)서|발)(?![가-힣])");
    // "서울", "대전"처럼 등록되지 않은 도시명 자체는 TERMINALS 목록에 없어 ARRIVAL_PATTERN이 못 잡으므로,
    // "행"뿐 아니라 ARRIVAL_PATTERN과 같은 동사 어미("가는데", "가고" 등)도 함께 허용한다.
    private static final Pattern GENERIC_ARR_PATTERN = Pattern.compile(
            "([가-힣]{2,})\\s*(?:행|(?:로|에)?\\s*(?:가(?:요|는|자|고|려고|는데)?|갈|도착))(?![가-힣])");

    private static final Pattern MONTH_DAY_PATTERN = Pattern.compile("(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일");
    private static final Pattern NEXT_MONTH_DAY_PATTERN = Pattern.compile("다음\\s*달\\s*(\\d{1,2})\\s*일");
    private static final Pattern DAY_OF_MONTH_PATTERN = Pattern.compile("(?:돌아오는|다가오는|이번\\s*달)?\\s*(\\d{1,2})\\s*일");
    private static final Pattern DAY_AFTER_PATTERN = Pattern.compile("(\\d+)\\s*일\\s*(?:뒤|후)");
    private static final Pattern HOUR_AFTER_PATTERN = Pattern.compile("(\\d+)\\s*시간\\s*(?:뒤|후)");
    private static final Pattern MINUTE_AFTER_PATTERN = Pattern.compile("(\\d+)\\s*분\\s*(?:뒤|후)");
    
    private static final Pattern THIS_WEEKDAY_PATTERN = Pattern.compile("이번\\s*주\\s*([월화수목금토일])(?:요일)?");
    private static final Pattern NEXT_WEEKDAY_PATTERN = Pattern.compile("다음\\s*주\\s*([월화수목금토일])(?:요일)?");
    private static final Pattern WEEKDAY_PATTERN = Pattern.compile("(?:돌아오는|다가오는)?\\s*([월화수목금토일])요일");
    
    // 시각의 시(hour)는 "8시"처럼 숫자로도, "여덟 시"/"한 시"처럼 순우리말 수사로도 말할 수 있다.
    // 알파벳 순이 아니라 "열두/열한"을 "열"보다 먼저 두어, 짧은 대안이 먼저 매치되어 뒤의 "한/두"를
    // 못 보고 "시" 앞에서 실패하는 일이 없게 한다 (역추적으로 결국은 맞게 잡히지만 순서를 명확히 함).
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "(새벽|아침|낮|점심|저녁|밤|심야|오전|오후)?\\s*(\\d{1,2}|열두|열한|다섯|여섯|일곱|여덟|아홉|한|두|세|네|열)\\s*시\\s*(?:(\\d{1,2})\\s*분|반)?");
    private static final Pattern PASSENGER_PATTERN = Pattern.compile("(\\d+|[한두세네다섯여섯]+)\\s*(?:명|장|인|자리|좌석|표|사람|분|식구)");

    public RuleParse extract(String text, LocalDateTime baseDateTime) {
        String input = text == null ? "" : text.trim();

        // 발화 전체가 등록된 터미널명/별칭 그 자체와 완전히 일치하는 경우("부산서부" 등 반문에 대한 단답)를
        // 최우선으로 식별한다. TERMINALS 정규식은 "부산서부" 같은 긴 터미널명이 뒤에 아무 조사도 없이
        // 단독으로 오면 매칭에 실패하고, 그 안에 포함된 짧은 터미널명("부산")과 우연히 남은 글자("서")를
        // 조사로 잘못 묶어 엉뚱한 출발/도착지로 오인식하는 문제가 있었다. 완전 일치를 먼저 확인해
        // 이 오인식을 원천 차단하고, 방향 배정은 세션 문맥을 아는 ConversationParseService에 맡긴다.
        String wholeInputAsTerminal = findStandaloneTerminal(input);
        boolean isStandaloneTerminalToken = wholeInputAsTerminal != null
                && TagoClient.allNamesAndAliases().contains(input.replaceAll("\\s+", ""));

        String arrival = null;
        String departure = null;
        String standalone = null;

        if (isStandaloneTerminalToken) {
            standalone = wholeInputAsTerminal;
        } else {
            arrival = find(ARRIVAL_PATTERN, input);
            if (arrival == null) {
                String genericArrival = find(GENERIC_ARR_PATTERN, input);
                if (genericArrival != null && isPlausibleTerminal(genericArrival)) arrival = genericArrival;
            }

            departure = find(DEPARTURE_PATTERN, input);
            if (departure == null) {
                String genericDeparture = find(GENERIC_DEP_PATTERN, input);
                if (genericDeparture != null && isPlausibleTerminal(genericDeparture)) departure = genericDeparture;
            }

            // 단독 단어 입력(조사 없는 "강남", "사상")은 특정 방향으로 단정짓지 않고 식별만 수행!
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
        // 날짜, 시간, 좌석, 약자, 인원 추출
        DateTimeResolution resolution = resolveDateTime(input, baseDateTime);
        List<String> seats = extractSeatPreferences(input);
        List<String> needs = extractAccessibilityNeeds(input);
        int passengerCount = extractPassengers(input);
        boolean passengerMentioned = hasPassengerExpression(input);

        return new RuleParse(
                input.contains("취소") ? "CANCEL" : (input.contains("문의") || input.contains("얼마") ? "INQUIRY" : "BUS_SEARCH"),
                departure,
                arrival,
                resolution.date(),
                resolution.departureTime(),
                timePreference(input, resolution.departureTime()),
                servicePreference(input),
                busGradePreference(input),
                passengerCount,
                passengerMentioned,
                seats,
                needs,
                hasSeatPreferenceExpression(input),
                hasAccessibilityExpression(input),
                standalone,
                wantsEarlierBus(input),
                wantsLaterBus(input)
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
     * 지명 표준명 정규화. "서울", "대전"처럼 터미널이 여럿인 도시명 그 자체는 그대로 두어
     * ConversationParseService가 세부 터미널을 되묻도록 한다. "강남", "동대구"처럼 특정
     * 터미널(별칭)을 콕 집은 경우에만 정식 명칭으로 치환한다.
     */
    private String canonicalizeTerminal(String raw) {
        if (TagoClient.isMultiTerminalCity(raw)) return raw;
        String canon = TagoClient.resolveCanonicalName(raw);
        return canon != null ? canon : raw;
    }

    /**
     * GENERIC_DEP_PATTERN/GENERIC_ARR_PATTERN은 "대구", "대전"처럼 별칭으로 등록되지 않은 지명까지
     * 넓게 잡으려고 조사(에서/서/발/행)만 보고 판단한다. 문제는 "-아서/-어서/-해서"(이유를 나타내는
     * 연결어미, 예: "싫어서", "불편해서")도 표면적으로 똑같이 "~서"로 끝나서, "햇빛이 싫어서"의 "싫어"를
     * 지명으로 오인해 기존 출발지를 엉뚱한 값으로 덮어써 버리는 사고가 있었다. TERMINALS 정규식으로
     * 직접 매칭된 경우(DEPARTURE_PATTERN/ARRIVAL_PATTERN)는 애초에 등록된 이름이라 항상 신뢰할 수
     * 있지만, 이 조사 기반 fallback은 실제 도시/터미널과 조금이라도 연관되는지 다시 확인해야 한다.
     */
    private boolean isPlausibleTerminal(String candidate) {
        return TagoClient.isMultiTerminalCity(candidate) || TagoClient.resolveCanonicalName(candidate) != null;
    }

    /** 단독 지명 입력 처리 헬퍼 (TagoClient 연동) */
    private String findStandaloneTerminal(String text) {
        if (text == null || text.isBlank()) return null;
        String clean = text.trim().replaceAll("\\s+", "");
        return TagoClient.resolveCanonicalName(clean);
    }

    private DateTimeResolution resolveDateTime(String text, LocalDateTime base) {
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
            int minute = text.contains("반") ? 30 : (timeMatcher.group(3) != null ? Integer.parseInt(timeMatcher.group(3)) : 0);

            // ampm은 "8시"처럼 오전/오후 표현 없이 시각만 말한 경우 null일 수 있다 (List.of(...).contains(null)은
            // NullPointerException을 던지므로 반드시 null 체크 후에 검사해야 한다).
            if (ampm != null && List.of("오후", "저녁", "밤", "심야").contains(ampm) && hour < 12) hour += 12;
            else if (ampm != null && List.of("낮", "점심").contains(ampm) && hour <= 6) hour += 12;
            else if (ampm != null && List.of("오전", "새벽", "아침").contains(ampm) && hour == 12) hour = 0;

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

    private LocalDate resolveWeekdayOrRelativeDay(String text, LocalDateTime base, LocalDate current) {
        LocalDate baseDate = base.toLocalDate();
        if (text.contains("그글피")) return baseDate.plusDays(4);
        if (text.contains("글피")) return baseDate.plusDays(3);
        if (text.contains("모레")) return baseDate.plusDays(2);
        if (text.contains("내일")) return baseDate.plusDays(1);
        if (text.contains("오늘")) return baseDate;
        
        if (text.contains("이번 주말") || text.contains("이번주말")) {
            return baseDate.plusDays(Math.max(0, DayOfWeek.SATURDAY.getValue() - baseDate.getDayOfWeek().getValue()));
        }

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
                || List.of("혼자", "둘이", "셋이", "넷이", "다섯이", "부부", "데리고", "모시고", "고치", "같이").stream().anyMatch(text::contains);
    }

    private int extractPassengers(String text) {
        if (text == null || text.isBlank()) return 0;

        Matcher digitMatcher = PASSENGER_PATTERN.matcher(text);
        if (digitMatcher.find()) {
            try {
                String val = digitMatcher.group(1);
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

        if (List.of("여섯", "여섯이", "여섯 명", "여섯 장").stream().anyMatch(text::contains)) return 6;
        if (List.of("다섯", "다섯이", "다섯 명", "다섯 장").stream().anyMatch(text::contains)) return 5;
        if (List.of("네 명", "넷이서", "넷이", "네 장", "네 식구").stream().anyMatch(text::contains)) return 4;
        if (List.of("세 명", "셋이서", "셋이", "세 장", "세 식구").stream().anyMatch(text::contains)) return 3;
        if (List.of("두 명", "둘이서", "둘이", "두 장", "두 식구", "부부").stream().anyMatch(text::contains)) return 2;
        if (List.of("한 명", "혼자서", "혼자", "한 장").stream().anyMatch(text::contains)) return 1;

        boolean hasFamily = List.of("할머니", "할아버지", "할망", "하르방", "손주", "손자", "손녀", "손지", "영감", "바깥양반", "안사람", "집사람", "딸래미", "아들래미").stream().anyMatch(text::contains);
        boolean hasTogether = List.of("데리고", "데꼬", "모시고", "이랑", "하고", "고치", "같이", "탈 건데", "갈 건데").stream().anyMatch(text::contains);
        if (hasFamily && hasTogether) return 2;

        return 0;
    }

    private List<String> extractSeatPreferences(String text) {
        List<String> result = new ArrayList<>();
        if (!text.contains("창가 말고") && !text.contains("창가말고") && text.contains("창가")) result.add("WINDOW");
        if (text.contains("통로")) result.add("AISLE");
        if (List.of("앞쪽", "앞 자리", "앞자리", "앞좌석").stream().anyMatch(text::contains)) result.add("FRONT");
        if (text.contains("중간")) result.add("MIDDLE");
        if (List.of("뒤쪽", "뒷자리", "뒷좌석").stream().anyMatch(text::contains)) result.add("BACK");
        if (text.contains("혼자") || text.contains("단독")) result.add("SINGLE");
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
        if (List.of("첫차", "시방", "싸게싸게", "젤 빠른", "일찍이").stream().anyMatch(text::contains)) return "FIRST";
        if (text.contains("막차")) return "LAST";
        return null;
    }

    private String busGradePreference(String text) {
        if (text.contains("우등") && !text.contains("우등 말고")) return "EXCELLENT";
        if (List.of("프리미엄", "비싼 놈", "제일 좋은", "누워서", "억수로 편한").stream().anyMatch(text::contains)) return "PREMIUM";
        if (List.of("일반", "고속", "싼 놈", "싼 거", "젤 싼", "제일 싼", "저렴한", "가성비").stream().anyMatch(text::contains)) return "GENERAL";
        return null;
    }

    private String find(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
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
        return List.of("창가", "통로", "앞쪽", "앞자리", "앞좌석", "중간", "뒤쪽", "뒷자리", "혼자").stream().anyMatch(text::contains);
    }

    private boolean hasAccessibilityExpression(String text) {
        return List.of("다리", "무릎", "허리", "어르신", "할머니", "할아버지", "손주", "영감", "멀미", "도가니", "시큰", "삭신").stream().anyMatch(text::contains);
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
        boolean wantsEarlierBus,
        boolean wantsLaterBus
    ) {}

    private record DateTimeResolution(LocalDate date, LocalTime departureTime) {}
}