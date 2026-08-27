package com.malrota.service;

import com.malrota.client.TagoClient;
import com.malrota.dto.request.BusSearchRequest;
import com.malrota.dto.response.BusRecommendation;
import com.malrota.dto.response.BusSchedule;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class BusSearchService {

    /** 요청 시각보다 이른 버스는 최대 2시간 전, 늦은 버스는 최대 30분 후까지만 추천한다. */
    private static final int MAX_EARLY_MINUTES = 120;
    private static final int MAX_LATE_MINUTES = 30;
    /** 추천 카드(최저가/가까운 시간)는 먼저 30분 이내에서 후보를 찾고, 없으면 1시간까지 범위를 넓힌다. */
    private static final int OTHER_TIME_PRIMARY_MINUTES = 30;
    private static final int OTHER_TIME_FALLBACK_MINUTES = 60;
    /** 이르게 출발하는 버스에 주는 소폭의 페널티(분) — 같은 거리면 늦게 출발하는 쪽을 더 선호한다. */
    private static final int EARLY_PENALTY_MINUTES = 10;

    private final TagoClient tagoClient;

    public BusSearchService(TagoClient tagoClient) {
        this.tagoClient = tagoClient;
    }

    public List<BusSchedule> search(BusSearchRequest request) {
        List<BusSchedule> gradeFiltered = gradeFilteredSchedules(request);
        if (gradeFiltered.isEmpty()) return List.of();

        // 정확한 시각/등급 조건과 시간 조건 정렬
        BusSearchRequest effective = withEffectiveDepartureTime(gradeFiltered, request);
        return gradeFiltered.stream()
                .filter(schedule -> isWithinRequestedTimeWindow(schedule, effective))
                .sorted(scheduleComparator(effective))
                .toList();
    }

    /** 출발지/도착지/날짜로 운행편을 조회하고 등급만 필터링한, 시간창 적용 전의 원본 목록 */
    private List<BusSchedule> gradeFilteredSchedules(BusSearchRequest request) {
        return rawSchedules(request).stream()
                .filter(schedule -> matchesGrade(schedule, request.busGradePreference()))
                .toList();
    }

    /** 등급/시간 조건 적용 전, 출발지-도착지 사이에 그날 실제로 존재하는 모든 배차 원본 목록 */
    private List<BusSchedule> rawSchedules(BusSearchRequest request) {
        // 필수값(출발지, 도착지, 날짜) null 체크 방어 (14시 버스 에러 방지)
        if (request == null || !hasText(request.departure()) || !hasText(request.arrival()) || !hasText(request.date())) {
            return List.of();
        }

        // 출발지·도착지 이름 → 터미널ID 변환
        String depId = tagoClient.findTerminalId(request.departure());
        String arrId = tagoClient.findTerminalId(request.arrival());
        if (depId == null || arrId == null) {
            return List.of();
        }

        // 날짜 포맷 변환 (2026-08-24 → 20260824)
        String date = request.date().replace("-", "");

        return tagoClient.searchBuses(depId, arrId, date);
    }

    /**
     * 등급/시간 조건과 무관하게, 이 출발지-도착지 사이에 그날 배차가 하나라도 있는지 확인한다.
     * 우리는 직행 노선만 다루므로, 이게 false면 두 도시 사이에 직행 버스가 아예 없다는 뜻이다
     * (조건에 안 맞는 게 아니라 노선 자체가 없는 경우와 구분하기 위한 용도).
     *
     * 출발/도착이 아직 세부 터미널로 확정되지 않은 도시(예: "서울")여도 확인할 수 있다 — 그 도시의
     * 터미널 중 하나라도 노선이 있으면 존재하는 것으로 본다. rawSchedules()처럼 findTerminalId의
     * 부정확한 부분일치 폴백에 기대 도시 중 하나를 임의로 골라버리면(예: "서울"→우연히 서울경부만
     * 확인하고 센트럴시티는 확인 안 함) 실제로는 노선이 있는데 없다고 오판할 수 있어서, 도시 단위
     * 검사는 그 도시의 등록된 터미널 전부를 명시적으로 순회한다. 세부 터미널을 다 되묻기 전에 먼저
     * 노선 존재부터 확인해, 노선 자체가 없으면 터미널 되묻기 없이 바로 안내할 수 있게 하기 위해서다.
     */
    public boolean hasAnyScheduleBetween(BusSearchRequest request) {
        if (request == null || !hasText(request.departure()) || !hasText(request.arrival()) || !hasText(request.date())) {
            return false;
        }
        List<String> depIds = resolveTerminalIds(request.departure());
        List<String> arrIds = resolveTerminalIds(request.arrival());
        if (depIds.isEmpty() || arrIds.isEmpty()) return false;

        String date = request.date().replace("-", "");
        for (String depId : depIds) {
            for (String arrId : arrIds) {
                if (depId.equals(arrId)) continue;
                if (!tagoClient.searchBuses(depId, arrId, date).isEmpty()) return true;
            }
        }
        return false;
    }

    /** 도시명이면 그 도시의 모든 터미널 ID를, 이미 구체적인 터미널이면 그 터미널 ID 하나만 반환한다. */
    private List<String> resolveTerminalIds(String terminalOrCity) {
        if (TagoClient.isMultiTerminalCity(terminalOrCity)) {
            return TagoClient.terminalsInCity(terminalOrCity).stream()
                    .map(tagoClient::findTerminalId)
                    .filter(Objects::nonNull)
                    .toList();
        }
        String id = tagoClient.findTerminalId(terminalOrCity);
        return id == null ? List.of() : List.of(id);
    }

    /**
     * 첫차/막차는 정확한 시각을 말한 게 아니라서 request.departureTime()이 비어 있다. 그 상태로
     * 두면 이후 시간창 필터링/거리 정렬이 전부 무력화되어(항상 통과) "막차"를 골라도 낮 시간대의
     * 아무 저렴한 버스가 추천에 섞여 나온다. 그날 실제로 존재하는 가장 이르거나 늦은 운행편의
     * 시각을 "요청 시각"으로 채워 넣어, 이후 로직이 그 시각 근처로만 추천을 좁히게 만든다.
     */
    private BusSearchRequest withEffectiveDepartureTime(List<BusSchedule> gradeFilteredSchedules, BusSearchRequest request) {
        if (hasText(request.departureTime())) return request;
        boolean isFirst = "FIRST".equalsIgnoreCase(request.servicePreference());
        boolean isLast = "LAST".equalsIgnoreCase(request.servicePreference());
        if (!isFirst && !isLast) return request;

        Comparator<BusSchedule> byTime = Comparator.comparing(this::departureTime);
        BusSchedule anchor = gradeFilteredSchedules.stream()
                .filter(schedule -> !departureTime(schedule).equals(LocalTime.MAX))
                .reduce((a, b) -> isLast
                        ? (byTime.compare(a, b) >= 0 ? a : b)
                        : (byTime.compare(a, b) <= 0 ? a : b))
                .orElse(null);
        if (anchor == null) return request;

        return new BusSearchRequest(request.departure(), request.arrival(), request.date(),
                departureTime(anchor).toString(), request.timePreference(),
                request.servicePreference(), request.busGradePreference());
    }

    /**
     * 버스 2개 추천 (최저가 / 가장 가까운 시간). 이 두 카테고리는 항상 둘 다 보여준다. 같은 버스가
     * 두 조건 모두에 해당하면(예: 등급이 고정 요금이라 가격이 다 같을 때) 카드를 둘로 쪼개지 않고
     * 뱃지 2개를 붙인 카드 하나로 합쳐서 보여준다 — "가격 차이가 없는데 최저가라고 하면 거짓말"
     * 이라는 이유로 카드를 생략하거나 라벨을 바꿔치기하지 않는다. 두 카테고리 이름은 항상 고정이다.
     */
    public List<BusRecommendation> recommend(BusSearchRequest request) {
        return recommendFrom(gradeFilteredSchedules(request), request);
    }

    /** recommend()와 hasAnyScheduleBetween()을 한 번의 TAGO 조회로 함께 계산한 결과. */
    public record RecommendResult(List<BusRecommendation> recommendations, boolean routeExists) {}

    /**
     * recommend()와 동일하지만, 노선 자체의 존재 여부(등급/시간 조건과 무관)도 함께 계산한다.
     * TAGO 원본 조회를 한 번만 하도록 recommend()/hasAnyScheduleBetween()을 각각 호출하는 대신
     * 이 메서드로 합쳐서 쓴다 — 안 그러면 같은 노선을 두 번 조회하게 되어 TAGO 응답이 느릴 때
     * 대기 시간이 두 배가 된다.
     */
    public RecommendResult recommendWithRouteInfo(BusSearchRequest request) {
        List<BusSchedule> raw = rawSchedules(request);
        List<BusSchedule> gradeFiltered = raw.stream()
                .filter(schedule -> matchesGrade(schedule, request.busGradePreference()))
                .toList();
        List<BusRecommendation> recommendations = recommendFrom(gradeFiltered, request);
        boolean routeExists = !recommendations.isEmpty() || !raw.isEmpty();
        return new RecommendResult(recommendations, routeExists);
    }

    private List<BusRecommendation> recommendFrom(List<BusSchedule> gradeFiltered, BusSearchRequest request) {
        List<BusRecommendation> result = new ArrayList<>();
        if (gradeFiltered.isEmpty()) return result;

        BusSearchRequest effective = withEffectiveDepartureTime(gradeFiltered, request);

        if (!hasText(effective.departureTime())) {
            // 비교할 기준 시각이 없는 완전 자유 조건: 등급/시간대만 반영해 정렬된 순서에서
            // 맨 앞을 "가장 가까운 시간"으로 삼는다.
            List<BusSchedule> sorted = gradeFiltered.stream().sorted(scheduleComparator(effective)).toList();
            if (sorted.isEmpty()) return result;
            BusSchedule cheapest = sorted.stream().min(Comparator.comparingInt(BusSchedule::charge)).orElse(null);
            BusSchedule closest = sorted.get(0);
            addCheapestAndClosest(result, cheapest, closest);
            return result;
        }

        LocalTime requestedTime = parseTime(effective.departureTime());
        if (requestedTime == null) return result;

        // "최저가"는 30분 이내 후보 중에서 고르고, 30분 이내에 아무것도 없을 때만 1시간까지 넓힌다.
        List<BusSchedule> primaryPool = withinMinutes(gradeFiltered, requestedTime, OTHER_TIME_PRIMARY_MINUTES);
        List<BusSchedule> cheapestPool = !primaryPool.isEmpty() ? primaryPool
                : withinMinutes(gradeFiltered, requestedTime, OTHER_TIME_FALLBACK_MINUTES);
        if (cheapestPool.isEmpty()) return result;

        BusSchedule cheapest = cheapestPool.stream().min(Comparator.comparingInt(BusSchedule::charge)).orElse(null);

        // "가까운 시간": 최저가와 다른 버스를 30분 이내에서 먼저 찾고, (최저가가 그 안의 유일한
        // 후보였던 경우 등) 없으면 1시간까지 범위를 넓힌다. 이르게 출발하는 쪽보다 늦게 출발하는
        // 쪽을 살짝 더 선호한다. 그래도 다른 버스가 전혀 없으면 최저가와 같은 버스로 합쳐서
        // 보여준다 — 두 카테고리("최저가"/"가까운 시간")는 항상 둘 다 존재해야 한다.
        BusSchedule closest = primaryPool.stream()
                .filter(s -> cheapest == null || !isSameBus(s, cheapest))
                .min(Comparator.comparingInt(s -> weightedDistance(departureTime(s), requestedTime)))
                .orElse(null);
        if (closest == null) {
            closest = withinMinutes(gradeFiltered, requestedTime, OTHER_TIME_FALLBACK_MINUTES).stream()
                    .filter(s -> cheapest == null || !isSameBus(s, cheapest))
                    .min(Comparator.comparingInt(s -> weightedDistance(departureTime(s), requestedTime)))
                    .orElse(null);
        }
        if (closest == null) {
            closest = cheapest;
        }

        addCheapestAndClosest(result, cheapest, closest);
        return result;
    }

    /**
     * 최저가/가까운 시간 카드를 만든다. 같은 버스(routeId+출발시각)가 둘 다 해당하면 카드 하나에
     * 뱃지 2개를 붙이고, 다르면 카드 2개로 각각 보여준다.
     */
    private void addCheapestAndClosest(List<BusRecommendation> result, BusSchedule cheapest, BusSchedule closest) {
        if (cheapest != null && closest != null && isSameBus(cheapest, closest)) {
            result.add(new BusRecommendation(cheapest,
                    "가장 저렴하면서 요청하신 시간과도 가장 가까운 버스입니다.",
                    List.of("최저가", "가까운 시간")));
            return;
        }
        if (cheapest != null) {
            result.add(new BusRecommendation(cheapest, "가장 저렴한 버스입니다.", List.of("최저가")));
        }
        if (closest != null) {
            result.add(new BusRecommendation(closest, "요청하신 시간과 가장 가까운 버스입니다.", List.of("가까운 시간")));
        }
    }

    private boolean isSameBus(BusSchedule a, BusSchedule b) {
        return a.routeId() != null && a.routeId().equals(b.routeId())
                && a.departureTime() != null && a.departureTime().equals(b.departureTime());
    }

    private List<BusSchedule> withinMinutes(List<BusSchedule> schedules, LocalTime requestedTime, int maxMinutes) {
        return schedules.stream()
                .filter(s -> minutesFromRequested(departureTime(s), requestedTime) <= maxMinutes)
                .toList();
    }

    /** 이르게 출발하는 버스에 소폭의 페널티를 더해, 같은 거리면 늦게 출발하는 버스를 우선한다. */
    private int weightedDistance(LocalTime departure, LocalTime requestedTime) {
        int diffMinutes = (int) (departure.toSecondOfDay() - requestedTime.toSecondOfDay()) / 60;
        int penalty = diffMinutes < 0 ? EARLY_PENALTY_MINUTES : 0;
        return Math.abs(diffMinutes) + penalty;
    }

    private Comparator<BusSchedule> scheduleComparator(BusSearchRequest request) {
        Comparator<BusSchedule> byDepartureTime = Comparator.comparing(this::departureTime);

        // 특정 시각(예: 12:00, 15:00, 16:00)이 지정된 경우
        if (hasText(request.departureTime())) {
            LocalTime requested = parseTime(request.departureTime());
            if (requested != null) {
                return Comparator
                        .comparingInt((BusSchedule schedule) -> minutesFromRequested(departureTime(schedule), requested))
                        .thenComparing(byDepartureTime);
            }
        }

        // 시간대(MORNING, AFTERNOON 등)가 지정된 경우
        Comparator<BusSchedule> byTimePreference = Comparator.comparingInt(
            (BusSchedule schedule) -> timePreferenceRank(departureTime(schedule), request.timePreference())
        );

        // 첫차 / 막차 정렬
        boolean isLast = "LAST".equalsIgnoreCase(request.servicePreference());
        Comparator<BusSchedule> secondarySort = isLast ? byDepartureTime.reversed() : byDepartureTime;

        return byTimePreference.thenComparing(secondarySort);
    }

    private boolean matchesGrade(BusSchedule schedule, String preference) {
        if (!hasText(preference) || "ANY".equalsIgnoreCase(preference)) return true;
        String grade = schedule.grade() == null ? "" : schedule.grade();
        // TAGO는 "심야우등"/"심야프리미엄"을 각각 "우등"/"프리미엄"과는 다른 요금(더 비쌈)의 별도
        // 등급으로 취급한다. 단순 contains만 쓰면 "심야프리미엄"도 "프리미엄" 요청에 같이 잡혀서,
        // 같은 등급을 요청했는데 카드마다 요금이 다르게 보이는 사고가 난다.
        return switch (preference.toUpperCase()) {
            case "EXCELLENT" -> grade.contains("우등") && !grade.startsWith("심야");
            case "PREMIUM" -> grade.contains("프리미엄") && !grade.startsWith("심야");
            case "GENERAL" -> grade.contains("일반") || grade.contains("고속");
            default -> true;
        };
    }

    private int timePreferenceRank(LocalTime departure, String preference) {
        if (!hasText(preference) || "ANY".equalsIgnoreCase(preference)) return 0;
        
        // 시간대 정의:
        // - MORNING   : 06:00 ~ 12:00 (새벽 심야 제외)
        // - AFTERNOON : 12:00 ~ 17:00
        // - EVENING   : 17:00 ~ 21:00
        // - NIGHT     : 21:00 ~ 24:00 또는 00:00 ~ 06:00
        boolean matches = switch (preference.toUpperCase()) {
            case "MORNING" -> !departure.isBefore(LocalTime.of(6, 0)) && departure.isBefore(LocalTime.NOON);
            case "AFTERNOON" -> !departure.isBefore(LocalTime.NOON) && departure.isBefore(LocalTime.of(17, 0));
            case "EVENING" -> !departure.isBefore(LocalTime.of(17, 0)) && departure.isBefore(LocalTime.of(21, 0));
            case "NIGHT" -> departure.isBefore(LocalTime.of(6, 0)) || !departure.isBefore(LocalTime.of(21, 0));
            default -> true;
        };
        return matches ? 0 : 1;
    }

    private LocalTime departureTime(BusSchedule schedule) {
        String value = schedule.departureTime();
        if (value == null || value.length() < 4) return LocalTime.MAX;
        
        // HH:mm 또는 HHmm 형식 모두 지원
        if (value.contains(":")) {
            LocalTime parsed = parseTime(value);
            return parsed == null ? LocalTime.MAX : parsed;
        }
        
        String clean = value.replaceAll("[^0-9]", "");
        if (clean.length() >= 4) {
            String timeStr = clean.substring(clean.length() - 4, clean.length() - 2) + ":" + clean.substring(clean.length() - 2);
            LocalTime parsed = parseTime(timeStr);
            return parsed == null ? LocalTime.MAX : parsed;
        }
        return LocalTime.MAX;
    }

    /** 정확한 시각을 말한 경우에만 요청 시각 2시간 전부터 30분 후까지 후보를 제한한다. */
    private boolean isWithinRequestedTimeWindow(BusSchedule schedule, BusSearchRequest request) {
        if (!hasText(request.departureTime())) return true;
        LocalTime requested = parseTime(request.departureTime());
        LocalTime departure = departureTime(schedule);
        if (requested == null || departure.equals(LocalTime.MAX)) return false;
        int difference = (int) (departure.toSecondOfDay() - requested.toSecondOfDay()) / 60;
        return difference >= -MAX_EARLY_MINUTES && difference <= MAX_LATE_MINUTES;
    }

    private int minutesFromRequested(LocalTime departure, LocalTime requested) {
        return Math.abs((int) (departure.toSecondOfDay() - requested.toSecondOfDay()) / 60);
    }

    private LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
