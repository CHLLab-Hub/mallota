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

        // 1. 특정 시각(예: 14:00)을 지정한 경우 -> 해당 시각과 가장 가까운 순서
        if (hasText(request.departureTime())) {
            LocalTime requested = parseTime(request.departureTime());
            if (requested != null) {
                return Comparator.comparingInt((BusSchedule schedule) -> Math.abs(minutesBetween(departureTime(schedule), requested)))
                        .thenComparing(byDepartureTime);
            }
        }

        // 2. 1순위: 사용자가 지정한 시간대(MORNING: 06시~12시)에 해당하는 버스를 최우선(rank 0) 배치
        Comparator<BusSchedule> byTimePreference = Comparator.comparingInt(
                (BusSchedule schedule) -> timePreferenceRank(departureTime(schedule), request.timePreference())
        );

        // 3. 2순위: 그 시간대(오전) 안에서 첫차(오름차순) / 막차(내림차순) 정렬
        boolean isLast = "LAST".equalsIgnoreCase(request.servicePreference());
        Comparator<BusSchedule> secondarySort = isLast ? byDepartureTime.reversed() : byDepartureTime;

        return byTimePreference.thenComparing(secondarySort);
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
        if (!hasText(preference) || "ANY".equalsIgnoreCase(preference)) return 0;
        
        // 시간대 정의:
        // - MORNING   : 06:00 ~ 12:00 (새벽 1시 같은 심야는 제외!)
        // - AFTERNOON : 12:00 ~ 17:00
        // - EVENING   : 17:00 ~ 21:00
        // - NIGHT     : 21:00 ~ 24:00 또는 00:00 ~ 06:00 (심야)
        boolean matches = switch (preference.toUpperCase()) {
            case "MORNING" -> !departure.isBefore(LocalTime.of(6, 0)) && departure.isBefore(LocalTime.NOON);
            case "AFTERNOON" -> !departure.isBefore(LocalTime.NOON) && departure.isBefore(LocalTime.of(17, 0));
            case "EVENING" -> !departure.isBefore(LocalTime.of(17, 0)) && departure.isBefore(LocalTime.of(21, 0));
            case "NIGHT" -> departure.isBefore(LocalTime.of(6, 0)) || !departure.isBefore(LocalTime.of(21, 0));
            default -> true;
        };
        
        return matches ? 0 : 1; // 0이 우선순위 높음 (해당 시간대 버스가 맨 위에 옴)
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
