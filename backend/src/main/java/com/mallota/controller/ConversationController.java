package com.mallota.controller;

import com.mallota.domain.ConversationSession;
import com.mallota.dto.request.ConversationParseRequest;
import com.mallota.dto.response.ConversationParseResponse;
import com.mallota.dto.response.ConversationSearchResponse;
import com.mallota.dto.response.ConversationSessionResponse;
import com.mallota.service.ConversationParseService;
import com.mallota.service.ConversationSearchService;
import com.mallota.service.ConversationSessionService;
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
                    parsed.passengerMentioned(),
                    parsed.seatPreferences(),
                    parsed.seatPreferenceMentioned(),
                    parsed.accessibilityNeeds(),
                    parsed.clarificationPrompt()
            );
        }

        sessionService.refreshAfterParse(session);
        // wantsEarlierBus/wantsLaterBus/routeNotFound/correctedText는 세션에 저장하지 않는 1회성
        // 신호라 이번 응답에만 실어 보낸다.
        boolean wantsEarlierBus = parsed != null && parsed.wantsEarlierBus();
        boolean wantsLaterBus = parsed != null && parsed.wantsLaterBus();
        boolean routeNotFound = parsed != null && parsed.routeNotFound();
        String correctedText = parsed != null ? parsed.correctedText() : null;
        return ConversationSessionResponse.from(session, wantsEarlierBus, wantsLaterBus, routeNotFound, correctedText);
    }

    /**
     * 고속버스 운행 검색 (세션 조건 연동)
     */
    @PostMapping("/search")
    public ConversationSearchResponse search(@Valid @RequestBody ConversationParseRequest request) {
        return searchService.search(request);
    }
}
