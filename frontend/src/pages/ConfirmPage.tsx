import { useEffect, useRef, useState } from 'react'
import { createBooking, getBookingOwnerId } from '../api/bookingApi'
import { useAppState } from '../features/conversation/AppState'
import { VoicePanel, speak } from '../features/conversation/VoicePanel'
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
    setScreen, addMessage, addBooking, resetMessages,
    setSelectedBus, setSeat, setSelectedSeatNo, setSessionId,
    passengers,
  } = useAppState()

  const announced = useRef(false)
  const [isPaying, setIsPaying] = useState(false)

  function appSay(t: string) {
    addMessage('app', t)
    speak(t)
  }

  const seatNo = selectedSeatNo ?? seat?.bestSeat?.seatNo ?? ''
  const totalFare = selectedBus ? selectedBus.charge * passengers : 0

  // 화면 뜰 때 승차권 안내
  useEffect(() => {
    if (selectedBus && !announced.current) {
      announced.current = true
      appSay(`${selectedBus.departure}에서 ${selectedBus.arrival}로 가는 ${formatTime(selectedBus.departureTime)} 출발 버스가 준비되었습니다. 좌석은 ${seatNo}번이고, ${passengers}인 총 요금은 ${totalFare.toLocaleString()}원입니다. 결제할까요?`)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function pay() {
    if (!selectedBus || isPaying) return
    setIsPaying(true)
    try {
      const booking = await createBooking({
        ownerId: getBookingOwnerId(),
        bus: selectedBus,
        seatNo,
        passengers,
        totalFare,
      })
      addBooking(booking)
      appSay('결제가 완료되었습니다. 안전한 여행 되세요.')
      setTimeout(() => {
        setSelectedBus(null)
        setSeat(null)
        setSelectedSeatNo(null)
        setSessionId(null)
        resetMessages()
        setScreen('home')
      }, 2000)
    } catch {
      appSay('예매 정보를 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.')
    } finally {
      setIsPaying(false)
    }
  }

  function handleUserSpeak(text: string) {
    addMessage('user', text)
    if (text.includes('결제') || text.includes('네') || text.includes('할게') || text.includes('좋아') || text.includes('그래') || text.includes('예')) {
      pay()
    } else if (text.includes('취소') || text.includes('아니') || text.includes('뒤로')) {
      setScreen('seat')
    } else {
      appSay('결제하시려면 결제할게요, 취소하시려면 취소라고 말씀해 주세요.')
    }
  }

  if (!selectedBus) {
    return (
      <div className="phone-frame">
        <p>예약 정보가 없습니다.</p>
        <button className="send-button" onClick={() => setScreen('home')}>홈으로</button>
      </div>
    )
  }

  return (
    <div className="phone-frame">
      <header className="home-header">
        <button type="button" className="info-button" onClick={() => setScreen('seat')}>
          ← 뒤로
        </button>
      </header>

      <h1 className="home-title" style={{ fontSize: '1.4rem' }}>예약 확인</h1>

      <div className="home-body">
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
            <div><span style={{ color: '#58665f' }}>인원 </span><b>{passengers}명</b></div>
            <div><span style={{ color: '#58665f' }}>1인 요금 </span><b>{selectedBus.charge.toLocaleString()}원</b></div>
            <div style={{ gridColumn: '1 / -1' }}><span style={{ color: '#58665f' }}>총 요금 </span><b style={{ color: '#f07f21' }}>{totalFare.toLocaleString()}원</b></div>
          </div>
        </div>

        <button type="button" className="send-button" onClick={pay} disabled={isPaying} style={{ marginTop: '20px' }}>
          {isPaying ? '예매 저장 중...' : '결제하기'}
        </button>

        <VoicePanel onUserSpeak={handleUserSpeak} />
      </div>
    </div>
  )
}
