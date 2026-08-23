package com.malrota.controller;

import com.malrota.dto.request.ConversationParseRequest;
import com.malrota.dto.response.ConversationParseResponse;
import com.malrota.service.ConversationParseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversation")
public class ConversationController {

    private final ConversationParseService parseService;

    public ConversationController(ConversationParseService parseService) {
        this.parseService = parseService;
    }

    @PostMapping("/parse")
    public ConversationParseResponse parse(@Valid @RequestBody ConversationParseRequest request) {
        return parseService.parse(request);
    }
}