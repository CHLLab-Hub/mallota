import { useAppState } from '../features/conversation/AppState'
import { BottomTab } from './BottomTab'
import './HomePage.css'

function formatTime(raw: string): string {
  if (!raw || raw.length < 12) return raw
  const hour = parseInt(raw.substring(8, 10), 10)
  const minute = raw.substring(10, 12)
  const period = hour < 12 ? '오전' : '오후'
  const h = hour === 0 ? 12 : hour > 12 ? hour - 12 : hour
  return `${period} ${h}:${minute}`
}

export function HistoryPage() {
  const { bookings, removeBooking } = useAppState()

  function cancel(id: string) {
    removeBooking(id)
    alert('예매가 취소되었습니다.')
  }

  return (
    <div className="phone-frame">
      <h1 className="home-title" style={{ fontSize: '1.5rem' }}>예매 내역</h1>

      <div className="home-body">
        {bookings.length === 0 ? (
          <p style={{ color: '#58665f' }}>예매 내역이 없습니다.</p>
        ) : (
          bookings.map((b) => (
            <div
              key={b.id}
              style={{
                border: '2px solid #f0e6d8',
                borderRadius: '16px',
                padding: '18px',
                marginBottom: '12px',
                background: '#fff',
              }}
            >
              <div style={{ fontSize: '1.2rem', fontWeight: 800 }}>
                {b.bus.departure} → {b.bus.arrival}
              </div>
              <div style={{ color: '#f07f21', marginTop: '4px' }}>
                {formatTime(b.bus.departureTime)} 출발 · 좌석 {b.seatNo}
              </div>
              <div style={{ color: '#58665f', marginTop: '4px' }}>
                {b.bus.grade} · {b.bus.charge.toLocaleString()}원
              </div>
              <button
                type="button"
                onClick={() => cancel(b.id)}
                style={{
                  marginTop: '12px',
                  padding: '8px 16px',
                  border: '1px solid #e23b3b',
                  borderRadius: '10px',
                  background: '#fff',
                  color: '#e23b3b',
                  fontWeight: 700,
                  cursor: 'pointer',
                }}
              >
                예매 취소
              </button>
            </div>
          ))
        )}
      </div>

      <BottomTab />
    </div>
  )
}