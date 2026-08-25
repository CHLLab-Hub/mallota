import { useState } from 'react'
import { parseConversation, searchBuses } from '../../api/conversationApi'
import { recommendSeat } from '../../api/seatApi'
import { speechToText, textToSpeech } from '../../api/voiceApi'
import { ApiError } from '../../api/httpClient'
import { useVoiceRecorder } from './useVoiceRecorder'
import { SeatMap } from './SeatMap'
import { BusTicket } from './BusTicket'
import logo from '../../assets/logo.png'
import './ConversationPanel.css'
import type {
  ConversationSessionResult,
  BusSchedule,
  SeatRecommendation,
} from './types'

function buildQuestion(session: ConversationSessionResult): string {
  if (!session.departure) return '어디에서 출발하시나요?'
  if (!session.arrival) return '어디로 가시나요?'
  if (!session.date) return '언제 출발하시나요?'
  return ''
}

function formatTime(raw: string): string {
  if (!raw || raw.length < 12) return raw
  const hour = parseInt(raw.substring(8, 10), 10)
  const minute = raw.substring(10, 12)
  const period = hour < 12 ? '오전' : '오후'
  const displayHour = hour === 0 ? 12 : hour > 12 ? hour - 12 : hour
  return minute === '00' ? `${period} ${displayHour}시` : `${period} ${displayHour}시 ${minute}분`
}

function pickBusByTime(buses: BusSchedule[], timePref: string | null): BusSchedule {
  if (!timePref || timePref === 'ANY') return buses[0]
  const filtered = buses.filter((bus) => {
    const hour = parseInt(bus.departureTime.substring(8, 10), 10)
    if (timePref === 'MORNING') return hour >= 5 && hour < 12
    if (timePref === 'AFTERNOON') return hour >= 12 && hour < 18
    if (timePref === 'EVENING') return hour >= 18 || hour < 5
    return true
  })
  return filtered.length > 0 ? filtered[0] : buses[0]
}

type Stage = 'chat' | 'payment' | 'ticket'
type ChatMessage = { role: 'app' | 'user'; text: string }

