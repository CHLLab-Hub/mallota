import { useState, useEffect, useRef } from 'react'
import { SeatMap } from '../features/conversation/SeatMap'
import { useAppState } from '../features/conversation/AppState'
import { VoicePanel, speak } from '../features/conversation/VoicePanel'
import './HomePage.css'


export function SeatPage() {
  const { seat, selectedSeatNo, setSelectedSeatNo, setScreen, addMessage } = useAppState()
  const [selecting, setSelecting] = useState(false)

  function appSay(t: string) {
    addMessage('app', t)
    speak(t)
  }

  function handleUserSpeak(text: string) {
    addMessage('user', text)
    if (!seat || !seat.bestSeat) return

    if (text.includes('추천') || text.includes('예약') || text.includes('이걸로') || text.includes('그걸로') || text.includes('네') || text.includes('좋아') || text.includes('결제')) {
      appSay('예약을 진행할게요.')
      setTimeout(() => setScreen('confirm'), 600)
    } else if (text.includes('창가') || text.includes('창문')) {
      const window = seat.allSeats.find((s) => s.side === 'WINDOW' && s.available)
      if (window) {
        setSelectedSeatNo(window.seatNo)
        appSay(`창가 좌석 ${window.seatNo}번으로 선택했어요.`)
      } else {
        appSay('빈 창가 좌석이 없어요.')
      }
    } else if (text.includes('통로')) {
      const aisle = seat.allSeats.find((s) => s.side === 'AISLE' && s.available)
      if (aisle) {
        setSelectedSeatNo(aisle.seatNo)
        appSay(`통로 좌석 ${aisle.seatNo}번으로 선택했어요.`)
      } else {
        appSay('빈 통로 좌석이 없어요.')
      }
    } else {
      appSay('추천 좌석으로 예약하거나, 창가 또는 통로를 말씀해 주세요.')
    }
  }

  // 화면 뜰 때 한 번 안내
  
  if (!seat || !seat.bestSeat) {
    return (
      <div className="phone-frame">
        <p>좌석 정보가 없습니다.</p>
        <button className="send-button" onClick={() => setScreen('bus')}>뒤로</button>
      </div>
    )
  }

  const finalSeatNo = selectedSeatNo ?? seat.bestSeat.seatNo

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
        </p>
        <ul>
          {seat.reasons.map((r, i) => (<li key={i}>{r}</li>))}
        </ul>

        {!selecting ? (
          <button type="button" className="send-button" onClick={() => setSelecting(true)} style={{ marginBottom: '12px' }}>
            다른 좌석 선택하기
          </button>
        ) : (
          <p style={{ color: '#f07f21' }}>앉고 싶은 좌석을 눌러주세요.</p>
        )}

        <SeatMap
          seats={seat.allSeats}
          recommendedNo={seat.bestSeat.seatNo}
          alternativeNos={seat.alternatives.map((s) => s.seatNo)}
          selectedNo={selectedSeatNo ?? undefined}
          onSelect={selecting ? (s) => setSelectedSeatNo(s.seatNo) : undefined}
        />

        <button type="button" className="send-button" onClick={() => setScreen('confirm')} style={{ marginTop: '16px' }}>
          이 좌석으로 예약하기
        </button>

        <VoicePanel onUserSpeak={handleUserSpeak} />
      </div>
    </div>
  )
}