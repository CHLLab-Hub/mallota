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

    /** 엔티티 ➔ DTO 변환 */
    public static ConversationSessionResponse from(ConversationSession session) {
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
                .build();
    }
}
