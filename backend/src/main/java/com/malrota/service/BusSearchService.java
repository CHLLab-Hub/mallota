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
        // 1. 필수값(출발지, 도착지, 날짜) null 체크 방어 (14시 버스 에러 방지)
        if (request == null || !hasText(request.departure()) || !hasText(request.arrival()) || !hasText(request.date())) {
            return List.of();
        }

        // 2. 출발지·도착지 이름 → 터미널ID 변환
        String depId = tagoClient.findTerminalId(request.departure());
        String arrId = tagoClient.findTerminalId(request.arrival());
        if (depId == null || arrId == null) {
            return List.of();
        }

        // 3. 날짜 포맷 변환 (2026-08-24 → 20260824)
        String date = request.date().replace("-", "");

        // 4. 운행편 조회 후 등급 필터링 및 시간 조건 정렬
        List<BusSchedule> schedules = tagoClient.searchBuses(depId, arrId, date);
        return schedules.stream()
                .filter(schedule -> matchesGrade(schedule, request.busGradePreference()))
                .sorted(scheduleComparator(request))
                .toList();
    }

    private Comparator<BusSchedule> scheduleComparator(BusSearchRequest request) {
        Comparator<BusSchedule> byDepartureTime = Comparator.comparing(this::departureTime);

        // 특정 시각(예: 12:00, 15:00, 16:00)이 지정된 경우
        if (hasText(request.departureTime())) {
            LocalTime requested = parseTime(request.departureTime());
            if (requested != null) {
                return Comparator.comparingInt((BusSchedule schedule) -> {
                    LocalTime dep = departureTime(schedule);
                    // 요청 시각 이전(예: 15:00 이전) 버스는 무조건 뒤로 보냄 (rank 1)
                    return dep.isBefore(requested) ? 1 : 0;
                }).thenComparing(byDepartureTime); // 15:00 이후 버스 중 가장 빠른 순서 정렬
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