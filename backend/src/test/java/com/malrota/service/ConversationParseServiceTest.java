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

        // 절대 시각을 새로 말한 경우는 그 자체가 요청이므로, 상대적 "더 이르게" 신호와 겹치지 않는다.
        ConversationParseResponse r = service.parse(new ConversationParseRequest("더 빠른 8시로 바꿔줘", "s1"), session);

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
    void saying_first_bus_satisfies_the_departure_time_question_instead_of_repeating_it() {
        // 실제로 보고된 사고: "몇 시쯤 출발하는 버스를 원하시나요? '첫차', '막차'처럼 말씀해 주세요"
        // 라는 질문 자체가 '첫차'/'막차'를 유효한 답으로 안내하는데, missingRequired가 servicePreference를
        // 몰라서 "첫차"라고 정확히 답해도 시간 질문을 무한 반복했다.
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("서울", "대전", "2026-08-28", null, "ANY", "ANY", "ANY",
                1, session.getSeatPreferences(), session.getAccessibilityNeeds(), null);

        ConversationParseResponse r = service.parse(new ConversationParseRequest("첫차로 갈게요", "s1"), session);

        assertThat(r.servicePreference()).isEqualTo("FIRST");
        assertThat(r.missingFields()).doesNotContain("timePreference");
        assertThat(r.clarificationPrompt()).doesNotContain("몇 시쯤");
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
        session.mergeConditions("서울경부", "동대구", "2026-08-27", null, "MORNING", "ANY", "ANY",
                2, List.of(), List.of("ELDERLY_CARE"),
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
}
