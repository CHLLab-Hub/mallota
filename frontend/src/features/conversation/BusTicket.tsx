import type { BusSchedule } from './types'

interface BusTicketProps {
  bus: BusSchedule
  seatNo: string
  onClose: () => void
}

// TAGO 시간(202608260100) → "오전 6:00"
function formatTime(raw: string): string {
  if (!raw || raw.length < 12) return raw
  const hour = parseInt(raw.substring(8, 10), 10)
  const minute = raw.substring(10, 12)
  const period = hour < 12 ? '오전' : '오후'
  const h = hour === 0 ? 12 : hour > 12 ? hour - 12 : hour
  return `${period} ${h}:${minute}`
}

// TAGO 날짜(20260826...) → "2026-08-26"
function formatDate(raw: string): string {
  if (!raw || raw.length < 8) return raw
  return `${raw.substring(0, 4)}-${raw.substring(4, 6)}-${raw.substring(6, 8)}`
}

export function BusTicket({ bus, seatNo, onClose }: BusTicketProps) {
  return (
    <div style={{ marginTop: '20px' }}>
      <div
        style={{
          border: '2px dashed #0f766e',
          borderRadius: '16px',
          padding: '24px',
          background: '#f0fdfa',
          maxWidth: '420px',
        }}
      >
        <div style={{ textAlign: 'center', fontSize: '1.4rem', fontWeight: 'bold', color: '#0f766e' }}>
          🎫 승차권
        </div>
        <div style={{ borderTop: '1px solid #99f6e4', margin: '16px 0' }} />

        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
          <div style={{ textAlign: 'center' }}>
            <div style={{ fontSize: '0.85rem', color: '#64748b' }}>출발</div>
            <div style={{ fontSize: '1.3rem', fontWeight: 'bold' }}>{bus.departure}</div>
            <div style={{ fontSize: '1.1rem', color: '#0f766e' }}>{formatTime(bus.departureTime)}</div>
          </div>
          <div style={{ fontSize: '1.5rem' }}>→</div>
          <div style={{ textAlign: 'center' }}>
            <div style={{ fontSize: '0.85rem', color: '#64748b' }}>도착</div>
            <div style={{ fontSize: '1.3rem', fontWeight: 'bold' }}>{bus.arrival}</div>
            <div style={{ fontSize: '1.1rem', color: '#0f766e' }}>{formatTime(bus.arrivalTime)}</div>
          </div>
        </div>

        <div style={{ borderTop: '1px dashed #99f6e4', margin: '16px 0' }} />

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', fontSize: '1rem' }}>
          <div>
            <span style={{ color: '#64748b' }}>날짜 </span>
            {formatDate(bus.departureTime)}
          </div>
          <div>
            <span style={{ color: '#64748b' }}>등급 </span>
            {bus.grade}
          </div>
          <div>
            <span style={{ color: '#64748b' }}>좌석 </span>
            <span style={{ fontWeight: 'bold', color: '#0f766e' }}>{seatNo}</span>
          </div>
          <div>
            <span style={{ color: '#64748b' }}>요금 </span>
            <span style={{ fontWeight: 'bold' }}>{bus.charge.toLocaleString()}원</span>
          </div>
        </div>

        <div style={{ borderTop: '1px solid #99f6e4', margin: '16px 0' }} />
        <div style={{ textAlign: 'center', fontSize: '0.85rem', color: '#64748b' }}>
          안전한 여행 되세요 🚌
        </div>
      </div>

      <button
        type="button"
        className="primary-button"
        onClick={onClose}
        style={{ marginTop: '16px' }}
      >
        처음으로
      </button>
    </div>
  )
}