package com.malrota.controller;

import com.malrota.domain.ConversationSession;
import com.malrota.dto.request.ConversationParseRequest;
import com.malrota.dto.response.ConversationParseResponse;
import com.malrota.dto.response.ConversationSearchResponse;
import com.malrota.dto.response.ConversationSessionResponse;
import com.malrota.service.ConversationParseService;
import com.malrota.service.ConversationSearchService;
import com.malrota.service.ConversationSessionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversation")
public class ConversationController {

    private final ConversationParseService parseService;
    private final ConversationSessionService sessionService;
    private final ConversationSearchService searchService;

    // 세 가지 서비스를 모두 주입받도록 합침 (충돌 해결)
    public ConversationController(ConversationParseService parseService,
                                  ConversationSessionService sessionService,
                                  ConversationSearchService searchService) {
        this.parseService = parseService;
        this.sessionService = sessionService;
        this.searchService = searchService;
    }

    /**
     * 1. 자연어 발화 파싱 및 세션 상태 누적 갱신 (내 작업물)
     */
    @PostMapping("/parse")
    public ConversationSessionResponse parse(@Valid @RequestBody ConversationParseRequest request) {
        ConversationSession session = sessionService.getOrCreate(request.sessionId());

        ConversationParseResponse parsed = parseService.parse(request);
        if (parsed != null) {
            session.mergeConditions(
                    parsed.departure(),
                    parsed.arrival(),
                    parsed.date(),
                    parsed.timePreference(),
                    parsed.seatPreferences(),
                    parsed.accessibilityNeeds()
            );
        }

        sessionService.refreshAfterParse(session);
        return ConversationSessionResponse.from(session);
    }

    /**
     * 2. 고속버스 운행 검색 (팀원 작업물)
     */
    @PostMapping("/search")
    public ConversationSearchResponse search(@Valid @RequestBody ConversationParseRequest request) {
        return searchService.search(request);
    }
}