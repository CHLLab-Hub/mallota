package com.malrota.service;

import com.malrota.dto.request.SeatRecommendRequest;
import com.malrota.recommendation.MockSeatGenerator;
import com.malrota.recommendation.Seat;
import com.malrota.recommendation.SeatRecommendation;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SeatRecommendService {

    private final MockSeatGenerator seatGenerator;

    public SeatRecommendService(MockSeatGenerator seatGenerator) {
        this.seatGenerator = seatGenerator;
    }

    public SeatRecommendation recommend(SeatRecommendRequest request) {
        List<Seat> seats = seatGenerator.generate(request.busGrade());

        List<String> access = request.accessibilityNeeds() != null
                ? request.accessibilityNeeds() : List.of();
        List<String> prefs = request.seatPreferences() != null
                ? request.seatPreferences() : List.of();

        int bestScore = -1;
        List<Seat> bestSeats = new ArrayList<>();   // 최고 점수 좌석들 (동률 포함)
        List<String> bestReasons = new ArrayList<>();

        // 좌석은 생성 순서(1A,1B,...앞줄·왼쪽 우선)대로 검사
        for (Seat seat : seats) {
            if (!seat.available()) {
                continue; // 예약된 좌석 제외
            }

            int score = 0;
            List<String> reasons = new ArrayList<>();

            // 정책 문서의 점수표
            if (access.contains("WALKING_DIFFICULTY") && seat.position().equals("FRONT")) {
                score += 5;
                reasons.add("다리가 불편하셔서 타고 내리기 쉬운 앞쪽 좌석입니다.");
            }
            if (access.contains("WALKING_DIFFICULTY") && seat.side().equals("AISLE")) {
                score += 3;
                reasons.add("이동이 편한 통로 쪽 좌석입니다.");
            }
            if (prefs.contains("WINDOW") && seat.side().equals("WINDOW")) {
                score += 3;
                reasons.add("창가를 선호하셔서 창가 좌석입니다.");
            }
            if (access.contains("MOTION_SICKNESS") && seat.position().equals("FRONT")) {
                score += 4;
                reasons.add("멀미가 있으셔서 흔들림이 적은 앞쪽 좌석입니다.");
            }

            if (score > bestScore) {
                // 더 높은 점수 발견 → 새로 시작
                bestScore = score;
                bestSeats = new ArrayList<>();
                bestSeats.add(seat);
                bestReasons = reasons;
            } else if (score == bestScore) {
                // 동점 → 목록에 추가
                bestSeats.add(seat);
            }
        }

        // 예약 가능한 좌석이 아예 없는 경우
        if (bestSeats.isEmpty()) {
            return new SeatRecommendation(null, 0, List.of("예약 가능한 좌석이 없습니다."), List.of());
        }

        // 첫 번째 = 대표 추천 (앞줄·왼쪽 우선), 나머지 = 동률 대안
        Seat bestSeat = bestSeats.get(0);
        List<Seat> alternatives = bestSeats.subList(1, bestSeats.size());

        if (bestReasons.isEmpty()) {
            bestReasons = new ArrayList<>();
            bestReasons.add("예약 가능한 좌석입니다.");
        }

        return new SeatRecommendation(bestSeat, bestScore, bestReasons, alternatives);
    }
}