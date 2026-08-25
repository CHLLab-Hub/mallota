package com.malrota.service.nlu;

import org.springframework.stereotype.Component;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * watsonx 호출이 불가능하거나 상대 날짜/시간을 보정해야 할 때 사용하는 결정론적 NLU 규칙 엔진.
 */
@Component
public class ConversationRuleExtractor {
    
    // 전국의 세부 터미널명과 별칭(강남, 사상, 유스퀘어, 센트럴, 노포 등)을 모두 등록하는 위치
    private static final String TERMINALS = 
            "서울경부|센트럴시티|센트럴|동서울|서울남부|서울|강남|고터|" +
            "동대구|서대구|대구북부|대구서부|대구|" +
            "부산종합|부산서부|사상|해운대|부산|노포|" +
            "대전복합|유성고속|대전청사|유성|대전|" +
            "광주종합|유스퀘어|광주|" +
            "인천종합|인천|수원종합|수원|성남종합|성남|야탑|" +
            "청주고속|북청주|청주|천안고속|천안|전주고속|전주|" +
            "강릉고속|강릉|원주고속|원주|속초고속|속초|포항고속|포항|창원고속|창원|마산고속|마산|완도";

    // 아래 DEPARTURE_PATTERN과 ARRIVAL_PATTERN이 위 TERMINALS를 자동으로 참조합니다.
    private static final Pattern DEPARTURE_PATTERN = Pattern.compile("(?:출발(?:지)?[:\\s]*)?(" + TERMINALS + ")\\s*(?:에서|서|발)");
    private static final Pattern ARRIVAL_PATTERN = Pattern.compile("(" + TERMINALS + ")\\s*(?:행|(?:로|에)?\\s*(?:가(?:요|는|자|고|려고|는데)?|갈|도착))");
    
    // 지명 목록 외 접미사 일반 정규식 안전망 (~행, ~발)
    private static final Pattern GENERIC_DEP_PATTERN = Pattern.compile("([가-힣]{2,})\\s*(?:에서|서|발)");
    private static final Pattern GENERIC_ARR_PATTERN = Pattern.compile("([가-힣]{2,})\\s*행");

    private static final Pattern MONTH_DAY_PATTERN = Pattern.compile("(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일");
    private static final Pattern NEXT_MONTH_DAY_PATTERN = Pattern.compile("다음\\s*달\\s*(\\d{1,2})\\s*일");
    private static final Pattern DAY_OF_MONTH_PATTERN = Pattern.compile("(?:돌아오는|다가오는|이번\\s*달)?\\s*(\\d{1,2})\\s*일");
    private static final Pattern DAY_AFTER_PATTERN = Pattern.compile("(\\d+)\\s*일\\s*(?:뒤|후)");
    private static final Pattern HOUR_AFTER_PATTERN = Pattern.compile("(\\d+)\\s*시간\\s*(?:뒤|후)");
    private static final Pattern MINUTE_AFTER_PATTERN = Pattern.compile("(\\d+)\\s*분\\s*(?:뒤|후)");
    
    private static final Pattern THIS_WEEKDAY_PATTERN = Pattern.compile("이번\\s*주\\s*([월화수목금토일])(?:요일)?");
    private static final Pattern NEXT_WEEKDAY_PATTERN = Pattern.compile("다음\\s*주\\s*([월화수목금토일])(?:요일)?");
    private static final Pattern WEEKDAY_PATTERN = Pattern.compile("(?:돌아오는|다가오는)?\\s*([월화수목금토일])요일");
    
    // 24시간제 세부 시간 정규식 (새벽, 아침, 낮, 점심, 저녁, 밤, 심야)
    private static final Pattern TIME_PATTERN = Pattern.compile("(새벽|아침|낮|점심|저녁|밤|심야|오전|오후)?\\s*(\\d{1,2})\\s*시\\s*(?:(\\d{1,2})\\s*분|반)?");
    private static final Pattern COLON_TIME_PATTERN = Pattern.compile("(\\d{1,2}):(\\d{2})");
    private static final Pattern PASSENGER_PATTERN = Pattern.compile("(\\d+|[한두세네다섯여섯]+)\\s*(?:명|장|인|자리|좌석|표)");

