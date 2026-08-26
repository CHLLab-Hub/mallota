package com.malrota.service.nlu;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationRuleExtractorTest {

    private final ConversationRuleExtractor extractor = new ConversationRuleExtractor();
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
}
