package com.malrota.service;

import com.malrota.client.TagoClient;
import com.malrota.dto.request.BusSearchRequest;
import com.malrota.dto.response.BusSchedule;
import org.springframework.stereotype.Service;

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

        // 3. 운행편 조회
        return tagoClient.searchBuses(depId, arrId, date);
    }
}