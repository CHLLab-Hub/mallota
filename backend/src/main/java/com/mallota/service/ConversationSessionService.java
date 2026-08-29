package com.mallota.service;

import com.mallota.domain.ConversationSession;
import com.mallota.domain.ConversationState;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 대화 세션 보관 + 상태 전환 규칙 담당.
 */
@Service
public class ConversationSessionService {

    private final Map<String, ConversationSession> sessions = new ConcurrentHashMap<>();

    /** 세션 조회, 없으면 새로 생성 */
    public ConversationSession getOrCreate(String sessionId) {
        String id = (sessionId == null || sessionId.isBlank())
                ? UUID.randomUUID().toString()
                : sessionId;
        return sessions.computeIfAbsent(id, ConversationSession::new);
    }

    /** 조건 수집 후 상태 갱신 (필수값 충족 시 READY_TO_SEARCH로 전환) */
    public void refreshAfterParse(ConversationSession session) {
        session.resetConfirmationIfNeeded();

        if (session.hasAllRequiredFields()) {
            if (session.getState() == ConversationState.COLLECTING_CONDITIONS) {
                session.setState(ConversationState.READY_TO_SEARCH);
            }
        } else {
            session.setState(ConversationState.COLLECTING_CONDITIONS);
        }
    }

    /** 버스 선택 → BUS_SELECTED */
    public void selectBus(ConversationSession session, String busId) {
        requireState(session, ConversationState.READY_TO_SEARCH, ConversationState.BUS_SELECTED);
        session.setSelectedBusId(busId);
        session.setState(ConversationState.BUS_SELECTED);
    }

    /** 좌석 추천 완료 → SEAT_RECOMMENDED */
    public void recommendSeat(ConversationSession session, String seatNo) {
        requireState(session, ConversationState.BUS_SELECTED, ConversationState.SEAT_RECOMMENDED);
        session.setRecommendedSeatNo(seatNo);
        session.setState(ConversationState.SEAT_RECOMMENDED);
    }

    /** 최종 확인 화면 진입 → AWAITING_CONFIRMATION */
    public void awaitConfirmation(ConversationSession session) {
        requireState(session, ConversationState.SEAT_RECOMMENDED, ConversationState.AWAITING_CONFIRMATION);
        session.setState(ConversationState.AWAITING_CONFIRMATION);
    }

    /** 예매 확정 → BOOKED */
    public void confirmBooking(ConversationSession session, String bookingId) {
        if (session.getState() != ConversationState.AWAITING_CONFIRMATION) {
            throw new IllegalStateException(
                    "최종 확인 단계를 거치지 않아 예매할 수 없습니다. 현재 상태: " + session.getState());
        }
        session.setBookingId(bookingId);
        session.setState(ConversationState.BOOKED);
    }

    /** 대화 세션 초기화 */
    public void reset(String sessionId) {
        sessions.remove(sessionId);
    }

    /** 허용된 상태에서만 전환되도록 검증 */
    private void requireState(ConversationSession session, ConversationState... allowed) {
        for (ConversationState s : allowed) {
            if (session.getState() == s) return;
        }
        throw new IllegalStateException(
                "현재 상태(" + session.getState() + ")에서는 진행할 수 없습니다.");
    }
}