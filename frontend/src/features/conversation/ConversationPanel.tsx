import { useState } from 'react'
import { parseConversation, searchBuses } from '../../api/conversationApi'
import { recommendSeat } from '../../api/seatApi'
import { speechToText, textToSpeech } from '../../api/voiceApi'
import { ApiError } from '../../api/httpClient'
import { useVoiceRecorder } from './useVoiceRecorder'
import { SeatMap, findSeatGroup, formatSeats } from './SeatMap'
import { BusTicket } from './BusTicket'
import type {
  ConversationSessionResult,
  BusSchedule,
  Seat,
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

const WEEKDAY_NAMES = ['일', '월', '화', '수', '목', '금', '토']

// TAGO 시간(202608280630) → "모레(8월 28일 금요일)"
function formatDate(raw: string): string {
  if (!raw || raw.length < 8) return ''
  const year = parseInt(raw.substring(0, 4), 10)
  const month = parseInt(raw.substring(4, 6), 10)
  const day = parseInt(raw.substring(6, 8), 10)
  const target = new Date(year, month - 1, day)
  if (Number.isNaN(target.getTime())) return ''

  const label = `${month}월 ${day}일 ${WEEKDAY_NAMES[target.getDay()]}요일`

  // 오늘/내일/모레는 어르신이 알아듣기 쉽도록 앞에 붙여 드린다
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const diffDays = Math.round((target.getTime() - today.getTime()) / 86400000)
  const relative = diffDays === 0 ? '오늘' : diffDays === 1 ? '내일' : diffDays === 2 ? '모레' : null

  return relative ? `${relative}(${label})` : label
}

// "모레(8월 28일 금요일) 오전 6시 30분"
function formatDateTime(raw: string): string {
  const date = formatDate(raw)
  return date ? `${date} ${formatTime(raw)}` : formatTime(raw)
}

// 방금 보여준 버스(current)보다 출발 시각이 이른 버스 중, 가장 가까운(=가장 늦은) 것을 고른다.
// departureTime은 "yyyyMMddHHmm" 형식이라 같은 날짜끼리는 문자열 비교로 시간 순서를 알 수 있다.
function findEarlierBus(buses: BusSchedule[], current: BusSchedule): BusSchedule | null {
  const earlierCandidates = buses.filter((b) => b.departureTime < current.departureTime)
  if (earlierCandidates.length === 0) return null
  return earlierCandidates.reduce((latest, b) => (b.departureTime > latest.departureTime ? b : latest))
}

// findEarlierBus의 대칭: 방금 보여준 버스보다 출발 시각이 늦은 버스 중 가장 가까운(=가장 이른) 것을 고른다.
function findLaterBus(buses: BusSchedule[], current: BusSchedule): BusSchedule | null {
  const laterCandidates = buses.filter((b) => b.departureTime > current.departureTime)
  if (laterCandidates.length === 0) return null
  return laterCandidates.reduce((earliest, b) => (b.departureTime < earliest.departureTime ? b : earliest))
}

// 좌석 재추천이 필요한지 판단하는 기준(버스 + 좌석 선호/배려/인원)을 하나의 값으로 묶는다.
function seatRecommendationKey(bus: BusSchedule, session: ConversationSessionResult): string {
  return JSON.stringify({
    bus: `${bus.routeId}-${bus.departureTime}`,
    seatPreferences: session.seatPreferences,
    accessibilityNeeds: session.accessibilityNeeds,
    passengers: session.passengers,
  })
}

type Stage = 'chat' | 'payment' | 'ticket'

export function ConversationPanel() {
  const [text, setText] = useState('')
  const [sessionId, setSessionId] = useState<string | null>(null)
  const [message, setMessage] = useState("어디에서 출발해서 어디로 가시나요? 출발지와 도착지를 말씀해 주세요.")
  const [loading, setLoading] = useState(false)
  const [transcribing, setTranscribing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [bus, setBus] = useState<BusSchedule | null>(null)
  const [seat, setSeat] = useState<SeatRecommendation | null>(null)
  // 마지막 좌석 추천을 만든 조건(버스+좌석 선호/배려/인원). 조건이 그대로면 좌석을 다시 랜덤으로
  // 뽑지 않고 그대로 보여준다 (안 그러면 "더 빠른 거 없어?"처럼 버스와 무관한 질문에도 좌석 배치가
  // 매번 랜덤하게 다시 섞여서 마치 좌석만 바뀌는 것처럼 보인다).
  const [seatSignature, setSeatSignature] = useState<string | null>(null)
  const [selectedSeat, setSelectedSeat] = useState<string | null>(null)
  const [selecting, setSelecting] = useState(false)
  const [seatHint, setSeatHint] = useState<string | null>(null)
  const [stage, setStage] = useState<Stage>('chat')

  const { recording, startRecording, stopRecording } = useVoiceRecorder()

  // 백엔드가 실제 그룹 좌석(2/3/4인)을 배정했을 때만 여러 자리로 표기한다 (동률 대안과 구분)
  const hasGroup = Boolean(seat?.adjacentPair && seat.alternatives.length > 0)
  const groupSeats = hasGroup && seat?.bestSeat ? [seat.bestSeat, ...seat.alternatives] : []
  const groupSize = groupSeats.length
  const finalSeatNo = selectedSeat ?? (hasGroup
    ? formatSeats(groupSeats)
    : (seat?.bestSeat?.seatNo ?? ''));

  // 두 분 이상이면 좌석을 바꿀 때도 함께 앉으실 나머지 자리까지 같은 모양으로 골라야 한다
  function handleSeatSelect(clicked: Seat) {
    if (!hasGroup) {
      setSelectedSeat(clicked.seatNo)
      setSeatHint(null)
      return
    }

    const group = findSeatGroup(seat?.allSeats ?? [], clicked, groupSize)
    if (!group) {
      setSeatHint(`${clicked.seatNo}번은 함께 앉으실 나머지 자리가 없어요. 다른 자리를 눌러주세요.`)
      return
    }
    setSelectedSeat(formatSeats(group))
    setSeatHint(null)
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
    setSeatHint(null)
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
        setSeatSignature(null)
        return
      }

      // 필수값이 비어있는 경우 (백업)
      if (!session.departure || !session.arrival || !session.date) {
        const question = buildQuestion(session)
        setMessage(question)
        speak(question)
        setBus(null)
        setSeat(null)
        setSeatSignature(null)
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
        setSeatSignature(null)
      } else {
        let chosenBus = buses[0]
        let boundaryMessage: string | null = null

        // "더 빠른/더 늦은 거 없어?"는 목록의 첫 버스를 다시 보여달라는 뜻이 아니라, 방금 안내한
        // 버스보다 더 이르거나 늦은 시간을 찾아달라는 상대적 요청이다.
        const sameRouteAsBefore = Boolean(bus) && bus!.departure === session.departure && bus!.arrival === session.arrival
        if (session.wantsEarlierBus && sameRouteAsBefore) {
          const earlier = findEarlierBus(buses, bus!)
          if (earlier) {
            chosenBus = earlier
          } else {
            chosenBus = bus!
            boundaryMessage = '죄송해요, 이미 조건에 맞는 가장 이른 시간의 버스예요. '
          }
        } else if (session.wantsLaterBus && sameRouteAsBefore) {
          const later = findLaterBus(buses, bus!)
          if (later) {
            chosenBus = later
          } else {
            chosenBus = bus!
            boundaryMessage = '죄송해요, 이미 조건에 맞는 가장 늦은 시간의 버스예요. '
          }
        }
        setBus(chosenBus)

        // 버스와 좌석 선호/배려/인원이 지난번과 똑같으면 좌석을 다시 뽑지 않고 그대로 유지한다.
        const nextSignature = seatRecommendationKey(chosenBus, session)
        let seatData = seat
        if (!seatData || nextSignature !== seatSignature) {
          seatData = await recommendSeat({
            seatPreferences: session.seatPreferences,
            accessibilityNeeds: session.accessibilityNeeds,
            busGrade: chosenBus.grade,
            passengers: session.passengers ?? 2,
          })
          setSeat(seatData)
          setSeatSignature(nextSignature)
        }

        const isGroup = Boolean(seatData?.adjacentPair && seatData.alternatives.length > 0)
        const seatText = isGroup && seatData?.bestSeat
          ? formatSeats([seatData.bestSeat, ...seatData.alternatives])
          : (seatData?.bestSeat?.seatNo ?? '');

        const reasonText = seatData?.reasons && seatData.reasons.length > 0 ? seatData.reasons[0] : ''
        const msg = `${boundaryMessage ?? ''}${formatDateTime(chosenBus.departureTime)} 출발 ${chosenBus.grade} 버스입니다. ${reasonText} 추천 좌석은 ${seatText}번입니다.`
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
    setSeatSignature(null)
    setSelectedSeat(null)
    setSelecting(false)
    setSeatHint(null)
    setSessionId(null)
    setMessage("어디에서 출발해서 어디로 가시나요? 출발지와 도착지를 말씀해 주세요.")
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
          <div>{formatDate(bus.departureTime)}</div>
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
            {formatDate(bus.departureTime)} {formatTime(bus.departureTime)} 출발 · {bus.grade} · {bus.charge.toLocaleString()}원
            {' '}({bus.departure} → {bus.arrival})
          </p>
        </div>
      )}

      {seat && seat.bestSeat && (
        <div style={{ marginTop: '20px' }}>
          <h3>
            추천 좌석: {finalSeatNo}
            {hasGroup && <span style={{ fontSize: '0.9rem', color: '#64748b' }}> (연석 {groupSize}자리)</span>}
          </h3>
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
              {hasGroup
                ? `앉고 싶은 자리를 눌러주세요. 나머지 ${groupSize}자리가 함께 선택됩니다.`
                : '앉고 싶은 좌석을 눌러주세요.'}
            </p>
          )}

          {seatHint && <p style={{ color: '#b45309', marginTop: '4px' }}>{seatHint}</p>}

          <SeatMap
            seats={seat.allSeats}
            recommendedNo={hasGroup ? formatSeats(groupSeats) : seat.bestSeat.seatNo}
            alternativeNos={hasGroup ? [] : seat.alternatives.map((s) => s.seatNo)}
            selectedNo={selectedSeat ?? undefined}
            onSelect={selecting ? handleSeatSelect : undefined}
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