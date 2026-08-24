package com.malrota.service;

import com.malrota.dto.request.BusSearchRequest;
import com.malrota.dto.request.ConversationParseRequest;
import com.malrota.dto.response.BusSchedule;
import com.malrota.dto.response.ConversationParseResponse;
import com.malrota.dto.response.ConversationSearchResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConversationSearchService {

    private final ConversationParseService parseService;
    private final BusSearchService busSearchService;

    public ConversationSearchService(ConversationParseService parseService,
                                     BusSearchService busSearchService) {
        this.parseService = parseService;
        this.busSearchService = busSearchService;
    }

    public ConversationSearchResponse search(ConversationParseRequest request) {
        // 1. watsonx로 조건 추출 (기존 서비스 재활용)
        ConversationParseResponse condition = parseService.parse(request);

        // 2. 필수 정보(출발지·도착지·날짜)가 다 있는지 확인
        boolean hasAllRequired = condition.missingFields() == null
                || condition.missingFields().isEmpty();

        // 3. 조건이 부족하면 조회 안 하고 반환
        if (!hasAllRequired) {
            return new ConversationSearchResponse(condition, List.of(), false);
        }

        // 4. 조건이 충분하면 TAGO로 버스 조회 (기존 서비스 재활용)
        BusSearchRequest busRequest = new BusSearchRequest(
                condition.departure(),
                condition.arrival(),
                condition.date()
        );
        List<BusSchedule> buses = busSearchService.search(busRequest);

        return new ConversationSearchResponse(condition, buses, true);
    }
}