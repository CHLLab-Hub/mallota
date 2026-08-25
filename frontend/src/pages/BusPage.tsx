import { recommendSeat } from '../api/seatApi'
import { useAppState } from '../features/conversation/AppState'
import type { BusSchedule } from '../features/conversation/types'
import './HomePage.css'

function formatTime(raw: string): string {
  if (!raw || raw.length < 12) return raw
  const hour = parseInt(raw.substring(8, 10), 10)
  const minute = raw.substring(10, 12)
  const period = hour < 12 ? '오전' : '오후'
  const h = hour === 0 ? 12 : hour > 12 ? hour - 12 : hour
  return minute === '00' ? `${period} ${h}시` : `${period} ${h}시 ${minute}분`
}

export function BusPage() {
  const { buses, setSelectedBus, setSeat, setScreen } = useAppState()

  async function chooseBus(bus: BusSchedule) {
    setSelectedBus(bus)
    // 좌석 추천 받기
    try {
      const seatData = await recommendSeat({
        seatPreferences: [],
        accessibilityNeeds: [],
        busGrade: bus.grade,
      })
      setSeat(seatData)
      setScreen('seat')
    } catch (e) {
      alert('좌석 정보를 불러오지 못했습니다.')
    }
  }

  return (
    <div className="phone-frame">
      <header className="home-header">
        <button type="button" className="info-button" onClick={() => setScreen('home')}>
          ← 뒤로
        </button>
      </header>

      <h1 className="home-title" style={{ fontSize: '1.4rem' }}>
        추천 버스를 골라주세요
      </h1>

      <div className="home-body">
        {buses.length === 0 ? (
          <p>추천할 버스가 없습니다.</p>
        ) : (
          buses.map((bus, i) => (
            <button
              key={i}
              type="button"
              onClick={() => chooseBus(bus)}
              style={{
                width: '100%',
                textAlign: 'left',
                background: '#fff',
                border: '2px solid #f0e6d8',
                borderRadius: '16px',
                padding: '18px',
                marginBottom: '12px',
                cursor: 'pointer',
              }}
            >
              <div style={{ fontSize: '1.2rem', fontWeight: 800, color: '#2b2320' }}>
                {bus.departure} → {bus.arrival}
              </div>
              <div style={{ fontSize: '1.1rem', color: '#f07f21', marginTop: '6px' }}>
                {formatTime(bus.departureTime)} 출발 · {formatTime(bus.arrivalTime)} 도착
              </div>
              <div style={{ fontSize: '1rem', color: '#58665f', marginTop: '4px' }}>
                {bus.grade} · {bus.charge.toLocaleString()}원
              </div>
            </button>
          ))
        )}
      </div>
    </div>
  )
}