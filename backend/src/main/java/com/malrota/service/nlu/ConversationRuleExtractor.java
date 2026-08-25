package com.malrota.service.nlu;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * watsonx 호출이 불가능하거나 상대 날짜를 보정해야 할 때 사용하는 결정적 NLU 규칙이다.
 * 이 클래스는 운행편·요금·터미널 ID를 추측하지 않고, 사용자가 말한 조건만 다룬다.
 */
@Component
public class ConversationRuleExtractor {

    private static final String TERMINALS = "동서울|서울|대전|부산|광주|대구|강릉|속초|전주|원주|천안|완도";
    private static final Pattern DEPARTURE_PATTERN = Pattern.compile("(" + TERMINALS + ")\\s*(?:에서|서|발)");
    private static final Pattern ARRIVAL_PATTERN = Pattern.compile("(" + TERMINALS + ")\\s*(?:로|에)?\\s*(?:가(?:요|는|자|고|려고|는데)?|갈|행)");
    private static final Pattern MONTH_DAY_PATTERN = Pattern.compile("(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일");
    private static final Pattern NEXT_MONTH_DAY_PATTERN = Pattern.compile("다음\\s*달\\s*(\\d{1,2})\\s*일");
    private static final Pattern DAY_AFTER_PATTERN = Pattern.compile("(\\d+)\\s*일\\s*(?:뒤|후)");
    private static final Pattern HOUR_AFTER_PATTERN = Pattern.compile("(\\d+)\\s*시간\\s*(?:뒤|후)");
    private static final Pattern DAY_OF_MONTH_PATTERN = Pattern.compile("(?:돌아오는|다가오는|이번\\s*달)?\\s*(\\d{1,2})\\s*일");
    private static final Pattern THIS_WEEKDAY_PATTERN = Pattern.compile("이번\\s*주\\s*([월화수목금토일])(?:요일)?");
    private static final Pattern NEXT_WEEKDAY_PATTERN = Pattern.compile("다음\\s*주\\s*([월화수목금토일])(?:요일)?");
    private static final Pattern WEEKDAY_PATTERN = Pattern.compile("(?:돌아오는|다가오는)?\\s*([월화수목금토일])요일");
    private static final Pattern TIME_PATTERN = Pattern.compile("(오전|오후)?\\s*(\\d{1,2})\\s*시\\s*(?:(\\d{1,2})\\s*분|반)?");
    private static final Pattern PASSENGER_PATTERN = Pattern.compile("(\\d+)\\s*(?:명|장)");

