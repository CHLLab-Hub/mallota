package com.malrota.service;

import com.malrota.client.WatsonxClient;
import com.malrota.domain.ConversationSession;
import com.malrota.dto.request.ConversationParseRequest;
import com.malrota.dto.response.ConversationParseResponse;
import com.malrota.service.nlu.ConversationRuleExtractor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationParseServiceTest {

    // watsonx 클라이언트 없이(rule-base 전용) 실행해 결정론적으로 검증한다.
    private final ConversationParseService service = new ConversationParseService(null, new ConversationRuleExtractor());

    @Test
    void does_not_fall_back_to_the_departure_city_when_arrival_is_stated_with_a_particle() {
        // 실제로 보고된 사고: "강릉에서 서울로 가는 버스 예매해줘"에서 도착지 추출이 실패해(원인은
        // ConversationRuleExtractor의 정규식 버그), 세션/LLM 폴백을 타다가 결국 출발지와 도착지가
        // 둘 다 "강릉"이 되어버렸다. 첫 발화이므로 세션도 LLM도 없어 순수하게 룰베이스 추출 결과로
        // 도착지가 채워져야 한다.
        ConversationSession session = new ConversationSession("s1");

        ConversationParseResponse r = service.parse(
                new ConversationParseRequest("강릉에서 서울로 가는 버스 예매해줘", "s1"), session);

        assertThat(r.departure()).isEqualTo("강릉고속");
        assertThat(r.arrival()).isEqualTo("서울");
    }

    @Test
    void remembers_explicit_passenger_count_and_seat_preference_without_asking_again() {
        // "한 명"도 기본값 1과 구별해 사용자가 직접 답했다는 사실을 세션에 남겨야 한다.
        // 그렇지 않으면 다음 발화에서 인원/좌석 질문이 다시 나타난다.
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("서울경부", "대전복합", "2026-08-28", "09:00", "MORNING", "ANY", "ANY",
                1, List.of(), List.of(), null);

        ConversationParseResponse first = service.parse(
                new ConversationParseRequest("한 명이고 창가가 좋아요", "s1"), session);
        assertThat(first.passengerMentioned()).isTrue();
        assertThat(first.seatPreferenceMentioned()).isTrue();

        session.mergeConditions(first.departure(), first.arrival(), first.date(), first.departureTime(),
                first.timePreference(), first.servicePreference(), first.busGradePreference(),
                first.passengers(), first.passengerMentioned(), first.seatPreferences(),
                first.seatPreferenceMentioned(), first.accessibilityNeeds(), first.clarificationPrompt());

        ConversationParseResponse next = service.parse(new ConversationParseRequest("네", "s1"), session);

        assertThat(session.isPassengerCountConfirmed()).isTrue();
        assertThat(session.isSeatPreferenceConfirmed()).isTrue();
        assertThat(next.clarificationPrompt()).isNull();
    }

    @Test
    void flags_relative_earlier_request_but_does_not_persist_it_in_the_session() {
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("서울", "대전", "2026-08-25", null, "MORNING", "ANY", "ANY",
                1, session.getSeatPreferences(), session.getAccessibilityNeeds(), null);

        ConversationParseResponse r1 = service.parse(new ConversationParseRequest("더 빠른 거 없어?", "s1"), session);
        assertThat(r1.wantsEarlierBus()).isTrue();

        // "더 빠른 거"는 세션에 쌓이는 조건이 아니라 이번 발화 1회성 신호라서, 다음 턴에 아무 언급이
        // 없으면 다시 false로 돌아와야 한다 (계속 남아 매번 "더 빠른 버스"를 찾으려 들면 안 됨).
        ConversationParseResponse r2 = service.parse(new ConversationParseRequest("창가로 주세요", "s1"), session);
        assertThat(r2.wantsEarlierBus()).isFalse();
    }

    @Test
    void suppresses_the_relative_earlier_flag_when_an_explicit_time_is_given_in_the_same_turn() {
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("서울", "대전", "2026-08-25", null, "MORNING", "ANY", "ANY",
                1, session.getSeatPreferences(), session.getAccessibilityNeeds(), null);

        // 오전/오후가 포함된 절대 시각을 새로 말한 경우는 그 자체가 요청이므로,
        // 상대적 "더 이르게" 신호와 겹치지 않는다.
        ConversationParseResponse r = service.parse(new ConversationParseRequest("더 빠른 오전 8시로 바꿔줘", "s1"), session);

        assertThat(r.departureTime()).isEqualTo("08:00");
        assertThat(r.wantsEarlierBus()).isFalse();
    }

    @Test
    void flags_relative_later_request_symmetrically_and_does_not_persist_it_either() {
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("서울", "대전", "2026-08-25", null, "MORNING", "ANY", "ANY",
                1, session.getSeatPreferences(), session.getAccessibilityNeeds(), null);

        ConversationParseResponse r1 = service.parse(new ConversationParseRequest("더 늦은 거 없어?", "s1"), session);
        assertThat(r1.wantsLaterBus()).isTrue();
        assertThat(r1.wantsEarlierBus()).isFalse();

        ConversationParseResponse r2 = service.parse(new ConversationParseRequest("창가로 주세요", "s1"), session);
        assertThat(r2.wantsLaterBus()).isFalse();
    }

    @Test
    void reason_clause_ending_in_seo_does_not_overwrite_the_existing_departure() {
        // 실제로 보고된 사고: 출발지가 이미 "부산종합"으로 정해진 상태에서 "햇빛이 싫어서 통로자리로
        // 잡아줘"라고 하자 "싫어"가 지명으로 오인되어 출발지가 "실어"(STT 오인식) 같은 값으로
        // 덮어써졌다. 좌석 선호는 정상적으로 반영하면서 기존 출발/도착지는 그대로 유지해야 한다.
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("부산종합", "동대구", "2026-09-10", "19:00", "EVENING", "ANY", "ANY",
                2, session.getSeatPreferences(), session.getAccessibilityNeeds(), null);

        ConversationParseResponse r = service.parse(new ConversationParseRequest("햇빛이 싫어서 통로자리로 잡아줘", "s1"), session);

        assertThat(r.departure()).isEqualTo("부산종합");
        assertThat(r.arrival()).isEqualTo("동대구");
        assertThat(r.seatPreferences()).contains("AISLE");
    }

    @Test
    void first_or_last_bus_satisfies_the_required_exact_departure_time_without_asking_again() {
        // "첫차"/"막차"는 그 자체로 이미 명확한 출발 시각 의도이므로, 정확한 시각을 다시 되묻지 않는다.
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("서울", "대전", "2026-08-28", null, "ANY", "ANY", "ANY",
                1, session.getSeatPreferences(), session.getAccessibilityNeeds(), null);

        ConversationParseResponse r = service.parse(new ConversationParseRequest("첫차로 갈게요", "s1"), session);

        assertThat(r.servicePreference()).isEqualTo("FIRST");
        assertThat(r.missingFields()).doesNotContain("departureTime");
    }

    @Test
    void last_bus_correction_clears_a_previously_set_time_of_day_preference() {
        // 실제로 보고된 사고: 세션에 이미 "오후"(timePreference=AFTERNOON) 시간대가 남아있는 상태에서
        // "아니다 막차로 할래"라고 정정하면, departureTime은 지워지는데 timePreference는 그대로
        // 남아 있어서 검색 단계가 "오후 안에서 가장 늦은 버스"만 찾아버려 진짜 막차가 아닌
        // 엉뚱한 시각이 추천됐다. 첫차/막차는 시간대와 무관한 절대적인 의미이므로 함께 지워야 한다.
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("서울", "대전", "2026-08-28", "15:00", "AFTERNOON", "ANY", "ANY",
                1, session.getSeatPreferences(), session.getAccessibilityNeeds(), null);

        ConversationParseResponse r = service.parse(new ConversationParseRequest("아니다 막차로 할래", "s1"), session);

        assertThat(r.servicePreference()).isEqualTo("LAST");
        assertThat(r.departureTime()).isNull();
        assertThat(r.timePreference()).isEqualTo("ANY");
    }

    @Test
    void acknowledges_a_service_preference_change_even_while_an_unrelated_question_is_pending() {
        // 실제로 보고된 사고: 출발지 도시("대구")가 아직 세부 터미널 되묻기 중인 상태에서 "첫차로
        // 부탁해"라고 말하면, servicePreference는 제대로 FIRST로 바뀌는데도 응답 문구는 여전히
        // "대구 어느 터미널로..."만 그대로 나가서 사용자는 자신의 말이 반영됐는지 알 수 없었다.
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("대구", "서울", "2026-08-28", null, "ANY", "ANY", "ANY",
                1, session.getSeatPreferences(), session.getAccessibilityNeeds(),
                "대구 어느 터미널로 원하시나요? 동대구, 서대구, 대구북부, 대구서부 중 편하신 곳을 말씀해 주세요.");

        ConversationParseResponse r = service.parse(
                new ConversationParseRequest("아니다 내일 아침 첫차로 부탁해", "s1"), session);

        assertThat(r.servicePreference()).isEqualTo("FIRST");
        assertThat(r.timePreference()).isEqualTo("MORNING");
        assertThat(r.clarificationPrompt()).startsWith("네, 첫차로 준비할게요.");
        assertThat(r.clarificationPrompt()).doesNotContain("죄송해요");
    }

    @Test
    void recognizes_common_mishearings_of_cheotcha_as_first_bus() {
        // "저차", "쳐차"는 음성 인식이 "첫차"를 잘못 받아적은 실제 사용자 보고 사례.
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("서울", "대전", "2026-08-28", null, "ANY", "ANY", "ANY",
                1, session.getSeatPreferences(), session.getAccessibilityNeeds(), null);

        assertThat(service.parse(new ConversationParseRequest("이번주 금요일에 저차 타고 싶다고", "s1"), session).servicePreference())
                .isEqualTo("FIRST");
    }

    @Test
    void applies_departure_correction_when_user_rejects_the_already_confirmed_terminal() {
        // 실제로 보고된 사고: 출발/도착 터미널이 이미 둘 다 확정된 뒤 "대전청사 말고 대전종합으로"처럼
        // 정정했는데, 시스템이 정정을 무시하고 옛 터미널(대전청사)을 그대로 쓴 채 인원수를 물었다.
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("대전청사", "서대구", "2026-08-28", null, "ANY", "ANY", "ANY",
                1, session.getSeatPreferences(), session.getAccessibilityNeeds(), null);

        ConversationParseResponse r = service.parse(
                new ConversationParseRequest("대전 청사 말고 대전 종합으로 부탁해", "s1"), session);

        assertThat(r.departure()).isEqualTo("대전복합");
        assertThat(r.arrival()).isEqualTo("서대구");
    }

    @Test
    void applies_arrival_correction_even_when_stt_mangles_the_rejected_terminal_name() {
        // "서대구"를 STT가 "선대 후"로 잘못 받아써도, "말고" 뒤의 원하는 터미널("동대구")만 정확히
        // 잡히면 도착지를 바꿀 수 있어야 한다.
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("대전청사", "서대구", "2026-08-28", null, "ANY", "ANY", "ANY",
                1, session.getSeatPreferences(), session.getAccessibilityNeeds(), null);

        ConversationParseResponse r = service.parse(
                new ConversationParseRequest("선대 후 말고 동대구로 부탁해", "s1"), session);

        assertThat(r.arrival()).isEqualTo("동대구");
        assertThat(r.departure()).isEqualTo("대전청사");
    }

    @Test
    void applies_correction_that_swaps_the_arrival_to_a_completely_different_city() {
        // 실제로 보고된 사고: "광주 종합 말고 동 대구로 부탁해"처럼 도착지를 아예 다른 도시(광주→대구)로
        // 바꾸는 정정은, 새 터미널(동대구)의 도시가 기존 출발(강릉)/도착(광주종합) 어느 쪽과도 같지
        // 않아서 예전 로직(같은 도시 매칭)으로는 판단할 수 없었다 — "말고" 앞쪽(광주종합)의 도시로
        // 판단해야 한다.
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("강릉고속", "광주종합", "2026-08-29", null, "MORNING", "FIRST", "ANY",
                1, session.getSeatPreferences(), session.getAccessibilityNeeds(), null);

        ConversationParseResponse r = service.parse(
                new ConversationParseRequest("광주 종합 말고 동 대구로 부탁해", "s1"), session);

        assertThat(r.arrival()).isEqualTo("동대구");
        assertThat(r.departure()).isEqualTo("강릉고속");
        assertThat(r.clarificationPrompt()).contains("도착지를 동대구로 바꿔드릴게요");
    }

    @Test
    void correcting_to_first_bus_clears_the_old_exact_time_already_in_session() {
        // 실제로 보고된 사고: 세션에 이미 정확한 시각(19:00, 저녁)이 있는 상태에서 "저녁 일곱시
        // 말고 첫차로 부탁해"라고 정정했는데, 옛 19:00이 세션에서 다시 끌려와 정확한 시각과
        // servicePreference=FIRST가 동시에 남는 모순이 생겼다.
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("서울", "대전", "2026-08-30", "19:00", "EVENING", "ANY", "ANY",
                1, session.getSeatPreferences(), session.getAccessibilityNeeds(), null);

        ConversationParseResponse r = service.parse(
                new ConversationParseRequest("저녁 일곱시 말고 첫 차로 부탁해", "s1"), session);

        assertThat(r.servicePreference()).isEqualTo("FIRST");
        assertThat(r.departureTime()).isNull();
    }

    @Test
    void acknowledges_a_terminal_correction_even_when_the_next_question_is_unchanged() {
        // 실제로 보고된 사고: 도착지를 "동대구 말고 서대구로" 정정해도, 뒤이어 나올 질문(날짜/시간)이
        // 정정 전과 똑같은 문구라서 사용자는 정정이 반영됐는지 전혀 알 수 없었다.
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("천안고속", "동대구", null, null, "ANY", "ANY", "ANY",
                1, session.getSeatPreferences(), session.getAccessibilityNeeds(), null);

        ConversationParseResponse r = service.parse(
                new ConversationParseRequest("동대구 말고 서대구로 부탁해", "s1"), session);

        assertThat(r.arrival()).isEqualTo("서대구");
        assertThat(r.clarificationPrompt()).contains("도착지를 서대구로 바꿔드릴게요");
        assertThat(r.clarificationPrompt()).doesNotContain("죄송해요");
    }

    @Test
    void recognizes_first_and_last_bus_even_when_stt_inserts_a_space() {
        // 실제로 보고된 사고: STT가 "첫차"/"막차"를 "첫 차"/"막 차"처럼 중간에 공백을 끼워 받아쓰면
        // 인식을 못 했다.
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("서울", "대전", "2026-08-28", null, "ANY", "ANY", "ANY",
                1, session.getSeatPreferences(), session.getAccessibilityNeeds(), null);

        assertThat(service.parse(new ConversationParseRequest("첫 차로 갈게요", "s1"), session).servicePreference())
                .isEqualTo("FIRST");
        assertThat(service.parse(new ConversationParseRequest("막 차 타고 싶어요", "s1"), session).servicePreference())
                .isEqualTo("LAST");
    }

    @Test
    void acknowledges_when_asking_the_exact_same_clarification_question_again() {
        // 사용자가 뭐라고 답했는데 시스템이 못 알아들어서 직전과 똑같은 질문을 또 하게 되면,
        // 아무 티도 없이 조용히 반복하지 말고 "잘 못 알아들었어요"라고 먼저 알려줘야 한다.
        ConversationSession session = new ConversationSession("s1");

        ConversationParseResponse r1 = service.parse(new ConversationParseRequest("아무 말이나", "s1"), session);
        session.mergeConditions(r1.departure(), r1.arrival(), r1.date(), r1.departureTime(), r1.timePreference(),
                r1.servicePreference(), r1.busGradePreference(), r1.passengers(), r1.seatPreferences(),
                r1.accessibilityNeeds(), r1.clarificationPrompt());

        ConversationParseResponse r2 = service.parse(new ConversationParseRequest("또 알아들을 수 없는 말", "s1"), session);

        assertThat(r1.clarificationPrompt()).isNotNull().doesNotStartWith("죄송해요");
        assertThat(r2.clarificationPrompt()).startsWith("죄송해요, 잘 못 알아들었어요. ");
        assertThat(r2.clarificationPrompt()).endsWith(r1.clarificationPrompt());
    }

    // watsonx가 "언급 없는 필드는 기존 값을 그대로 복사하라"는 지시를 놓치고, 자기 나름의
    // 기본값(도시명 단순화, 인원 1명, 빈 배열)으로 되돌려버리는 실제 상황을 흉내낸다.
    private static class MisbehavingWatsonxClient extends WatsonxClient {
        MisbehavingWatsonxClient() { super(null); }
        @Override public boolean isConfigured() { return true; }
        @Override public String ask(String prompt) {
            return """
                {"intent":"BUS_SEARCH","departure":"서울","arrival":"대구","date":"2026-08-27",
                 "departureTime":null,"timePreference":"MORNING","servicePreference":"ANY",
                 "busGradePreference":"ANY","passengers":1,"seatPreferences":[],"accessibilityNeeds":[]}
                """;
        }
    }

    @Test
    void session_survives_a_misbehaving_llm_that_forgets_unmentioned_fields() {
        // 실제로 보고된 사고: 출발지 "서울경부"(구체 터미널)가 "서울"(도시명)로, 인원 2명이 1명으로,
        // 접근성 배려 ELDERLY_CARE가 통째로 사라지는 일이 있었다 — 사용자는 시간대만 말했을 뿐인데도.
        // LLM이 "기존 값 유지" 지시를 못 지켜도, 룰베이스가 못 찾은 필드는 LLM보다 세션을 먼저
        // 신뢰해야 이미 확정된 조건이 조용히 초기화되지 않는다.
        ConversationParseService serviceWithLlm = new ConversationParseService(new MisbehavingWatsonxClient(), new ConversationRuleExtractor());
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("서울경부", "대구", "2026-08-27", null, "MORNING", "ANY", "ANY",
                2, List.of(), List.of("ELDERLY_CARE"), "대구 어느 터미널로 원하시나요?");

        ConversationParseResponse r = serviceWithLlm.parse(new ConversationParseRequest("이번주 목요일 아침", "s1"), session);

        assertThat(r.departure()).isEqualTo("서울경부");
        assertThat(r.passengers()).isEqualTo(2);
        assertThat(r.accessibilityNeeds()).contains("ELDERLY_CARE");
    }

    @Test
    void seat_preference_question_is_still_asked_even_when_accessibility_was_inferred_earlier() {
        // 실제로 보고된 사고: "할머니 모시고" 같은 말에서 ELDERLY_CARE가 자동으로 채워지면, 그 이후
        // "seatPrefs/accessNeeds가 비어있으면 물어본다"는 조건이 거짓이 되어 정작 창가/통로 같은
        // 좌석 자체 선호는 한 번도 못 물어보고 넘어가 버렸다 (accessNeeds가 채워진 건 추론일 뿐,
        // 실제로 좌석 선호를 물어본 적은 없는데도).
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("서울경부", "동대구", "2026-08-27", "09:00", "MORNING", "ANY", "ANY",
                2, true, List.of(), false, List.of("ELDERLY_CARE"),
                "몇 시쯤 출발하는 버스를 원하시나요? '오전 9시', '오후 3시', '첫차', '막차'처럼 말씀해 주세요.");

        ConversationParseResponse r = service.parse(new ConversationParseRequest("이번주 목요일 아침", "s1"), session);
        assertThat(r.clarificationPrompt()).contains("더 편하신 좌석이 있으신가요");

        // 답을 하고 나면 같은 질문을 다시 반복하지 않는다.
        session.mergeConditions(r.departure(), r.arrival(), r.date(), r.departureTime(), r.timePreference(),
                r.servicePreference(), r.busGradePreference(), r.passengers(), r.seatPreferences(),
                r.accessibilityNeeds(), r.clarificationPrompt());
        ConversationParseResponse r2 = service.parse(new ConversationParseRequest("네 괜찮아요", "s1"), session);
        boolean askedAgain = r2.clarificationPrompt() != null && r2.clarificationPrompt().contains("더 편하신 좌석이 있으신가요");
        assertThat(askedAgain).isFalse();
    }

    @Test
    void a_vague_time_of_day_bucket_alone_is_not_enough_to_proceed() {
        // 실제 피드백: "오전"/"오후"만 받고 넘어가면 실제 버스 시각이 중구난방으로 흩어진다.
        // 정확한 시각(또는 첫차/막차)을 받을 때까지 계속 시간을 되물어야 한다.
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("서울", "대전", "2026-08-25", null, "MORNING", "ANY", "ANY",
                1, session.getSeatPreferences(), session.getAccessibilityNeeds(), null);

        ConversationParseResponse r = service.parse(new ConversationParseRequest("네", "s1"), session);

        assertThat(r.missingFields()).contains("departureTime");
        assertThat(r.clarificationPrompt()).contains("몇 시쯤");
        // 이미 "오전"이라고 말한 건 알고 있으니, 처음부터 다시 묻지 않고 오전 중 몇 시인지만 좁혀 묻는다.
        assertThat(r.clarificationPrompt()).contains("오전");
    }

    @Test
    void an_exact_time_satisfies_the_requirement_and_stops_asking() {
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("서울", "대전", "2026-08-25", null, "MORNING", "ANY", "ANY",
                1, session.getSeatPreferences(), session.getAccessibilityNeeds(), null);

        ConversationParseResponse r = service.parse(new ConversationParseRequest("오전 9시요", "s1"), session);

        assertThat(r.departureTime()).isEqualTo("09:00");
        assertThat(r.missingFields()).doesNotContain("departureTime");
    }

    @Test
    void standalone_terminal_for_the_wrong_city_gets_a_specific_message_instead_of_a_generic_apology() {
        // 실제로 보고된 사고: 출발지가 이미 "센트럴시티"(서울)로 정해진 상태에서 도착지 "대전"의
        // 세부 터미널을 물었더니 "센트럴시티"(서울 터미널)라고 답한 경우. 둘 다 센트럴시티로
        // 덮어써지지는 않는지, 그리고 "잘 못 알아들었어요"가 아니라 어느 도시 터미널인지 구체적으로
        // 안내하는지 확인한다.
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("센트럴시티", "대전", "2026-08-28", "09:00", "MORNING", "ANY", "ANY",
                1, List.of(), List.of(),
                "대전 어느 터미널로 원하시나요? 대전복합, 유성고속, 대전청사 중 편하신 곳을 말씀해 주세요.");

        ConversationParseResponse r = service.parse(new ConversationParseRequest("센트럴시티", "s1"), session);

        assertThat(r.departure()).isEqualTo("센트럴시티");
        assertThat(r.arrival()).isEqualTo("대전");
        assertThat(r.clarificationPrompt()).doesNotContain("죄송해요");
        assertThat(r.clarificationPrompt()).contains("서울").contains("대전");
    }

    @Test
    void assigns_a_standalone_terminal_change_to_the_matching_endpoint_city() {
        ConversationSession busanSession = new ConversationSession("s1");
        busanSession.mergeConditions("부산서부", "동대구", "2026-09-10", "19:00", "EVENING", "ANY", "ANY",
                1, true, List.of("WINDOW"), true, List.of(), null);

        ConversationParseResponse busan = service.parse(new ConversationParseRequest("노포동으로 바꿔줘", "s1"), busanSession);
        assertThat(busan.departure()).isEqualTo("부산종합");
        assertThat(busan.arrival()).isEqualTo("동대구");

        ConversationSession seoulSession = new ConversationSession("s2");
        seoulSession.mergeConditions("서울경부", "대전복합", "2026-09-10", "19:00", "EVENING", "ANY", "ANY",
                1, true, List.of("WINDOW"), true, List.of(), null);

        ConversationParseResponse seoul = service.parse(new ConversationParseRequest("센트럴시티로 바꿔줘", "s2"), seoulSession);
        assertThat(seoul.departure()).isEqualTo("센트럴시티");
        assertThat(seoul.arrival()).isEqualTo("대전복합");
    }

    @Test
    void asks_for_am_or_pm_instead_of_guessing_any_bare_clock_time() {
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("서울경부", "대전복합", "2026-09-10", null, "ANY", "ANY", "ANY",
                1, true, List.of("WINDOW"), true, List.of(), null);

        ConversationParseResponse result = service.parse(new ConversationParseRequest("8시로 할게요", "s1"), session);

        assertThat(result.departureTime()).isNull();
        assertThat(result.clarificationPrompt()).contains("오전인지 오후인지");
    }

    @Test
    void accepts_no_seat_preference_as_a_final_answer_and_uses_default_recommendation() {
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("서울경부", "부산종합", "2026-09-10", "19:00", "EVENING", "ANY", "ANY",
                2, true, List.of(), false, List.of(), "더 편하신 좌석이 있으신가요?");

        ConversationParseResponse first = service.parse(new ConversationParseRequest("아무렇게나 해주세요", "s1"), session);

        assertThat(first.seatPreferenceMentioned()).isTrue();
        assertThat(first.seatPreferences()).isEmpty();
        assertThat(first.clarificationPrompt()).isNull();

        session.mergeConditions(first.departure(), first.arrival(), first.date(), first.departureTime(),
                first.timePreference(), first.servicePreference(), first.busGradePreference(),
                first.passengers(), first.passengerMentioned(), first.seatPreferences(),
                first.seatPreferenceMentioned(), first.accessibilityNeeds(), first.clarificationPrompt());
        ConversationParseResponse next = service.parse(new ConversationParseRequest("네", "s1"), session);

        assertThat(session.isSeatPreferenceConfirmed()).isTrue();
        assertThat(next.clarificationPrompt()).isNull();
    }

    @Test
    void corrects_a_previously_selected_departure_terminal_while_arrival_is_already_selected() {
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("동서울", "부산종합", "2026-09-10", "19:00", "EVENING", "ANY", "ANY",
                1, true, List.of(), true, List.of(), null);

        ConversationParseResponse result = service.parse(
                new ConversationParseRequest("아니야, 동서울말고 센트럴로 바꿔줘", "s1"), session);

        assertThat(result.departure()).isEqualTo("센트럴시티");
        assertThat(result.arrival()).isEqualTo("부산종합");
        // 정정이 조용히 반영되고 넘어가면 사용자는 실제로 반영됐는지 알 수 없으므로 확인 문구를 붙인다.
        assertThat(result.clarificationPrompt()).contains("센트럴시티");
    }

    @Test
    void corrects_departure_expressed_only_as_bare_city_names_without_a_specific_terminal() {
        // 실제로 보고된 사고: "서울 말고 대구로 할께"처럼 세부 터미널 없이 도시명만으로 정정하면
        // 아무 반응 없이 조용히 무시됐다. "서울"/"대구"는 도시명 자체가 등록된 터미널 별칭이 아니라서
        // TagoClient.cityOf가 null을 반환해, 이미 확실히 추출된 correctionTerminal/rejectedTerminal이
        // 있어도 어느 방향(출발/도착)인지 판단하는 단계에서 조용히 실패했었다.
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("서울", "대전", "2026-09-10", null, "ANY", "ANY", "ANY",
                1, session.getSeatPreferences(), session.getAccessibilityNeeds(), null);

        ConversationParseResponse result = service.parse(
                new ConversationParseRequest("서울 말고 대구로 할께", "s1"), session);

        assertThat(result.departure()).isEqualTo("대구");
        assertThat(result.arrival()).isEqualTo("대전");
        assertThat(result.clarificationPrompt()).contains("대구");
    }
}
