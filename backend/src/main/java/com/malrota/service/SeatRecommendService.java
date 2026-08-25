package com.malrota.service;

import com.malrota.dto.request.SeatRecommendRequest;
import com.malrota.recommendation.MockSeatGenerator;
import com.malrota.recommendation.Seat;
import com.malrota.recommendation.SeatRecommendation;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SeatRecommendService {

    private final MockSeatGenerator seatGenerator;

    public SeatRecommendService(MockSeatGenerator seatGenerator) {
        this.seatGenerator = seatGenerator;
    }

    public SeatRecommendation recommend(SeatRecommendRequest request) {
        List<Seat> seats = seatGenerator.generate(request.busGrade());
        int passengers = (request.passengers() != null && request.passengers() > 0) ? request.passengers() : 1;

        List<String> access = request.accessibilityNeeds() != null ? request.accessibilityNeeds() : List.of();
        List<String> prefs = request.seatPreferences() != null ? request.seatPreferences() : List.of();

        // 2인 이상 예매 시 ➔ 연석 우선 탐색 (원하는 위치가 매진이면 설명과 함께 차선책 연석 배정)
        if (passengers >= 2) {
            SeatRecommendation pairRec = recommendAdjacentPair(seats, access, prefs);
            if (pairRec != null) {
                return pairRec;
            }
        }

        // 2. 1인 예매 (또는 연석이 전무할 때) ➔ 개별 좌석 가중치 추천
        return recommendSingleSeat(seats, access, prefs);
    }

    /** 2인 연석 탐색 및 친절한 설명 생성 */
    private SeatRecommendation recommendAdjacentPair(List<Seat> seats, List<String> access, List<String> prefs) {
        Map<String, Seat> seatMap = new HashMap<>();
        for (Seat s : seats) {
            if (s.available()) seatMap.put(s.seatNo(), s);
        }

        // 28인승 우등 좌석 연석 쌍 정의 (열 번호 및 구역)
        List<PairDefinition> pairDefs = List.of(
                new PairDefinition("1B", "1C", "FRONT", 1),
                new PairDefinition("2B", "2C", "FRONT", 2),
                new PairDefinition("3B", "3C", "MIDDLE", 3),
                new PairDefinition("4B", "4C", "MIDDLE", 4),
                new PairDefinition("5B", "5C", "MIDDLE", 5),
                new PairDefinition("6B", "6C", "BACK", 6),
                new PairDefinition("7B", "7C", "BACK", 7),
                new PairDefinition("8B", "8C", "BACK", 8)
        );

        // 사용자가 원했던 핵심 위치 판별 (앞쪽 / 중간 / 뒤쪽)
        String preferredSection = "ANY";
        String preferredSectionKorean = "";
        if (access.contains("WALKING_DIFFICULTY") || access.contains("ELDERLY_CARE") || prefs.contains("FRONT")) {
            preferredSection = "FRONT";
            preferredSectionKorean = "앞쪽";
        } else if (access.contains("MOTION_SICKNESS") || prefs.contains("MIDDLE")) {
            preferredSection = "MIDDLE";
            preferredSectionKorean = "중간";
        } else if (prefs.contains("BACK")) {
            preferredSection = "BACK";
            preferredSectionKorean = "뒤쪽";
        }

        List<PairCandidate> availablePairs = new ArrayList<>();

        for (PairDefinition p : pairDefs) {
            if (seatMap.containsKey(p.no1) && seatMap.containsKey(p.no2)) {
                int score = 0;
                // 기본 점수: 앞줄일수록 높은 가중치
                score += (10 - p.row);

                // 사용자가 원했던 구역과 일치하면 +20점 대폭 가산
                if (preferredSection.equals(p.position)) {
                    score += 20;
                }

                availablePairs.add(new PairCandidate(p, score));
            }
        }

        // 예약 가능한 연석이 1개라도 존재하는 경우
        if (!availablePairs.isEmpty()) {
            availablePairs.sort(Comparator.comparingInt(PairCandidate::score).reversed());
            PairCandidate best = availablePairs.get(0);
            PairDefinition bestDef = best.def;

            List<String> reasons = new ArrayList<>();
            String combinedNo = bestDef.no1 + ", " + bestDef.no2;

            // 사용자가 원했던 구역에 연석이 잘 있었던 경우
            if ("ANY".equals(preferredSection) || preferredSection.equals(bestDef.position)) {
                if ("FRONT".equals(bestDef.position)) {
                    reasons.add("어르신과 함께 편안히 가실 수 있도록 승하차가 편한 앞쪽 연석입니다.");
                } else if ("MIDDLE".equals(bestDef.position)) {
                    reasons.add("흔들림이 적어 멀미가 덜한 중간 연석으로 나란히 준비했습니다.");
                } else {
                    reasons.add("두 분이서 함께 나란히 앉으실 수 있는 연석입니다.");
                }
            }
            // 사용자가 원했던 구역에 연석이 없어 다른 위치의 연석을 찾은 경우 (친절한 설명!)
            else {
                String actualSectionKorean = "FRONT".equals(bestDef.position) ? "앞쪽" : ("MIDDLE".equals(bestDef.position) ? "중간" : "뒤쪽");
                reasons.add(String.format("요청하신 %s에는 나란히 앉으실 수 있는 자리가 없어, 두 분이 함께 가실 수 있도록 가장 가까운 %s(%s번) 연석으로 준비했습니다.",
                        preferredSectionKorean, actualSectionKorean, combinedNo));
            }

            Seat representativeSeat = new Seat(combinedNo, bestDef.position, "PAIR", true);
            return new SeatRecommendation(representativeSeat, best.score, reasons, List.of(), seats);
        }

        return null; // 연석이 아예 없을 때만 개별 좌석으로 폴백
    }

    private SeatRecommendation recommendSingleSeat(List<Seat> seats, List<String> access, List<String> prefs) {
        int bestScore = -1;
        List<Seat> bestSeats = new ArrayList<>();
        List<String> bestReasons = new ArrayList<>();

        for (Seat seat : seats) {
            if (!seat.available()) continue;

            int score = 0;
            List<String> reasons = new ArrayList<>();

            if (access.contains("WALKING_DIFFICULTY") && seat.position().equals("FRONT")) {
                score += 15;
                reasons.add("다리가 불편하셔서 타고 내리기 편한 앞쪽 좌석입니다.");
            }
            if (access.contains("WALKING_DIFFICULTY") && seat.side().equals("AISLE")) {
                score += 8;
                reasons.add("이동이 편한 통로 쪽 좌석입니다.");
            }
            if (access.contains("MOTION_SICKNESS") && seat.position().equals("MIDDLE")) {
                score += 12;
                reasons.add("멀미가 덜하도록 흔들림이 적은 중간 좌석입니다.");
            }
            if (access.contains("ELDERLY_CARE") && seat.position().equals("FRONT")) {
                score += 10;
                reasons.add("어르신이 이용하기 편한 앞쪽 좌석입니다.");
            }

            if (prefs.contains("FRONT") && seat.position().equals("FRONT")) score += 6;
            if (prefs.contains("AISLE") && seat.side().equals("AISLE")) score += 6;
            if (prefs.contains("WINDOW") && seat.side().equals("WINDOW")) score += 6;

            if (score > bestScore) {
                bestScore = score;
                bestSeats = new ArrayList<>();
                bestSeats.add(seat);
                bestReasons = reasons;
            } else if (score == bestScore) {
                bestSeats.add(seat);
            }
        }

        if (bestSeats.isEmpty()) {
            return new SeatRecommendation(null, 0, List.of("예약 가능한 좌석이 없습니다."), List.of(), List.of());
        }

        Seat bestSeat = bestSeats.get(0);
        if (bestReasons.isEmpty()) bestReasons.add("예약 가능한 좌석입니다.");

        return new SeatRecommendation(bestSeat, bestScore, bestReasons, bestSeats.subList(1, bestSeats.size()), seats);
    }

    private record PairDefinition(String no1, String no2, String position, int row) {}
    private record PairCandidate(PairDefinition def, int score) {}
}
