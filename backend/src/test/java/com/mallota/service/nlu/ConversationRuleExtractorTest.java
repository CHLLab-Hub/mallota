package com.mallota.service.nlu;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationRuleExtractorTest {

    private final ConversationRuleExtractor extractor = new ConversationRuleExtractor();

    @Test
    void treats_chocheong_as_a_stt_mishearing_of_cheotcha() {
        // 실제로 보고된 사고: STT가 "첫차"를 "초청"으로 잘못 받아쓴다("저차"/"쳐차"와 같은 종류의
        // 오인식). 이 앱은 초청장 예약 같은 기능이 없으므로 안전하게 "첫차"로 간주한다.
        var result = extractor.extract("매일 아침 초청", LocalDateTime.of(2026, 8, 24, 10, 0));

        assertThat(result.servicePreference()).isEqualTo("FIRST");
    }

    @Test
    void recognizes_a_terminal_name_split_by_a_spurious_space_and_the_butak_ending() {
        // 실제로 보고된 사고: "서울 경부로 부탁해요"에서 (1) "서울"과 "경부" 사이에 공백이 끼어들어
        // "서울경부"와 정확히 일치하지 않았고, (2) "부탁해요"라는 종결 어미 자체가 도착지 어미
        // 목록(가다/도착)에 없어서 이중으로 인식이 실패했다.
        var result = extractor.extract("서울 경부로 부탁해요", LocalDateTime.of(2026, 8, 24, 10, 0));

        assertThat(result.arrival()).isEqualTo("서울경부");
    }

    @Test
    void does_not_collapse_a_space_that_is_a_real_word_boundary() {
        // 실제로 보고된 사고: "천안에서 부산가는"에서 "에[서] 부산"이 "서부산"(부산서부의 별칭)과
        // 우연히 겹쳐서, "에서"의 "서"와 "부산"이 하나로 잘못 합쳐졌다("천안에서부산가는"). 매칭
        // 시작 지점 바로 앞이 이미 한글(다른 단어 중간)이면 진짜 터미널명이 아니므로 제외해야 한다.
        var result = extractor.extract("천안에서 부산가는 버스 알려줘", LocalDateTime.of(2026, 8, 24, 10, 0));

        assertThat(result.departure()).isEqualTo("천안고속");
        assertThat(result.arrival()).isEqualTo("부산");
    }

    @Test
    void does_not_double_count_a_correction_target_as_a_fresh_arrival_statement() {
        // 실제로 보고된 사고: "대전청사 말고 대전터미널로 부탁해"에서 "대전터미널로 부탁"이 정정
        // 문구(말고 뒤)이자 동시에 일반 도착지 문형(부탁 어미)에도 걸려서, 같은 지명이 "정정 대상"과
        // "새로 언급된 도착지" 양쪽으로 중복 인식되며 충돌했다.
        var result = extractor.extract("대전청사 말고 대전터미널로 부탁해", LocalDateTime.of(2026, 8, 24, 10, 0));

        assertThat(result.arrival()).isNull();
        assertThat(result.correctionTerminal()).isEqualTo("대전복합");
        assertThat(result.rejectedTerminal()).isEqualTo("대전청사");
    }

    @Test
    void treats_maeil_as_a_stt_mishearing_of_naeil() {
        // 실제로 보고된 사고: STT가 "내일"을 "매일"로 잘못 받아쓴다(ㄴ/ㅁ 자음 혼동이라 모음 혼동
        // 보정으로는 못 잡는다). 이 앱은 특정 하루짜리 예매만 다루므로 "매일"이 실제로 "매번/반복"
        // 의미로 쓰일 문맥이 없어, 안전하게 "내일"로 간주해야 한다.
        var result = extractor.extract("매일 아침 첫 차로 예매해줘", LocalDateTime.of(2026, 8, 24, 10, 0));

        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 8, 25));
    }

    @Test
    void flags_this_weekend_as_ambiguous_between_saturday_and_sunday() {
        // 실제로 보고된 사고: "이번 주말"을 임의로 토요일로 조용히 확정해버려서, 사용자가 일요일을
        // 의도했어도 확인 없이 넘어갔다. 요일까지 명시하지 않았다면 추측하지 말고 되물어야 한다.
        var result = extractor.extract("이번 주말 오후에 갈게요", LocalDateTime.of(2026, 8, 24, 10, 0));

        assertThat(result.date()).isNull();
        assertThat(result.ambiguousWeekend()).isTrue();
    }

    @Test
    void does_not_flag_weekend_as_ambiguous_once_a_specific_day_is_named() {
        // "이번 주말 토요일"처럼 요일까지 명시하면 더 이상 애매하지 않다.
        var result = extractor.extract("이번 주말 토요일 오후에 갈게요", LocalDateTime.of(2026, 8, 24, 10, 0));

        assertThat(result.date()).isNotNull();
        assertThat(result.ambiguousWeekend()).isFalse();
    }

    @Test
    void captures_an_unregistered_place_name_stated_as_the_arrival() {
        // 실제로 보고된 사고: 등록 안 된 지명("완도")을 "-(으)로 가는" 문형으로 말하면
        // isPlausibleTerminal이 조용히 걸러내 버려서, 사용자는 아무 반응이 없거나 "어디로
        // 가시나요?"만 계속 반복해서 듣게 됐다. 이젠 그 지명을 unrecognizedArrival에 담아서
        // ConversationParseService가 "그 지역은 아직 지원하지 않는다"고 알려줄 수 있게 한다.
        var result = extractor.extract("서울경부에서 완도로 가는 버스", LocalDateTime.of(2026, 8, 24, 10, 0));

        assertThat(result.arrival()).isNull();
        assertThat(result.unrecognizedArrival()).isEqualTo("완도");
    }

    @Test
    void does_not_flag_a_registered_place_name_as_unrecognized() {
        var result = extractor.extract("서울경부에서 포항으로 가는 버스", LocalDateTime.of(2026, 8, 24, 10, 0));

        assertThat(result.arrival()).isNotNull();
        assertThat(result.unrecognizedArrival()).isNull();
    }

    @Test
    void keeps_departure_and_arrival_distinct_for_a_full_route_sentence() {
        var result = extractor.extract("서울에서 대전으로 가는 버스 예약해줘", LocalDateTime.of(2026, 8, 24, 10, 0));

        assertThat(result.departure()).isEqualTo("서울");
        assertThat(result.arrival()).isEqualTo("대전");
    }

    @Test
    void does_not_mistake_traveling_alone_for_an_unrecognized_departure_place() {
        // 실제로 보고된 사고: "혼자서 서울 갈려고"에서 "혼자서"의 "혼자"가 "-서" 문형(출발지 표현)에
        // 우연히 걸려서 미지원 지역("혼자")으로 잘못 안내됐다. "혼자"는 인원수 표현일 뿐 지명이
        // 아니므로 출발지 후보로 잡히면 안 되고, 도착지("서울")는 정상적으로 인식돼야 한다.
        var result = extractor.extract("혼자서 서울 갈려고", LocalDateTime.of(2026, 8, 24, 10, 0));

        assertThat(result.unrecognizedDeparture()).isNull();
        assertThat(result.departure()).isNull();
        assertThat(result.arrival()).isEqualTo("서울");
        assertThat(result.passengers()).isEqualTo(1);
    }

    @Test
    void recognizes_arrival_even_when_glued_directly_to_galryeogo_with_no_space() {
        // 실제로 보고된 사고: "혼자 서울갈려고"처럼 지명과 "갈려고" 사이에 공백이 없어도(띄어쓰기
        // 없이 붙여 말해도) 도착지를 인식해야 한다.
        var result = extractor.extract("혼자 서울갈려고", LocalDateTime.of(2026, 8, 24, 10, 0));

        assertThat(result.unrecognizedDeparture()).isNull();
        assertThat(result.arrival()).isEqualTo("서울");
    }

    @Test
    void does_not_mistake_location_pronouns_or_connectives_for_an_unrecognized_departure() {
        // "혼자"와 같은 정규식 구조(2음절 이상 + "-서")로 오탐되는 다른 흔한 단어들: "여기/거기/저기"
        // (지시대명사)와 "그래서/따라서"(접속사). 실제 지명이 아니므로 출발지 후보로 잡히면 안 된다.
        assertThat(extractor.extract("여기서 대전 가는 버스 있어?", LocalDateTime.of(2026, 8, 24, 10, 0)).unrecognizedDeparture()).isNull();
        assertThat(extractor.extract("거기서 출발할게요", LocalDateTime.of(2026, 8, 24, 10, 0)).unrecognizedDeparture()).isNull();
        assertThat(extractor.extract("저기서 타면 돼요", LocalDateTime.of(2026, 8, 24, 10, 0)).unrecognizedDeparture()).isNull();
        assertThat(extractor.extract("그래서 서울 갈게요", LocalDateTime.of(2026, 8, 24, 10, 0)).unrecognizedDeparture()).isNull();
        assertThat(extractor.extract("따라서 대전으로 갑니다", LocalDateTime.of(2026, 8, 24, 10, 0)).unrecognizedDeparture()).isNull();
    }

    @Test
    void does_not_mistake_the_subject_particle_ga_for_the_go_verb_ending() {
        // 실제로 보고된 사고: "친구가 다리를 다쳤어"에서 "친구가"의 "가"가 주격 조사일 뿐인데,
        // 어미 없는 맨 "가"도 "가다"(go) 동사로 오인해서 "친구"가 도착지 후보로 잡혔다.
        // "명사+가"(주격 조사)는 극히 흔한 문형이라 이 오탐의 파급력이 크다.
        var result = extractor.extract("친구가 다리를 다쳤어", base);

        assertThat(result.arrival()).isNull();
        assertThat(result.unrecognizedArrival()).isNull();
    }

    @Test
    void does_not_mistake_directional_verbs_for_an_unrecognized_arrival() {
        // GENERIC_ARR_PATTERN의 "-가다" 어미 매칭이 "돌아가다/내려가다/올라가다/넘어가다/지나가다/
        // 다녀가다" 같은 흔한 동사의 어간을 지명으로 오인하면 안 된다.
        assertThat(extractor.extract("다시 돌아가고 싶어요", LocalDateTime.of(2026, 8, 24, 10, 0)).unrecognizedArrival()).isNull();
        assertThat(extractor.extract("내려가고 싶어요", LocalDateTime.of(2026, 8, 24, 10, 0)).unrecognizedArrival()).isNull();
        assertThat(extractor.extract("올라가고 싶어요", LocalDateTime.of(2026, 8, 24, 10, 0)).unrecognizedArrival()).isNull();
        assertThat(extractor.extract("넘어가고 싶어요", LocalDateTime.of(2026, 8, 24, 10, 0)).unrecognizedArrival()).isNull();
        assertThat(extractor.extract("지나가고 싶어요", LocalDateTime.of(2026, 8, 24, 10, 0)).unrecognizedArrival()).isNull();
    }
    private final LocalDateTime base = LocalDateTime.of(2026, 8, 24, 10, 0);

    @Test
    void resolves_arrival_stated_with_a_particle_for_every_multi_terminal_city() {
        // 실제 보고된 사고: "강릉에서 서울로 가는 버스 예매해줘"에서 도착지가 아예 안 잡혀서,
        // 세션/LLM 폴백을 타다가 결국 출발지와 같은 "강릉"으로 도착지가 잘못 채워졌다. 근본 원인은
        // GENERIC_ARR_PATTERN의 탐욕적 캡처가 "서울" 대신 "서울로"(조사 포함)를 통째로 잡아버려서
        // 등록된 터미널명이 아니라고 거부(isPlausibleTerminal)해 버리는 것이었다. 터미널이 여럿이라
        // 별칭에 등록되지 않은 4개 도시(서울/대구/대전/부산) 전부에서 재현되므로 전부 확인한다.
        // (광주는 이제 단일 터미널 도시라 "광주" 자체가 별칭으로 등록돼 있어 이 목록에서 빠졌다.)
        assertThat(extractor.extract("강릉에서 서울로 가는 버스 예매해줘", base).arrival()).isEqualTo("서울");
        assertThat(extractor.extract("천안에서 대구로 가는 버스", base).arrival()).isEqualTo("대구");
        assertThat(extractor.extract("천안에서 대전으로 가는 버스", base).arrival()).isEqualTo("대전");
        assertThat(extractor.extract("천안에서 부산으로 가는 버스", base).arrival()).isEqualTo("부산");
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
    void recognizes_pregnancy_and_visual_impairment_accessibility_needs() {
        assertThat(extractor.extract("임산부라 좌석 부탁드려요", base).accessibilityNeeds()).containsExactly("PREGNANCY");
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
    void mentioning_traveling_alone_does_not_count_as_answering_the_seat_preference_question() {
        // 실제로 보고된 사고: "혼자서 서울 갈려고"라는 인원수 발화만으로 좌석/배려 선호 질문
        // 자체를 "이미 물어봤다"고 세션에 영구 확정해버려서, 이후 "멀미 심해요" 같은 진짜 배려
        // 사항을 말할 기회(질문)가 다시는 오지 않는 사고로 이어졌다. "혼자"는 SINGLE 좌석
        // 선호로는 반영하되, 좌석/배려 질문에 답했다는 뜻은 아니어야 한다.
        var result = extractor.extract("혼자서 서울 갈려고", base);

        assertThat(result.seatPreferenceMentioned()).isFalse();
        assertThat(result.seatPreferences()).containsExactly("SINGLE");
    }

    @Test
    void infers_two_passengers_when_traveling_with_a_friend() {
        // 실제로 보고된 사고: "친구랑 대전에서 부산으로 갈려고"라고 말해도 인원이 기본값 1에서
        // 바뀌지 않았다. "친구"는 가족은 아니지만 "혼자"가 아니라는 뜻이므로 2명으로 추론하고,
        // 이 발화 자체로 인원을 이미 언급한 것으로 처리해야 한다.
        var result = extractor.extract("친구랑 대전에서 부산으로 갈려고", base);

        assertThat(result.passengers()).isEqualTo(2);
        assertThat(result.passengerMentioned()).isTrue();
    }

    @Test
    void recognizes_wa_gwa_particle_as_a_companion_expression_too() {
        // "랑" 말고 "와/과"(표준적인 "함께" 조사)로 말해도 동행으로 인정해야 한다.
        assertThat(extractor.extract("친구와 부산 가는 버스", base).passengers()).isEqualTo(2);
        assertThat(extractor.extract("동생과 대전 가는 버스", base).passengers()).isEqualTo(2);
    }

    @Test
    void does_not_treat_a_bare_wa_gwa_syllable_unattached_to_a_companion_noun_as_traveling_together() {
        // "출발과 도착"처럼 "과"가 동행 명사와 무관하게 그냥 접속조사로 쓰인 경우까지 동행으로
        // 오인하면 안 된다 — 조사가 동행 명사 바로 뒤에 붙어 있을 때만 인정해야 한다.
        var result = extractor.extract("출발과 도착 시간을 알려줘", base);

        assertThat(result.passengers()).isEqualTo(0);
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
        // 실제 보고된 사례: "대전청사 말고 대전터미널로", "선대 후 말고 동대구로"(STT가 "서대구"를
        // "선대 후"로 오인식)처럼 이미 확정한 터미널을 다른 터미널로 바꿔달라는 표현. "말고" 앞쪽이
        // 못 알아들을 말이어도(등록되지 않은 터미널이어도) "말고" 뒤의 원하는 터미널만 정확히 잡으면 된다.
        assertThat(extractor.extract("대전 청사 말고 대전 터미널로 부탁해", base).correctionTerminal()).isEqualTo("대전복합");
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
    void extracts_a_correction_expressed_only_as_bare_city_names_without_a_specific_terminal() {
        // 실제 보고된 사례: "서울 말고 대구로 할께"처럼 세부 터미널 없이 도시명만으로 정정하면
        // 아무것도 못 알아듣고 조용히 무시했다 — "서울"/"대구"는 터미널이 여럿이라 도시명 자체가
        // 어느 터미널의 별칭으로도 등록돼 있지 않기 때문. 도시명만으로도 정정을 잡아내야 한다.
        var result = extractor.extract("서울 말고 대구로 할께", base);

        assertThat(result.rejectedTerminal()).isEqualTo("서울");
        assertThat(result.correctionTerminal()).isEqualTo("대구");
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
    void infers_aisle_or_window_from_sunlight_preference() {
        // 실제로 보고된 사고: "햇빛이 안들어오는 자리로 해줘"가 통로(AISLE)가 아니라 창가(WINDOW)로
        // 잘못 채택됐다 — "햇빛"이라는 단어 자체만 보고 부정 표현("안"/"싫어")을 놓쳤기 때문이다.
        assertThat(extractor.extract("햇빛이 안들어오는 자리로 해줘", base).seatPreferences())
                .containsExactly("AISLE");
        assertThat(extractor.extract("햇빛 싫어서 그늘진 자리로 주세요", base).seatPreferences())
                .containsExactly("AISLE");
        assertThat(extractor.extract("햇빛 잘 드는 자리로 주세요", base).seatPreferences())
                .containsExactly("WINDOW");
    }

    @Test
    void corrects_passenger_count_stated_twice_in_the_same_sentence() {
        // "3명 말고 2명이요"처럼 한 문장 안에서 인원수를 정정하면, find()가 첫 번째 값(3명)만
        // 잡아서 정정된 값(2명)이 무시되던 문제.
        assertThat(extractor.extract("3명 말고 2명이요", base).passengers()).isEqualTo(2);
        assertThat(extractor.extract("두 명 말고 세 명으로 바꿔줘", base).passengers()).isEqualTo(3);
    }

    @Test
    void does_not_mistake_the_trailing_syllable_of_an_unrelated_word_for_a_passenger_count() {
        // 실제로 보고된 사고: "편안한 자리로 해줘"에서 "편안한"의 끝 글자 "한"이 순우리말 수사
        // "한"(1명)으로 오인되고 뒤이은 "자리"와 엮여 "한 자리"로 잡혀, 세션에 이미 있던 인원수
        // (예: 3명)가 아무도 인원을 언급하지 않았는데도 1명으로 조용히 바뀌어 버렸다.
        var result = extractor.extract("임산부가 있어서 편안한 자리로 해주고 뒤쪽 자리로 줘", base);

        assertThat(result.passengers()).isZero();
        assertThat(result.passengerMentioned()).isFalse();
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
