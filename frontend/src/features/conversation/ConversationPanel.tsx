import { useState } from 'react'
import { parseConversation, recommendBuses } from '../../api/conversationApi'
import { ApiError } from '../../api/httpClient'
import { useAppState } from './AppState'
import { VoicePanel, speak } from './VoicePanel'
import type { ConversationSessionResult } from './types'

export function ConversationPanel() {
  const {
    sessionId, setSessionId,
    addMessage, setScreen,
    setSeatPreferences, setAccessibilityNeeds,
    setPassengers,
    setRecommendations,
  } = useAppState()

  const [loading, setLoading] = useState(false)

  function appSay(t: string) {
    addMessage('app', t)
    speak(t)
  }

  async function handleUserSpeak(sendText: string) {
    addMessage('user', sendText)
    setLoading(true)

    try {
      const session: ConversationSessionResult = await parseConversation(sendText, sessionId)
      setSessionId(session.sessionId)

      // 서버가 더 물어볼 게 있으면 (clarificationPrompt) → 그걸 물어보기
      if (session.clarificationPrompt) {
        appSay(session.clarificationPrompt)
      } else {
        // 더 물을 게 없으면 → 버스 검색
        const dep = session.departure && session.departure !== 'null' ? session.departure : null
        const arr = session.arrival && session.arrival !== 'null' ? session.arrival : null
        const dt = session.date && session.date !== 'null' ? session.date : null

        const departureTime = session.departureTime && session.departureTime !== 'null' ? session.departureTime : null
        if (!dep || !arr || !dt || !departureTime) {
          // 안전망: 혹시 필수값 없으면 되묻기
          appSay('출발지, 도착지, 날짜와 정확한 출발 시간을 말씀해 주세요.')
        } else {
          // 좌석 선호·접근성·인원 창고에 저장 (좌석 추천에 쓰려고)
          setSeatPreferences(session.seatPreferences ?? [])
          setAccessibilityNeeds(session.accessibilityNeeds ?? [])
          setPassengers(session.passengers ?? 1)
          const recs = await recommendBuses({
            departure: dep,
            arrival: arr,
            date: dt,
            departureTime,
            timePreference: session.timePreference,
            servicePreference: session.servicePreference,
            busGradePreference: session.busGradePreference,
          })
          if (recs.length === 0) {
            appSay('해당 조건의 버스를 찾지 못했습니다.')
          } else {
            setRecommendations(recs)
            setTimeout(() => setScreen('bus'), 800)
          }
        }
      }
    } catch (error) {
      if (error instanceof ApiError) {
        appSay(error.errors[0]?.message ?? '오류가 발생했습니다.')
      } else {
        appSay('처리 중 문제가 발생했습니다. 서버 상태를 확인해 주세요.')
      }
    } finally {
      setLoading(false)
    }
  }

  return <VoicePanel onUserSpeak={handleUserSpeak} loading={loading} />
}
