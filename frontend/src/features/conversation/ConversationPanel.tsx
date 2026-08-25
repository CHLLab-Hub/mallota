import { useState } from 'react'
import { parseConversation, searchBuses } from '../../api/conversationApi'
import { ApiError } from '../../api/httpClient'
import { useAppState } from './AppState'
import { VoicePanel, speak } from './VoicePanel'
import type { ConversationSessionResult } from './types'

export function ConversationPanel() {
  const {
    sessionId, setSessionId,
    addMessage, setBuses, setScreen,
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

      const dep = session.departure && session.departure !== 'null' ? session.departure : null
      const arr = session.arrival && session.arrival !== 'null' ? session.arrival : null
      const dt = session.date && session.date !== 'null' ? session.date : null

      if (!dep || !arr || !dt) {
        if (!dep) appSay('어디에서 출발하시나요?')
        else if (!arr) appSay('어디로 가시나요?')
        else appSay('언제 출발하시나요?')
      } else {
        const buses = await searchBuses({ departure: dep, arrival: arr, date: dt })
        if (buses.length === 0) {
          appSay('해당 조건의 버스를 찾지 못했습니다.')
        } else {
          appSay('조건에 맞는 버스를 찾았어요. 추천 버스를 보여드릴게요.')
          setBuses(buses)
          setTimeout(() => setScreen('bus'), 800)
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