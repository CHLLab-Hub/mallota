import { useAppState } from '../features/conversation/AppState'
import './HomePage.css'

function formatTime(raw: string): string {
  if (!raw || raw.length < 12) return raw
  const hour = parseInt(raw.substring(8, 10), 10)
  const minute = raw.substring(10, 12)
  const period = hour < 12 ? '오전' : '오후'
  const h = hour === 0 ? 12 : hour > 12 ? hour - 12 : hour
  return `${period} ${h}:${minute}`
}

function formatDate(raw: string): string {
  if (!raw || raw.length < 8) return raw
  return `${raw.substring(0, 4)}.${raw.substring(4, 6)}.${raw.substring(6, 8)}`
}

export function ConfirmPage() {
  const {
    selectedBus, seat, selectedSeatNo,
    setScreen, addBooking, resetMessages,
    setSelectedBus, setSeat, setSelectedSeatNo, setSessionId,
  } = useAppState()

  if (!selectedBus) {
    return (
      <div className="phone-frame">
        <p>예약 정보가 없습니다.</p>
        <button className="send-button" onClick={() => setScreen('home')}>홈으로</button>
      </div>
    )
  }

  const seatNo = selectedSeatNo ?? seat?.bestSeat?.seatNo ?? ''

  function pay() {
    // 예매 내역에 추가
    addBooking({
      bus: selectedBus!,
      seatNo,
      id: Date.now().toString(),
    })
    alert('결제가 완료되었습니다!')
    // 초기화하고 홈으로
    setSelectedBus(null)
    setSeat(null)
    setSelectedSeatNo(null)
    setSessionId(null)
    resetMessages()
    setScreen('home')
  }

  return (
    <div className="phone-frame">
      <header className="home-header">
        <button type="button" className="info-button" onClick={() => setScreen('seat')}>
          ← 뒤로
        </button>
      </header>

      <h1 className="home-title" style={{ fontSize: '1.4rem' }}>
        예약 확인
      </h1>

      <div className="home-body">
        {/* 승차권 */}
        <div style={{
          border: '2px dashed #f07f21',
          borderRadius: '16px',
          padding: '24px',
          background: '#fff8f0',
        }}>
          <div style={{ textAlign: 'center', fontSize: '1.3rem', fontWeight: 800, color: '#f07f21' }}>
            🎫 승차권
          </div>
          <div style={{ borderTop: '1px solid #f0d5b8', margin: '16px 0' }} />

          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '16px' }}>
            <div style={{ textAlign: 'center' }}>
              <div style={{ color: '#58665f', fontSize: '0.9rem' }}>출발</div>
              <div style={{ fontSize: '1.3rem', fontWeight: 800 }}>{selectedBus.departure}</div>
              <div style={{ color: '#f07f21' }}>{formatTime(selectedBus.departureTime)}</div>
            </div>
            <div style={{ fontSize: '1.5rem', alignSelf: 'center' }}>→</div>
            <div style={{ textAlign: 'center' }}>
              <div style={{ color: '#58665f', fontSize: '0.9rem' }}>도착</div>
              <div style={{ fontSize: '1.3rem', fontWeight: 800 }}>{selectedBus.arrival}</div>
              <div style={{ color: '#f07f21' }}>{formatTime(selectedBus.arrivalTime)}</div>
            </div>
          </div>

          <div style={{ borderTop: '1px dashed #f0d5b8', margin: '16px 0' }} />
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px', fontSize: '1rem' }}>
            <div><span style={{ color: '#58665f' }}>날짜 </span>{formatDate(selectedBus.departureTime)}</div>
            <div><span style={{ color: '#58665f' }}>등급 </span>{selectedBus.grade}</div>
            <div><span style={{ color: '#58665f' }}>좌석 </span><b style={{ color: '#f07f21' }}>{seatNo}</b></div>
            <div><span style={{ color: '#58665f' }}>요금 </span><b>{selectedBus.charge.toLocaleString()}원</b></div>
          </div>
        </div>

        <button type="button" className="send-button" onClick={pay} style={{ marginTop: '20px' }}>
          결제하기
        </button>
      </div>
    </div>
  )
}