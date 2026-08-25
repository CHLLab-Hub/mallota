import type { Seat } from './types'

interface SeatMapProps {
  seats: Seat[]              // 전체 좌석
  recommendedNo: string      // 추천 좌석 번호
  alternativeNos: string[]   // 동률 대안 좌석 번호들
  selectedNo?: string        // 사용자가 고른 좌석
  onSelect?: (seat: Seat) => void  // 좌석 클릭 시
}

export function SeatMap({ seats, recommendedNo, alternativeNos, selectedNo, onSelect }: SeatMapProps) {
  if (!seats || seats.length === 0) return null

  // 줄(row)별로 좌석 묶기
  const rows = new Map<number, Seat[]>()
  for (const seat of seats) {
    if (!rows.has(seat.row)) rows.set(seat.row, [])
    rows.get(seat.row)!.push(seat)
  }
  // 줄 번호 순 정렬
  const sortedRows = Array.from(rows.entries()).sort((a, b) => a[0] - b[0])

  function seatColor(seat: Seat): string {
    if (!seat.available) return '#cbd5e1'        // 예약됨 = 회색
    if (seat.seatNo === selectedNo) return '#2563eb'   // 내가 고른 = 파랑
    if (seat.seatNo === recommendedNo) return '#16a34a' // 추천 = 초록
    if (alternativeNos.includes(seat.seatNo)) return '#86efac' // 동률 대안 = 연초록
    return '#f1f5f9'                              // 빈 자리 = 흰색
  }

  return (
    <div style={{ marginTop: '20px' }}>
      <h3>좌석 배치도</h3>

      {/* 운전석 표시 */}
      <div style={{ textAlign: 'right', marginBottom: '8px', color: '#64748b' }}>🚍 앞 (운전석)</div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', alignItems: 'center' }}>
        {sortedRows.map(([rowNum, rowSeats]) => {
          const sorted = [...rowSeats].sort((a, b) => a.column - b.column)
          const totalCols = Math.max(...sorted.map((s) => s.column))
          return (
            <div key={rowNum} style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
              {sorted.map((seat, idx) => {
                // 통로 표현: 3칸이면 2번째 뒤, 4칸이면 2번째 뒤에 간격
                const aisleAfter = totalCols <= 3 ? 2 : 2
                const showAisle = seat.column === aisleAfter && seat.column < totalCols
                return (
                  <div key={seat.seatNo} style={{ display: 'flex', alignItems: 'center' }}>
                    <button
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
                    {showAisle && <div style={{ width: '24px' }} />}
                  </div>
                )
              })}
            </div>
          )
        })}
      </div>

      {/* 색상 설명 */}
      <div style={{ marginTop: '16px', fontSize: '0.9rem', display: 'flex', gap: '20px', flexWrap: 'wrap' }}>
        <LegendItem color="#16a34a" label="추천 좌석" />
        <LegendItem color="#86efac" label="같은 조건 좌석" />
        <LegendItem color="#f1f5f9" label="빈 자리" border />
        <LegendItem color="#cbd5e1" label="예약됨" />
      </div>
    </div>
  )
}

// 범례 한 칸 (색 네모 + 설명)
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