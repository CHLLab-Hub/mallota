package com.malrota.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ConversationParseRequest(
    @NotBlank(message = "발화 내용을 입력해 주세요.")
    String text,
    String sessionId
) {
}