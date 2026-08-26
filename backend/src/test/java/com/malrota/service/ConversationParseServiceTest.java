package com.malrota.service;

import com.malrota.domain.ConversationSession;
import com.malrota.dto.request.ConversationParseRequest;
import com.malrota.dto.response.ConversationParseResponse;
import com.malrota.service.nlu.ConversationRuleExtractor;
import org.junit.jupiter.api.Test;

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
}
