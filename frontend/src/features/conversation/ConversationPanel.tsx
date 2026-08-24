import { useState } from 'react'
import { parseConversation, searchBuses } from '../../api/conversationApi'
import { recommendSeat } from '../../api/seatApi'
import { speechToText } from '../../api/voiceApi'
import { ApiError } from '../../api/httpClient'
import { useVoiceRecorder } from './useVoiceRecorder'
import type {
  ConversationSessionResult,
  BusSchedule,
  SeatRecommendation,
} from './types'

// 빠진 필수 정보를 보고 되물을 질문 만들기
function buildQuestion(session: ConversationSessionResult): string {
  if (!session.departure) return '어디에서 출발하시나요?'
  if (!session.arrival) return '어디로 가시나요?'
  if (!session.date) return '언제 출발하시나요?'
  return ''
}

export function ConversationPanel() {
  const [text, setText] = useState('')
  const [sessionId, setSessionId] = useState<string | null>(null)
  const [message, setMessage] = useState('어디로 가실 예정인지 말씀해 주세요.')
  const [loading, setLoading] = useState(false)
  const [transcribing, setTranscribing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [bus, setBus] = useState<BusSchedule | null>(null)
  const [seat, setSeat] = useState<SeatRecommendation | null>(null)

  const { recording, startRecording, stopRecording } = useVoiceRecorder()

  async function handleMicClick() {
    if (recording) {
      setTranscribing(true)
      setError(null)
      try {
        const audio = await stopRecording()
        const data = await speechToText(audio)
        setText(data.transcript)
      } catch (e) {
        setError('음성 인식에 실패했습니다. 다시 시도해 주세요.')
      } finally {
        setTranscribing(false)
      }
    } else {
      setError(null)
      try {
        await startRecording()
      } catch (e) {
        setError('마이크를 사용할 수 없습니다. 권한을 확인해 주세요.')
      }
    }
  }

  async function handleSend() {
    if (!text.trim()) return
    setLoading(true)
    setError(null)
    try {
      // 1. 대화 파싱 (조건 누적)
      const session: ConversationSessionResult = await parseConversation(text, sessionId)
      setSessionId(session.sessionId) // 세션 유지
      setText('') // 입력창 비우기

      if (session.state === 'COLLECTING_CONDITIONS') {
        // 2a. 아직 정보 부족 → 되묻기
        setMessage(buildQuestion(session))
        setBus(null)
        setSeat(null)
      } else {
        // 2b. 조건 다 모임 → 버스 조회
        setMessage('조건이 모두 확인되었습니다. 버스를 찾고 있어요...')
        const buses = await searchBuses({
          departure: session.departure!,
          arrival: session.arrival!,
          date: session.date!,
        })

        if (buses.length === 0) {
          setMessage('해당 조건의 버스를 찾지 못했습니다.')
          setBus(null)
          setSeat(null)
        } else {
          const chosenBus = buses[0]
          setBus(chosenBus)
          setMessage(`${chosenBus.departureTime} ${chosenBus.grade} 버스를 추천합니다.`)

          // 좌석 추천
          const seatData = await recommendSeat({
            seatPreferences: session.seatPreferences,
            accessibilityNeeds: session.accessibilityNeeds,
            busGrade: chosenBus.grade,
          })
          setSeat(seatData)
        }
      }
    } catch (error) {
      if (error instanceof ApiError) {
        setError(error.errors[0]?.message ?? error.message)
      } else {
        setError('처리 중 문제가 발생했습니다. 서버 상태를 확인해 주세요.')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="conversation-panel">
      <p style={{ fontSize: '1.2rem', fontWeight: 'bold' }}>{message}</p>

      <label htmlFor="travel-request">말씀하시거나 입력해 주세요</label>
      <textarea
        id="travel-request"
        value={text}
        onChange={(e) => setText(e.target.value)}
      />

      <div className="panel-actions">
        <button
          className="primary-button"
          type="button"
          onClick={handleMicClick}
          disabled={transcribing || loading}
        >
          {recording ? '🔴 녹음 중지' : transcribing ? '인식 중...' : '🎤 말하기'}
        </button>
        <button
          className="primary-button"
          type="button"
          onClick={handleSend}
          disabled={loading || recording || !text.trim()}
        >
          {loading ? '처리 중...' : '보내기'}
        </button>
      </div>

      {error && <p style={{ color: 'red' }}>{error}</p>}

      {bus && (
        <div style={{ marginTop: '20px' }}>
          <h3>추천 버스</h3>
          <p>
            {bus.departureTime} 출발 · {bus.grade} · {bus.charge.toLocaleString()}원
            {' '}({bus.departure} → {bus.arrival})
          </p>
        </div>
      )}

      {seat && seat.bestSeat && (
        <div style={{ marginTop: '20px' }}>
          <h3>추천 좌석: {seat.bestSeat.seatNo}</h3>
          <ul>
            {seat.reasons.map((reason, i) => (
              <li key={i}>{reason}</li>
            ))}
          </ul>
          {seat.alternatives.length > 0 && (
            <p>같은 조건의 다른 좌석: {seat.alternatives.map((s) => s.seatNo).join(', ')}</p>
          )}
        </div>
      )}
    </div>
  )
}