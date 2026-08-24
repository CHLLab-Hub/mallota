package com.malrota.controller;

import com.malrota.dto.request.ConversationParseRequest;
import com.malrota.dto.response.ConversationParseResponse;
import com.malrota.service.ConversationParseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.malrota.dto.response.ConversationSearchResponse;
import com.malrota.service.ConversationSearchService;

@RestController
@RequestMapping("/api/conversation")
public class ConversationController {

    private final ConversationParseService parseService;
    private final ConversationSearchService searchService;

    public ConversationController(ConversationParseService parseService,
                                  ConversationSearchService searchService) {
        this.parseService = parseService;
        this.searchService = searchService;
    }

    @PostMapping("/parse")
    public ConversationParseResponse parse(@Valid @RequestBody ConversationParseRequest request) {
        return parseService.parse(request);
    }

    @PostMapping("/search")
    public ConversationSearchResponse search(@Valid @RequestBody ConversationParseRequest request) {
        return searchService.search(request);
    }
}