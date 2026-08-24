package com.malrota.controller;

import com.malrota.domain.ConversationSession;
import com.malrota.dto.request.ConversationParseRequest;
import com.malrota.dto.response.ConversationParseResponse;
import com.malrota.dto.response.ConversationSessionResponse;
import com.malrota.service.ConversationParseService;
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

    public ConversationController(ConversationParseService parseService, ConversationSessionService sessionService) {
        this.parseService = parseService;
        this.sessionService = sessionService;
    }

    /**
     * 자연어 발화 파싱 및 세션 상태 누적 갱신
     */
    @PostMapping("/parse")
    public ConversationSessionResponse parse(@Valid @RequestBody ConversationParseRequest request) {
        // 1. 세션 가져오기 (record 메서드: request.sessionId())
        ConversationSession session = sessionService.getOrCreate(request.sessionId());

        // 2. watsonx 자연어 파싱 수행
        ConversationParseResponse parsed = parseService.parse(request);
        if (parsed != null) {
            // 3. 레코드 메서드(get 제외)로 세션에 누적 병합
            session.mergeConditions(
                    parsed.departure(),
                    parsed.arrival(),
                    parsed.date(),
                    parsed.timePreference(),
                    parsed.seatPreferences(),
                    parsed.accessibilityNeeds()
            );
        }

        // 4. 상태 갱신 (필수값 완료 시 READY_TO_SEARCH 로 자동 전이)
        sessionService.refreshAfterParse(session);

        // 5. 누적된 최종 세션 상태 반환
        return ConversationSessionResponse.from(session);
    }
}