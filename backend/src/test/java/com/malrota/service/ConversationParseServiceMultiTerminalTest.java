package com.malrota.service;

import com.malrota.domain.ConversationSession;
import com.malrota.dto.request.ConversationParseRequest;
import com.malrota.dto.response.ConversationParseResponse;
import com.malrota.service.nlu.ConversationRuleExtractor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 복수 터미널 도시(서울/대구/부산/대전) 되묻기 흐름을 광범위하게 스트레스 테스트한다.
 * "센트럴시티 중복" 같은 사고가 다른 도시 조합/상황에서도 재발하지 않는지 계속 검증하기 위한 용도.
 * (광주/수원/청주는 실제로는 고속버스 터미널이 하나뿐이거나, 여러 개처럼 등록됐던 것들이 시외버스
 * 전용/존재하지 않는 이름으로 밝혀져 단일 터미널 도시가 됐다 — 더 이상 이 테스트의 대상이 아니다.)
 */
class ConversationParseServiceMultiTerminalTest {

    private final ConversationParseService service = new ConversationParseService(null, new ConversationRuleExtractor());

    private ConversationSession newSession(String departure, String arrival) {
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions(departure, arrival, "2026-08-28", "09:00", "MORNING", "ANY", "ANY",
                1, session.getSeatPreferences(), session.getAccessibilityNeeds(), null);
        return session;
    }

    private void apply(ConversationSession session, ConversationParseResponse r) {
        session.mergeConditions(r.departure(), r.arrival(), r.date(), r.departureTime(), r.timePreference(),
                r.servicePreference(), r.busGradePreference(), r.passengers(), r.seatPreferences(),
                r.accessibilityNeeds(), r.clarificationPrompt());
    }

    @Test
    void resolves_both_ambiguous_directions_one_at_a_time_without_cross_contamination() {
        // 출발/도착 둘 다 복수 터미널 도시(서울→대구)일 때, 한 번에 하나씩만 묻고 답이 엉뚱한
        // 방향으로 새지 않는지 확인한다.
        ConversationSession session = newSession("서울", "대구");

        ConversationParseResponse r1 = service.parse(new ConversationParseRequest("아무 말", "s1"), session);
        assertThat(r1.clarificationPrompt()).startsWith("서울 어느 터미널로");
        apply(session, r1);

        ConversationParseResponse r2 = service.parse(new ConversationParseRequest("강남", "s1"), session);
        assertThat(r2.departure()).isEqualTo("서울경부");
        assertThat(r2.arrival()).isEqualTo("대구"); // 아직 안 건드려야 함
        assertThat(r2.clarificationPrompt()).startsWith("대구 어느 터미널로");
        apply(session, r2);

        ConversationParseResponse r3 = service.parse(new ConversationParseRequest("동대구", "s1"), session);
        assertThat(r3.departure()).isEqualTo("서울경부"); // 여전히 유지
        assertThat(r3.arrival()).isEqualTo("동대구");
    }

    @Test
    void resolves_the_same_ambiguous_city_used_for_both_directions_independently() {
        // 출발/도착이 우연히 같은 복수 터미널 도시(서울↔서울)인 극단적인 경우에도 서로 안 섞이는지.
        ConversationSession session = newSession("서울", "서울");

        ConversationParseResponse r1 = service.parse(new ConversationParseRequest("아무 말", "s1"), session);
        assertThat(r1.clarificationPrompt()).startsWith("서울 어느 터미널로");
        apply(session, r1);

        // 첫 번째 "강남" 답변은 출발지(아직 구체화 안 된 첫 번째 서울)에 적용되어야 한다.
        ConversationParseResponse r2 = service.parse(new ConversationParseRequest("강남", "s1"), session);
        assertThat(r2.departure()).isEqualTo("서울경부");
        assertThat(r2.arrival()).isEqualTo("서울"); // 도착지는 아직 그대로
        apply(session, r2);

        // 두 번째 답변은 이제 도착지에 적용되어야 한다.
        ConversationParseResponse r3 = service.parse(new ConversationParseRequest("동서울", "s1"), session);
        assertThat(r3.departure()).isEqualTo("서울경부");
        assertThat(r3.arrival()).isEqualTo("동서울");
    }

    @Test
    void a_stray_standalone_terminal_after_everything_is_resolved_does_not_corrupt_anything() {
        // 모든 조건이 이미 확정된 상태에서 뜬금없이 단독 지명을 한 번 더 말해도 아무것도 안 바뀌어야 한다.
        ConversationSession session = newSession("서울경부", "동대구");

        ConversationParseResponse r = service.parse(new ConversationParseRequest("노포동", "s1"), session);

        assertThat(r.departure()).isEqualTo("서울경부");
        assertThat(r.arrival()).isEqualTo("동대구");
    }

