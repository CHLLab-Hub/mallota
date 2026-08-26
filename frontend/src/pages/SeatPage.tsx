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
  const [manualSeatNos, setManualSeatNos] = useState<string[]>([])
  const [seatHint, setSeatHint] = useState<string | null>(null)

  function appSay(t: string) {
    addMessage('app', t)
    speak(t)
  }

  // 백엔드가 함께 추천한 좌석 묶음(2/3/4인)이다. 직접 선택을 시작하면 이 묶음 대신 한 자리씩 고른다.
  const hasGroup = Boolean(seat?.adjacentPair && seat.alternatives.length > 0)
  const groupSeats = hasGroup && seat?.bestSeat ? [seat.bestSeat, ...seat.alternatives] : []
  const groupSize = groupSeats.length
  const requiredSeatCount = Math.max(passengers, 1)

  function startManualSelection() {
    setSelecting(true)
    setManualSeatNos([])
    setSelectedSeatNo(null)
    setSeatHint(`${requiredSeatCount}명 중 첫 번째 좌석을 눌러 주세요. 한 자리씩 따로 선택할 수 있어요.`)
  }

  /**
   * 직접 선택은 추천 연석 규칙을 강제하지 않는다.
   * 인원수만큼 채워진 뒤 다른 좌석을 누르면 가장 먼저 고른 좌석부터 교체해, 순서대로 바꿀 수 있다.
   */
  function selectSeatIndividually(clicked: Seat) {
    let next: string[]
    if (manualSeatNos.includes(clicked.seatNo)) {
      next = manualSeatNos.filter((seatNo) => seatNo !== clicked.seatNo)
    } else if (manualSeatNos.length < requiredSeatCount) {
      next = [...manualSeatNos, clicked.seatNo]
    } else {
      next = [...manualSeatNos.slice(1), clicked.seatNo]
    }

    setManualSeatNos(next)
    setSelectedSeatNo(next.length > 0 ? next.join(', ') : null)
    if (next.length < requiredSeatCount) {
      setSeatHint(`${requiredSeatCount}명 중 ${next.length}자리 선택됨: 다음 좌석을 눌러 주세요.`)
    } else {
      setSeatHint(`${next.join(', ')} 선택 완료. 다른 좌석을 누르면 가장 먼저 고른 좌석부터 교체됩니다.`)
    }
  }

  // 음성으로 창가/통로를 고르는 경우에는 기존처럼 함께 추천 가능한 묶음을 우선 고른다.
  function selectSuggestedGroupFrom(clicked: Seat) {
    if (!hasGroup) {
      setSelectedSeatNo(clicked.seatNo)
      setSeatHint(null)
      return true
    }
    const group = findSeatGroup(seat?.allSeats ?? [], clicked, groupSize)
    if (!group) {
      setSeatHint(`${clicked.seatNo}번은 함께 추천할 자리가 없어요. 화면에서 한 자리씩 직접 선택해 주세요.`)
      return false
    }
    setSelectedSeatNo(formatSeats(group))
    setSeatHint(null)
    return true
  }

  function finalSeatNoFor(clicked: Seat): string {
    if (!hasGroup) return clicked.seatNo
    const group = findSeatGroup(seat?.allSeats ?? [], clicked, groupSize)
    return group ? formatSeats(group) : clicked.seatNo
  }

  function proceedToConfirmation() {
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
  const selectedForMap = manualSeatNos.length > 0 ? manualSeatNos.join(', ') : (selectedSeatNo ?? undefined)

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
          {hasGroup && <span style={{ fontSize: '0.9rem', color: '#58665f' }}> (함께 배정된 {groupSize}자리)</span>}
        </p>
        <ul>
          {seat.reasons.map((r, i) => (<li key={i}>{r}</li>))}
        </ul>

        {!selecting ? (
          <button type="button" className="send-button" onClick={startManualSelection} style={{ marginBottom: '12px' }}>
            직접 좌석 선택하기
          </button>
        ) : (
          <p style={{ color: '#f07f21' }}>
            좌석을 한 자리씩 눌러 주세요. {requiredSeatCount}명 중 {manualSeatNos.length}자리 선택됨
          </p>
        )}

        {seatHint && <p style={{ color: '#b45309' }}>{seatHint}</p>}

        <SeatMap
          seats={seat.allSeats}
          recommendedNo={hasGroup ? formatSeats(groupSeats) : seat.bestSeat.seatNo}
          alternativeNos={hasGroup ? [] : seat.alternatives.map((s) => s.seatNo)}
          selectedNo={selectedForMap}
          onSelect={selecting ? selectSeatIndividually : undefined}
        />

        <button type="button" className="send-button" onClick={proceedToConfirmation} style={{ marginTop: '16px' }}>
          이 좌석으로 예약하기
        </button>

        <VoicePanel onUserSpeak={handleUserSpeak} compact />
      </div>
    </div>
  )
}
