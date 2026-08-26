package com.malrota.service;

import com.malrota.dto.request.SeatRecommendRequest;
import com.malrota.recommendation.MockSeatGenerator;
import com.malrota.recommendation.Seat;
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
        assertThat(result.reasons()).contains("다리가 불편하셔서 타고 내리기 편한 앞쪽 좌석입니다.");
    }

    @Test
    @DisplayName("3. 멀미(MOTION_SICKNESS) 시 흔들림이 적은 중간 4B 좌석 추천")
    void prioritizes_middle_seat_for_motion_sickness() {
        SeatRecommendService service = serviceWith(List.of(
                new Seat("1A", 1, 1, "FRONT", "AISLE", true),
                new Seat("4B", 4, 2, "MIDDLE", "WINDOW", true),
                new Seat("8B", 8, 2, "BACK", "WINDOW", true)
        ));

        var result = service.recommend(new SeatRecommendRequest("우등", List.of(), List.of("MOTION_SICKNESS"), 1));

        assertThat(result.bestSeat().seatNo()).isEqualTo("4B");
        assertThat(result.reasons()).contains("멀미가 덜하도록 흔들림이 적은 중간 좌석입니다.");
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
    @DisplayName("5. 통로를 사이에 둔 B-C는 연석이 아니므로 연석 배정 대신 개별 좌석으로 폴백")
    void does_not_treat_seats_across_the_aisle_as_a_pair() {
        SeatRecommendService service = serviceWith(List.of(
                seat("3B", 3, 2, "MIDDLE", true),
                seat("3C", 3, 3, "MIDDLE", true)
        ));

        var result = service.recommend(new SeatRecommendRequest("우등", List.of(), List.of(), 2));

        assertThat(result.adjacentPair()).isFalse();
    }

    @Test
    @DisplayName("6. 뒤쪽 연석이 매진되면 앞쪽이 아니라 뒤쪽에서 가장 가까운 연석을 추천")
    void falls_back_to_the_pair_closest_to_the_requested_section() {
        // 7~9줄(뒤쪽) 연석은 모두 매진, 앞쪽 1줄과 중간 5줄 연석만 남은 상황
        SeatRecommendService service = serviceWith(excellentLayout(
                "2A", "3A", "4A", "6A", "7B", "8B", "9B"
        ));

        var result = service.recommend(new SeatRecommendRequest("우등", List.of("BACK"), List.of(), 2));

        // 남은 연석은 1줄과 5줄뿐 → 뒤쪽에서 더 가까운 5A, 5B 를 골라야 한다
        assertThat(result.bestSeat().seatNo()).isEqualTo("5A");
        assertThat(result.alternatives()).extracting(Seat::seatNo).containsExactly("5B");
        assertThat(result.reasons()).anyMatch(r -> r.contains("뒤쪽에는 나란히 앉으실 자리가 없어") && r.contains("5A, 5B"));
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
}
