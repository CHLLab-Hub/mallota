package com.malrota.service;

import com.malrota.client.TagoClient;
import com.malrota.client.WatsonxClient;
import com.malrota.domain.ConversationSession;
import com.malrota.dto.request.ConversationParseRequest;
import com.malrota.dto.response.BusSchedule;
import com.malrota.dto.response.ConversationParseResponse;
import com.malrota.service.nlu.ConversationRuleExtractor;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationParseServiceTest {

    // 출발/도착 사이에 노선이 있는지 확인하는 로직이 이 테스트들의 관심사(NLU/세션 상태)를 방해하지
    // 않도록, 어떤 출발지-도착지를 물어봐도 항상 노선이 있다고 답하는 가짜 TagoClient를 쓴다.
    private static BusSearchService alwaysHasRouteService() {
        TagoClient fakeTagoClient = new TagoClient(null) {
            @Override public String findTerminalId(String terminalName) { return terminalName; }

            @Override public List<BusSchedule> searchBuses(String depId, String arrId, String date) {
                return List.of(new BusSchedule("R01", "우등", depId, arrId, date + "0900", date + "1000", 10_000));
            }
        };
        return new BusSearchService(fakeTagoClient);
    }

    // watsonx 클라이언트 없이(rule-base 전용) 실행해 결정론적으로 검증한다.
    private final ConversationParseService service = new ConversationParseService(null, new ConversationRuleExtractor(), alwaysHasRouteService());

    @Test
    void reports_route_not_found_as_soon_as_departure_and_arrival_are_both_resolved() {
        // 실제로 보고된 요구사항: 노선이 없으면 날짜/인원/좌석까지 다 물어본 뒤에야 알리지 말고,
        // 출발지-도착지가 확정되는 즉시(날짜를 묻기 전에) 바로 확인해서 알려줘야 한다.
        TagoClient noRouteTagoClient = new TagoClient(null) {
            @Override public String findTerminalId(String terminalName) { return terminalName; }

            @Override public List<BusSchedule> searchBuses(String depId, String arrId, String date) {
                return List.of(); // 이 두 터미널 사이에는 노선이 없다
            }
        };
        ConversationParseService serviceWithNoRoute = new ConversationParseService(
                null, new ConversationRuleExtractor(), new BusSearchService(noRouteTagoClient));

        ConversationParseResponse r = serviceWithNoRoute.parse(
                new ConversationParseRequest("서울경부에서 포항으로 가는 버스", "s1"), new ConversationSession("s1"));

        assertThat(r.routeNotFound()).isTrue();
        assertThat(r.clarificationPrompt()).contains("서울경부").contains("포항").contains("직행");
        // 노선이 없다는 걸 안 이상, 날짜를 묻는 등 다음 단계로 넘어가면 안 된다.
        assertThat(r.clarificationPrompt()).doesNotContain("날짜");
    }

    @Test
    void reports_route_not_found_before_asking_which_terminal_of_an_ambiguous_city() {
        // 실제로 보고된 사고: "서울"처럼 세부 터미널이 여러 개인 도시는 "서울경부/센트럴시티/동서울
        // 중 어디로?"를 먼저 물어본 뒤에야(한 턴 낭비) 노선 존재를 확인했다. 그 도시 전체에 노선이
        // 없으면, 세부 터미널을 되묻지 말고 바로 "노선을 찾지 못했다"고 알려야 한다.
        TagoClient noRouteTagoClient = new TagoClient(null) {
            @Override public String findTerminalId(String terminalName) { return terminalName; }

            @Override public List<BusSchedule> searchBuses(String depId, String arrId, String date) {
                return List.of();
            }
        };
        ConversationParseService serviceWithNoRoute = new ConversationParseService(
                null, new ConversationRuleExtractor(), new BusSearchService(noRouteTagoClient));

        ConversationParseResponse r = serviceWithNoRoute.parse(
                new ConversationParseRequest("서울에서 전주로 가는 버스", "s1"), new ConversationSession("s1"));

        assertThat(r.routeNotFound()).isTrue();
        // "서울 어느 터미널로 원하시나요?" 되묻기가 아니라 노선 없음 안내가 나가야 한다.
        assertThat(r.clarificationPrompt()).doesNotContain("어느 터미널");
    }

    @Test
    void preserves_unrelated_fields_when_reporting_a_route_failure() {
        // 실제로 보고된 사고: "노선을 찾지 못했다" 응답을 만들 때 인원/좌석선호/접근성 등 이번
        // 실패와 무관한 필드를 전부 기본값(1명, 빈 리스트)으로 하드코딩해서 반환했다.
        // mergeConditions는 null만 "언급 없음=기존 값 유지"로 해석하고 빈 리스트/숫자는 실제
        // 값으로 덮어써 버려서, 노선 문제를 한 번이라도 겪으면 이미 답했던 인원수("2명")나
        // 배려사항("멀미가 심해요")이 조용히 초기화됐다 — 확인 플래그는 이미 true라 다시
        // 물어보지도 않은 채로.
        TagoClient noRouteTagoClient = new TagoClient(null) {
            @Override public String findTerminalId(String terminalName) { return terminalName; }

            @Override public List<BusSchedule> searchBuses(String depId, String arrId, String date) {
                return List.of();
            }
        };
        ConversationParseService serviceWithNoRoute = new ConversationParseService(
                null, new ConversationRuleExtractor(), new BusSearchService(noRouteTagoClient));

        ConversationSession session = new ConversationSession("s1");
        // 출발/도착은 아직 모르지만, 인원/좌석선호/접근성은 이미 확정된 상태를 흉내낸다.
        session.mergeConditions(null, null, null, null, "ANY", "ANY", "ANY",
                2, true, List.of("WINDOW"), true, List.of("MOTION_SICKNESS"), null);

        ConversationParseResponse r = serviceWithNoRoute.parse(
                new ConversationParseRequest("서울경부에서 포항으로 가는 버스", "s1"), session);

        assertThat(r.routeNotFound()).isTrue();
        assertThat(r.passengers()).isEqualTo(2);
        assertThat(r.seatPreferences()).contains("WINDOW");
        assertThat(r.accessibilityNeeds()).contains("MOTION_SICKNESS");
    }

    @Test
    void rechecks_the_route_once_an_ambiguous_city_is_narrowed_to_the_specific_terminal_with_no_service() {
        // 실제로 보고된 사고: "서울"이라고만 하면 그 도시 터미널 중 하나(예: 센트럴시티)라도 노선이
        // 있어 통과되고 "서울경부/센트럴시티/동서울 중 어디로?"를 물어봤는데, 정작 사용자가 고른
        // 구체적인 터미널(서울경부)에는 그 노선이 없었다. 날짜/인원/좌석까지 다 물어본 뒤 최종
        // 검색에서야 "노선 없음"이 드러나면 안 되고, 구체적인 터미널로 좁혀지는 바로 그 턴에
        // 다시 확인해서 즉시 알려줘야 한다.
        TagoClient onlyCentralCityHasRoute = new TagoClient(null) {
            @Override public String findTerminalId(String terminalName) { return terminalName; }

            @Override public List<BusSchedule> searchBuses(String depId, String arrId, String date) {
                if ("센트럴시티".equals(depId) && "전주고속".equals(arrId)) {
                    return List.of(new BusSchedule("R01", "우등", depId, arrId, date + "0800", date + "1200", 20_000));
                }
                return List.of();
            }
        };
        ConversationParseService serviceWithPartialRoute = new ConversationParseService(
                null, new ConversationRuleExtractor(), new BusSearchService(onlyCentralCityHasRoute));

        ConversationSession session = new ConversationSession("s1");

        // 1턴: "서울"(도시 단위, 아직 애매함)이 알려짐 -> 센트럴시티 경유로 노선이 존재하니 통과되고
        // 세부 터미널을 되묻는다.
        ConversationParseResponse turn1 = serviceWithPartialRoute.parse(
                new ConversationParseRequest("내일 오전 8시 서울에서 전주로 가는 버스", "s1"), session);
        assertThat(turn1.routeNotFound()).isFalse();
        assertThat(turn1.clarificationPrompt()).contains("어느 터미널");

        session.mergeConditions(turn1.departure(), turn1.arrival(), turn1.date(), turn1.departureTime(),
                turn1.timePreference(), turn1.servicePreference(), turn1.busGradePreference(),
                turn1.passengers(), turn1.seatPreferences(), turn1.accessibilityNeeds(), turn1.clarificationPrompt());

        // 2턴: 사용자가 구체적인 터미널("서울경부")을 답한다 — 그런데 이 터미널에는 노선이 없다.
        ConversationParseResponse turn2 = serviceWithPartialRoute.parse(
                new ConversationParseRequest("서울경부요", "s1"), session);

        assertThat(turn2.routeNotFound()).isTrue();
        // 실제로 보고된 사고: "서울경부"만 노선이 없는 건데 무관한 도착지(전주)까지 통째로 지워버리고
        // "다시 어디에서 어디로 가시는지"부터 새로 물어봤다. 도착지는 그대로 두고, 출발지는 도시
        // 단위("서울")로 되돌려 다른 세부 터미널(센트럴시티/동서울)을 다시 고를 수 있게 해야 한다.
        assertThat(turn2.departure()).isEqualTo("서울");
        assertThat(turn2.arrival()).isEqualTo("전주고속");
        assertThat(turn2.clarificationPrompt()).contains("서울경부").contains("전주고속").contains("서울의 다른 터미널");
        // 실제로 보고된 사고: "OO의 다른 터미널로 다시 말씀해 주세요"처럼 실제 터미널명을 나열하지
        // 않으면, 사용자도 뭐라고 답해야 할지 막막하고 다음 턴 LLM의 STT 오인식 교정도 참고할
        // 단서가 없어 엉뚱한 지명으로 틀리곤 했다. 후보 터미널명을 직접 나열해야 한다.
        assertThat(turn2.clarificationPrompt()).contains("센트럴시티").contains("동서울");
    }

    @Test
    void rechecks_the_route_when_a_correction_swaps_one_concrete_terminal_for_another() {
        // 실제로 보고된 사고: "동대구 말고 서대구"처럼 이미 확정된 구체적 터미널을 다른 구체적
        // 터미널로 바꾸는 정정은 "처음 알려짐"도 "애매한 도시→구체적 터미널로 좁혀짐"도 아니라서
        // (둘 다 이미 concrete였으므로) 재확인이 걸리지 않았다 — 노선이 없는 서대구로 바뀌었는데도
        // 아무 확인 없이 통과되어 버스 종류를 계속 물어봤다. 값이 실제로 바뀌면 재확인해야 한다.
        TagoClient onlyDongdaeguHasRoute = new TagoClient(null) {
            @Override public String findTerminalId(String terminalName) { return terminalName; }

            @Override public List<BusSchedule> searchBuses(String depId, String arrId, String date) {
                if ("동대구".equals(depId) && "부산종합".equals(arrId)) {
                    return List.of(new BusSchedule("R01", "우등", depId, arrId, date + "0700", date + "0815", 11_000));
                }
                return List.of();
            }
        };
        ConversationParseService serviceWithPartialRoute = new ConversationParseService(
                null, new ConversationRuleExtractor(), new BusSearchService(onlyDongdaeguHasRoute));

        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("동대구", "부산종합", "2026-08-30", "08:00", "MORNING", "ANY", "ANY",
                1, session.getSeatPreferences(), session.getAccessibilityNeeds(), null);

        ConversationParseResponse r = serviceWithPartialRoute.parse(
                new ConversationParseRequest("동대구 말고 서대구", "s1"), session);

        assertThat(r.routeNotFound()).isTrue();
        // 도착지(부산종합)는 이번 실패와 무관하니 그대로 두고, 출발지는 도시 단위("대구")로 되돌려
        // 다른 세부 터미널(동대구/대구용계)을 다시 고를 수 있게 해야 한다.
        assertThat(r.departure()).isEqualTo("대구");
        assertThat(r.arrival()).isEqualTo("부산종합");
    }

    @Test
    void rechecks_the_route_when_a_correction_switches_to_an_entirely_different_city() {
        // 같은 도시 안에서 세부 터미널만 바뀌는 경우뿐 아니라, "대전청사 말고 포항으로"처럼 아예
        // 다른 도시로 통째로 바뀌는 정정도 재확인이 걸려야 한다. "포항고속"은 터미널이 하나뿐인
        // 도시라, 되돌려도 같은 값이므로 도시가 아니라 완전히 비워서 새로 입력받아야 한다.
        TagoClient noRouteToPohang = new TagoClient(null) {
            @Override public String findTerminalId(String terminalName) { return terminalName; }

            @Override public List<BusSchedule> searchBuses(String depId, String arrId, String date) {
                return List.of(); // 포항고속-부산종합 사이엔 노선이 없다고 가정
            }
        };
        ConversationParseService serviceWithNoRoute = new ConversationParseService(
                null, new ConversationRuleExtractor(), new BusSearchService(noRouteToPohang));

        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("대전청사", "부산종합", "2026-08-30", "08:00", "MORNING", "ANY", "ANY",
                1, session.getSeatPreferences(), session.getAccessibilityNeeds(), null);

        ConversationParseResponse r = serviceWithNoRoute.parse(
                new ConversationParseRequest("대전청사 말고 포항으로", "s1"), session);

        assertThat(r.routeNotFound()).isTrue();
        // 포항은 터미널이 하나뿐이라 되돌려봐야 소용없으므로 비워서 새로 물어보고, 무관한 도착지는 유지한다.
        assertThat(r.departure()).isNull();
        assertThat(r.arrival()).isEqualTo("부산종합");
    }

    @Test
    void rejects_a_date_beyond_tagos_actual_publishing_window_with_a_specific_message() {
        // 실제로 보고된 사고: TAGO 실시간 조회 API는 오늘부터 3일치(오늘/내일/모레) 시간표만
        // 주고 그 이후 날짜는 노선이 매일 운행돼도 무조건 0건을 반환한다. "다음 주 화요일"처럼
        // 그 범위 밖 날짜를 물으면, 노선이 멀쩡히 있는데도 "노선을 찾지 못했다"고 잘못 안내됐다.
        // 날짜 자체를 범위 안으로 제한하고, 벗어나면 정직하게 아직 조회할 수 없다고 안내해야 한다.
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("서울경부", "포항고속", null, null, "ANY", "ANY", "ANY",
                1, session.getSeatPreferences(), session.getAccessibilityNeeds(), null);

        ConversationParseResponse r = service.parse(
                new ConversationParseRequest("다음 주 화요일에 갈게요", "s1"), session);

        assertThat(r.date()).isNull();
        assertThat(r.clarificationPrompt()).contains("아직 시간표를 조회할 수 없어요").contains("3일 이내");
        assertThat(r.routeNotFound()).isFalse();
    }

    @Test
    void does_not_recheck_an_already_established_date_against_the_real_time_clock_on_later_turns() {
        // 실제 시간이 흘러 세션에 이미 들어있는(예: 이전 턴에 정상 확정된) 날짜가 나중에 범위를
        // 벗어나게 되더라도, 이번 턴에 사용자가 그 날짜를 다시 말한 게 아니라면 아무 말 없이
        // 갑자기 거절되면 안 된다 — 검증은 "이번 턴에 새로 말한 날짜"에만 적용해야 한다.
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("서울경부", "포항고속", LocalDate.now().plusDays(30).toString(), null,
                "ANY", "ANY", "ANY", 1, session.getSeatPreferences(), session.getAccessibilityNeeds(), null);

        ConversationParseResponse r = service.parse(
                new ConversationParseRequest("한 명이요", "s1"), session);

        assertThat(r.date()).isEqualTo(LocalDate.now().plusDays(30).toString());
        assertThat(r.clarificationPrompt() == null || !r.clarificationPrompt().contains("아직 시간표를 조회할 수 없어요")).isTrue();
    }

    @Test
    void tells_the_user_an_unregistered_place_is_not_supported_instead_of_repeating_the_same_question() {
        // 실제로 보고된 사고: 등록 안 된 지명("완도")을 도착지로 말하면 추출기가 그 단어 자체를
        // 지명으로 인식하지 못해서 "어디로 가시나요?"만 계속 반복됐다. 이젠 그 지역을 아직
        // 지원하지 않는다고 정직하게 안내해야 한다.
        ConversationParseResponse r = service.parse(
                new ConversationParseRequest("서울경부에서 완도로 가는 버스", "s1"), new ConversationSession("s1"));

        assertThat(r.clarificationPrompt()).contains("완도").contains("지원하지 않는");
        assertThat(r.routeNotFound()).isFalse(); // 노선 없음이 아니라 아예 모르는 지명이라는 별개의 사유
    }

    @Test
    void does_not_recheck_the_route_on_every_subsequent_turn_once_confirmed() {
        // 노선이 있다고 이미 확인된 뒤에는(이전 턴에 출발/도착이 확정됨) 매 턴마다 TAGO를 다시
        // 조회하지 않아야 한다 — 그렇지 않으면 뒤이은 모든 턴이 TAGO 응답 속도에 발목을 잡힌다.
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("서울경부", "부산종합", "2026-08-28", null, "ANY", "ANY", "ANY",
                1, List.of(), List.of(), null);

        ConversationParseResponse r = service.parse(
                new ConversationParseRequest("한 명이요", "s1"), session);

        assertThat(r.routeNotFound()).isFalse();
    }

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
    void resolves_a_bare_hour_using_the_time_of_day_already_confirmed_in_the_session() {
        // 실제로 보고된 사고: "내일 오후"라고 이미 말해서 timePreference=AFTERNOON이 세션에 확정된
        // 상태에서, 되묻는 질문에 오전/오후 없이 "8시"라고만 답하면 매번 "오전인지 오후인지
        // 확인이 필요해요"라며 되물었다. 세션에 이미 확정된 시간대가 있으면 그걸로 판단해야 한다.
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("서울", "대전", "2026-08-28", null, "AFTERNOON", "ANY", "ANY",
                1, session.getSeatPreferences(), session.getAccessibilityNeeds(), null);

        ConversationParseResponse r = service.parse(new ConversationParseRequest("8시요", "s1"), session);

        assertThat(r.departureTime()).isEqualTo("20:00");
        assertThat(r.missingFields()).doesNotContain("departureTime");
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
        // 실제로 보고된 사고: 출발/도착 터미널이 이미 둘 다 확정된 뒤 "대전청사 말고 대전터미널로"처럼
        // 정정했는데, 시스템이 정정을 무시하고 옛 터미널(대전청사)을 그대로 쓴 채 인원수를 물었다.
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("대전청사", "서대구", "2026-08-28", null, "ANY", "ANY", "ANY",
                1, session.getSeatPreferences(), session.getAccessibilityNeeds(), null);

        ConversationParseResponse r = service.parse(
                new ConversationParseRequest("대전 청사 말고 대전 터미널로 부탁해", "s1"), session);

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

    // LLM이 STT 오인식("참가죽" -> "창가 쪽")을 correctedText로 교정해서 돌려주는 상황을 흉내낸다.
    // LLM 자신의 구조화 필드(seatPreferences)는 일부러 비워둬서, 실제로 반영되는 값이 LLM 자신의
    // 판단이 아니라 correctedText로 다시 돌린 룰베이스 추출 결과임을 검증할 수 있게 한다.
    private static class SeatCorrectingWatsonxClient extends WatsonxClient {
        SeatCorrectingWatsonxClient() { super(null); }
        @Override public boolean isConfigured() { return true; }
        @Override public String ask(String prompt) {
            return """
                {"intent":"BUS_SEARCH","departure":null,"arrival":null,"date":null,
                 "departureTime":null,"timePreference":"ANY","servicePreference":"ANY",
                 "busGradePreference":"ANY","passengers":1,"seatPreferences":[],"accessibilityNeeds":[],
                 "correctedText":"창가 쪽으로 주세요"}
                """;
        }
    }

    // 실제로 보고된 사고: "요"처럼 조각난 입력을 교정할 자신이 없자, LLM이 원문 대신 자기 설명
    // 문구를 correctedText에 담아 돌려줬다. 이걸 그대로 믿으면 사용자가 하지도 않은 말이 화면
    // 말풍선에 뜬다.
    private static class RefusingWatsonxClient extends WatsonxClient {
        RefusingWatsonxClient() { super(null); }
        @Override public boolean isConfigured() { return true; }
        @Override public String ask(String prompt) {
            return """
                {"intent":"BUS_SEARCH","departure":null,"arrival":null,"date":null,
                 "departureTime":null,"timePreference":"ANY","servicePreference":"ANY",
                 "busGradePreference":"ANY","passengers":1,"seatPreferences":[],"accessibilityNeeds":[],
                 "correctedText":"사용자 발화를 이해하지 못함"}
                """;
        }
    }

    @Test
    void ignores_a_self_explanatory_correction_that_is_not_an_actual_correction() {
        ConversationParseService serviceWithRefusal = new ConversationParseService(
                new RefusingWatsonxClient(), new ConversationRuleExtractor(), alwaysHasRouteService());

        ConversationParseResponse r = serviceWithRefusal.parse(
                new ConversationParseRequest("요", "s1"), new ConversationSession("s1"));

        assertThat(r.correctedText()).isNull();
    }

    @Test
    void re_extracts_with_rules_using_the_llm_corrected_text_when_stt_mangles_a_keyword() {
        // 실제로 보고된 사고: STT가 "창가 쪽"을 "참가죽"처럼 잘못 받아써서, 룰베이스가 원문 그대로는
        // 좌석 선호를 알아듣지 못했다. LLM이 직전 질문 등 문맥으로 교정한 correctedText로 룰베이스를
        // 다시 돌려서, LLM 자신의 구조화 필드가 비어 있어도(신뢰 우선순위가 낮으므로) 정규식의
        // 결정성으로 정확한 값을 얻어야 한다.
        ConversationParseService serviceWithCorrection = new ConversationParseService(
                new SeatCorrectingWatsonxClient(), new ConversationRuleExtractor(), alwaysHasRouteService());
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("서울경부", "대전복합", "2026-08-27", "09:00", "MORNING", "ANY", "ANY",
                1, true, List.of(), false, List.of(), null);

        ConversationParseResponse r = serviceWithCorrection.parse(
                new ConversationParseRequest("참가죽으로 주세요", "s1"), session);

        assertThat(r.seatPreferences()).contains("WINDOW");
        // 실제로 보고된 사고: 교정이 실제로 반영됐는데도 응답의 correctedText가 항상 null로
        // 하드코딩돼 있어서, 프론트가 사용자 말풍선을 교정된 텍스트로 갱신할 방법이 없었다.
        assertThat(r.correctedText()).isEqualTo("창가 쪽으로 주세요");
    }

    // "서울경부"를 "서울 경구"/"서울 경국"처럼 마지막 음절을 잘못 알아듣는 STT 오인식 상황을
    // 흉내낸다. LLM 자신의 departure 필드는 일부러 비워둬서, 실제로 반영되는 값이 LLM의 raw
    // 구조화 필드 추측이 아니라 correctedText 기반 룰베이스 재추출 결과임을 검증한다.
    private static class TerminalCorrectingWatsonxClient extends WatsonxClient {
        TerminalCorrectingWatsonxClient() { super(null); }
        @Override public boolean isConfigured() { return true; }
        @Override public String ask(String prompt) {
            return """
                {"intent":"BUS_SEARCH","departure":null,"arrival":null,"date":null,
                 "departureTime":null,"timePreference":"ANY","servicePreference":"ANY",
                 "busGradePreference":"ANY","passengers":1,"seatPreferences":[],"accessibilityNeeds":[],
                 "correctedText":"서울경부에서 대전 가는 버스"}
                """;
        }
    }

    @Test
    void still_recovers_a_mangled_terminal_name_via_llm_corrected_text_after_removing_raw_llm_guesses() {
        // 출발/도착지에서 LLM의 raw 구조화 필드 추측을 제거했다고 해서, "서울 경구"/"서울 경국"처럼
        // 끝 음절이 잘못 들린 터미널명을 correctedText로 통째 교정해 룰베이스가 다시 정확히
        // 잡아내는 기존 STT 오인식 교정 경로까지 막히면 안 된다 — 이 둘은 서로 다른 메커니즘이다.
        ConversationParseService serviceWithCorrection = new ConversationParseService(
                new TerminalCorrectingWatsonxClient(), new ConversationRuleExtractor(), alwaysHasRouteService());

        ConversationParseResponse r = serviceWithCorrection.parse(
                new ConversationParseRequest("서울 경구에서 대전 가는 버스", "s1"), new ConversationSession("s1"));

        assertThat(r.departure()).isEqualTo("서울경부");
        assertThat(r.arrival()).isEqualTo("대전");
        assertThat(r.correctedText()).isEqualTo("서울경부에서 대전 가는 버스");
    }

    // "부산"을 "두산"으로 잘못 알아듣는 STT 오인식(이전에 실제로 보고된 사고)을 correctedText로
    // 교정하는 경우. "서울경부" 같은 구체적 터미널명뿐 아니라 "부산"처럼 세부 터미널이 여럿인
    // 도시명 자체도 같은 방식(룰베이스 재추출)으로 교정되는지 확인한다.
    private static class CityNameCorrectingWatsonxClient extends WatsonxClient {
        CityNameCorrectingWatsonxClient() { super(null); }
        @Override public boolean isConfigured() { return true; }
        @Override public String ask(String prompt) {
            return """
                {"intent":"BUS_SEARCH","departure":null,"arrival":null,"date":null,
                 "departureTime":null,"timePreference":"ANY","servicePreference":"ANY",
                 "busGradePreference":"ANY","passengers":1,"seatPreferences":[],"accessibilityNeeds":[],
                 "correctedText":"부산에서 대전 가는 버스"}
                """;
        }
    }

    @Test
    void also_recovers_a_mangled_city_name_not_just_a_specific_terminal_via_llm_corrected_text() {
        // 구체적 터미널명("서울경부")뿐 아니라 세부 터미널이 여럿인 도시명 자체("부산")도
        // correctedText -> 룰베이스 재추출 경로로 똑같이 교정되는지 확인한다.
        ConversationParseService serviceWithCorrection = new ConversationParseService(
                new CityNameCorrectingWatsonxClient(), new ConversationRuleExtractor(), alwaysHasRouteService());

        ConversationParseResponse r = serviceWithCorrection.parse(
                new ConversationParseRequest("두산에서 대전 가는 버스", "s1"), new ConversationSession("s1"));

        assertThat(r.departure()).isEqualTo("부산");
        assertThat(r.arrival()).isEqualTo("대전");
        assertThat(r.correctedText()).isEqualTo("부산에서 대전 가는 버스");
    }

    @Test
    void leaves_corrected_text_null_when_the_llm_does_not_change_anything() {
        // 교정이 없었다면(원문 그대로) 프론트가 말풍선을 괜히 다시 그리지 않도록 null이어야 한다.
        ConversationParseResponse r = service.parse(
                new ConversationParseRequest("서울경부에서 포항으로 가는 버스", "s1"), new ConversationSession("s1"));

        assertThat(r.correctedText()).isNull();
    }

    // "두 장"이 "두잠"으로 STT 오인식된 상황. LLM 자신의 passengers 필드는 일부러 1(틀린 값)로
    // 둬서, 실제 반영되는 2가 correctedText 기반 룰베이스 재추출 결과임을 검증한다.
    private static class PassengerCorrectingWatsonxClient extends WatsonxClient {
        PassengerCorrectingWatsonxClient() { super(null); }
        @Override public boolean isConfigured() { return true; }
        @Override public String ask(String prompt) {
            return """
                {"intent":"BUS_SEARCH","departure":null,"arrival":null,"date":null,
                 "departureTime":null,"timePreference":"ANY","servicePreference":"ANY",
                 "busGradePreference":"ANY","passengers":1,"seatPreferences":[],"accessibilityNeeds":[],
                 "correctedText":"두 장이요"}
                """;
        }
    }

    @Test
    void re_extracts_passenger_count_from_llm_corrected_text_when_stt_mangles_the_number() {
        // 실제로 보고된 사고: "두 장"이 STT로 "두잠"/"부장"/"주점" 등으로 잘못 받아써져서, 룰베이스가
        // 원문 그대로는 인원수를 못 알아들었다. 직전 질문(인원 확인)을 참고해 LLM이 교정한
        // correctedText로 룰베이스를 다시 돌려서 정확한 인원수를 얻어야 한다.
        ConversationParseService serviceWithCorrection = new ConversationParseService(
                new PassengerCorrectingWatsonxClient(), new ConversationRuleExtractor(), alwaysHasRouteService());
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("서울경부", "대전복합", "2026-08-27", "09:00", "MORNING", "ANY", "ANY",
                1, false, List.of(), false, List.of(),
                "표를 찾을게요. 탑승하시는 인원은 총 몇 분이신가요? (혼자이시면 '한 명'이라고 말씀해 주세요.)");

        ConversationParseResponse r = serviceWithCorrection.parse(
                new ConversationParseRequest("두잠이요", "s1"), session);

        assertThat(r.passengers()).isEqualTo(2);
    }

    @Test
    void session_survives_a_misbehaving_llm_that_forgets_unmentioned_fields() {
        // 실제로 보고된 사고: 출발지 "서울경부"(구체 터미널)가 "서울"(도시명)로, 인원 2명이 1명으로,
        // 접근성 배려 ELDERLY_CARE가 통째로 사라지는 일이 있었다 — 사용자는 시간대만 말했을 뿐인데도.
        // LLM이 "기존 값 유지" 지시를 못 지켜도, 룰베이스가 못 찾은 필드는 LLM보다 세션을 먼저
        // 신뢰해야 이미 확정된 조건이 조용히 초기화되지 않는다.
        ConversationParseService serviceWithLlm = new ConversationParseService(new MisbehavingWatsonxClient(), new ConversationRuleExtractor(), alwaysHasRouteService());
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
        // 세부 터미널까지 이미 확정된(더 이상 애매하지 않은) 출발/도착을 써서, 이 테스트의 관심사인
        // "모호한 시간대" 로직이 터미널 되묻기 우선순위 변경(이제 시간보다 먼저 확인됨)의 영향을
        // 받지 않게 한다.
        ConversationSession session = new ConversationSession("s1");
        session.mergeConditions("서울경부", "포항고속", "2026-08-25", null, "MORNING", "ANY", "ANY",
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
                "대전 어느 터미널로 원하시나요? 대전복합, 대전청사 중 편하신 곳을 말씀해 주세요.");

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

    // 실제로 보고된 사고 재현: STT가 "부산"을 "두산"으로 잘못 알아들었는데, 원문 자체가 어떤 지명
    // 패턴에도 걸리지 않아 룰베이스는 아무 값도 못 뽑았다. 이때 LLM이 최후 수단으로 지명을 지어내
    // 채워 넣으면(설령 "서울"처럼 실존하는 지명이어도, 사용자가 그 슬롯을 언급한 적이 없다면), 그
    // 값을 확정된 조건으로 취급하면 안 된다 — 출발/도착지는 룰베이스/세션에 없으면 LLM에 기대지
    // 않고 정직하게 되묻는다.
    private static class RegionGuessingWatsonxClient extends WatsonxClient {
        RegionGuessingWatsonxClient() { super(null); }
        @Override public boolean isConfigured() { return true; }
        @Override public String ask(String prompt) {
            return """
                {"intent":"BUS_SEARCH","departure":"서울","arrival":"두산","date":null,
                 "departureTime":null,"timePreference":"ANY","servicePreference":"ANY",
                 "busGradePreference":"ANY","passengers":1,"seatPreferences":[],"accessibilityNeeds":[]}
                """;
        }
    }

    @Test
    void ignores_llm_guessed_departure_and_arrival_entirely_when_the_user_mentioned_neither() {
        ConversationParseService serviceWithGuess = new ConversationParseService(
                new RegionGuessingWatsonxClient(), new ConversationRuleExtractor(), alwaysHasRouteService());

        ConversationParseResponse r = serviceWithGuess.parse(
                new ConversationParseRequest("그냥 아무데나 예약해주세요", "s1"), new ConversationSession("s1"));

        // LLM의 추측은 미등록 지명("두산")이든 실존하는 지명("서울")이든 둘 다 무시되고, 정직하게
        // 다시 물어봐야 한다 — 사용자가 이 발화에서 둘 중 아무것도 언급하지 않았기 때문이다.
        assertThat(r.arrival()).isNull();
        assertThat(r.departure()).isNull();
        assertThat(r.routeNotFound()).isFalse();
        assertThat(r.missingFields()).contains("departure").contains("arrival");
    }

    // 실제로 보고된 사고 재현: 사용자가 "서울로 가고 싶어"라고 도착지만 말했는데("싶어"는 어미일
    // 뿐 지명이 아님), LLM이 "싶어"를 지명으로 착각해 출발지를 "싫어"로 추측해 돌려줬다. 룰베이스는
    // "서울"을 정확히 도착지로 잡아내지만, LLM의 근거 없는 출발지 추측은 신뢰하면 안 된다.
    private static class PhantomDepartureWatsonxClient extends WatsonxClient {
        PhantomDepartureWatsonxClient() { super(null); }
        @Override public boolean isConfigured() { return true; }
        @Override public String ask(String prompt) {
            return """
                {"intent":"BUS_SEARCH","departure":"싫어","arrival":"싫어","date":null,
                 "departureTime":null,"timePreference":"ANY","servicePreference":"ANY",
                 "busGradePreference":"ANY","passengers":1,"seatPreferences":[],"accessibilityNeeds":[]}
                """;
        }
    }

    @Test
    void does_not_let_the_llm_invent_a_departure_from_a_verb_ending_mistaken_for_a_place_name() {
        ConversationParseService serviceWithPhantomGuess = new ConversationParseService(
                new PhantomDepartureWatsonxClient(), new ConversationRuleExtractor(), alwaysHasRouteService());

        ConversationParseResponse r = serviceWithPhantomGuess.parse(
                new ConversationParseRequest("서울로 가고 싶어", "s1"), new ConversationSession("s1"));

        // 룰베이스가 정확히 잡아낸 도착지("서울")는 그대로 유지되고, LLM이 지어낸 "싫어"는
        // 출발지로도 반영되지 않아야 한다.
        assertThat(r.arrival()).isEqualTo("서울");
        assertThat(r.departure()).isNull();
        assertThat(r.missingFields()).contains("departure");
        assertThat(r.clarificationPrompt()).doesNotContain("싫어");
    }

    // LLM이 accessibilityNeeds에 프롬프트가 정의하지 않은 값을 지어내는 상황을 흉내낸다.
    private static class AccessibilityGuessingWatsonxClient extends WatsonxClient {
        AccessibilityGuessingWatsonxClient() { super(null); }
        @Override public boolean isConfigured() { return true; }
        @Override public String ask(String prompt) {
            return """
                {"intent":"BUS_SEARCH","departure":null,"arrival":null,"date":null,
                 "departureTime":null,"timePreference":"ANY","servicePreference":"ANY",
                 "busGradePreference":"ANY","passengers":1,"seatPreferences":[],
                 "accessibilityNeeds":["GHOST_NEED","MOTION_SICKNESS"]}
                """;
        }
    }

    @Test
    void filters_out_an_unknown_accessibility_need_value_invented_by_the_llm() {
        ConversationParseService serviceWithGuess = new ConversationParseService(
                new AccessibilityGuessingWatsonxClient(), new ConversationRuleExtractor(), alwaysHasRouteService());

        ConversationParseResponse r = serviceWithGuess.parse(
                new ConversationParseRequest("그냥 아무데나 예약해주세요", "s1"), new ConversationSession("s1"));

        assertThat(r.accessibilityNeeds()).contains("MOTION_SICKNESS");
        assertThat(r.accessibilityNeeds()).doesNotContain("GHOST_NEED");
    }

    // 사용자가 도착지만 말했는데 LLM이 (룰베이스/세션 둘 다 모르는) 출발지를 방금 채운 도착지와
    // 똑같이 추측해 돌려주는 상황을 흉내낸다.
    private static class DuplicatingDestinationWatsonxClient extends WatsonxClient {
        DuplicatingDestinationWatsonxClient() { super(null); }
        @Override public boolean isConfigured() { return true; }
        @Override public String ask(String prompt) {
            return """
                {"intent":"BUS_SEARCH","departure":"부산","arrival":"부산","date":null,
                 "departureTime":null,"timePreference":"ANY","servicePreference":"ANY",
                 "busGradePreference":"ANY","passengers":1,"seatPreferences":[],"accessibilityNeeds":[]}
                """;
        }
    }

    @Test
    void does_not_let_the_llm_invent_a_departure_that_duplicates_the_just_stated_arrival() {
        // 실제로 보고된 사고: 사용자가 도착지("부산")만 말했는데, 룰베이스와 세션 둘 다 모르는
        // 출발지를 LLM이 방금 채운 도착지와 똑같이 추측해서 "부산에서 부산으로"라는 말이 안 되는
        // 노선이 세션에 고착돼, 매번 "노선이 없다"만 반복되고 정작 출발지를 물어보지 않았다.
        ConversationParseService serviceWithGuess = new ConversationParseService(
                new DuplicatingDestinationWatsonxClient(), new ConversationRuleExtractor(), alwaysHasRouteService());

        ConversationParseResponse r = serviceWithGuess.parse(
                new ConversationParseRequest("내일 부산으로 가고 싶어", "s1"), new ConversationSession("s1"));

        assertThat(r.arrival()).isEqualTo("부산");
        assertThat(r.departure()).isNull();
        assertThat(r.routeNotFound()).isFalse();
        assertThat(r.missingFields()).contains("departure");
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
