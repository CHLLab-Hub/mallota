package com.malrota.service;

import com.malrota.dto.request.SeatRecommendRequest;
import com.malrota.recommendation.MockSeatGenerator;
import com.malrota.recommendation.Seat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SeatRecommendServiceTest {

    @Test
    @DisplayName("1. 뒷좌석(BACK) 선호 시 뒤쪽 8B 좌석 추천 및 사유 검증")
    void honors_explicit_back_seat_preference() {
        MockSeatGenerator generator = new MockSeatGenerator() {
            @Override
            public List<Seat> generate(String grade) {
                return List.of(
                        new Seat("1A", 1, 1, "FRONT", "WINDOW", true),
                        new Seat("8B", 8, 2, "BACK", "AISLE", true)
                );
            }
        };
        SeatRecommendService service = new SeatRecommendService(generator);

        var result = service.recommend(new SeatRecommendRequest("우등", List.of("BACK"), List.of(), 1));

        assertThat(result.bestSeat().seatNo()).isEqualTo("8B");
        assertThat(result.reasons()).contains("뒷좌석을 선호하셔서 뒤쪽 좌석입니다.");
    }

    @Test
    @DisplayName("2. 보행 불편(WALKING_DIFFICULTY) 시 승하차가 편한 앞쪽 1A 좌석 우선 추천")
    void prioritizes_front_seat_for_walking_difficulty() {
        MockSeatGenerator generator = new MockSeatGenerator() {
            @Override
            public List<Seat> generate(String grade) {
                return List.of(
                        new Seat("1A", 1, 1, "FRONT", "AISLE", true),
                        new Seat("4B", 4, 2, "MIDDLE", "WINDOW", true),
                        new Seat("8B", 8, 2, "BACK", "WINDOW", true)
                );
            }
        };
        SeatRecommendService service = new SeatRecommendService(generator);

        var result = service.recommend(new SeatRecommendRequest("우등", List.of(), List.of("WALKING_DIFFICULTY"), 1));

        assertThat(result.bestSeat().seatNo()).isEqualTo("1A");
        assertThat(result.reasons()).contains("다리가 불편하셔서 타고 내리기 편한 앞쪽 좌석입니다.");
    }

    @Test
    @DisplayName("3. 멀미(MOTION_SICKNESS) 시 흔들림이 적은 중간 4B 좌석 추천")
    void prioritizes_middle_seat_for_motion_sickness() {
        MockSeatGenerator generator = new MockSeatGenerator() {
            @Override
            public List<Seat> generate(String grade) {
                return List.of(
                        new Seat("1A", 1, 1, "FRONT", "AISLE", true),
                        new Seat("4B", 4, 2, "MIDDLE", "WINDOW", true),
                        new Seat("8B", 8, 2, "BACK", "WINDOW", true)
                );
            }
        };
        SeatRecommendService service = new SeatRecommendService(generator);

        var result = service.recommend(new SeatRecommendRequest("우등", List.of(), List.of("MOTION_SICKNESS"), 1));

        assertThat(result.bestSeat().seatNo()).isEqualTo("4B");
        assertThat(result.reasons()).contains("멀미가 덜하도록 흔들림이 적은 중간 좌석입니다.");
    }

    @Test
    @DisplayName("4. 2명(passengers=2) 예매 시 나란히 앉는 앞쪽 1B, 1C 연석 배정")
    void recommends_adjacent_pair_for_two_passengers() {
        MockSeatGenerator generator = new MockSeatGenerator() {
            @Override
            public List<Seat> generate(String grade) {
                return List.of(
                        new Seat("1A", 1, 1, "FRONT", "SINGLE", true),
                        new Seat("1B", 1, 2, "FRONT", "AISLE", true),
                        new Seat("1C", 1, 3, "FRONT", "WINDOW", true),
                        new Seat("4B", 4, 2, "MIDDLE", "AISLE", true)
                );
            }
        };
        SeatRecommendService service = new SeatRecommendService(generator);

        var result = service.recommend(new SeatRecommendRequest("우등", List.of("FRONT"), List.of(), 2));

        assertThat(result.bestSeat().seatNo()).isEqualTo("1B");
        assertThat(result.alternatives()).extracting(Seat::seatNo).contains("1C");
        assertThat(result.reasons()).anyMatch(r -> r.contains("1B, 1C"));
    }

    @Test
    @DisplayName("5. 앞쪽 연석이 매진되었을 때 차선책 중간 연석(3B, 3C) 추천 및 설명 생성")
    void falls_back_to_middle_pair_with_explanation() {
        MockSeatGenerator generator = new MockSeatGenerator() {
            @Override
            public List<Seat> generate(String grade) {
                return List.of(
                        // 1열은 1B만 비어있고 1C는 매진(false) 상태
                        new Seat("1B", 1, 2, "FRONT", "AISLE", true),
                        new Seat("1C", 1, 3, "FRONT", "WINDOW", false),
                        // 3열 중간 연석은 둘 다 비어있음
                        new Seat("3B", 3, 2, "MIDDLE", "AISLE", true),
                        new Seat("3C", 3, 3, "MIDDLE", "WINDOW", true)
                );
            }
        };
        SeatRecommendService service = new SeatRecommendService(generator);

        // 사용자는 앞쪽(FRONT)을 원했으나 앞쪽 연석이 없는 상황
        var result = service.recommend(new SeatRecommendRequest("우등", List.of("FRONT"), List.of(), 2));

        assertThat(result.bestSeat().seatNo()).isEqualTo("3B");
        assertThat(result.alternatives()).extracting(Seat::seatNo).contains("3C");
        // 앞쪽 자리가 없어 중간 연석으로 준비했다는 설명 포함 확인
        assertThat(result.reasons()).anyMatch(r -> r.contains("앞쪽에는 나란히 앉으실 자리가 없어") && r.contains("중간"));
    }
}