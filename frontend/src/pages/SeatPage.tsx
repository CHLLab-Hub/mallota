import { useState } from 'react'
import { SeatMap } from '../features/conversation/SeatMap'
import { useAppState } from '../features/conversation/AppState'
import './HomePage.css'

export function SeatPage() {
  const { seat, selectedBus, selectedSeatNo, setSelectedSeatNo, setScreen } = useAppState()
  const [selecting, setSelecting] = useState(false)

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

      <h1 className="home-title" style={{ fontSize: '1.4rem' }}>
        좌석 선택
      </h1>

      <div className="home-body">
        <p style={{ fontSize: '1.1rem', fontWeight: 700 }}>
          추천 좌석: <span style={{ color: '#f07f21' }}>{finalSeatNo}</span>
        </p>
        <ul>
          {seat.reasons.map((r, i) => (<li key={i}>{r}</li>))}
        </ul>

        {!selecting ? (
          <button
            type="button"
            className="send-button"
            onClick={() => setSelecting(true)}
            style={{ marginBottom: '12px' }}
          >
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

        <button
          type="button"
          className="send-button"
          onClick={() => setScreen('confirm')}
          style={{ marginTop: '16px' }}
        >
          이 좌석으로 예약하기
        </button>
      </div>
    </div>
  )
}