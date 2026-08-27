import { useEffect, useState } from 'react'
import { parseConversation, recommendBuses } from '../../api/conversationApi'
import { ApiError } from '../../api/httpClient'
import { useAppState } from './AppState'
import { VoicePanel, speak } from './VoicePanel'
import type { ConversationSessionResult } from './types'

const SEAT_LABELS: Record<string, string> = {
  WINDOW: '창가', AISLE: '통로', FRONT: '앞쪽', MIDDLE: '중간', BACK: '뒤쪽', ADJACENT: '연석',
}
const GRADE_LABELS: Record<string, string> = { EXCELLENT: '우등', PREMIUM: '프리미엄', GENERAL: '일반' }
const ACCESS_LABELS: Record<string, string> = {
  WALKING_DIFFICULTY: '보행 불편', ELDERLY_CARE: '어르신 동반', MOTION_SICKNESS: '멀미',
  PREGNANCY: '임산부', VISUAL_IMPAIRMENT: '시각장애',
}

function formatDateKorean(dateStr: string): string {
  const [, m, d] = dateStr.split('-').map(Number)
  return `${m}월 ${d}일`
}

function formatTimeKorean(timeStr: string): string {
  const [h, m] = timeStr.split(':').map(Number)
  const period = h < 12 ? '오전' : '오후'
  const hour12 = h % 12 === 0 ? 12 : h % 12
  return m === 0 ? `${period} ${hour12}시` : `${period} ${hour12}시 ${m}분`
}

// 버스 목록으로 넘어가기 직전에, 지금까지 모인 조건을 한 번에 정리해서 들려준다 —
// 여러 턴에 걸쳐 말한 조건이 실제로 어떻게 반영됐는지 사용자가 한눈에 확인할 수 있게.
function buildConditionSummary(
  session: ConversationSessionResult, dep: string, arr: string, date: string, departureTime: string | null,
): string {
  const parts: string[] = [`${dep}에서 ${arr}까지`, formatDateKorean(date)]

  if (departureTime) {
    parts.push(formatTimeKorean(departureTime))
  } else if (session.servicePreference === 'FIRST') {
    parts.push('첫차')
  } else if (session.servicePreference === 'LAST') {
    parts.push('막차')
  }

  parts.push(`${session.passengers}명`)

  const grade = session.busGradePreference ? GRADE_LABELS[session.busGradePreference] : null
  if (grade) parts.push(`${grade} 등급`)

  const seatLabels = (session.seatPreferences ?? []).map((s) => SEAT_LABELS[s] ?? s)
  if (seatLabels.length > 0) parts.push(`${seatLabels.join('/')} 선호`)

  const accessLabels = (session.accessibilityNeeds ?? []).map((a) => ACCESS_LABELS[a] ?? a)
  if (accessLabels.length > 0) parts.push(accessLabels.join('/'))

  return `지금까지 확인한 조건이에요: ${parts.join(', ')}. 이 조건으로 버스를 찾아볼게요.`
}

export function ConversationPanel() {
  const {
    sessionId, setSessionId,
    messages, addMessage, setScreen,
    setSeatPreferences, setAccessibilityNeeds,
    setPassengers,
    setRecommendations,
  } = useAppState()

  const [loading, setLoading] = useState(false)

  function appSay(t: string) {
    addMessage('app', t)
    speak(t)
  }

  // 아직 아무 대화도 없는(맨 처음 안내 문구만 있는) 상태로 화면에 들어오면, 다른 안내와
  // 마찬가지로 이 첫 질문도 음성으로 들려준다 — 글자로만 떠 있고 음성 안내가 없으면
  // 음성 우선 앱에서 사용자가 뭘 해야 할지 놓치기 쉽다.
  useEffect(() => {
    if (messages.length === 1) {
      speak(messages[0].text)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

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
        // "첫차"/"막차"는 그 자체로 출발 시각이 정해지므로 departureTime 없이도 충분하다.
        const hasServicePreference = session.servicePreference === 'FIRST' || session.servicePreference === 'LAST'
        if (!dep || !arr || !dt || (!departureTime && !hasServicePreference)) {
          // 안전망: 혹시 필수값 없으면 되묻기
          appSay('출발지, 도착지, 날짜와 정확한 출발 시간을 말씀해 주세요.')
        } else {
          // 좌석 선호·접근성·인원 창고에 저장 (좌석 추천에 쓰려고)
          setSeatPreferences(session.seatPreferences ?? [])
          setAccessibilityNeeds(session.accessibilityNeeds ?? [])
          setPassengers(session.passengers ?? 1)
          const { recommendations: recs, routeExists } = await recommendBuses({
            departure: dep,
            arrival: arr,
            date: dt,
            departureTime,
            timePreference: session.timePreference,
            servicePreference: session.servicePreference,
            busGradePreference: session.busGradePreference,
          })
          if (recs.length === 0) {
            if (!routeExists) {
              // 조건이 안 맞는 게 아니라 이 두 도시 사이에 직행 노선 자체가 없는 경우 — 우리는
              // 직행만 다루므로 그 사실을 정직하게 안내하고, 세션을 초기화해서 출발지/도착지부터
              // 다시 물어보게 한다.
              appSay(`${dep}에서 ${arr}까지 가는 직행 버스 노선을 찾지 못했어요. 다시 어디에서 어디로 가시는지 말씀해 주세요.`)
              setSessionId(null)
            } else {
              appSay('해당 조건의 버스를 찾지 못했습니다.')
            }
          } else {
            appSay(buildConditionSummary(session, dep, arr, dt, departureTime))
            setRecommendations(recs)
            // 조건 요약을 다 들을 시간을 준 뒤 버스 목록으로 넘어간다 — 곧바로 넘어가면
            // 버스 목록 화면 자체의 안내 음성이 곧장 겹쳐 들어와 요약이 끊겨 버린다.
            setTimeout(() => setScreen('bus'), 4000)
          }
        }
      }
    } catch (error) {
      if (error instanceof ApiError) {
        // ApiError.message에는 타임아웃/네트워크 오류처럼 상황에 맞는 안내가 이미 들어있는데,
        // errors[0]이 항상 비어있는 경우(예: TIMEOUT) errors[0]?.message만 보면 그 안내를
        // 놓치고 뭉뚱그린 "오류가 발생했습니다."만 들려주게 된다.
        appSay(error.errors[0]?.message ?? error.message ?? '오류가 발생했습니다.')
      } else {
        appSay('처리 중 문제가 발생했습니다. 서버 상태를 확인해 주세요.')
      }
    } finally {
      setLoading(false)
    }
  }

  return <VoicePanel onUserSpeak={handleUserSpeak} loading={loading} />
}
