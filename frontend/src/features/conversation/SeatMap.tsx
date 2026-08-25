import type { Seat } from './types'

interface SeatMapProps {
  seats: Seat[]
  recommendedNo: string
  alternativeNos: string[]
  selectedNo?: string
  onSelect?: (seat: Seat) => void
}

export function SeatMap({ seats, recommendedNo, alternativeNos, selectedNo, onSelect }: SeatMapProps) {
  if (!seats || seats.length === 0) return null

  // 줄(row)별로 좌석 묶기
  const rows = new Map<number, Seat[]>()
  for (const seat of seats) {
    if (!rows.has(seat.row)) rows.set(seat.row, [])
    rows.get(seat.row)!.push(seat)
  }
  const sortedRows = Array.from(rows.entries()).sort((a, b) => a[0] - b[0])

  // 전체 격자의 열 수 (제일 큰 column 값)
  const totalCols = Math.max(...seats.map((s) => s.column))

  function seatColor(seat: Seat): string {
    if (!seat.available) return '#cbd5e1'
    if (seat.seatNo === selectedNo) return '#2563eb'
    if (seat.seatNo === recommendedNo) return '#16a34a'
    if (alternativeNos && alternativeNos.includes(seat.seatNo)) return '#86efac'
    return '#f1f5f9'
  }

  return (
    <div style={{ marginTop: '20px' }}>
      <h3>좌석 배치도</h3>
      <div style={{ textAlign: 'right', marginBottom: '8px', color: '#64748b' }}>🚍 앞 (운전석)</div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', alignItems: 'center' }}>
        {sortedRows.map(([rowNum, rowSeats]) => {
          // column 위치로 좌석 찾기 (없으면 빈 칸)
          const byColumn = new Map<number, Seat>()
          for (const s of rowSeats) byColumn.set(s.column, s)

          return (
            <div key={rowNum} style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
              {Array.from({ length: totalCols }, (_, i) => i + 1).map((colPos) => {
                const seat = byColumn.get(colPos)
                if (!seat) {
                  // 빈 칸 (통로) — 좌석 자리만큼 공간 차지
                  return <div key={colPos} style={{ width: '44px', height: '44px' }} />
                }
                return (
                  <button
                    key={seat.seatNo}
                    type="button"
                    onClick={() => onSelect?.(seat)}
                    disabled={!seat.available}
                    style={{
                      width: '44px',
                      height: '44px',
                      borderRadius: '8px',
                      border: '1px solid #94a3b8',
                      background: seatColor(seat),
                      color: seat.available ? '#0f172a' : '#94a3b8',
                      fontSize: '0.8rem',
                      cursor: seat.available && onSelect ? 'pointer' : 'default',
                    }}
                  >
                    {seat.seatNo}
                  </button>
                )
              })}
            </div>
          )
        })}
      </div>

      <div style={{ marginTop: '16px', fontSize: '0.9rem', display: 'flex', gap: '20px', flexWrap: 'wrap' }}>
        <LegendItem color="#16a34a" label="추천 좌석" />
        <LegendItem color="#86efac" label="같은 조건 좌석" />
        <LegendItem color="#f1f5f9" label="빈 자리" border />
        <LegendItem color="#cbd5e1" label="예약됨" />
      </div>
    </div>
  )
}

function LegendItem({ color, label, border }: { color: string; label: string; border?: boolean }) {
  return (
    <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
      <span
        style={{
          width: '18px',
          height: '18px',
          borderRadius: '4px',
          background: color,
          border: border ? '1px solid #94a3b8' : 'none',
          display: 'inline-block',
        }}
      />
      {label}
    </span>
  )
}