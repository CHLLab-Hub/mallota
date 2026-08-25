package com.malrota.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSession {

    private String sessionId;

    @Builder.Default
    private ConversationState state = ConversationState.COLLECTING_CONDITIONS;

    // 예매 조건
    private String departure;
    private String arrival;
    private String date;
    private String departureTime;
    private String timePreference;
    private String servicePreference;
    private String busGradePreference;
    @Builder.Default
    private int passengers = 1;

    private String clarificationPrompt;

    @Builder.Default
    private List<String> seatPreferences = new ArrayList<>();

    @Builder.Default
    private List<String> accessibilityNeeds = new ArrayList<>();

    // 선택된 운행편 및 좌석
    private String selectedBusId;
    private String recommendedSeatNo;
    private String bookingId;

    /** sessionId를 받는 생성자 */
    public ConversationSession(String sessionId) {
        this.sessionId = sessionId;
        this.state = ConversationState.COLLECTING_CONDITIONS;
        this.seatPreferences = new ArrayList<>();
        this.accessibilityNeeds = new ArrayList<>();
    }

    /** 필수 조건(출발지, 도착지, 날짜)이 모두 채워졌는지 검사 */
    public boolean hasAllRequiredFields() {
        return departure != null && !departure.isBlank()
                && arrival != null && !arrival.isBlank()
                && date != null && !date.isBlank();
    }

    /** 조건이 바뀌었을 때 확인 상태를 초기화 */
    public void resetConfirmationIfNeeded() {
        if (this.state == ConversationState.AWAITING_CONFIRMATION || this.state == ConversationState.BOOKED) {
            this.state = hasAllRequiredFields() ? ConversationState.READY_TO_SEARCH : ConversationState.COLLECTING_CONDITIONS;
            this.bookingId = null;
        }
    }

    /** 새로 추출된 조건 병합. 파서가 세션의 기존 값을 반영한 완성 상태를 전달한다. */
    public void mergeConditions(String departure, String arrival, String date, String departureTime,
                                String timePreference, String servicePreference, String busGradePreference,
                                int passengers, List<String> seatPrefs, List<String> accessNeeds,
                                String clarificationPrompt) {
        if (departure != null && !departure.isBlank()) this.departure = departure;
        if (arrival != null && !arrival.isBlank()) this.arrival = arrival;
        if (date != null && !date.isBlank()) this.date = date;
        if (departureTime != null && !departureTime.isBlank()) this.departureTime = departureTime;
        if (timePreference != null && !timePreference.isBlank()) this.timePreference = timePreference;
        if (servicePreference != null && !servicePreference.isBlank()) this.servicePreference = servicePreference;
        if (busGradePreference != null && !busGradePreference.isBlank()) this.busGradePreference = busGradePreference;
        if (passengers > 0) this.passengers = passengers;

        if (seatPrefs != null) {
            this.seatPreferences = new ArrayList<>(seatPrefs);
        }
        if (accessNeeds != null) {
            this.accessibilityNeeds = new ArrayList<>(accessNeeds);
        }
        this.clarificationPrompt = clarificationPrompt;
    }
}
