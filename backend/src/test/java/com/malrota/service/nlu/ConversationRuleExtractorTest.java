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
    void resolves_arrival_stated_with_a_particle_for_every_multi_terminal_city() {
        // 실제 보고된 사고: "강릉에서 서울로 가는 버스 예매해줘"에서 도착지가 아예 안 잡혀서,
        // 세션/LLM 폴백을 타다가 결국 출발지와 같은 "강릉"으로 도착지가 잘못 채워졌다. 근본 원인은
        // GENERIC_ARR_PATTERN의 탐욕적 캡처가 "서울" 대신 "서울로"(조사 포함)를 통째로 잡아버려서
        // 등록된 터미널명이 아니라고 거부(isPlausibleTerminal)해 버리는 것이었다. 터미널이 여럿이라
        // 별칭에 등록되지 않은 5개 도시(서울/대구/대전/부산/광주) 전부에서 재현되므로 전부 확인한다.
        assertThat(extractor.extract("강릉에서 서울로 가는 버스 예매해줘", base).arrival()).isEqualTo("서울");
        assertThat(extractor.extract("천안에서 대구로 가는 버스", base).arrival()).isEqualTo("대구");
        assertThat(extractor.extract("천안에서 대전으로 가는 버스", base).arrival()).isEqualTo("대전");
        assertThat(extractor.extract("천안에서 부산으로 가는 버스", base).arrival()).isEqualTo("부산");
        assertThat(extractor.extract("천안에서 광주로 가는 버스", base).arrival()).isEqualTo("광주");
    }

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
    void recognizes_pregnancy_infant_and_visual_impairment_accessibility_needs() {
        assertThat(extractor.extract("임산부라 좌석 부탁드려요", base).accessibilityNeeds()).containsExactly("PREGNANCY");
        assertThat(extractor.extract("신생아 데리고 타요", base).accessibilityNeeds()).containsExactly("INFANT_CARE");
        assertThat(extractor.extract("시각장애가 있어서 안내견과 함께 타요", base).accessibilityNeeds()).containsExactly("VISUAL_IMPAIRMENT");
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
    void resolves_relative_date_even_when_stt_confuses_ae_e_vowels() {
        // 실제 보고된 사례: 음성 인식이 "모레"를 "모래"로 받아쓴다 (ㅔ/ㅐ 혼동).
        var result = extractor.extract("음 모래 아침에 갈게요", base);

        assertThat(result.date()).hasToString("2026-08-26");
        assertThat(result.timePreference()).isEqualTo("MORNING");
    }

    @Test
    void recognizes_formal_phrasing_for_the_first_bus() {
        // 실제 보고된 사례: 기존엔 캐주얼한 "젤 빠른"만 인식하고, 표준적인 "가장 빠른"/"제일 빠른"은
        // 놓쳐서 같은 뜻인데도 "잘 못 알아들었어요"가 반복됐다.
        assertThat(extractor.extract("가장 빠른 걸로 부탁해", base).servicePreference()).isEqualTo("FIRST");
        assertThat(extractor.extract("제일 빠른 버스로 주세요", base).servicePreference()).isEqualTo("FIRST");
    }

    @Test
    void extracts_correction_terminal_after_malgo_even_when_the_rejected_part_is_mistranscribed() {
        // 실제 보고된 사례: "대전청사 말고 대전종합으로", "선대 후 말고 동대구로"(STT가 "서대구"를
        // "선대 후"로 오인식)처럼 이미 확정한 터미널을 다른 터미널로 바꿔달라는 표현. "말고" 앞쪽이
        // 못 알아들을 말이어도(등록되지 않은 터미널이어도) "말고" 뒤의 원하는 터미널만 정확히 잡으면 된다.
        assertThat(extractor.extract("대전 청사 말고 대전 종합으로 부탁해", base).correctionTerminal()).isEqualTo("대전복합");
        assertThat(extractor.extract("선대 후 말고 동대구로 부탁해", base).correctionTerminal()).isEqualTo("동대구");
    }

    @Test
    void extracts_the_rejected_terminal_before_malgo_when_it_is_itself_registered() {
        // 실제 보고된 사례: "광주 종합 말고 동 대구로"처럼 아예 다른 도시로 통째로 바꾸는 정정에서는
        // "말고" 뒤쪽(동대구)의 도시(대구)가 기존 출발/도착 어느 쪽과도 같지 않을 수 있다 — 이럴 땐
        // "말고" 앞쪽(광주종합)의 도시(광주)로 확실하게 판단해야 하므로, 등록된 터미널이면 함께 잡는다.
        var result = extractor.extract("광주 종합 말고 동 대구로 부탁해", base);

        assertThat(result.rejectedTerminal()).isEqualTo("광주종합");
        assertThat(result.correctionTerminal()).isEqualTo("동대구");
    }

    @Test
    void rejected_time_before_malgo_does_not_leak_in_alongside_the_correction() {
        // 실제 보고된 사례: "저녁 일곱시 말고 첫차로 부탁해"에서 거부된 "저녁 일곱시"가 "첫차"와
        // 동시에 추출돼 정확한 시각(19:00)과 servicePreference=FIRST가 모순되게 함께 잡혔다.
        var result = extractor.extract("저녁 일곱시 말고 첫 차로 부탁해", base);

        assertThat(result.servicePreference()).isEqualTo("FIRST");
        assertThat(result.departureTime()).isNull();
        assertThat(result.timePreference()).isNull();
    }

    @Test
    void corrects_seat_position_preference_without_keeping_the_rejected_side() {
        // "말고" 정정 처리가 터미널/시간에만 있고 좌석 위치(앞쪽/중간/뒤쪽)와 통로에는 없어서,
        // "앞쪽 말고 뒤쪽으로"처럼 말하면 거부된 옛 선호와 새 선호가 동시에 잡히던 문제.
        assertThat(extractor.extract("앞쪽 말고 뒤쪽으로 주세요", base).seatPreferences())
                .containsExactly("BACK");
        assertThat(extractor.extract("뒤쪽 말고 앞쪽으로 주세요", base).seatPreferences())
                .containsExactly("FRONT");
        assertThat(extractor.extract("통로 말고 창가로 주세요", base).seatPreferences())
                .containsExactly("WINDOW");
    }

    @Test
    void corrects_bus_grade_preference_in_both_directions() {
        // 기존엔 "우등 말고"만 예외 처리돼 있어서, "프리미엄 말고 일반으로"처럼 다른 등급끼리
        // 정정하면 거부된 등급(프리미엄)이 그대로 잡혔다.
        assertThat(extractor.extract("프리미엄 말고 일반으로 주세요", base).busGradePreference())
                .isEqualTo("GENERAL");
        assertThat(extractor.extract("일반 말고 프리미엄으로 주세요", base).busGradePreference())
                .isEqualTo("PREMIUM");
    }

    @Test
    void recognizes_back_seat_preference_despite_spacing_and_common_misspelling() {
        // 실제 보고된 사례: "뒷 쪽 통로"라고 직접 입력했는데 BACK을 인식하지 못했다. 음절 사이에
        // 공백이 낀 경우와, 표준 표기 "뒤쪽"의 흔한 오기 "뒷쪽"(뒷자리/뒷좌석에서 사이시옷을
        // 유추) 둘 다 원인이었다.
        assertThat(extractor.extract("뒷 쪽 통로", base).seatPreferences())
                .containsExactlyInAnyOrder("BACK", "AISLE");
        assertThat(extractor.extract("뒷쪽으로 주세요", base).seatPreferences())
                .containsExactly("BACK");
    }

    @Test
    void corrects_passenger_count_stated_twice_in_the_same_sentence() {
        // "3명 말고 2명이요"처럼 한 문장 안에서 인원수를 정정하면, find()가 첫 번째 값(3명)만
        // 잡아서 정정된 값(2명)이 무시되던 문제.
        assertThat(extractor.extract("3명 말고 2명이요", base).passengers()).isEqualTo(2);
        assertThat(extractor.extract("두 명 말고 세 명으로 바꿔줘", base).passengers()).isEqualTo(3);
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
}
