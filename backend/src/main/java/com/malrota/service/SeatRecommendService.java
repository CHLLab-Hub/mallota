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
        // 앞뒤로 붙은 좌석("세로 연석")은 프리미엄처럼 통로 건너편이 매줄 홀로 좌석이라 가로 연석이
        // 원천적으로 불가능한 등급에서만 "나란히 앉는 것"에 준하는 의미가 있다. 일반/우등 등급에서는
        // 앞뒤로 떨어진 자리를 "연석"이라 부르면 오해를 줄 수 있어 프리미엄에서만 허용한다.
        boolean allowVerticalPair = request.busGrade() != null && request.busGrade().contains("프리미엄");

        // 2인 이상 예매 시 ➔ 인원수에 맞는 그룹 배치 우선 탐색. 나란히 붙은 자리(가로/세로)가 전혀 없는
        // 등급(예: 프리미엄의 통로 건너 홀로 좌석)에서도 마지막에는 "따로따로"로라도 반드시 인원수만큼
        // 배정한다 — 좌석 배치가 안 맞는다고 아무것도 못 찾아주는 일이 없게 한다.
        String busGrade = request.busGrade();
        if (passengers >= 2) {
            SeatRecommendation groupRec = switch (passengers) {
                case 2 -> recommendPair(seats, access, prefs, allowVerticalPair, busGrade);
                case 3 -> {
                    SeatRecommendation triple = recommendTriple(seats, access, prefs, busGrade);
                    yield triple != null ? triple : recommendSeparatePassengers(seats, access, prefs, 3, busGrade);
                }
                default -> {
                    SeatRecommendation quad = recommendQuad(seats, access, prefs, busGrade);
                    if (quad != null) yield quad;
                    SeatRecommendation triple = recommendTriple(seats, access, prefs, busGrade);
                    yield triple != null ? triple : recommendSeparatePassengers(seats, access, prefs, passengers, busGrade);
                }
            };
            if (groupRec != null) {
                return groupRec;
            }
        }

        // 1인 예매 ➔ 개별 좌석 가중치 추천
        return recommendSingleSeat(seats, access, prefs, busGrade);
    }

    // ---- 공통 헬퍼: 선호 구역 판별 ----

    /**
     * 사용자가 좌석 위치를 직접 말했으면(FRONT/MIDDLE/BACK) 그게 최우선이다. "할머니 모시고" 같은
     * 말에서 자동으로 추론된 접근성 배려(WALKING_DIFFICULTY/ELDERLY_CARE → 앞쪽 등)는 사용자가
     * 아무 위치도 안 말했을 때만 기본값으로 쓴다 — 안 그러면 "뒷자리로 주세요"라고 명확히 말해도
     * 추론된 배려가 그걸 조용히 덮어써 버린다 (뒷좌석 요청했는데 앞좌석이 나오는 사고의 원인).
     */
    private String preferredSection(List<String> access, List<String> prefs) {
        if (prefs.contains("FRONT")) return "FRONT";
        if (prefs.contains("MIDDLE")) return "MIDDLE";
        if (prefs.contains("BACK")) return "BACK";
        if (access.contains("WALKING_DIFFICULTY") || access.contains("ELDERLY_CARE") || access.contains("VISUAL_IMPAIRMENT")) {
            return "FRONT";
        }
        if (access.contains("MOTION_SICKNESS")) return "MIDDLE";
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

    /**
     * 2인 배치 탐색: 가로(나란히) 연석을 최우선으로, 프리미엄 등급이면 없을 때 세로(앞뒤) 연석,
     * 그마저 없으면 따로따로라도 가까운 자리 2석을 배정한다 (예: 프리미엄 등급은 통로 건너편이
     * 홀로 좌석이라 가로 연석이 원천적으로 불가능한 줄이 있다).
     */
    private SeatRecommendation recommendPair(List<Seat> seats, List<String> access, List<String> prefs, boolean allowVerticalPair, String busGrade) {
        List<PairDefinition> pairDefs = findAdjacentPairs(seats);
        boolean vertical = false;
        if (pairDefs.isEmpty() && allowVerticalPair) {
            pairDefs = findVerticalPairs(seats);
            vertical = true;
        }

        String preferredSection = preferredSection(access, prefs);
        String preferredSectionKorean = sectionKorean(preferredSection);

        // 나란히든 앞뒤든, 원하는 구역 안에 연석이 아예 없으면 엉뚱한 다른 구역의 연석보다는 원하는
        // 구역 "안에서" 따로라도 앉는 걸 우선한다 — 안 그러면 "뒷자리로 주세요"라고 말해도 앞쪽에
        // 연석이 있다는 이유만으로 앞자리가 나와버린다. 구역을 지키는 게 연석 여부보다 중요하다.
        boolean hasPairInPreferredSection = !"ANY".equals(preferredSection)
                && pairDefs.stream().anyMatch(p -> preferredSection.equals(p.position));
        if (!"ANY".equals(preferredSection) && !hasPairInPreferredSection) {
            SeatRecommendation inSection = trySeparateWithinSection(seats, access, prefs, preferredSection, preferredSectionKorean, 2, "두 분", busGrade);
            if (inSection != null) return inSection;
        }

        if (pairDefs.isEmpty()) {
            return recommendSeparatePassengers(seats, access, prefs, 2, busGrade);
        }

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

        // 점수가 동률인 다른 연석도 "같은 조건 좌석"으로 함께 보여준다 — 배정받은 자리 말고
        // 다른 자리를 원할 때 동등하게 좋은 선택지가 어디 있는지 바로 알 수 있게 한다.
        List<Seat> tiedGroupSeats = new ArrayList<>();
        for (PairCandidate c : availablePairs) {
            if (c != best && c.score() == best.score()) {
                if (!tiedGroupSeats.contains(c.def.seat1)) tiedGroupSeats.add(c.def.seat1);
                if (!tiedGroupSeats.contains(c.def.seat2)) tiedGroupSeats.add(c.def.seat2);
            }
        }

        List<String> reasons = new ArrayList<>();
        String combinedNo = bestDef.seat1.seatNo() + ", " + bestDef.seat2.seatNo();
        String arrangement = vertical ? "앞뒤로 붙은 자리" : "연석";

        // 사용자가 원했던 구역에 자리가 잘 있었던 경우
        if ("ANY".equals(preferredSection) || preferredSection.equals(bestDef.position)) {
            if (vertical) {
                reasons.add(String.format("나란히 앉으실 옆자리가 없어, 두 분이 함께 가실 수 있도록 %s로 준비했습니다.", arrangement));
            } else if ("FRONT".equals(bestDef.position)) {
                reasons.add("승하차가 편한 앞쪽 연석입니다.");
            } else if ("MIDDLE".equals(bestDef.position)) {
                reasons.add("흔들림이 적어 멀미가 덜한 중간 연석으로 나란히 준비했습니다.");
            } else {
                reasons.add("요청하신 뒤쪽에 두 분이 나란히 앉으실 수 있는 연석으로 준비했습니다.");
            }
        }
        // 사용자가 원했던 구역에 자리가 없어 다른 위치에서 찾은 경우 (친절한 설명!)
        else {
            String suffixPhrase = vertical ? (combinedNo + "번 앞뒤로 붙은 자리로 준비했습니다.") : (combinedNo + "번 연석으로 준비했습니다.");
            reasons.add(String.format("요청하신 %s에는 나란히 앉으실 자리가 없어, 두 분이 함께 가실 수 있도록 %s에서 가장 가까운 %s",
                    preferredSectionKorean, preferredSectionKorean, suffixPhrase));
        }

        return new SeatRecommendation(bestDef.seat1, best.score(), reasons, List.of(bestDef.seat2), true, seats, tiedGroupSeats);
    }

    /** 3인 배치 탐색: 같은 줄에서 연석(2) + 통로 건너 1석을 함께 배정 */
    private SeatRecommendation recommendTriple(List<Seat> seats, List<String> access, List<String> prefs, String busGrade) {
        List<TripleDefinition> tripleDefs = findRowTriples(seats);

        String preferredSection = preferredSection(access, prefs);
        String preferredSectionKorean = sectionKorean(preferredSection);

        boolean hasTripleInPreferredSection = !"ANY".equals(preferredSection)
                && tripleDefs.stream().anyMatch(t -> preferredSection.equals(t.position));
        if (!"ANY".equals(preferredSection) && !hasTripleInPreferredSection) {
            SeatRecommendation inSection = trySeparateWithinSection(seats, access, prefs, preferredSection, preferredSectionKorean, 3, "세 분", busGrade);
            if (inSection != null) return inSection;
        }

        if (tripleDefs.isEmpty()) {
            return null; // 같은 줄에 세 자리를 만들 수 없을 때만 다른 배치로 폴백
        }

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
        TripleCandidate best = availableTriples.get(0);
        TripleDefinition bestDef = best.def;

        List<Seat> group = new ArrayList<>(List.of(bestDef.seat1, bestDef.seat2, bestDef.extra));
        group.sort(Comparator.comparingInt(Seat::column));

        // 점수가 동률인 다른 3인 배치도 "같은 조건 좌석"으로 함께 보여준다.
        List<Seat> tiedGroupSeats = new ArrayList<>();
        for (TripleCandidate c : availableTriples) {
            if (c != best && c.score() == best.score()) {
                for (Seat s : List.of(c.def.seat1, c.def.seat2, c.def.extra)) {
                    if (!tiedGroupSeats.contains(s)) tiedGroupSeats.add(s);
                }
            }
        }

        List<String> reasons = new ArrayList<>();
        String combinedNo = String.join(", ", group.stream().map(Seat::seatNo).toList());

        if ("ANY".equals(preferredSection) || preferredSection.equals(bestDef.position)) {
            if ("FRONT".equals(bestDef.position)) {
                reasons.add("승하차가 편한 앞쪽 자리로 세 분 좌석을 나란히 준비했습니다.");
            } else if ("MIDDLE".equals(bestDef.position)) {
                reasons.add("흔들림이 적어 멀미가 덜한 중간 자리로 세 분이 함께 앉으실 수 있게 준비했습니다.");
            } else {
                reasons.add("요청하신 뒤쪽에 세 분이 함께 앉으실 수 있는 자리로 준비했습니다.");
            }
        } else {
            reasons.add(String.format("요청하신 %s에는 세 분이 함께 앉으실 자리가 없어, %s에서 가장 가까운 %s번 자리로 준비했습니다.",
                    preferredSectionKorean, preferredSectionKorean, combinedNo));
        }

        return new SeatRecommendation(group.get(0), best.score(), reasons, group.subList(1, group.size()), true, seats, tiedGroupSeats);
    }

    /** 4인 배치 탐색: 앞뒤로 이어진 두 줄에 동일한 칸의 연석을 배정하여 사각형(2x2) 배치를 만듦 */
    private SeatRecommendation recommendQuad(List<Seat> seats, List<String> access, List<String> prefs, String busGrade) {
        List<QuadDefinition> quadDefs = findRectangles(seats);

        String preferredSection = preferredSection(access, prefs);
        String preferredSectionKorean = sectionKorean(preferredSection);

        boolean hasQuadInPreferredSection = !"ANY".equals(preferredSection)
                && quadDefs.stream().anyMatch(q -> preferredSection.equals(q.position));
        if (!"ANY".equals(preferredSection) && !hasQuadInPreferredSection) {
            SeatRecommendation inSection = trySeparateWithinSection(seats, access, prefs, preferredSection, preferredSectionKorean, 4, "네 분", busGrade);
            if (inSection != null) return inSection;
        }

        if (quadDefs.isEmpty()) {
            return null; // 앞뒤로 이어진 사각형 배치를 만들 수 없을 때만 다른 배치로 폴백
        }

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
        QuadCandidate best = availableQuads.get(0);
        QuadDefinition bestDef = best.def;

        // 앞줄 좌우, 뒷줄 좌우 순서로 표기 (예: "4A, 4B, 5A, 5B")
        List<Seat> group = List.of(bestDef.topLeft, bestDef.topRight, bestDef.bottomLeft, bestDef.bottomRight);

        // 점수가 동률인 다른 사각형 배치도 "같은 조건 좌석"으로 함께 보여준다.
        List<Seat> tiedGroupSeats = new ArrayList<>();
        for (QuadCandidate c : availableQuads) {
            if (c != best && c.score() == best.score()) {
                for (Seat s : List.of(c.def.topLeft, c.def.topRight, c.def.bottomLeft, c.def.bottomRight)) {
                    if (!tiedGroupSeats.contains(s)) tiedGroupSeats.add(s);
                }
            }
        }

        List<String> reasons = new ArrayList<>();
        String combinedNo = String.join(", ", group.stream().map(Seat::seatNo).toList());

        if ("ANY".equals(preferredSection) || preferredSection.equals(bestDef.position)) {
            if ("FRONT".equals(bestDef.position)) {
                reasons.add("승하차가 편한 앞쪽  두 줄씩 연석으로 준비했습니다.");
            } else if ("MIDDLE".equals(bestDef.position)) {
                reasons.add("흔들림이 적어 멀미가 덜한 중간 자리에 네 분이 앉으실 수 있게 준비했습니다.");
            } else {
                reasons.add("요청하신 뒤쪽에 네 분이 두 줄씩 앉으실 수 있는 자리로 준비했습니다.");
            }
        } else {
            reasons.add(String.format("요청하신 %s에는 네 분이 함께 앉으실 자리가 없어, %s에서 가장 가까운 %s번 자리(두 줄 연석)로 준비했습니다.",
                    preferredSectionKorean, preferredSectionKorean, combinedNo));
        }

        return new SeatRecommendation(group.get(0), best.score(), reasons, group.subList(1, group.size()), true, seats, tiedGroupSeats);
    }

    /**
     * 요청한 구역(예: 뒤쪽) 안에 나란히/앞뒤로 붙은 연석이 없을 때, 엉뚱한 다른 구역의 연석보다는
     * 요청한 구역 "안에서" 따로라도 앉는 걸 우선한다 — "뒷자리로 주세요"라고 했는데 앞쪽에 연석이
     * 있다는 이유만으로 앞자리가 나와버리는 사고를 막는다. 요청한 구역에 인원수만큼 빈자리가 없으면
     * null을 돌려줘서 호출한 쪽이 다음 단계(다른 구역의 가장 가까운 연석)로 넘어가게 한다.
     */
    private SeatRecommendation trySeparateWithinSection(List<Seat> seats, List<String> access, List<String> prefs,
                                                          String preferredSection, String preferredSectionKorean,
                                                          int size, String peopleNoun, String busGrade) {
        List<ScoredSeat> inSection = rankSingleSeatsScored(seats, access, prefs, busGrade).stream()
                .filter(s -> preferredSection.equals(s.seat().position()))
                .toList();
        if (inSection.size() < size) {
            return null;
        }

        List<ScoredSeat> chosenScored = inSection.subList(0, size);
        List<Seat> chosen = chosenScored.stream().map(ScoredSeat::seat).toList();
        List<String> reasons = List.of(String.format(
                "요청하신 %s에는 나란히 앉으실 자리가 없어, %s 안에서 %s 자리를 따로 준비했습니다.",
                preferredSectionKorean, preferredSectionKorean, peopleNoun));
        Seat bestSeat = chosen.get(0);
        List<Seat> alternatives = new ArrayList<>(chosen.subList(1, chosen.size()));

        // 뽑힌 마지막 자리와 점수가 같은데 뽑히지 않은 자리도 "같은 조건 좌석"으로 함께 보여준다.
        int cutoffScore = chosenScored.get(chosenScored.size() - 1).score();
        List<Seat> tiedGroupSeats = inSection.stream()
                .skip(chosenScored.size())
                .filter(s -> s.score() == cutoffScore)
                .map(ScoredSeat::seat)
                .toList();

        return new SeatRecommendation(bestSeat, 0, reasons, alternatives, !alternatives.isEmpty(), seats, tiedGroupSeats);
    }

    /**
     * 나란히든 앞뒤든 붙어있는 자리를 아예 만들 수 없을 때(예: 프리미엄 등급의 통로 건너 홀로 좌석만
     * 남은 경우)의 마지막 수단: 인원수만큼 "따로따로"라도 각자 가장 좋은 자리를 배정한다.
     */
    private SeatRecommendation recommendSeparatePassengers(List<Seat> seats, List<String> access, List<String> prefs, int count, String busGrade) {
        List<ScoredSeat> ranked = rankSingleSeatsScored(seats, access, prefs, busGrade);
        if (ranked.isEmpty()) {
            return new SeatRecommendation(null, 0, List.of("예약 가능한 좌석이 없습니다."), List.of(), false, seats, List.of());
        }

        List<ScoredSeat> chosenScored = ranked.subList(0, Math.min(count, ranked.size()));
        List<Seat> chosen = chosenScored.stream().map(ScoredSeat::seat).toList();
        List<String> reasons = new ArrayList<>();
        if (chosen.size() < count) {
            reasons.add(String.format("죄송합니다, 남은 좌석이 %d석뿐이라 %d분 모두 배정해 드리지 못했습니다.", chosen.size(), count));
        } else {
            reasons.add(String.format("나란히 앉으실 자리가 없어, %d분 모두 각자 가까운 자리로 따로 준비했습니다.", count));
        }

        Seat bestSeat = chosen.get(0);
        List<Seat> alternatives = new ArrayList<>(chosen.subList(1, chosen.size()));

        // 뽑힌 마지막 자리와 점수가 같은데 뽑히지 않은 자리도 "같은 조건 좌석"으로 함께 보여준다.
        int cutoffScore = chosenScored.get(chosenScored.size() - 1).score();
        List<Seat> tiedGroupSeats = ranked.stream()
                .skip(chosenScored.size())
                .filter(s -> s.score() == cutoffScore)
                .map(ScoredSeat::seat)
                .toList();

        return new SeatRecommendation(bestSeat, 0, reasons, alternatives, !alternatives.isEmpty(), seats, tiedGroupSeats);
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
     * 앞뒤로 바로 이어진 두 줄에서 같은 칸의 빈 좌석 쌍을 모두 찾는다 (세로 연석). 프리미엄 등급처럼
     * 통로 건너편이 매줄 홀로 좌석이라 가로 연석이 전혀 없는 칸에서도 이걸로 나란히 앉힐 수 있다.
     */
    private List<PairDefinition> findVerticalPairs(List<Seat> seats) {
        Map<Integer, List<Seat>> byRow = availableSeatsByRow(seats);
        List<PairDefinition> pairs = new ArrayList<>();
        for (Map.Entry<Integer, List<Seat>> entry : byRow.entrySet()) {
            int row = entry.getKey();
            List<Seat> nextRow = byRow.get(row + 1);
            if (nextRow == null) continue;
            for (Seat s1 : entry.getValue()) {
                for (Seat s2 : nextRow) {
                    if (s1.column() != s2.column()) continue;
                    boolean hasWindow = "WINDOW".equals(s1.side()) || "WINDOW".equals(s2.side());
                    pairs.add(new PairDefinition(s1, s2, s1.position(), row, hasWindow));
                }
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

    private SeatRecommendation recommendSingleSeat(List<Seat> seats, List<String> access, List<String> prefs, String busGrade) {
        List<Seat> ranked = rankSingleSeats(seats, access, prefs, busGrade);
        if (ranked.isEmpty()) {
            return new SeatRecommendation(null, 0, List.of("예약 가능한 좌석이 없습니다."), List.of(), false, List.of(), List.of());
        }

        Seat bestSeat = ranked.get(0);
        List<String> reasons = seatReasons(bestSeat, access, prefs, busGrade);
        if (reasons.isEmpty()) reasons.add("예약 가능한 좌석입니다.");

        // 동점(같은 점수)인 나머지 좌석은 "동률 대안"("같은 조건 좌석")으로 함께 보여준다.
        int targetRow = targetRow(seats, preferredSection(access, prefs));
        int bestScore = seatScore(bestSeat, access, prefs, targetRow, busGrade);
        List<Seat> tiedAlternatives = ranked.stream()
                .skip(1)
                .filter(s -> seatScore(s, access, prefs, targetRow, busGrade) == bestScore)
                .toList();

        return new SeatRecommendation(bestSeat, bestScore, reasons, tiedAlternatives, false, seats, tiedAlternatives);
    }

    /** 좌석 + 개인 선호/배려 점수를 함께 담아, 동률 여부를 판단할 수 있게 한다 */
    private record ScoredSeat(Seat seat, int score) {}

    /** 예약 가능한 좌석을 개인 선호/배려 점수 내림차순으로 정렬하고, 각 좌석의 점수도 함께 담는다 */
    private List<ScoredSeat> rankSingleSeatsScored(List<Seat> seats, List<String> access, List<String> prefs, String busGrade) {
        String preferredSection = preferredSection(access, prefs);
        int targetRow = targetRow(seats, preferredSection);
        return seats.stream()
                .filter(Seat::available)
                .map(s -> new ScoredSeat(s, seatScore(s, access, prefs, targetRow, busGrade)))
                .sorted(Comparator.comparingInt(ScoredSeat::score).reversed())
                .toList();
    }

    /** 예약 가능한 좌석을 개인 선호/배려 점수 내림차순으로 정렬 (없으면 빈 리스트) */
    private List<Seat> rankSingleSeats(List<Seat> seats, List<String> access, List<String> prefs, String busGrade) {
        return rankSingleSeatsScored(seats, access, prefs, busGrade).stream().map(ScoredSeat::seat).toList();
    }

    /**
     * "다리가 불편해서/할머니 모시고" 같은 말에서 자동 추론된 접근성 배려(WALKING_DIFFICULTY,
     * ELDERLY_CARE → 앞쪽 선호)는 사용자가 명시적으로 다른 위치를 요청했을 때는 점수에 반영하지
     * 않는다 — 안 그러면 "뒷좌석으로 주세요"라고 말해도 추론된 배려가 그걸 덮어써서 앞좌석이
     * 나와 버린다. 통로 쪽 좌석 배려(이동 편의)는 위치와 무관하므로 항상 반영한다.
     *
     * targetRow 기준 거리 가산점도 항상 반영한다 — 원하는 구역이 완전히 매진이라 정확히 일치하는
     * 좌석이 하나도 없을 때, 점수가 전부 0으로 동률이 되어 좌석 생성 순서상 우연히 앞자리가 먼저
     * 나오는 사고(뒷자리 요청 → 매진 → 조용히 앞자리 배정)를 막는다. 원하는 구역과 가까울수록
     * 유리하게 해서, 매진이면 최소한 "가장 가까운" 자리가 나오게 한다.
     */
    private int seatScore(Seat seat, List<String> access, List<String> prefs, int targetRow, String busGrade) {
        boolean explicitPositionGiven = prefs.contains("FRONT") || prefs.contains("MIDDLE") || prefs.contains("BACK");
        boolean isPremium = busGrade != null && busGrade.contains("프리미엄");
        int score = 0;

        if (!explicitPositionGiven && access.contains("WALKING_DIFFICULTY") && seat.position().equals("FRONT")) score += 15;
        if (access.contains("WALKING_DIFFICULTY") && seat.side().equals("AISLE")) score += 8;
        if (!explicitPositionGiven && access.contains("MOTION_SICKNESS") && seat.position().equals("MIDDLE")) score += 12;
        if (!explicitPositionGiven && access.contains("ELDERLY_CARE") && seat.position().equals("FRONT")) score += 10;
        // 임산부는 앞쪽 위치보다 화장실 접근·승하차가 쉬운 통로 쪽 좌석을 준다 (프리미엄 등급은
        // 아래 1열 창가석 규칙이 우선한다).
        if (access.contains("PREGNANCY") && seat.side().equals("AISLE")) score += 15;
        // 프리미엄 등급은 임산부라면 위 일반 배려보다도 1열 창가석("C" 좌석)을 특히 우선한다.
        if (!explicitPositionGiven && !prefs.contains("AISLE") && isPremium && access.contains("PREGNANCY")
                && seat.row() == 1 && seat.seatNo().endsWith("C")) {
            score += 25;
        }
        if (!explicitPositionGiven && access.contains("VISUAL_IMPAIRMENT") && seat.position().equals("FRONT")) score += 15;
        if (access.contains("VISUAL_IMPAIRMENT") && seat.side().equals("AISLE")) score += 8;

        if (prefs.contains("FRONT") && seat.position().equals("FRONT")) score += 6;
        if (prefs.contains("MIDDLE") && seat.position().equals("MIDDLE")) score += 6;
        if (prefs.contains("BACK") && seat.position().equals("BACK")) score += 6;
        if (prefs.contains("AISLE") && seat.side().equals("AISLE")) score += 6;
        if (prefs.contains("WINDOW") && seat.side().equals("WINDOW")) score += 6;

        score += 10 - Math.abs(seat.row() - targetRow);

        return score;
    }

    /** seatScore와 반드시 같은 조건으로 유지해야 하는 사유 문구 목록 */
    private List<String> seatReasons(Seat seat, List<String> access, List<String> prefs, String busGrade) {
        boolean explicitPositionGiven = prefs.contains("FRONT") || prefs.contains("MIDDLE") || prefs.contains("BACK");
        boolean isPremium = busGrade != null && busGrade.contains("프리미엄");
        List<String> reasons = new ArrayList<>();

        if (!explicitPositionGiven && access.contains("WALKING_DIFFICULTY") && seat.position().equals("FRONT")) {
            reasons.add("다리가 불편하셔서 승하차 편한 앞쪽 좌석입니다.");
        }
        if (access.contains("WALKING_DIFFICULTY") && seat.side().equals("AISLE")) {
            reasons.add("이동이 편한 통로 쪽 좌석입니다.");
        }
        if (!explicitPositionGiven && access.contains("MOTION_SICKNESS") && seat.position().equals("MIDDLE")) {
            reasons.add("멀미가 덜하도록 흔들림이 적은 중간 좌석입니다.");
        }
        if (!explicitPositionGiven && access.contains("ELDERLY_CARE") && seat.position().equals("FRONT")) {
            reasons.add("승하차가 편한 앞쪽 좌석입니다.");
        }
        if (access.contains("PREGNANCY") && seat.side().equals("AISLE")) {
            reasons.add("화장실 이용이 편한 통로 쪽 좌석입니다.");
        }
        if (!explicitPositionGiven && !prefs.contains("AISLE") && isPremium && access.contains("PREGNANCY")
                && seat.row() == 1 && seat.seatNo().endsWith("C")) {
            reasons.add("임산부분이시라 프리미엄 1열 창가 좌석으로 편안하게 준비했습니다.");
        }
        if (!explicitPositionGiven && access.contains("VISUAL_IMPAIRMENT") && seat.position().equals("FRONT")) {
            reasons.add("승무원 도움을 받기 편한 앞쪽 좌석입니다.");
        }
        if (access.contains("VISUAL_IMPAIRMENT") && seat.side().equals("AISLE")) {
            reasons.add("이동이 편한 통로 쪽 좌석입니다.");
        }
        if (prefs.contains("MIDDLE") && seat.position().equals("MIDDLE")) {
            reasons.add("중간 좌석을 선호하셔서 가운데 좌석입니다.");
        }
        if (prefs.contains("BACK") && seat.position().equals("BACK")) {
            reasons.add("뒷좌석을 선호하셔서 뒤쪽 좌석입니다.");
        }
        if (explicitPositionGiven && !seat.position().equals(preferredSectionFromPrefs(prefs))) {
            reasons.add(String.format("요청하신 %s에는 자리가 없어, 가장 가까운 자리로 준비했습니다.", sectionKorean(preferredSectionFromPrefs(prefs))));
        }
        return reasons;
    }

    /** seatReasons에서 "요청한 구역이 매진" 안내 문구를 붙일지 판단하기 위한, prefs만으로 정해지는 명시적 구역 */
    private String preferredSectionFromPrefs(List<String> prefs) {
        if (prefs.contains("FRONT")) return "FRONT";
        if (prefs.contains("MIDDLE")) return "MIDDLE";
        if (prefs.contains("BACK")) return "BACK";
        return "ANY";
    }

    private record PairDefinition(Seat seat1, Seat seat2, String position, int row, boolean hasWindow) {}
    private record PairCandidate(PairDefinition def, int score) {}

    private record TripleDefinition(Seat seat1, Seat seat2, Seat extra, String position, int row, boolean hasWindow) {}
    private record TripleCandidate(TripleDefinition def, int score) {}

    private record QuadDefinition(Seat topLeft, Seat topRight, Seat bottomLeft, Seat bottomRight, String position, int row, boolean hasWindow) {}
    private record QuadCandidate(QuadDefinition def, int score) {}
}
