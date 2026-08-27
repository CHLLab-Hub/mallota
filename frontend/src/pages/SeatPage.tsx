import { useState } from 'react'
import { SeatMap, findSeatGroup, formatSeats } from '../features/conversation/SeatMap'
import { useAppState } from '../features/conversation/AppState'
import { VoicePanel, speak } from '../features/conversation/VoicePanel'
import type { Seat } from '../features/conversation/types'
import './HomePage.css'

function splitSeatNos(value: string | null): string[] {
  return value ? value.split(',').map((seatNo) => seatNo.trim()).filter(Boolean) : []
}

export function SeatPage() {
  const {
    seat, selectedSeatNo, setSelectedSeatNo, setScreen, addMessage, passengers,
  } = useAppState()
  const [selecting, setSelecting] = useState(false)
  const [seatHint, setSeatHint] = useState<string | null>(null)
  // 묶어서(나란히/앞뒤) 앉을 자리를 고르는 중인지, 각자 따로 앉을 자리를 한 명씩 고르는 중인지
  const [separateMode, setSeparateMode] = useState(false)
  const [manualPicks, setManualPicks] = useState<Seat[]>([])

  function appSay(t: string) {
    addMessage('app', t)
    speak(t)
  }

  // 백엔드가 함께 추천한 좌석 묶음(2/3/4인)이다. 직접 선택을 시작하면 이 묶음 대신 한 자리씩 고른다.
  const hasGroup = Boolean(seat?.adjacentPair && seat.alternatives.length > 0)
  const groupSeats = hasGroup && seat?.bestSeat ? [seat.bestSeat, ...seat.alternatives] : []
  const groupSize = groupSeats.length
  // 백엔드가 반환한 묶음 크기(groupSize) 대신 실제 인원수를 기준으로 삼아,
  // 묶음을 못 찾아 좌석이 1개만 돌아온 경우에도 인원수만큼 고르도록 한다.
  const requiredSeatCount = Math.max(passengers, 1)

  function startSelecting(separate: boolean) {
    setSelecting(true)
    setSeparateMode(separate)
    setManualPicks([])
    setSeatHint(null)
    setSelectedSeatNo(null)
  }

  // 두 분 이상이면 좌석을 바꿀 때도 함께 앉으실 나머지 자리까지 같은 모양으로 골라야 한다
  function selectSuggestedGroupFrom(clicked: Seat) {
    if (!hasGroup) {
      setSelectedSeatNo(clicked.seatNo)
      setSeatHint(null)
      return true
    }
    const group = findSeatGroup(seat?.allSeats ?? [], clicked, groupSize)
    if (!group) {
      // 이 버스에 애초에 나란히/앞뒤로 붙은 자리가 하나도 없으면 "따로따로 앉기" 모드로 바꾸라고 안내
      setSeatHint(`${clicked.seatNo}번은 함께 앉으실 나머지 자리가 없어요. 다른 자리를 눌러보시거나, "따로따로 앉을 자리 고르기"를 이용해 주세요.`)
      return false
    }
    setSelectedSeatNo(formatSeats(group))
    setSeatHint(null)
    return true
  }

  // 나란히/앞뒤가 아니어도, 인원수만큼 한 분씩 원하는 자리를 따로따로 고를 수 있게 함
  function toggleManualPick(clicked: Seat) {
    setManualPicks((prev) => {
      const already = prev.some((s) => s.seatNo === clicked.seatNo)
      const next = already
        ? prev.filter((s) => s.seatNo !== clicked.seatNo)
        : prev.length >= requiredSeatCount
          ? [...prev.slice(1), clicked]
          : [...prev, clicked]

      setSelectedSeatNo(next.length > 0 ? formatSeats(next) : null)
      setSeatHint(next.length < requiredSeatCount ? `${next.length}/${requiredSeatCount}명 자리를 고르셨어요. 나머지 분의 자리도 눌러주세요.` : null)
      return next
    })
  }

  const readyToConfirm = !selecting || !separateMode || manualPicks.length === requiredSeatCount

  function finalSeatNoFor(clicked: Seat): string {
    if (!hasGroup) return clicked.seatNo
    const group = findSeatGroup(seat?.allSeats ?? [], clicked, groupSize)
    return group ? formatSeats(group) : clicked.seatNo
  }

  function proceedToConfirmation() {
    if (!readyToConfirm) return
    const defaultSeatNos = hasGroup ? groupSeats.map((s) => s.seatNo) : [seat?.bestSeat?.seatNo ?? ''].filter(Boolean)
    const chosenSeatNos = splitSeatNos(selectedSeatNo)
    const seatCount = chosenSeatNos.length > 0 ? chosenSeatNos.length : defaultSeatNos.length
    if (seatCount < requiredSeatCount) {
      setSelecting(true)
      setSeatHint(`${requiredSeatCount}명 예매에는 좌석 ${requiredSeatCount}개가 필요해요. 화면에서 한 자리씩 선택해 주세요.`)
      return
    }
    setScreen('confirm')
  }

  function handleUserSpeak(text: string) {
    addMessage('user', text)
    if (!seat || !seat.bestSeat) return

    if (text.includes('추천') || text.includes('예약') || text.includes('이걸로') || text.includes('그걸로') || text.includes('네') || text.includes('좋아') || text.includes('결제')) {
      proceedToConfirmation()
    } else if (text.includes('창가') || text.includes('창문')) {
      const window = seat.allSeats.find((s) => s.side === 'WINDOW' && s.available)
      if (window && selectSuggestedGroupFrom(window)) {
        appSay(`창가 좌석 ${finalSeatNoFor(window)}으로 선택했어요.`)
      } else if (!window) {
        appSay('빈 창가 좌석이 없어요.')
      }
    } else if (text.includes('통로')) {
      const aisle = seat.allSeats.find((s) => s.side === 'AISLE' && s.available)
      if (aisle && selectSuggestedGroupFrom(aisle)) {
        appSay(`통로 좌석 ${finalSeatNoFor(aisle)}으로 선택했어요.`)
      } else if (!aisle) {
        appSay('빈 통로 좌석이 없어요.')
      }
    } else {
      appSay('추천 좌석으로 예약하거나, 창가 또는 통로를 말씀해 주세요.')
    }
  }

  if (!seat || !seat.bestSeat) {
    return (
      <div className="phone-frame">
        <p>좌석 정보가 없습니다.</p>
        <button className="send-button" onClick={() => setScreen('bus')}>뒤로</button>
      </div>
    )
  }

  const finalSeatNo = selectedSeatNo ?? (hasGroup ? formatSeats(groupSeats) : seat.bestSeat.seatNo)

  return (
    <div className="phone-frame">
      <header className="home-header">
        <button type="button" className="info-button" onClick={() => setScreen('bus')}>
          ← 뒤로
        </button>
      </header>

      <h1 className="home-title" style={{ fontSize: '1.4rem' }}>좌석 선택</h1>

      <div className="home-body">
        <p style={{ fontSize: '1.1rem', fontWeight: 700 }}>
          추천 좌석: <span style={{ color: '#f07f21' }}>{finalSeatNo}</span>
          {/* 붙어있는 연석일 수도, 구역만 맞춰 따로 배정된 자리일 수도 있어 "연석"이라 단정하지 않는다 */}
          {hasGroup && <span style={{ fontSize: '0.9rem', color: '#58665f' }}> ({groupSize}자리 배정)</span>}
        </p>
        <ul>
          {seat.reasons.map((r, i) => (<li key={i}>{r}</li>))}
        </ul>

        {!selecting ? (
          <div style={{ display: 'flex', gap: '8px', marginBottom: '12px', flexWrap: 'wrap' }}>
            <button type="button" className="send-button" onClick={() => startSelecting(false)}>
              다른 좌석 선택하기
            </button>
            {hasGroup && (
              <button type="button" className="send-button" onClick={() => startSelecting(true)}>
                따로따로 앉을 자리 고르기
              </button>
            )}
          </div>
        ) : (
          <p style={{ color: '#f07f21' }}>
            {separateMode
              ? `${requiredSeatCount}명이 각자 앉으실 자리를 한 분씩 눌러주세요.`
              : hasGroup
                ? `앉고 싶은 자리를 눌러주세요. 나머지 ${groupSize}자리가 함께 선택됩니다.`
                : '앉고 싶은 좌석을 눌러주세요.'}
          </p>
        )}

        {seatHint && <p style={{ color: '#b45309' }}>{seatHint}</p>}

        <SeatMap
          seats={seat.allSeats}
          recommendedNo={hasGroup ? formatSeats(groupSeats) : seat.bestSeat.seatNo}
          alternativeNos={seat.tiedAlternativeSeats.map((s) => s.seatNo)}
          selectedNo={selectedSeatNo ?? undefined}
          onSelect={selecting ? (s) => (separateMode ? toggleManualPick(s) : selectSuggestedGroupFrom(s)) : undefined}
        />

        <button
          type="button"
          className="send-button"
          onClick={proceedToConfirmation}
          disabled={!readyToConfirm}
          style={{ marginTop: '16px', opacity: readyToConfirm ? 1 : 0.5 }}
        >
          이 좌석으로 예약하기
        </button>

        <VoicePanel onUserSpeak={handleUserSpeak} compact />
      </div>
    </div>
  )
}
