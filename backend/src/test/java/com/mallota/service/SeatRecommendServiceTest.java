package com.mallota.service;

import com.mallota.dto.request.SeatRecommendRequest;
import com.mallota.recommendation.MockSeatGenerator;
import com.mallota.recommendation.Seat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SeatRecommendServiceTest {

    /** 좌석 배치도와 동일한 구조: [A][B] | [C] (A-B만 연석) */
    private static Seat seat(String no, int row, int col, String position, boolean available) {
        String side = (col == 1 || col == 3) ? "WINDOW" : "AISLE";
        return new Seat(no, row, col, position, side, available);
    }

    private static SeatRecommendService serviceWith(List<Seat> seats) {
        return new SeatRecommendService(new MockSeatGenerator() {
            @Override
            public List<Seat> generate(String grade) {
                return seats;
            }
        });
    }

    /** 9줄 우등 배치 생성 (모두 빈자리) 후 지정한 좌석만 예약 처리 */
    private static List<Seat> excellentLayout(String... reservedSeatNos) {
        List<String> reserved = List.of(reservedSeatNos);
        List<Seat> seats = new ArrayList<>();
        for (int row = 1; row <= 9; row++) {
            String position = row <= 3 ? "FRONT" : (row <= 6 ? "MIDDLE" : "BACK");
            for (int col = 1; col <= 3; col++) {
                String no = row + String.valueOf((char) ('A' + col - 1));
                seats.add(seat(no, row, col, position, !reserved.contains(no)));
            }
        }
        return seats;
    }

    @Test
    @DisplayName("1. 뒷좌석(BACK) 선호 시 뒤쪽 8B 좌석 추천 및 사유 검증")
    void honors_explicit_back_seat_preference() {
        SeatRecommendService service = serviceWith(List.of(
                seat("1A", 1, 1, "FRONT", true),
                seat("8B", 8, 2, "BACK", true)
        ));

        var result = service.recommend(new SeatRecommendRequest("우등", List.of("BACK"), List.of(), 1));

        assertThat(result.bestSeat().seatNo()).isEqualTo("8B");
        assertThat(result.reasons()).contains("뒷좌석을 선호하셔서 뒤쪽 좌석입니다.");
    }

    @Test
    @DisplayName("2. 보행 불편(WALKING_DIFFICULTY) 시 승하차가 편한 앞쪽 1A 좌석 우선 추천")
    void prioritizes_front_seat_for_walking_difficulty() {
        SeatRecommendService service = serviceWith(List.of(
                new Seat("1A", 1, 1, "FRONT", "AISLE", true),
                new Seat("4B", 4, 2, "MIDDLE", "WINDOW", true),
                new Seat("8B", 8, 2, "BACK", "WINDOW", true)
        ));

        var result = service.recommend(new SeatRecommendRequest("우등", List.of(), List.of("WALKING_DIFFICULTY"), 1));

        assertThat(result.bestSeat().seatNo()).isEqualTo("1A");
        assertThat(result.reasons()).contains("다리가 불편하셔서 승하차 편한 앞쪽 좌석입니다.");
    }

    @Test
    @DisplayName("임산부(PREGNANCY)는 앞쪽 자리보다 화장실 접근이 쉬운 통로 쪽 좌석을 우선 추천")
    void prioritizes_aisle_seat_over_front_seat_for_pregnancy() {
        SeatRecommendService service = serviceWith(List.of(
                new Seat("1A", 1, 1, "FRONT", "WINDOW", true),
                new Seat("3B", 3, 2, "MIDDLE", "AISLE", true)
        ));

        var result = service.recommend(new SeatRecommendRequest("우등", List.of(), List.of("PREGNANCY"), 1));

        assertThat(result.bestSeat().seatNo()).isEqualTo("3B");
        assertThat(result.reasons()).contains("화장실 이용이 편한 통로 쪽 좌석입니다.");
        assertThat(result.reasons()).doesNotContain("임산부분이시라 승하차 편한 앞쪽 좌석입니다.");
    }

    @Test
    @DisplayName("프리미엄 등급 + 임산부(PREGNANCY)는 1열 창가석(C좌석)을 다른 앞자리보다 우선 추천")
    void prioritizes_row_one_window_c_seat_for_pregnancy_on_premium_grade() {
        SeatRecommendService service = serviceWith(List.of(
                new Seat("1A", 1, 1, "FRONT", "WINDOW", true),
                new Seat("1B", 1, 2, "FRONT", "AISLE", true),
                new Seat("1C", 1, 4, "FRONT", "WINDOW", true),
                new Seat("8C", 8, 4, "BACK", "WINDOW", true)
        ));

        var result = service.recommend(new SeatRecommendRequest("프리미엄", List.of(), List.of("PREGNANCY"), 1));

        assertThat(result.bestSeat().seatNo()).isEqualTo("1C");
        assertThat(result.reasons()).contains("임산부분이시라 프리미엄 1열 창가 좌석으로 편안하게 준비했습니다.");
    }

    @Test
    @DisplayName("시각장애(VISUAL_IMPAIRMENT) 시 승무원 도움받기 편한 통로 쪽 앞자리 우선 추천")
    void prioritizes_front_aisle_seat_for_visual_impairment() {
        SeatRecommendService service = serviceWith(List.of(
                new Seat("1A", 1, 1, "FRONT", "AISLE", true),
                new Seat("8B", 8, 2, "BACK", "WINDOW", true)
        ));

        var result = service.recommend(new SeatRecommendRequest("우등", List.of(), List.of("VISUAL_IMPAIRMENT"), 1));

        assertThat(result.bestSeat().seatNo()).isEqualTo("1A");
        assertThat(result.reasons()).contains("승무원 도움을 받기 편한 앞쪽 좌석입니다.", "이동이 편한 통로 쪽 좌석입니다.");
    }

    @Test
    @DisplayName("명시한 뒷좌석 선호는 보행 배려의 자동 앞좌석 추천보다 우선한다")
    void explicit_back_preference_overrides_automatic_front_accessibility_boost() {
        SeatRecommendService service = serviceWith(List.of(
                new Seat("1A", 1, 1, "FRONT", "AISLE", true),
                new Seat("8B", 8, 2, "BACK", "WINDOW", true)
        ));

        var result = service.recommend(new SeatRecommendRequest("우등", List.of("BACK"), List.of("WALKING_DIFFICULTY"), 1));

        assertThat(result.bestSeat().seatNo()).isEqualTo("8B");
    }

    @Test
    @DisplayName("3. 멀미(MOTION_SICKNESS) 시 앞쪽보다 창가를 더 우선해 4B 좌석 추천")
    void prioritizes_window_seat_for_motion_sickness() {
        // 1A(앞쪽/통로)와 4B(중간/창가) 중, "창가에 더 가중치"라는 요청에 따라 앞쪽이 아니어도
        // 창가인 4B가 더 높은 점수를 받아야 한다.
        SeatRecommendService service = serviceWith(List.of(
                new Seat("1A", 1, 1, "FRONT", "AISLE", true),
                new Seat("4B", 4, 2, "MIDDLE", "WINDOW", true),
                new Seat("8B", 8, 2, "BACK", "WINDOW", true)
        ));

        var result = service.recommend(new SeatRecommendRequest("우등", List.of(), List.of("MOTION_SICKNESS"), 1));

        assertThat(result.bestSeat().seatNo()).isEqualTo("4B");
        assertThat(result.reasons()).contains("멀미가 덜하도록 시야를 고정할 수 있는 창가 좌석입니다.");
    }

    @Test
    @DisplayName("4. 2명(passengers=2) 예매 시 통로 건너가 아닌 실제 연석 1A, 1B 배정")
    void recommends_adjacent_pair_for_two_passengers() {
        SeatRecommendService service = serviceWith(List.of(
                seat("1A", 1, 1, "FRONT", true),
                seat("1B", 1, 2, "FRONT", true),
                seat("1C", 1, 3, "FRONT", true),
                seat("4B", 4, 2, "MIDDLE", true)
        ));

        var result = service.recommend(new SeatRecommendRequest("우등", List.of("FRONT"), List.of(), 2));

        assertThat(result.bestSeat().seatNo()).isEqualTo("1A");
        assertThat(result.adjacentPair()).isTrue();
        assertThat(result.alternatives()).extracting(Seat::seatNo).containsExactly("1B");
    }

    @Test
    @DisplayName("5. 통로를 사이에 둔 B-C는 연석이 아니라서 나란히/앞뒤 배치가 안 되면 따로라도 두 분 다 배정")
    void does_not_treat_seats_across_the_aisle_as_a_true_pair_but_still_seats_both() {
        SeatRecommendService service = serviceWith(List.of(
                seat("3B", 3, 2, "MIDDLE", true),
                seat("3C", 3, 3, "MIDDLE", true)
        ));

        var result = service.recommend(new SeatRecommendRequest("우등", List.of(), List.of(), 2));

        // 나란히도 앞뒤로도 붙은 자리가 없으니 연석이라고 속이지 않고, 대신 두 분 모두 자리는 받는다.
        assertThat(result.bestSeat()).isNotNull();
        assertThat(result.alternatives()).hasSize(1);
        assertThat(result.reasons()).anyMatch(r -> r.contains("따로"));
    }

    @Test
    @DisplayName("프리미엄 독립 1인석 열은 앞뒤 빈 좌석을 두 분 좌석 후보로 사용한다")
    void recommends_front_back_pair_for_a_premium_single_seat_column() {
        SeatRecommendService service = serviceWith(List.of(
                new Seat("1C", 1, 4, "FRONT", "WINDOW", true),
                new Seat("2C", 2, 4, "FRONT", "WINDOW", true)
        ));

        var result = service.recommend(new SeatRecommendRequest("프리미엄", List.of("FRONT"), List.of(), 2));

        assertThat(result.bestSeat().seatNo()).isEqualTo("1C");
        assertThat(result.alternatives()).extracting(Seat::seatNo).containsExactly("2C");
        assertThat(result.adjacentPair()).isTrue();
    }

    @Test
    @DisplayName("6. 뒤쪽에 연석이 없어도, 엉뚱한 다른 구역의 연석보다 뒤쪽 안에서 따로 배정하는 걸 우선한다")
    void prefers_separate_seats_within_requested_section_over_a_pair_in_another_section() {
        // 7~9줄(뒤쪽)은 나란히/앞뒤 연석은 없지만 따로 앉을 자리(7A,7C,8A,8C,9A,9C)는 남아있고,
        // 앞쪽 1줄과 중간 5줄에는 연석이 남아있는 상황. "뒷자리로 주세요"라고 했는데 앞쪽/중간에
        // 연석이 있다는 이유만으로 그쪽이 나와버리면 안 된다 — 뒤쪽 안에서 따로라도 배정해야 한다.
        SeatRecommendService service = serviceWith(excellentLayout(
                "2A", "3A", "4A", "6A", "7B", "8B", "9B"
        ));

        var result = service.recommend(new SeatRecommendRequest("우등", List.of("BACK"), List.of(), 2));

        // 뒤쪽 안에서 뒤쪽에 가장 가까운 9A, 9C를 따로 배정해야 한다
        assertThat(result.bestSeat().seatNo()).isEqualTo("9A");
        assertThat(result.alternatives()).extracting(Seat::seatNo).containsExactly("9C");
        assertThat(result.reasons()).anyMatch(r -> r.contains("뒤쪽") && r.contains("따로"));
    }

    @Test
    @DisplayName("6-1. 원하는 구역이 통째로 매진이면(따로도 불가) 가장 가까운 다른 구역의 연석으로 폴백")
    void falls_back_to_pair_in_another_section_when_preferred_section_is_completely_sold_out() {
        // 뒤쪽(7~9줄)이 통째로 매진이라 "뒤쪽 안에서 따로"조차 불가능한 상황
        SeatRecommendService service = serviceWith(excellentLayout(
                "7A", "7B", "7C", "8A", "8B", "8C", "9A", "9B", "9C"
        ));

        var result = service.recommend(new SeatRecommendRequest("우등", List.of("BACK"), List.of(), 2));

        // 뒤쪽에는 자리가 아예 없으므로, 남은 자리 중 뒤쪽에 가장 가까운 6줄 연석으로 폴백
        assertThat(result.bestSeat().seatNo()).isEqualTo("6A");
        assertThat(result.alternatives()).extracting(Seat::seatNo).containsExactly("6B");
        assertThat(result.reasons()).anyMatch(r -> r.contains("뒤쪽에는 나란히 앉으실 자리가 없어") && r.contains("6A, 6B"));
    }

    @Test
    @DisplayName("7. 뒤쪽 연석이 남아 있으면 뒤쪽 연석을 그대로 추천")
    void recommends_back_pair_when_available() {
        SeatRecommendService service = serviceWith(excellentLayout("1C", "2B", "6B", "7B", "8A", "8B", "9C"));

        var result = service.recommend(new SeatRecommendRequest("우등", List.of("BACK"), List.of(), 2));

        assertThat(result.bestSeat().seatNo()).isEqualTo("9A");
        assertThat(result.alternatives()).extracting(Seat::seatNo).containsExactly("9B");
    }

    @Test
    @DisplayName("8. 3명(passengers=3) 예매 시 같은 줄 연석(2) + 통로 건너 1석으로 배정")
    void recommends_row_triple_for_three_passengers() {
        SeatRecommendService service = serviceWith(List.of(
                seat("1A", 1, 1, "FRONT", true),
                seat("1B", 1, 2, "FRONT", true),
                seat("1C", 1, 3, "FRONT", true),
                seat("4B", 4, 2, "MIDDLE", true)
        ));

        var result = service.recommend(new SeatRecommendRequest("우등", List.of("FRONT"), List.of(), 3));

        assertThat(result.bestSeat().seatNo()).isEqualTo("1A");
        assertThat(result.adjacentPair()).isTrue();
        assertThat(result.alternatives()).extracting(Seat::seatNo).containsExactly("1B", "1C");
    }

    @Test
    @DisplayName("9. 4명(passengers=4) 예매 시 앞뒤 두 줄의 동일한 칸 연석으로 사각형(2x2) 배정")
    void recommends_rectangle_for_four_passengers() {
        SeatRecommendService service = serviceWith(List.of(
                seat("1A", 1, 1, "FRONT", true),
                seat("1B", 1, 2, "FRONT", true),
                seat("1C", 1, 3, "FRONT", true),
                seat("2A", 2, 1, "FRONT", true),
                seat("2B", 2, 2, "FRONT", true),
                seat("2C", 2, 3, "FRONT", true)
        ));

        var result = service.recommend(new SeatRecommendRequest("우등", List.of(), List.of(), 4));

        assertThat(result.bestSeat().seatNo()).isEqualTo("1A");
        assertThat(result.adjacentPair()).isTrue();
        assertThat(result.alternatives()).extracting(Seat::seatNo).containsExactly("1B", "2A", "2B");
    }

    @Test
    @DisplayName("10. 4명인데 사각형(2줄 연석)이 없으면 3인 배치, 그마저 없으면 연석으로 폴백")
    void falls_back_from_rectangle_to_triple_to_pair_when_four_passengers() {
        SeatRecommendService service = serviceWith(List.of(
                seat("1A", 1, 1, "FRONT", true),
                seat("1B", 1, 2, "FRONT", true),
                seat("1C", 1, 3, "FRONT", true),
                // 2줄은 매진이라 사각형을 만들 수 없음
                seat("2A", 2, 1, "FRONT", false),
                seat("2B", 2, 2, "FRONT", false),
                seat("2C", 2, 3, "FRONT", false)
        ));

        var result = service.recommend(new SeatRecommendRequest("우등", List.of(), List.of(), 4));

        // 사각형이 없으므로 같은 줄 3인 배치(1A,1B,1C)로 폴백
        assertThat(result.bestSeat().seatNo()).isEqualTo("1A");
        assertThat(result.alternatives()).extracting(Seat::seatNo).containsExactly("1B", "1C");
    }

    @Test
    @DisplayName("11. 명시적으로 뒷좌석을 요청하면, 함께 추론된 ELDERLY_CARE(앞쪽 선호)보다 우선한다")
    void explicit_back_preference_overrides_inferred_elderly_care_front_bias() {
        // "할머니 모시고" 같은 말에서 ELDERLY_CARE가 자동으로 추론되어도, 사용자가 명시적으로
        // "뒷좌석으로 주세요"라고 하면 그 명시적 요청이 우선해야 한다 (앞좌석이 나오면 안 됨).
        SeatRecommendService service = serviceWith(List.of(
                new Seat("1A", 1, 1, "FRONT", "AISLE", true),
                new Seat("8B", 8, 2, "BACK", "WINDOW", true)
        ));

        var result = service.recommend(new SeatRecommendRequest(
                "우등", List.of("BACK"), List.of("ELDERLY_CARE"), 1));

        assertThat(result.bestSeat().seatNo()).isEqualTo("8B");
        assertThat(result.reasons()).contains("뒷좌석을 선호하셔서 뒤쪽 좌석입니다.");
    }

    @Test
    @DisplayName("12. 가로 연석이 전혀 없는 등급(프리미엄 등)에서는 세로(앞뒤) 연석으로 2인 배정")
    void falls_back_to_vertical_pair_when_no_horizontal_pair_exists() {
        // 프리미엄처럼 통로 건너편이 매줄 홀로 좌석인 배치를 흉내낸다: 각 줄에 A(창가,통로쪽아님)만
        // 있고 옆자리가 없어 가로 연석이 원천적으로 불가능하다.
        SeatRecommendService service = serviceWith(List.of(
                seat("1A", 1, 1, "FRONT", true),
                seat("2A", 2, 1, "FRONT", true),
                seat("3A", 3, 1, "MIDDLE", true)
        ));

        var result = service.recommend(new SeatRecommendRequest("프리미엄", List.of(), List.of(), 2));

        assertThat(result.bestSeat().seatNo()).isEqualTo("1A");
        assertThat(result.adjacentPair()).isTrue();
        assertThat(result.alternatives()).extracting(Seat::seatNo).containsExactly("2A");
        assertThat(result.reasons()).anyMatch(r -> r.contains("앞뒤로 붙은 자리"));
    }

    @Test
    @DisplayName("13. 3명 예매에서 뒤쪽에 같은 줄 3인 배치가 없어도, 뒤쪽 안에서 따로라도 세 자리를 배정한다")
    void prefers_separate_seats_within_requested_section_for_three_passengers() {
        // 뒤쪽 세 줄 모두 통로 자리(B)만 매진 → 같은 줄 3인 배치는 불가능하지만 A/C는 남아있음.
        // 앞쪽에는 같은 줄 3인 배치가 그대로 남아있지만, "뒷자리" 요청이므로 뒤쪽을 지켜야 한다.
        SeatRecommendService service = serviceWith(excellentLayout("7B", "8B", "9B"));

        var result = service.recommend(new SeatRecommendRequest("우등", List.of("BACK"), List.of(), 3));

        assertThat(result.bestSeat()).isNotNull();
        assertThat(result.alternatives()).hasSize(2);
        assertThat(result.alternatives()).allMatch(s -> s.position().equals("BACK"));
        assertThat(result.bestSeat().position()).isEqualTo("BACK");
        assertThat(result.reasons()).anyMatch(r -> r.contains("뒤쪽") && r.contains("따로"));
    }

    @Test
    @DisplayName("14-1. 점수가 동률인 다른 연석은 '같은 조건 좌석'으로 함께 보여준다")
    void includes_tied_pairs_as_same_condition_alternatives() {
        // 5줄을 통째로 매진시켜서, 목표 줄(5줄)과 똑같이 한 줄 떨어진 4줄과 6줄 연석이 정확히 동점이
        // 되게 만든다 — 배정받은 연석 말고 동등하게 좋은 다른 연석이 어디 있는지 보여줘야 한다.
        // (멀미는 이제 앞쪽/창가를 우선하므로, 중간 줄을 목표로 하는 이 테스트는 명시적 MIDDLE
        // 선호로 검증한다.)
        SeatRecommendService service = serviceWith(excellentLayout("5A", "5B", "5C"));

        var result = service.recommend(new SeatRecommendRequest("우등", List.of("MIDDLE"), List.of(), 2));

        assertThat(result.bestSeat().seatNo()).isEqualTo("4A");
        assertThat(result.alternatives()).extracting(Seat::seatNo).containsExactly("4B");
        assertThat(result.tiedAlternativeSeats()).extracting(Seat::seatNo).containsExactlyInAnyOrder("6A", "6B");
    }

    @Test
    @DisplayName("14. 1인 예매에서 뒷자리가 매진이면, 좌석 생성 순서상 우연히 앞자리를 주지 않고 뒤쪽에 가장 가까운 좌석을 추천")
    void single_passenger_falls_back_to_closest_seat_when_requested_section_is_sold_out() {
        SeatRecommendService service = serviceWith(List.of(
                new Seat("1A", 1, 1, "FRONT", "AISLE", true),
                new Seat("5A", 5, 1, "MIDDLE", "WINDOW", true),
                new Seat("9A", 9, 1, "BACK", "WINDOW", false)
        ));

        var result = service.recommend(new SeatRecommendRequest("우등", List.of("BACK"), List.of(), 1));

        // 뒷좌석이 매진이라도, 목록상 처음 나오는 1A가 아니라 뒤쪽에 가장 가까운 5A가 나와야 한다.
        assertThat(result.bestSeat().seatNo()).isEqualTo("5A");
        assertThat(result.reasons()).anyMatch(r -> r.contains("가장 가까운"));
    }
}
