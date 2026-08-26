package com.malrota.service;

import com.malrota.dto.request.SeatRecommendRequest;
import com.malrota.recommendation.MockSeatGenerator;
import com.malrota.recommendation.Seat;
import com.malrota.recommendation.SeatRecommendation;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SeatRecommendService {

    /** 좌석 배치도와 동일한 통로 위치: 2번 칸 뒤가 통로 (예: [A][B] | [C]) */
    private static final int AISLE_AFTER_COLUMN = 2;

    private final MockSeatGenerator seatGenerator;

    public SeatRecommendService(MockSeatGenerator seatGenerator) {
        this.seatGenerator = seatGenerator;
    }

    public SeatRecommendation recommend(SeatRecommendRequest request) {
        List<Seat> seats = seatGenerator.generate(request.busGrade());
        int passengers = (request.passengers() != null && request.passengers() > 0) ? request.passengers() : 1;

        List<String> access = request.accessibilityNeeds() != null ? request.accessibilityNeeds() : List.of();
        List<String> prefs = request.seatPreferences() != null ? request.seatPreferences() : List.of();

        // 2인 이상 예매 시 ➔ 인원수에 맞는 그룹 배치 우선 탐색 (원하는 위치가 매진이면 설명과 함께 차선책 배정)
        if (passengers >= 2) {
            SeatRecommendation groupRec = switch (passengers) {
                case 2 -> recommendPair(seats, access, prefs);
                case 3 -> {
                    SeatRecommendation triple = recommendTriple(seats, access, prefs);
                    yield triple != null ? triple : recommendPair(seats, access, prefs);
                }
                default -> {
                    SeatRecommendation quad = recommendQuad(seats, access, prefs);
                    if (quad != null) yield quad;
                    SeatRecommendation triple = recommendTriple(seats, access, prefs);
                    yield triple != null ? triple : recommendPair(seats, access, prefs);
                }
            };
            if (groupRec != null) {
                return groupRec;
            }
        }

        // 2. 1인 예매 (또는 원하는 그룹 배치가 전무할 때) ➔ 개별 좌석 가중치 추천
        return recommendSingleSeat(seats, access, prefs);
    }

    // ---- 공통 헬퍼: 선호 구역 판별 ----

    private String preferredSection(List<String> access, List<String> prefs) {
        if (access.contains("WALKING_DIFFICULTY") || access.contains("ELDERLY_CARE") || prefs.contains("FRONT")) {
            return "FRONT";
        }
        if (access.contains("MOTION_SICKNESS") || prefs.contains("MIDDLE")) {
            return "MIDDLE";
        }
        if (prefs.contains("BACK")) {
            return "BACK";
        }
        return "ANY";
    }

    private String sectionKorean(String section) {
        return switch (section) {
            case "FRONT" -> "앞쪽";
            case "MIDDLE" -> "중간";
            case "BACK" -> "뒤쪽";
            default -> "";
        };
    }

    /** 원하는 구역에 자리가 없을 때 "가장 가까운" 자리를 고르기 위한 기준 줄 */
    private int targetRow(List<Seat> seats, String preferredSection) {
        int lastRow = seats.stream().mapToInt(Seat::row).max().orElse(1);
        return switch (preferredSection) {
            case "FRONT" -> 1;
            case "MIDDLE" -> (lastRow + 1) / 2;
            case "BACK" -> lastRow;
            default -> 1; // 선호가 없으면 승하차가 편한 앞쪽 우선
        };
    }

    /** 2인 연석 탐색 및 친절한 설명 생성 */
    private SeatRecommendation recommendPair(List<Seat> seats, List<String> access, List<String> prefs) {
        // 통로를 사이에 두지 않고 실제로 나란히 붙어 있는 빈 좌석 쌍만 후보로 수집
        List<PairDefinition> pairDefs = findAdjacentPairs(seats);
        if (pairDefs.isEmpty()) {
            return null; // 연석이 아예 없을 때만 개별 좌석으로 폴백
        }

        String preferredSection = preferredSection(access, prefs);
        String preferredSectionKorean = sectionKorean(preferredSection);
        int targetRow = targetRow(seats, preferredSection);

        List<PairCandidate> availablePairs = new ArrayList<>();
        for (PairDefinition p : pairDefs) {
            // 기본 점수: 원하는 위치에서 멀어질수록 감점 (뒤쪽을 원하면 뒷줄이 유리)
            int score = 10 - Math.abs(p.row - targetRow);

            // 사용자가 원했던 구역과 일치하면 +20점 대폭 가산
            if (preferredSection.equals(p.position)) {
                score += 20;
            }
            // 창가를 원하시면 창가가 포함된 연석에 소폭 가산
            if (prefs.contains("WINDOW") && p.hasWindow) {
                score += 3;
            }

            availablePairs.add(new PairCandidate(p, score));
        }

        availablePairs.sort(Comparator.comparingInt(PairCandidate::score).reversed());
        PairCandidate best = availablePairs.get(0);
        PairDefinition bestDef = best.def;

        List<String> reasons = new ArrayList<>();
        String combinedNo = bestDef.seat1.seatNo() + ", " + bestDef.seat2.seatNo();

        // 사용자가 원했던 구역에 연석이 잘 있었던 경우
        if ("ANY".equals(preferredSection) || preferredSection.equals(bestDef.position)) {
            if ("FRONT".equals(bestDef.position)) {
                reasons.add("어르신과 함께 편안히 가실 수 있도록 승하차가 편한 앞쪽 연석입니다.");
            } else if ("MIDDLE".equals(bestDef.position)) {
                reasons.add("흔들림이 적어 멀미가 덜한 중간 연석으로 나란히 준비했습니다.");
            } else {
                reasons.add("요청하신 뒤쪽에 두 분이 나란히 앉으실 수 있는 연석으로 준비했습니다.");
            }
        }
        // 사용자가 원했던 구역에 연석이 없어 다른 위치의 연석을 찾은 경우 (친절한 설명!)
        else {
            reasons.add(String.format("요청하신 %s에는 나란히 앉으실 자리가 없어, 두 분이 함께 가실 수 있도록 %s에서 가장 가까운 %s번 연석으로 준비했습니다.",
                    preferredSectionKorean, preferredSectionKorean, combinedNo));
        }

        return new SeatRecommendation(bestDef.seat1, best.score(), reasons, List.of(bestDef.seat2), true, seats);
    }

    /** 3인 배치 탐색: 같은 줄에서 연석(2) + 통로 건너 1석을 함께 배정 */
    private SeatRecommendation recommendTriple(List<Seat> seats, List<String> access, List<String> prefs) {
        List<TripleDefinition> tripleDefs = findRowTriples(seats);
        if (tripleDefs.isEmpty()) {
            return null; // 같은 줄에 세 자리를 만들 수 없을 때만 다른 배치로 폴백
        }

        String preferredSection = preferredSection(access, prefs);
        String preferredSectionKorean = sectionKorean(preferredSection);
        int targetRow = targetRow(seats, preferredSection);

        List<TripleCandidate> availableTriples = new ArrayList<>();
        for (TripleDefinition t : tripleDefs) {
            int score = 10 - Math.abs(t.row - targetRow);
            if (preferredSection.equals(t.position)) {
                score += 20;
            }
            if (prefs.contains("WINDOW") && t.hasWindow) {
                score += 3;
            }
            availableTriples.add(new TripleCandidate(t, score));
        }

        availableTriples.sort(Comparator.comparingInt(TripleCandidate::score).reversed());
        TripleDefinition bestDef = availableTriples.get(0).def;

        List<Seat> group = new ArrayList<>(List.of(bestDef.seat1, bestDef.seat2, bestDef.extra));
        group.sort(Comparator.comparingInt(Seat::column));

        List<String> reasons = new ArrayList<>();
        String combinedNo = String.join(", ", group.stream().map(Seat::seatNo).toList());

        if ("ANY".equals(preferredSection) || preferredSection.equals(bestDef.position)) {
            if ("FRONT".equals(bestDef.position)) {
                reasons.add("어르신과 함께 편안히 가실 수 있도록 승하차가 편한 앞쪽 자리로 세 분 좌석을 나란히 준비했습니다.");
            } else if ("MIDDLE".equals(bestDef.position)) {
                reasons.add("흔들림이 적어 멀미가 덜한 중간 자리로 세 분이 함께 앉으실 수 있게 준비했습니다.");
            } else {
                reasons.add("요청하신 뒤쪽에 세 분이 함께 앉으실 수 있는 자리로 준비했습니다.");
            }
        } else {
            reasons.add(String.format("요청하신 %s에는 세 분이 함께 앉으실 자리가 없어, %s에서 가장 가까운 %s번 자리로 준비했습니다.",
                    preferredSectionKorean, preferredSectionKorean, combinedNo));
        }

        return new SeatRecommendation(group.get(0), availableTriples.get(0).score(), reasons, group.subList(1, group.size()), true, seats);
    }

    /** 4인 배치 탐색: 앞뒤로 이어진 두 줄에 동일한 칸의 연석을 배정하여 사각형(2x2) 배치를 만듦 */
    private SeatRecommendation recommendQuad(List<Seat> seats, List<String> access, List<String> prefs) {
        List<QuadDefinition> quadDefs = findRectangles(seats);
        if (quadDefs.isEmpty()) {
            return null; // 앞뒤로 이어진 사각형 배치를 만들 수 없을 때만 다른 배치로 폴백
        }

        String preferredSection = preferredSection(access, prefs);
        String preferredSectionKorean = sectionKorean(preferredSection);
        int targetRow = targetRow(seats, preferredSection);

        List<QuadCandidate> availableQuads = new ArrayList<>();
        for (QuadDefinition q : quadDefs) {
            int score = 10 - Math.abs(q.row - targetRow);
            if (preferredSection.equals(q.position)) {
                score += 20;
            }
            if (prefs.contains("WINDOW") && q.hasWindow) {
                score += 3;
            }
            availableQuads.add(new QuadCandidate(q, score));
        }

        availableQuads.sort(Comparator.comparingInt(QuadCandidate::score).reversed());
        QuadDefinition bestDef = availableQuads.get(0).def;

        // 앞줄 좌우, 뒷줄 좌우 순서로 표기 (예: "4A, 4B, 5A, 5B")
        List<Seat> group = List.of(bestDef.topLeft, bestDef.topRight, bestDef.bottomLeft, bestDef.bottomRight);

        List<String> reasons = new ArrayList<>();
        String combinedNo = String.join(", ", group.stream().map(Seat::seatNo).toList());

        if ("ANY".equals(preferredSection) || preferredSection.equals(bestDef.position)) {
            if ("FRONT".equals(bestDef.position)) {
                reasons.add("어르신과 함께 편안히 가실 수 있도록 승하차가 편한 앞쪽 연석 두 줄(사각형)로 준비했습니다.");
            } else if ("MIDDLE".equals(bestDef.position)) {
                reasons.add("흔들림이 적어 멀미가 덜한 중간 자리에 네 분이 마주 보고 앉으실 수 있게 준비했습니다.");
            } else {
                reasons.add("요청하신 뒤쪽에 네 분이 두 줄로 마주 보고 앉으실 수 있는 자리로 준비했습니다.");
            }
        } else {
            reasons.add(String.format("요청하신 %s에는 네 분이 함께 앉으실 자리가 없어, %s에서 가장 가까운 %s번 자리(두 줄 연석)로 준비했습니다.",
                    preferredSectionKorean, preferredSectionKorean, combinedNo));
        }

        return new SeatRecommendation(group.get(0), availableQuads.get(0).score(), reasons, group.subList(1, group.size()), true, seats);
    }

    /**
     * 같은 줄에서 통로를 사이에 두지 않고 붙어 있는 빈 좌석 쌍을 모두 찾는다.
     * 좌석 배치도가 [A][B] | [C] 이므로 B-C는 통로를 사이에 둔 자리라 연석이 아니다.
     */
    private List<PairDefinition> findAdjacentPairs(List<Seat> seats) {
        List<PairDefinition> pairs = new ArrayList<>();
        for (List<Seat> rowSeats : availableSeatsByRow(seats).values()) {
            for (Seat[] pair : rowPairs(rowSeats)) {
                boolean hasWindow = "WINDOW".equals(pair[0].side()) || "WINDOW".equals(pair[1].side());
                pairs.add(new PairDefinition(pair[0], pair[1], pair[0].position(), pair[0].row(), hasWindow));
            }
        }
        return pairs;
    }

    /**
     * 같은 줄에서 연석(2) + 통로 건너 남는 좌석 중 가장 가까운 1석을 묶은 3인 배치 후보를 모두 찾는다.
     */
    private List<TripleDefinition> findRowTriples(List<Seat> seats) {
        List<TripleDefinition> triples = new ArrayList<>();
        for (List<Seat> rowSeats : availableSeatsByRow(seats).values()) {
            if (rowSeats.size() < 3) continue;
            for (Seat[] pair : rowPairs(rowSeats)) {
                Seat extra = rowSeats.stream()
                        .filter(s -> s != pair[0] && s != pair[1])
                        .min(Comparator.comparingInt(s -> Math.min(Math.abs(s.column() - pair[0].column()), Math.abs(s.column() - pair[1].column()))))
                        .orElse(null);
                if (extra == null) continue;
                boolean hasWindow = "WINDOW".equals(pair[0].side()) || "WINDOW".equals(pair[1].side()) || "WINDOW".equals(extra.side());
                triples.add(new TripleDefinition(pair[0], pair[1], extra, pair[0].position(), pair[0].row(), hasWindow));
            }
        }
        return triples;
    }

    /**
     * 앞뒤로 바로 이어진 두 줄에서 같은 칸의 연석이 모두 비어 있는 사각형(2x2) 배치 후보를 모두 찾는다.
     */
    private List<QuadDefinition> findRectangles(List<Seat> seats) {
        Map<Integer, List<Seat>> byRow = availableSeatsByRow(seats);
        Map<Integer, List<Seat[]>> pairsByRow = new TreeMap<>();
        for (Map.Entry<Integer, List<Seat>> entry : byRow.entrySet()) {
            pairsByRow.put(entry.getKey(), rowPairs(entry.getValue()));
        }

        List<QuadDefinition> quads = new ArrayList<>();
        for (Map.Entry<Integer, List<Seat[]>> entry : pairsByRow.entrySet()) {
            int row = entry.getKey();
            List<Seat[]> nextRowPairs = pairsByRow.get(row + 1);
            if (nextRowPairs == null) continue;

            for (Seat[] topPair : entry.getValue()) {
                for (Seat[] bottomPair : nextRowPairs) {
                    if (topPair[0].column() != bottomPair[0].column() || topPair[1].column() != bottomPair[1].column()) {
                        continue; // 같은 칸끼리 앞뒤로 이어져야 사각형
                    }
                    boolean hasWindow = "WINDOW".equals(topPair[0].side()) || "WINDOW".equals(topPair[1].side());
                    quads.add(new QuadDefinition(topPair[0], topPair[1], bottomPair[0], bottomPair[1], topPair[0].position(), row, hasWindow));
                }
            }
        }
        return quads;
    }

    /** 줄(row)별로 빈 좌석만 모아 칸(column) 순으로 정렬 */
    private Map<Integer, List<Seat>> availableSeatsByRow(List<Seat> seats) {
        Map<Integer, List<Seat>> byRow = new TreeMap<>();
        for (Seat seat : seats) {
            if (seat.available()) {
                byRow.computeIfAbsent(seat.row(), r -> new ArrayList<>()).add(seat);
            }
        }
        for (List<Seat> rowSeats : byRow.values()) {
            rowSeats.sort(Comparator.comparingInt(Seat::column));
        }
        return byRow;
    }

    /** 한 줄 안에서 통로를 사이에 두지 않고 나란히 붙은 좌석 쌍을 모두 찾는다 (정렬된 줄 좌석 필요) */
    private List<Seat[]> rowPairs(List<Seat> sortedRowSeats) {
        List<Seat[]> pairs = new ArrayList<>();
        for (int i = 0; i + 1 < sortedRowSeats.size(); i++) {
            Seat left = sortedRowSeats.get(i);
            Seat right = sortedRowSeats.get(i + 1);
            if (right.column() - left.column() != 1) continue;      // 사이에 예약된 자리가 있음
            if (left.column() == AISLE_AFTER_COLUMN) continue;      // 통로를 사이에 둔 자리
            pairs.add(new Seat[]{left, right});
        }
        return pairs;
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
            if (prefs.contains("MIDDLE") && seat.position().equals("MIDDLE")) {
                score += 6;
                reasons.add("중간 좌석을 선호하셔서 가운데 좌석입니다.");
            }
            if (prefs.contains("BACK") && seat.position().equals("BACK")) {
                score += 6;
                reasons.add("뒷좌석을 선호하셔서 뒤쪽 좌석입니다.");
            }
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
            return new SeatRecommendation(null, 0, List.of("예약 가능한 좌석이 없습니다."), List.of(), false, List.of());
        }

        Seat bestSeat = bestSeats.get(0);
        if (bestReasons.isEmpty()) bestReasons.add("예약 가능한 좌석입니다.");

        return new SeatRecommendation(bestSeat, bestScore, bestReasons, bestSeats.subList(1, bestSeats.size()), false, seats);
    }

    private record PairDefinition(Seat seat1, Seat seat2, String position, int row, boolean hasWindow) {}
    private record PairCandidate(PairDefinition def, int score) {}

    private record TripleDefinition(Seat seat1, Seat seat2, Seat extra, String position, int row, boolean hasWindow) {}
    private record TripleCandidate(TripleDefinition def, int score) {}

    private record QuadDefinition(Seat topLeft, Seat topRight, Seat bottomLeft, Seat bottomRight, String position, int row, boolean hasWindow) {}
    private record QuadCandidate(QuadDefinition def, int score) {}
}
