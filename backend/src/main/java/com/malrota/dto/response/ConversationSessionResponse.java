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

    /** 엔티티 ➔ DTO 변환 */
    public static ConversationSessionResponse from(ConversationSession session) {
        return from(session, false, false);
    }

    /** 엔티티 ➔ DTO 변환 (이번 턴의 1회성 신호 포함) */
    public static ConversationSessionResponse from(ConversationSession session, boolean wantsEarlierBus, boolean wantsLaterBus) {
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
                .build();
    }
}