    public RuleParse extract(String text, LocalDateTime baseDateTime) {
        String input = text == null ? "" : text.trim();
        String departure = find(DEPARTURE_PATTERN, input);
        String arrival = find(ARRIVAL_PATTERN, input);

        DateTimeResolution resolution = resolveDateTime(input, baseDateTime);
        List<String> seats = extractSeatPreferences(input);
        List<String> needs = extractAccessibilityNeeds(input);

        Matcher passengers = PASSENGER_PATTERN.matcher(input);
        int passengerCount = passengers.find() ? Integer.parseInt(passengers.group(1)) : 0;
        if (input.contains("혼자") || input.contains("한 명") || input.contains("1명")) passengerCount = 1;
        if (input.contains("둘") || input.contains("두 명") || input.contains("2명")) passengerCount = 2;

        return new RuleParse(
                input.contains("취소") ? "CANCEL" : (input.contains("문의") ? "INQUIRY" : "BUS_SEARCH"),
                departure,
                arrival,
                resolution.date(),
                resolution.departureTime(),
                timePreference(input),
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

        Matcher hourAfter = HOUR_AFTER_PATTERN.matcher(text);
        if (hourAfter.find()) {
            LocalDateTime target = base.plusHours(Long.parseLong(hourAfter.group(1)));
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
        if (timeMatcher.find()) {
            int hour = Integer.parseInt(timeMatcher.group(2));
            int minute = "반".equals(timeMatcher.group(0).trim().substring(timeMatcher.group(0).trim().length() - 1))
                    ? 30 : timeMatcher.group(3) == null ? 0 : Integer.parseInt(timeMatcher.group(3));
            if ("오후".equals(timeMatcher.group(1)) && hour < 12) hour += 12;
            if ("오전".equals(timeMatcher.group(1)) && hour == 12) hour = 0;
            if (hour < 24 && minute < 60) time = LocalTime.of(hour, minute);
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
            return baseDate.plusDays(Math.floorMod(DayOfWeek.SATURDAY.getValue() - baseDate.getDayOfWeek().getValue(), 7));
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

    private String find(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private LocalDate safeDate(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private int weekday(String koreanDay) {
        return switch (koreanDay) {
            case "월" -> 1;
            case "화" -> 2;
            case "수" -> 3;
            case "목" -> 4;
            case "금" -> 5;
            case "토" -> 6;
            case "일" -> 7;
            default -> throw new IllegalArgumentException("지원하지 않는 요일: " + koreanDay);
        };
    }

    private List<String> extractSeatPreferences(String text) {
        List<String> result = new ArrayList<>();
        if (text.contains("창가 말고") || text.contains("창가말고")) {
            // 뒤의 통로 선호만 추가하고 WINDOW는 넣지 않는다.
        } else if (text.contains("창가")) result.add("WINDOW");
        if (text.contains("통로")) result.add("AISLE");
        if (text.contains("앞쪽") || text.contains("앞 자리") || text.contains("앞자리") || text.contains("앞좌석")) result.add("FRONT");
        if (text.contains("중간")) result.add("MIDDLE");
        if (text.contains("뒤쪽") || text.contains("뒷자리") || text.contains("뒷좌석") || text.contains("뒤 좌석")) result.add("BACK");
        if (text.contains("혼자 앉") || text.contains("혼자앉")) result.add("SINGLE");
        return result;
    }

    private List<String> extractAccessibilityNeeds(String text) {
        List<String> result = new ArrayList<>();
        if (text.contains("다리") || text.contains("무릎") || text.contains("허리")) result.add("WALKING_DIFFICULTY");
        if (text.contains("어르신") || text.contains("할머니") || text.contains("할아버지")) result.add("ELDERLY_CARE");
        if (text.contains("멀미") || text.contains("속이 메스")) result.add("MOTION_SICKNESS");
        return result;
    }

    private boolean hasSeatPreferenceExpression(String text) {
        return text.contains("창가") || text.contains("통로") || text.contains("앞쪽") || text.contains("앞 자리")
                || text.contains("앞자리") || text.contains("앞좌석") || text.contains("중간") || text.contains("뒤쪽") || text.contains("뒷자리")
                || text.contains("뒷좌석") || text.contains("뒤 좌석")
                || text.contains("혼자 앉") || text.contains("자리 아무거나") || text.contains("좌석 아무거나");
    }

    private boolean hasAccessibilityExpression(String text) {
        return text.contains("다리") || text.contains("무릎") || text.contains("허리") || text.contains("어르신")
                || text.contains("할머니") || text.contains("할아버지") || text.contains("멀미") || text.contains("속이 메스");
    }

    private String timePreference(String text) {
        if (text.contains("오전") || text.contains("아침")) return "MORNING";
        if (text.contains("오후") || text.contains("낮")) return "AFTERNOON";
        if (text.contains("저녁")) return "EVENING";
        if (text.contains("밤") || text.contains("야간") || text.contains("심야")) return "NIGHT";
        return null;
    }

    private String servicePreference(String text) {
        if (text.contains("첫차")) return "FIRST";
        if (text.contains("막차")) return "LAST";
        return text.contains("시간 아무거나") ? "ANY" : null;
    }

    private String busGradePreference(String text) {
        if (text.contains("우등")) return "EXCELLENT";
        if (text.contains("프리미엄")) return "PREMIUM";
        if (text.contains("일반") || text.contains("고속")) return "GENERAL";
        return text.contains("등급 아무거나") || text.contains("버스 아무거나") ? "ANY" : null;
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
    ) {
    }

    private record DateTimeResolution(LocalDate date, LocalTime departureTime) {
    }
}