export function ConversationPanel() {
  const [text, setText] = useState('')
  const [sessionId, setSessionId] = useState<string | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([
    { role: 'app', text: '어디로 가실 예정인지 큰 목소리로 말씀해 주세요.' },
  ])
  const [showInput, setShowInput] = useState(false)
  const [loading, setLoading] = useState(false)
  const [transcribing, setTranscribing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [bus, setBus] = useState<BusSchedule | null>(null)
  const [seat, setSeat] = useState<SeatRecommendation | null>(null)
  const [selectedSeat, setSelectedSeat] = useState<string | null>(null)
  const [selecting, setSelecting] = useState(false)
  const [stage, setStage] = useState<Stage>('chat')

  const { recording, startRecording, stopRecording } = useVoiceRecorder()

  const finalSeatNo = selectedSeat ?? seat?.bestSeat?.seatNo ?? ''

  // 대화에 메시지 추가
  function addMessage(role: 'app' | 'user', text: string) {
    setMessages((prev) => [...prev, { role, text }])
  }

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

  // 앱이 말하기 (말풍선 + 음성)
  function appSay(text: string) {
    addMessage('app', text)
    speak(text)
  }

  async function handleMicClick() {
    if (recording) {
      setTranscribing(true)
      setError(null)
      try {
        const audio = await stopRecording()
        const data = await speechToText(audio)
        if (data.transcript) {
          await handleSend(data.transcript)
        }
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

  async function handleSend(inputText?: string) {
    const sendText = (inputText ?? text).trim()
    if (!sendText) return

    addMessage('user', sendText) // 사용자 말풍선
    setText('')
    setLoading(true)
    setSelectedSeat(null)
    setSelecting(false)
    setError(null)

    try {
      const session: ConversationSessionResult = await parseConversation(sendText, sessionId)
      setSessionId(session.sessionId)

      if (session.state === 'COLLECTING_CONDITIONS') {
        appSay(buildQuestion(session))
        setBus(null)
        setSeat(null)
      } else {
        const buses = await searchBuses({
          departure: session.departure!,
          arrival: session.arrival!,
          date: session.date!,
        })

        if (buses.length === 0) {
          appSay('해당 조건의 버스를 찾지 못했습니다.')
          setBus(null)
          setSeat(null)
        } else {
          const chosenBus = pickBusByTime(buses, session.timePreference)
          setBus(chosenBus)

          const seatData = await recommendSeat({
            seatPreferences: session.seatPreferences,
            accessibilityNeeds: session.accessibilityNeeds,
            busGrade: chosenBus.grade,
          })
          setSeat(seatData)

          appSay(
            `${formatTime(chosenBus.departureTime)} 출발 ${chosenBus.grade} 버스를 추천합니다. 추천 좌석은 ${seatData.bestSeat?.seatNo ?? ''}번입니다.`,
          )
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

  function reset() {
    setStage('chat')
    setBus(null)
    setSeat(null)
    setSelectedSeat(null)
    setSelecting(false)
    setSessionId(null)
    setMessages([{ role: 'app', text: '어디로 가실 예정인지 큰 목소리로 말씀해 주세요.' }])
  }

  // 티켓 화면
  if (stage === 'ticket' && bus) {
    return <BusTicket bus={bus} seatNo={finalSeatNo} onClose={reset} />
  }

  // 결제 화면
  if (stage === 'payment' && bus) {
    return (
      <div>
        <h3>결제 확인</h3>
        <div style={{ marginTop: '16px', fontSize: '1.1rem', lineHeight: 1.8 }}>
          <div>{bus.departure} → {bus.arrival}</div>
          <div>{formatTime(bus.departureTime)} 출발 · {bus.grade}</div>
          <div>좌석: <b>{finalSeatNo}</b></div>
          <div>결제 금액: <b>{bus.charge.toLocaleString()}원</b></div>
        </div>
        <div style={{ display: 'flex', gap: '10px', marginTop: '20px' }}>
          <button className="send-button" type="button" onClick={() => setStage('chat')}>뒤로</button>
          <button className="send-button" type="button" onClick={() => setStage('ticket')}>결제 확인</button>
        </div>
      </div>
    )
  }

  // 대화 화면 (기본)
  return (
    <div>
      {/* 대화 말풍선 */}
      <div className="chat-container">
        {messages.map((m, i) => (
          <div key={i} className={`chat-row ${m.role}`}>
            {m.role === 'app' && <img src={logo} alt="" className="chat-avatar" />}
            <div className={`chat-bubble ${m.role}`}>{m.text}</div>
          </div>
        ))}
      </div>

      {error && <p style={{ color: 'red' }}>{error}</p>}

      {/* 버스 정보 */}
      {bus && (
        <div style={{ marginTop: '16px' }}>
          <h3>추천 버스</h3>
          <p>{formatTime(bus.departureTime)} 출발 · {bus.grade} · {bus.charge.toLocaleString()}원 ({bus.departure} → {bus.arrival})</p>
        </div>
      )}

      {/* 좌석 */}
      {seat && seat.bestSeat && (
        <div style={{ marginTop: '16px' }}>
          <h3>추천 좌석: {finalSeatNo}</h3>
          <ul>
            {seat.reasons.map((reason, i) => (<li key={i}>{reason}</li>))}
          </ul>
          {!selecting && (
            <button type="button" className="send-button" onClick={() => setSelecting(true)}>다른 좌석 선택하기</button>
          )}
          {selecting && <p style={{ color: '#f57f20' }}>앉고 싶은 좌석을 눌러주세요.</p>}
          <SeatMap
            seats={seat.allSeats}
            recommendedNo={seat.bestSeat.seatNo}
            alternativeNos={seat.alternatives.map((s) => s.seatNo)}
            selectedNo={selectedSeat ?? undefined}
            onSelect={selecting ? (s) => setSelectedSeat(s.seatNo) : undefined}
          />
          <button type="button" className="send-button" onClick={() => setStage('payment')}>결제하기</button>
        </div>
      )}

      {/* 마이크 영역 */}
      <div className="mic-area">
        <button
          type="button"
          className={`mic-button ${recording ? 'recording' : ''}`}
          onClick={handleMicClick}
          disabled={transcribing || loading}
        >
          {recording ? '⏹' : '🎤'}
        </button>
        <div className="mic-label">
          {recording ? '녹음 중... (누르면 완료)' : transcribing ? '인식 중...' : loading ? '처리 중...' : '눌러서 말하기'}
        </div>
        <button type="button" className="mic-sublabel" onClick={() => setShowInput((v) => !v)}>
          직접 글씨로 입력하기
        </button>

        {showInput && (
          <div style={{ width: '100%' }}>
            <textarea
              className="chat-input"
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder="여기에 입력하세요"
            />
            <button
              type="button"
              className="send-button"
              onClick={() => handleSend()}
              disabled={loading || !text.trim()}
            >
              보내기
            </button>
          </div>
        )}
      </div>
    </div>
  )
}