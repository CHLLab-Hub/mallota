package com.malrota.service.nlu;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationRuleExtractorTest {

    private final ConversationRuleExtractor extractor = new ConversationRuleExtractor();

    @Test
    void keeps_departure_and_arrival_distinct_for_a_full_route_sentence() {
        var result = extractor.extract("서울에서 대전으로 가는 버스 예약해줘", LocalDateTime.of(2026, 8, 24, 10, 0));

        assertThat(result.departure()).isEqualTo("서울");
        assertThat(result.arrival()).isEqualTo("대전");
    }
    private final LocalDateTime base = LocalDateTime.of(2026, 8, 24, 10, 0);

    @Test
    void extracts_route_relative_date_and_accessibility_preferences() {
        var result = extractor.extract("내일 오전 서울에서 대전 가는데 다리가 불편해서 앞쪽 창가로 줘", base);

        assertThat(result.departure()).isEqualTo("서울");
        assertThat(result.arrival()).isEqualTo("대전");
        assertThat(result.date()).hasToString("2026-08-25");
        assertThat(result.timePreference()).isEqualTo("MORNING");
        assertThat(result.seatPreferences()).containsExactlyInAnyOrder("FRONT", "WINDOW");
        assertThat(result.accessibilityNeeds()).containsExactly("WALKING_DIFFICULTY");
    }

    @Test
    void resolves_relative_hour_and_explicit_seat_correction() {
        var timeResult = extractor.extract("3시간 뒤 부산 가는 버스", base);
        var seatResult = extractor.extract("창가 말고 통로로 바꿔줘", base);

        assertThat(timeResult.date()).hasToString("2026-08-24");
        assertThat(timeResult.departureTime()).hasToString("13:00");
        assertThat(seatResult.seatPreferenceMentioned()).isTrue();
        assertThat(seatResult.seatPreferences()).containsExactly("AISLE");
    }

    @Test
    void resolves_next_weekday_without_guessing_route() {
        var result = extractor.extract("다음 주 토요일 첫차로 둘이 갈게", base);

        assertThat(result.departure()).isNull();
        assertThat(result.arrival()).isNull();
        assertThat(result.date()).hasToString("2026-09-05");
        assertThat(result.servicePreference()).isEqualTo("FIRST");
        assertThat(result.passengers()).isEqualTo(2);
    }

    @Test
    void treats_full_input_matching_a_terminal_alias_as_standalone_not_departure() {
        // "부산서부"는 그 자체가 등록된 터미널명이라 뒤에 조사가 없다. 예전에는 짧은 별칭 "부산" +
        // 우연히 남은 "서"를 출발지 조사로 잘못 묶어 departure="부산"으로 오인식했다 (도착지 세부
        // 터미널을 되묻는 반문에 답했을 뿐인데 출발지가 뒤바뀌는 버그).
        var result = extractor.extract("부산서부", base);

        assertThat(result.departure()).isNull();
        assertThat(result.arrival()).isNull();
        assertThat(result.standaloneTerminal()).isEqualTo("부산서부");
    }

    @Test
    void does_not_confuse_a_full_sentence_that_merely_contains_a_terminal_name() {
        var result = extractor.extract("천안에서 부산가는 버스 알려줘", base);

        assertThat(result.departure()).isEqualTo("천안고속");
        assertThat(result.arrival()).isEqualTo("부산");
    }

    @Test
    void detects_relative_request_for_an_earlier_bus() {
        assertThat(extractor.extract("더 빠른 거 없어?", base).wantsEarlierBus()).isTrue();
        assertThat(extractor.extract("더 이른 시간대는 없나요", base).wantsEarlierBus()).isTrue();
        assertThat(extractor.extract("조금 더 일찍 가는 걸로 줘", base).wantsEarlierBus()).isTrue();
        assertThat(extractor.extract("내일 오전에 대전 가요", base).wantsEarlierBus()).isFalse();
    }

    @Test
    void detects_relative_request_for_a_later_bus() {
        assertThat(extractor.extract("더 늦은 거 없어?", base).wantsLaterBus()).isTrue();
        assertThat(extractor.extract("더 나중 시간대는 없나요", base).wantsLaterBus()).isTrue();
        assertThat(extractor.extract("조금 더 늦게 가는 걸로 줘", base).wantsLaterBus()).isTrue();
        assertThat(extractor.extract("내일 오전에 대전 가요", base).wantsLaterBus()).isFalse();
        // "더 빠른"과 "더 늦은"은 서로 배타적이어야 한다
        assertThat(extractor.extract("더 빠른 거 없어?", base).wantsLaterBus()).isFalse();
    }

    @Test
    void marks_bare_hour_as_ambiguous_without_guessing_am_or_pm() {
        // "8시"처럼 오전/오후 표현이 없으면 예매 시각을 임의로 확정하면 안 된다.
        var result = extractor.extract("8시 버스로 주세요", base);

        assertThat(result.departureTime()).isNull();
        assertThat(result.ambiguousMeridiem()).isTrue();
    }

    @Test
    void resolves_native_korean_number_words_for_the_hour() {
        // "한 시"처럼 숫자가 아니라 순우리말 수사로 시각을 말하면 캐치하지 못하던 문제.
        var oneClock = extractor.extract("다음주 수요일 오후 한 시", base);
        assertThat(oneClock.departureTime()).hasToString("13:00");
        assertThat(oneClock.timePreference()).isEqualTo("AFTERNOON");

        var eightClock = extractor.extract("내일 오전 여덟 시 버스로 주세요", base);
        assertThat(eightClock.departureTime()).hasToString("08:00");

        var elevenClock = extractor.extract("밤 열한 시에 출발할게요", base);
        assertThat(elevenClock.departureTime()).hasToString("23:00");
    }

    @Test
    void does_not_mistake_a_reason_clause_ending_in_seo_for_a_place_name() {
        // "-아서/-어서"는 이유를 나타내는 연결어미인데, GENERIC_DEP_PATTERN이 조사 "-서"와 표면적으로
        // 똑같이 생겨서 "싫어"를 지명으로 오인하던 버그가 있었다 (기존 출발지를 엉뚱하게 덮어씀).
        var result = extractor.extract("햇빛이 싫어서 통로자리로 잡아줘", base);

        assertThat(result.departure()).isNull();
        assertThat(result.arrival()).isNull();
        assertThat(result.seatPreferences()).containsExactly("AISLE");
    }

    @Test
    void does_not_mistake_five_or_six_oclock_for_passenger_count() {
        // "여섯"/"다섯"을 단독으로도 인원수로 인식하던 예전 로직이 "여섯시"/"다섯시"(시각)에도
        // 걸려서, 시간만 말했을 뿐인데 인원이 5명/6명으로 잘못 잡히는 사고가 있었다.
        var six = extractor.extract("다음주 화요일 저녁 여섯시", base);
        assertThat(six.departureTime()).hasToString("18:00");
        assertThat(six.passengers()).isZero();

        var five = extractor.extract("다음주 화요일 저녁 다섯시", base);
        assertThat(five.departureTime()).hasToString("17:00");
        assertThat(five.passengers()).isZero();

        // 진짜 인원 표현은 여전히 잡혀야 한다
        assertThat(extractor.extract("여섯이 갈게요", base).passengers()).isEqualTo(6);
        assertThat(extractor.extract("여섯 명이요", base).passengers()).isEqualTo(6);
        assertThat(extractor.extract("다섯 명 예매할게요", base).passengers()).isEqualTo(5);
    }

    @Test
    void extracts_full_terminal_route_even_when_time_and_preferences_follow() {
        var result = extractor.extract("서울경부에서 대전복합으로 내일 오전 9시 한 명 창가", base);

        assertThat(result.departure()).isEqualTo("서울경부");
        assertThat(result.arrival()).isEqualTo("대전복합");
        assertThat(result.departureTime()).hasToString("09:00");
    }

    @Test
    void does_not_treat_relative_minutes_as_passengers() {
        var result = extractor.extract("30분 뒤 출발할게요", base);

        assertThat(result.passengers()).isZero();
        assertThat(result.passengerMentioned()).isFalse();
        assertThat(result.departureTime()).hasToString("10:30");
    }

    @Test
    void keeps_exact_time_when_the_word_general_contains_ban() {
        var result = extractor.extract("내일 오전 9시 일반으로 갈게요", base);

        assertThat(result.departureTime()).hasToString("09:00");
        assertThat(result.busGradePreference()).isEqualTo("GENERAL");
    }

    @Test
    void applies_the_preference_after_a_negative_seat_correction_only() {
        var window = extractor.extract("통로 말고 창가로 해줘", base);
        var back = extractor.extract("앞자리 말고 뒷자리로 해줘", base);

        assertThat(window.seatPreferences()).containsExactly("WINDOW");
        assertThat(back.seatPreferences()).containsExactly("BACK");
    }

    @Test
    void asks_for_am_or_pm_when_twelve_oclock_is_ambiguous() {
        var bareTwelve = extractor.extract("내일 12시 버스", base);
        var noon = extractor.extract("내일 오후 12시 버스", base);

        assertThat(bareTwelve.departureTime()).isNull();
        assertThat(bareTwelve.ambiguousMeridiem()).isTrue();
        assertThat(noon.departureTime()).hasToString("12:00");
        assertThat(noon.ambiguousMeridiem()).isFalse();
    }

    @Test
    void extracts_only_the_new_terminal_from_a_correction_sentence() {
        var result = extractor.extract("아니야, 동서울말고 센트럴로 바꿔줘", base);

        assertThat(result.standaloneTerminal()).isEqualTo("센트럴시티");
        assertThat(result.departure()).isNull();
        assertThat(result.arrival()).isNull();
    }
}
