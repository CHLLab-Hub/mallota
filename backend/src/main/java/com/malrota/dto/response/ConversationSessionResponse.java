package com.malrota.dto.response;

import com.malrota.domain.ConversationSession;
import com.malrota.domain.ConversationState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSessionResponse {

    private String sessionId;
    private ConversationState state;

    private String departure;
    private String arrival;
    private String date;
    private String departureTime;
    private String timePreference;
    private String servicePreference;
    private String busGradePreference;
    private int passengers;
    private String clarificationPrompt;

    private List<String> seatPreferences;
    private List<String> accessibilityNeeds;

    private String selectedBusId;
    private String recommendedSeatNo;
    private String bookingId;

    // 세션에 계속 남는 값이 아니라 "이번 발화"에 한해 프론트에 전달하는 1회성 신호
    // (예: "더 빠른/더 늦은 거 없어?" → 방금 보여준 버스보다 이르거나 늦은 시간을 찾아달라는 요청)
    private boolean wantsEarlierBus;
    private boolean wantsLaterBus;
    // 출발지-도착지 사이에 직행 노선 자체가 없다는 1회성 신호. 프론트는 이걸 받으면 세션을
    // 초기화해서 출발지/도착지부터 다시 물어봐야 한다 — 세션에 남겨두면 같은 노선을 계속 물게 된다.
    private boolean routeNotFound;
    // LLM이 STT 오인식을 문맥으로 교정한 원문(예: "이런 트렌치" -> "이런 센트럴시티"). 세션에
    // 남기지 않는 1회성 신호다 — 프론트가 방금 사용자가 말한 것으로 표시한 채팅 말풍선을 이
    // 값으로 갱신해서, 실제로 이해한 내용과 화면에 보이는 말풍선이 다르게 보이지 않게 한다.
    private String correctedText;

    /** 엔티티 ➔ DTO 변환 */
    public static ConversationSessionResponse from(ConversationSession session) {
        return from(session, false, false, false, null);
    }

    /** 엔티티 ➔ DTO 변환 (이번 턴의 1회성 신호 포함) */
    public static ConversationSessionResponse from(ConversationSession session, boolean wantsEarlierBus,
                                                    boolean wantsLaterBus, boolean routeNotFound,
                                                    String correctedText) {
        return ConversationSessionResponse.builder()
                .sessionId(session.getSessionId())
                .state(session.getState())
                .departure(session.getDeparture())
                .arrival(session.getArrival())
                .date(session.getDate())
                .departureTime(session.getDepartureTime())
                .timePreference(session.getTimePreference())
                .servicePreference(session.getServicePreference())
                .busGradePreference(session.getBusGradePreference())
                .passengers(session.getPassengers())
                .clarificationPrompt(session.getClarificationPrompt())
                .seatPreferences(session.getSeatPreferences())
                .accessibilityNeeds(session.getAccessibilityNeeds())
                .selectedBusId(session.getSelectedBusId())
                .recommendedSeatNo(session.getRecommendedSeatNo())
                .bookingId(session.getBookingId())
                .wantsEarlierBus(wantsEarlierBus)
                .wantsLaterBus(wantsLaterBus)
                .routeNotFound(routeNotFound)
                .correctedText(correctedText)
                .build();
    }
}
