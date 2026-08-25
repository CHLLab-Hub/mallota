import { useState } from 'react'
import { parseConversation, searchBuses } from '../../api/conversationApi'
import { recommendSeat } from '../../api/seatApi'
import { speechToText, textToSpeech } from '../../api/voiceApi'
import { ApiError } from '../../api/httpClient'
import { useVoiceRecorder } from './useVoiceRecorder'
import { SeatMap } from './SeatMap'
import { BusTicket } from './BusTicket'
import type {
  ConversationSessionResult,
  BusSchedule,
  SeatRecommendation,
} from './types'

function buildQuestion(session: ConversationSessionResult): string {
  if (!session.departure) return '어디에서 출발하시나요?'
  if (!session.arrival) return '어디로 가시나요?'
  if (!session.date) return '언제 출발하시나요?'
  return '몇 시쯤 출발하는 버스를 원하시나요?'
}

function formatTime(raw: string): string {
  if (!raw || raw.length < 12) return raw
  const hour = parseInt(raw.substring(8, 10), 10)
  const minute = raw.substring(10, 12)
  const period = hour < 12 ? '오전' : '오후'
  const displayHour = hour === 0 ? 12 : hour > 12 ? hour - 12 : hour
  return minute === '00' ? `${period} ${displayHour}시` : `${period} ${displayHour}시 ${minute}분`
}

type Stage = 'chat' | 'payment' | 'ticket'

export function ConversationPanel() {
  const [text, setText] = useState('')
  const [sessionId, setSessionId] = useState<string | null>(null)
  const [message, setMessage] = useState('어디로 가실 예정인지 말씀해 주세요.')
  const [loading, setLoading] = useState(false)
  const [transcribing, setTranscribing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [bus, setBus] = useState<BusSchedule | null>(null)
  const [seat, setSeat] = useState<SeatRecommendation | null>(null)
  const [selectedSeat, setSelectedSeat] = useState<string | null>(null)
  const [selecting, setSelecting] = useState(false)
  const [stage, setStage] = useState<Stage>('chat')

  const { recording, startRecording, stopRecording } = useVoiceRecorder()

  // 최종 확정된 좌석 (직접 선택했으면 그것, 아니면 추천)
  const finalSeatNo = selectedSeat ?? seat?.bestSeat?.seatNo ?? ''

  async function speak(text: string) {
    if (!text.trim()) return
    try {
      const data = await textToSpeech(text)
      if (data.audio) {
        const audio = new Audio('data:audio/mp3;base64,' + data.audio)
        await audio.play()
      }
    } catch (e) {
      // 무시
    }
  }

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
    setSelectedSeat(null)
    setSelecting(false)
    setError(null)
    try {
      const session: ConversationSessionResult = await parseConversation(text, sessionId)
      setSessionId(session.sessionId)
      setText('')

      // 백엔드가 보낸 질문(누락 질문 또는 약자/좌석 배려 질문)이 있으면 우선 질문하고 대기!
      if (session.clarificationPrompt) {
        setMessage(session.clarificationPrompt)
        speak(session.clarificationPrompt)
        setBus(null)
        setSeat(null)
        return
      }

      // 필수값이 비어있는 경우 (백업)
      if (!session.departure || !session.arrival || !session.date) {
        const question = buildQuestion(session)
        setMessage(question)
        speak(question)
        setBus(null)
        setSeat(null)
        return
      }

      // 더 이상 질문할 것이 없을 때 비로소 버스 조회 및 좌석 추천 진행
      setMessage('조건이 모두 확인되었습니다. 최적의 버스와 좌석을 찾고 있어요...')
      const buses = await searchBuses({
        departure: session.departure!,
        arrival: session.arrival!,
        date: session.date!,
        departureTime: session.departureTime,
        timePreference: session.timePreference,
        servicePreference: session.servicePreference,
        busGradePreference: session.busGradePreference,
      })

      if (buses.length === 0) {
        const msg = '해당 조건의 버스를 찾지 못했습니다. 다른 시간대를 말씀해 주세요.'
        setMessage(msg)
        speak(msg)
        setBus(null)
        setSeat(null)
      } else {
        const chosenBus = buses[0]
        setBus(chosenBus)

        const seatData = await recommendSeat({
            seatPreferences: session.seatPreferences,
            accessibilityNeeds: session.accessibilityNeeds,
            busGrade: chosenBus.grade,
            passengers: session.passengers ?? 2,
          })
          setSeat(seatData)

        const reasonText = seatData.reasons && seatData.reasons.length > 0 ? seatData.reasons[0] : ''
        const msg = `${formatTime(chosenBus.departureTime)} 출발 ${chosenBus.grade} 버스입니다. ${reasonText} 추천 좌석은 ${seatData.bestSeat?.seatNo ?? ''}번입니다.`
        setMessage(msg)
        speak(msg)
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

  // 처음으로 돌아가기 (초기화)
  function reset() {
    setStage('chat')
    setBus(null)
    setSeat(null)
    setSelectedSeat(null)
    setSelecting(false)
    setSessionId(null)
    setMessage('어디로 가실 예정인지 말씀해 주세요.')
  }

  // 티켓 화면
  if (stage === 'ticket' && bus) {
    return <BusTicket bus={bus} seatNo={finalSeatNo} onClose={reset} />
  }

  // 결제 화면
  if (stage === 'payment' && bus) {
    return (
      <div className="conversation-panel">
        <h3>결제 확인</h3>
        <div style={{ marginTop: '16px', fontSize: '1.1rem', lineHeight: 1.8 }}>
          <div>{bus.departure} → {bus.arrival}</div>
          <div>{formatTime(bus.departureTime)} 출발 · {bus.grade}</div>
          <div>좌석: <b>{finalSeatNo}</b></div>
          <div>결제 금액: <b>{bus.charge.toLocaleString()}원</b></div>
        </div>
        <div className="panel-actions" style={{ marginTop: '20px' }}>
          <button className="primary-button" type="button" onClick={() => setStage('chat')}>
            뒤로
          </button>
          <button className="primary-button" type="button" onClick={() => setStage('ticket')}>
            결제 확인
          </button>
        </div>
      </div>
    )
  }

  // 대화 화면 (기본)
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
            {formatTime(bus.departureTime)} 출발 · {bus.grade} · {bus.charge.toLocaleString()}원
            {' '}({bus.departure} → {bus.arrival})
          </p>
        </div>
      )}

      {seat && seat.bestSeat && (
        <div style={{ marginTop: '20px' }}>
          <h3>추천 좌석: {finalSeatNo}</h3>
          <ul>
            {seat.reasons.map((reason, i) => (
              <li key={i}>{reason}</li>
            ))}
          </ul>

          {!selecting && (
            <button
              type="button"
              className="primary-button"
              onClick={() => setSelecting(true)}
              style={{ marginTop: '8px' }}
            >
              다른 좌석 선택하기
            </button>
          )}

          {selecting && (
            <p style={{ color: '#2563eb', marginTop: '8px' }}>
              앉고 싶은 좌석을 눌러주세요.
            </p>
          )}

          <SeatMap
            seats={seat.allSeats}
            recommendedNo={seat.bestSeat.seatNo}
            alternativeNos={seat.alternatives.map((s) => s.seatNo)}
            selectedNo={selectedSeat ?? undefined}
            onSelect={selecting ? (s) => setSelectedSeat(s.seatNo) : undefined}
          />

          <button
            type="button"
            className="primary-button"
            onClick={() => setStage('payment')}
            style={{ marginTop: '16px', fontSize: '1.1rem' }}
          >
            결제하기
          </button>
        </div>
      )}
    </div>
  )
}