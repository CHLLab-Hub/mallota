package com.malrota.service;

import com.malrota.client.TagoClient;
import com.malrota.dto.request.BusSearchRequest;
import com.malrota.dto.response.BusSchedule;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

@Service
public class BusSearchService {

    private final TagoClient tagoClient;

    public BusSearchService(TagoClient tagoClient) {
        this.tagoClient = tagoClient;
    }

    public List<BusSchedule> search(BusSearchRequest request) {
        // 1. 출발지·도착지 이름 → 터미널ID 변환
        String depId = tagoClient.findTerminalId(request.departure());
        String arrId = tagoClient.findTerminalId(request.arrival());

        // 2. 날짜에서 하이픈 제거 (2026-08-24 → 20260824)
        String date = request.date().replace("-", "");

        // 3. 운행편 조회 후 사용자가 말한 시간·등급 조건에 맞게 정렬
        List<BusSchedule> schedules = tagoClient.searchBuses(depId, arrId, date);
        return schedules.stream()
                .filter(schedule -> matchesGrade(schedule, request.busGradePreference()))
                .sorted(scheduleComparator(request))
                .toList();
    }

    private Comparator<BusSchedule> scheduleComparator(BusSearchRequest request) {
        Comparator<BusSchedule> byDepartureTime = Comparator.comparing(this::departureTime);

        if (hasText(request.departureTime())) {
            LocalTime requested = parseTime(request.departureTime());
            if (requested != null) {
                // 정확한 시각이 없으면 요청 시각과 가장 가까운 운행편을 먼저 보여준다.
                return Comparator.comparingInt((BusSchedule schedule) -> Math.abs(minutesBetween(departureTime(schedule), requested)))
                        .thenComparing(byDepartureTime);
            }
        }

        return switch (request.servicePreference() == null ? "" : request.servicePreference()) {
            case "LAST" -> byDepartureTime.reversed();
            case "FIRST" -> byDepartureTime;
            default -> Comparator.comparingInt((BusSchedule schedule) -> timePreferenceRank(departureTime(schedule), request.timePreference()))
                    .thenComparing(byDepartureTime);
        };
    }

    private boolean matchesGrade(BusSchedule schedule, String preference) {
        if (!hasText(preference) || "ANY".equals(preference)) return true;
        String grade = schedule.grade() == null ? "" : schedule.grade();
        return switch (preference) {
            case "EXCELLENT" -> grade.contains("우등");
            case "PREMIUM" -> grade.contains("프리미엄");
            case "GENERAL" -> grade.contains("일반") || grade.contains("고속");
            default -> true;
        };
    }

    private int timePreferenceRank(LocalTime departure, String preference) {
        if (!hasText(preference) || "ANY".equals(preference)) return 0;
        boolean matches = switch (preference) {
            case "MORNING" -> !departure.isBefore(LocalTime.of(6, 0)) && departure.isBefore(LocalTime.NOON);
            case "AFTERNOON" -> !departure.isBefore(LocalTime.NOON) && departure.isBefore(LocalTime.of(17, 0));
            case "EVENING" -> !departure.isBefore(LocalTime.of(17, 0)) && departure.isBefore(LocalTime.of(20, 0));
            case "NIGHT" -> !departure.isBefore(LocalTime.of(20, 0));
            default -> true;
        };
        return matches ? 0 : 1;
    }

    private LocalTime departureTime(BusSchedule schedule) {
        String value = schedule.departureTime();
        if (value == null || value.length() < 4) return LocalTime.MAX;
        LocalTime parsed = parseTime(value.substring(value.length() - 4, value.length() - 2) + ":" + value.substring(value.length() - 2));
        return parsed == null ? LocalTime.MAX : parsed;
    }

    private LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private int minutesBetween(LocalTime first, LocalTime second) {
        return first == null || second == null ? Integer.MAX_VALUE : Math.abs(first.toSecondOfDay() - second.toSecondOfDay()) / 60;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
