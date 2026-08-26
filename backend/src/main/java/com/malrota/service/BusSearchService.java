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

@Service
public class BusSearchService {

    /** 요청 시각보다 이른 버스는 최대 2시간 전, 늦은 버스는 최대 30분 후까지만 추천한다. */
    private static final int MAX_EARLY_MINUTES = 120;
    private static final int MAX_LATE_MINUTES = 30;

    private final TagoClient tagoClient;

    public BusSearchService(TagoClient tagoClient) {
        this.tagoClient = tagoClient;
    }

    public List<BusSchedule> search(BusSearchRequest request) {
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

        // 운행편 조회 후 등급 필터링 및 시간 조건 정렬
        List<BusSchedule> schedules = tagoClient.searchBuses(depId, arrId, date);
        List<BusSchedule> filtered = schedules.stream()
                .filter(schedule -> matchesGrade(schedule, request.busGradePreference()))
                .filter(schedule -> isWithinRequestedTimeWindow(schedule, request))
                .sorted(scheduleComparator(request))
                .toList();
        return filtered;
    }
        /** 버스 3개 추천 (가장 가까운 시각 / 가장 저렴 / 근처 시각) */
    public List<BusRecommendation> recommend(BusSearchRequest request) {
        List<BusSchedule> schedules = search(request); // 조회+정렬 재활용
        List<BusRecommendation> result = new ArrayList<>();
        if (schedules.isEmpty()) return result;

        // 1. 가장 맞는 시각 (정렬 결과 첫 번째)
        BusSchedule best = schedules.get(0);
        result.add(new BusRecommendation(best, "말씀하신 시간과 가장 가까운 버스입니다.", "추천 시간"));
        boolean laterBusAlreadyIncluded = isAfterRequestedTime(best, request);
        boolean laterBusBeforeCheapest = laterBusAlreadyIncluded;

        // 2. 가장 저렴한 버스
        // search() 단계에서 이미 "요청 시각 2시간 전 ~ 30분 후"만 남겼으므로,
        // 새벽 최저가나 너무 늦은 운행편이 후보로 섞이지 않는다.
        BusSchedule cheapest = schedules.stream()
                .filter(schedule -> !isSameBus(schedule, best))
                // 추천 목록에는 요청 시각 뒤 버스를 최대 한 대만 넣고, 나머지는 이전 시간대로 구성한다.
                .filter(schedule -> !laterBusBeforeCheapest || !isAfterRequestedTime(schedule, request))
                .min(Comparator.comparingInt(BusSchedule::charge))
                .orElse(null);
        if (cheapest != null) {
            result.add(new BusRecommendation(cheapest, "가장 저렴한 버스입니다.", "최저가"));
            laterBusAlreadyIncluded = isAfterRequestedTime(cheapest, request);
        }

        // 3. 근처 시각 (1,2와 겹치지 않는 다음 버스)
        for (BusSchedule s : schedules) {
            if (!isSameBus(s, best) && !isSameBus(s, cheapest)
                    && (!laterBusAlreadyIncluded || !isAfterRequestedTime(s, request))) {
                result.add(new BusRecommendation(s, "비슷한 시간대의 다른 버스입니다.", "다른 시간"));
                break;
            }
        }
        return result;
    }

    private boolean isSameBus(BusSchedule a, BusSchedule b) {
        return a.routeId() != null && a.routeId().equals(b.routeId())
                && a.departureTime() != null && a.departureTime().equals(b.departureTime());
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
        return switch (preference.toUpperCase()) {
            case "EXCELLENT" -> grade.contains("우등");
            case "PREMIUM" -> grade.contains("프리미엄");
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

    private boolean isAfterRequestedTime(BusSchedule schedule, BusSearchRequest request) {
        if (!hasText(request.departureTime())) return false;
        LocalTime requested = parseTime(request.departureTime());
        LocalTime departure = departureTime(schedule);
        return requested != null && !departure.equals(LocalTime.MAX) && departure.isAfter(requested);
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