    public RuleParse extract(String text, LocalDateTime baseDateTime) {
        String input = text == null ? "" : text.trim();
        
        String departure = find(DEPARTURE_PATTERN, input);
        if (departure == null) departure = find(GENERIC_DEP_PATTERN, input);

        String arrival = find(ARRIVAL_PATTERN, input);
        if (arrival == null) arrival = find(GENERIC_ARR_PATTERN, input);

        DateTimeResolution resolution = resolveDateTime(input, baseDateTime);
        List<String> seats = extractSeatPreferences(input);
        List<String> needs = extractAccessibilityNeeds(input);
        int passengerCount = extractPassengers(input);

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
                seats,
                needs,
                hasSeatPreferenceExpression(input),
                hasAccessibilityExpression(input)
        );
    }

    private DateTimeResolution resolveDateTime(String text, LocalDateTime base) {
        LocalDate date = null;
        LocalTime time = null;

        // 1. N시간 뒤 / N분 뒤 (자정 롤오버 지원)
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

        // 2. N일 뒤
        Matcher dayAfter = DAY_AFTER_PATTERN.matcher(text);
        if (dayAfter.find()) date = base.toLocalDate().plusDays(Long.parseLong(dayAfter.group(1)));

        // 3. M월 D일
        Matcher monthDay = MONTH_DAY_PATTERN.matcher(text);
        if (monthDay.find()) {
            int month = Integer.parseInt(monthDay.group(1));
            int day = Integer.parseInt(monthDay.group(2));
            int year = base.getYear() + (month < base.getMonthValue() ? 1 : 0);
            date = safeDate(year, month, day);
        } else {
            // 4. 다음 달 N일
            Matcher nextMonth = NEXT_MONTH_DAY_PATTERN.matcher(text);
            if (nextMonth.find()) {
                YearMonth next = YearMonth.from(base).plusMonths(1);
                date = safeDate(next.getYear(), next.getMonthValue(), Integer.parseInt(nextMonth.group(1)));
            } else {
                date = resolveWeekdayOrRelativeDay(text, base, date);
            }
        }

        // 5. 시각 처리 (12/24시간제 및 저녁 7시 -> 19:00 변환)
        Matcher timeMatcher = TIME_PATTERN.matcher(text);
        if (timeMatcher.find()) {
            String ampm = timeMatcher.group(1);
            int hour = Integer.parseInt(timeMatcher.group(2));
            int minute = text.contains("반") ? 30 : (timeMatcher.group(3) != null ? Integer.parseInt(timeMatcher.group(3)) : 0);

            if (List.of("오후", "저녁", "밤", "심야").contains(ampm) && hour < 12) hour += 12;
            else if (List.of("낮", "점심").contains(ampm) && hour <= 6) hour += 12;
            else if (List.of("오전", "새벽", "아침").contains(ampm) && hour == 12) hour = 0;

            if (hour < 24 && minute < 60) time = LocalTime.of(hour, minute);
        } else {
            Matcher colonMatcher = COLON_TIME_PATTERN.matcher(text);
            if (colonMatcher.find()) {
                time = LocalTime.of(Integer.parseInt(colonMatcher.group(1)), Integer.parseInt(colonMatcher.group(2)));
            }
        }

        return new DateTimeResolution(date, time);
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

        // 돌아오는 N일 (지났으면 다음 달로 자동 롤오버)
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

    private int extractPassengers(String text) {
        if (text.contains("혼자") || text.contains("한 명") || text.contains("1명") || text.contains("한 장") || text.contains("1장")) return 1;
        if (text.contains("둘이") || text.contains("두 명") || text.contains("2명") || text.contains("두 장") || text.contains("2장") || text.contains("부부")) return 2;
        
        // 가족 호칭 + 동행 표현 -> 2명 자동 계산
        boolean hasFamily = List.of("할머니", "할아버지", "할망", "하르방", "손주", "손자", "손녀", "손지", "영감", "바깥양반", "안사람", "집사람", "딸래미", "아들래미").stream().anyMatch(text::contains);
        boolean hasTogether = List.of("데리고", "데꼬", "모시고", "이랑", "하고", "고치", "같이", "둘이", "탈 건데", "갈 건데").stream().anyMatch(text::contains);
        if (hasFamily && hasTogether) return 2;

        Matcher passengers = PASSENGER_PATTERN.matcher(text);
        if (passengers.find()) {
            String val = passengers.group(1);
            return switch (val) {
                case "한", "하나" -> 1;
                case "두", "둘" -> 2;
                case "세", "셋" -> 3;
                case "네", "넷" -> 4;
                default -> {
                    try { yield Integer.parseInt(val); } catch (Exception e) { yield 1; }
                }
            };
        }
        return 1;
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
            return "NIGHT";
        }
        if (text.contains("오전") || text.contains("아침") || text.contains("새벽")) return "MORNING";
        if (text.contains("오후") || text.contains("낮") || text.contains("점심")) return "AFTERNOON";
        if (text.contains("저녁")) return "EVENING";
        if (text.contains("밤") || text.contains("야간") || text.contains("심야")) return "NIGHT";
        return "ANY";
    }

    private String servicePreference(String text) {
        if (List.of("첫차", "시방", "싸게싸게", "젤 빠른", "일찍이").stream().anyMatch(text::contains)) return "FIRST";
        if (text.contains("막차")) return "LAST";
        return "ANY";
    }

    private String busGradePreference(String text) {
        if (text.contains("우등") && !text.contains("우등 말고")) return "EXCELLENT";
        if (List.of("프리미엄", "비싼 놈", "제일 좋은", "누워서", "억수로 편한").stream().anyMatch(text::contains)) return "PREMIUM";
        if (List.of("일반", "고속", "싼 놈", "싼 거", "젤 싼", "제일 싼", "저렴한", "가성비").stream().anyMatch(text::contains)) return "GENERAL";
        return "ANY";
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
            List<String> seatPreferences,
            List<String> accessibilityNeeds,
            boolean seatPreferenceMentioned,
            boolean accessibilityMentioned
    ) {}

    private record DateTimeResolution(LocalDate date, LocalTime departureTime) {}
}