/** Validated API request objects. */
package com.malrota.dto.request;

import jakarta.validation.constraints.NotBlank;

public record NluExtractRequest(
    @NotBlank(message = "사용자 발화 텍스트는 필수입니다.")
    String utterance, // 사용자 발화 텍스트
    String baseDate   // 기준 날짜 (YYYY-MM-DD, 미입력 시 null)
) {
}