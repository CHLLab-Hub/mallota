package com.mallota.dto.response;

import java.util.List;

public record ConversationSearchResponse(
        ConversationParseResponse condition,  // watsonx가 뽑은 조건
        List<BusSchedule> buses,              // 조회된 버스 목록 (조건 부족하면 빈 목록)
        boolean searched                      // 실제로 조회했는지 (조건 충분했는지)
) {
}