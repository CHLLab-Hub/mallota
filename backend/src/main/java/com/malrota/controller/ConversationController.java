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
    private final ConversationSearchService searchService;
    private final ConversationSessionService sessionService;

    public ConversationController(ConversationParseService parseService,
                                  ConversationSearchService searchService,
                                  ConversationSessionService sessionService) {
        this.parseService = parseService;
        this.searchService = searchService;
        this.sessionService = sessionService;
    }

    /**
     * 자연어 발화 파싱 및 세션 상태 누적 갱신
     */
    @PostMapping("/parse")
    public ConversationSessionResponse parse(@Valid @RequestBody ConversationParseRequest request) {
        // 세션을 조회하거나 생성하여 넘겨줌 -> 이전 턴의 인원(2명), 조건 영구 유지!
        ConversationSession session = sessionService.getOrCreate(request.sessionId());

        ConversationParseResponse parsed = parseService.parse(request, session);
        if (parsed != null) {
            session.mergeConditions(
                    parsed.departure(),
                    parsed.arrival(),
                    parsed.date(),
                    parsed.departureTime(),
                    parsed.timePreference(),
                    parsed.servicePreference(),
                    parsed.busGradePreference(),
                    parsed.passengers(),
                    parsed.seatPreferences(),
                    parsed.accessibilityNeeds(),
                    parsed.clarificationPrompt()
            );
        }

        sessionService.refreshAfterParse(session);
        return ConversationSessionResponse.from(session);
    }

    /**
     * 고속버스 운행 검색 (세션 조건 연동)
     */
    @PostMapping("/search")
    public ConversationSearchResponse search(@Valid @RequestBody ConversationParseRequest request) {
        return searchService.search(request);
    }
}