    @Test
    void wrong_city_standalone_answer_gives_a_specific_message_for_multiple_city_pairs() {
        // "센트럴시티/대전" 조합뿐 아니라 다른 도시 조합에서도 같은 종류의 사고가 없는지 확인.
        ConversationSession session = newSession("부산", "대전");
        ConversationParseResponse r1 = service.parse(new ConversationParseRequest("아무 말", "s1"), session);
        assertThat(r1.clarificationPrompt()).startsWith("부산 어느 터미널로");
        apply(session, r1);

        // 대전 터미널("둔산")을 부산 되묻기에 답해버린 경우
        ConversationParseResponse r2 = service.parse(new ConversationParseRequest("둔산", "s1"), session);
        assertThat(r2.departure()).isEqualTo("부산"); // 안 바뀜
        assertThat(r2.arrival()).isEqualTo("대전");   // 안 바뀜
        assertThat(r2.clarificationPrompt()).doesNotContain("죄송해요");
        assertThat(r2.clarificationPrompt()).contains("대전").contains("부산");
    }

    @Test
    void a_third_unrelated_city_standalone_answer_does_not_silently_resolve_either_pending_direction() {
        // 출발/도착이 둘 다 애매한 상황(부산↔대전)에서, 둘 중 어디에도 안 속하는 제3의 도시(대구)로
        // 답하면 아무 데도 조용히 반영되지 않고 "지금 묻고 있는" 부산 쪽을 다시 안내해야 한다.
        ConversationSession session = newSession("부산", "대전");
        ConversationParseResponse r1 = service.parse(new ConversationParseRequest("아무 말", "s1"), session);
        assertThat(r1.clarificationPrompt()).startsWith("부산 어느 터미널로");
        apply(session, r1);

        ConversationParseResponse r2 = service.parse(new ConversationParseRequest("동대구", "s1"), session);
        assertThat(r2.departure()).isEqualTo("부산");
        assertThat(r2.arrival()).isEqualTo("대전");
        assertThat(r2.clarificationPrompt()).doesNotContain("죄송해요");
        assertThat(r2.clarificationPrompt()).contains("대구").contains("부산");
    }

    @Test
    void once_the_currently_asked_direction_is_resolved_a_matching_city_answer_moves_to_the_other_direction() {
        // 부산↔대전이 둘 다 애매할 때, 먼저 부산을 정확히 답하고 나면(노포동), 그 다음 턴에는
        // 대전 터미널("둔산")로 정확히 답했을 때 이번엔 진짜로 도착지에 반영되어야 한다 — 앞서 고친
        // "지금 묻는 쪽만 반영" 로직이 두 번째 턴까지 막아버리는 과잉 수정은 아닌지 확인.
        ConversationSession session = newSession("부산", "대전");
        ConversationParseResponse r1 = service.parse(new ConversationParseRequest("노포동", "s1"), session);
        assertThat(r1.departure()).isEqualTo("부산종합");
        assertThat(r1.arrival()).isEqualTo("대전"); // 아직 그대로
        assertThat(r1.clarificationPrompt()).startsWith("대전 어느 터미널로");
        apply(session, r1);

        ConversationParseResponse r2 = service.parse(new ConversationParseRequest("둔산", "s1"), session);
        assertThat(r2.departure()).isEqualTo("부산종합");
        assertThat(r2.arrival()).isEqualTo("대전청사");
    }

    @Test
    void accepts_a_disambiguation_answer_with_a_trailing_particle() {
        // 실제 발화는 "동대구" 단독보다 "동대구요"/"동대구로 할게요"처럼 조사가 붙는 경우가 흔하다.
        ConversationSession session = newSession("서울경부", "대구");

        ConversationParseResponse r = service.parse(new ConversationParseRequest("동대구요", "s1"), session);

        assertThat(r.arrival()).isEqualTo("동대구");
        assertThat(r.departure()).isEqualTo("서울경부");
    }

    @Test
    void a_full_sentence_answer_is_also_understood_via_the_normal_arrival_pattern() {
        ConversationSession session = newSession("서울경부", "대구");

        ConversationParseResponse r = service.parse(new ConversationParseRequest("동대구로 갈게요", "s1"), session);

        assertThat(r.arrival()).isEqualTo("동대구");
        assertThat(r.departure()).isEqualTo("서울경부");
    }
}
