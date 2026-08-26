import type { Seat } from './types'

// 통로 위치: 2번 칸 뒤가 통로 (예: [A][B] | [C]) — 백엔드 연석 판정과 동일한 규칙
export const AISLE_AFTER_COLUMN = 2

/** 통로를 사이에 두지 않고 실제로 나란히 붙어 있는 빈 옆자리 찾기 */
export function findPairPartner(seats: Seat[], seat: Seat): Seat | null {
  const sameRow = seats.filter((s) => s.row === seat.row && s.available)
  const candidates = [
    sameRow.find((s) => s.column === seat.column - 1 && s.column !== AISLE_AFTER_COLUMN),
    sameRow.find((s) => s.column === seat.column + 1 && seat.column !== AISLE_AFTER_COLUMN),
  ].filter((s): s is Seat => Boolean(s))
  return candidates[0] ?? null
}

/** 두 좌석을 칸 순서대로 "9A, 9B" 형태로 표기 */
export function formatPair(a: Seat, b: Seat): string {
  return a.column <= b.column ? `${a.seatNo}, ${b.seatNo}` : `${b.seatNo}, ${a.seatNo}`
}

/** 좌석 묶음을 줄→칸 순으로 정렬해 "1A, 1B, 2A, 2B" 형태로 표기 */
export function formatSeats(seats: Seat[]): string {
  return [...seats]
    .sort((a, b) => a.row - b.row || a.column - b.column)
    .map((s) => s.seatNo)
    .join(', ')
}

/** 3인석: 클릭한 좌석이 속한(또는 그 줄의) 연석(2) + 통로 건너 가장 가까운 1석 */
export function findRowTriple(seats: Seat[], seat: Seat): Seat[] | null {
  const sameRow = seats.filter((s) => s.row === seat.row && s.available)

  let pair: Seat[] | null = null
  const clickedPartner = findPairPartner(seats, seat)
  if (clickedPartner) {
    pair = [seat, clickedPartner]
  } else {
    for (const candidate of sameRow) {
      const partner = findPairPartner(seats, candidate)
      if (partner) {
        pair = [candidate, partner]
        break
      }
    }
  }
  if (!pair) return null

  const pairNos = new Set(pair.map((s) => s.seatNo))
  const extra = sameRow
    .filter((s) => !pairNos.has(s.seatNo))
    .sort((a, b) => {
      const distA = Math.min(...pair!.map((p) => Math.abs(a.column - p.column)))
      const distB = Math.min(...pair!.map((p) => Math.abs(b.column - p.column)))
      return distA - distB
    })[0]
  if (!extra) return null

  return [...pair, extra]
}

/** 4인석: 클릭한 좌석이 속한 연석과 같은 칸으로 앞뒤 줄에 이어진 사각형(2x2) 배치 */
export function findRectangleQuad(seats: Seat[], seat: Seat): Seat[] | null {
  const partner = findPairPartner(seats, seat)
  if (!partner) return null
  const pair = [seat, partner].sort((a, b) => a.column - b.column)
  const cols = pair.map((s) => s.column)

  const pairAtRow = (row: number): Seat[] | null => {
    const rowSeats = seats.filter((s) => s.row === row && s.available)
    const a = rowSeats.find((s) => s.column === cols[0])
    const b = rowSeats.find((s) => s.column === cols[1])
    return a && b ? [a, b] : null
  }

  const below = pairAtRow(seat.row + 1)
  if (below) return [...pair, ...below]
  const above = pairAtRow(seat.row - 1)
  if (above) return [...above, ...pair]
  return null
}

/** 추천된 그룹 인원수에 맞춰, 클릭한 좌석을 기준으로 같은 모양의 자리 묶음을 찾는다 */
export function findSeatGroup(seats: Seat[], seat: Seat, groupSize: number): Seat[] | null {
  if (groupSize <= 1) return [seat]
  if (groupSize === 2) {
    const partner = findPairPartner(seats, seat)
    return partner ? [seat, partner] : null
  }
  if (groupSize === 3) return findRowTriple(seats, seat)
  return findRectangleQuad(seats, seat)
}

// "9A, 9B" 처럼 쉼표로 이어진 좌석 번호를 배열로
function splitSeatNos(value?: string): string[] {
  return value ? value.split(',').map((s) => s.trim()).filter(Boolean) : []
}

interface SeatMapProps {
  seats: Seat[]              // 전체 좌석
  recommendedNo: string      // 추천 좌석 번호 (예: "1B" 또는 "1B, 1C")
  alternativeNos: string[]   // 동률 대안 좌석 번호들
  selectedNo?: string        // 사용자가 고른 좌석 (연석이면 "9A, 9B")
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
  const sortedRows = Array.from(rows.entries()).sort((a, b) => a[0] - b[0])

  // 전체 격자의 열 수 (제일 큰 column 값) — 줄마다 실제 좌석이 없는 칸(통로)은 빈 칸으로 채운다
  const totalCols = Math.max(...seats.map((s) => s.column))

  // 쉼표로 연결된 "9A, 9B" 연석 번호는 모든 자리를 다 칠해야 한다 (2/3/4인 그룹 배정 지원)
  const selectedList = splitSeatNos(selectedNo)
  const recommendedList = splitSeatNos(recommendedNo)

  function seatColor(seat: Seat): string {
    if (!seat.available) return '#cbd5e1'                       // 예약됨 = 회색
    if (selectedList.includes(seat.seatNo)) return '#2563eb'    // 내가 고른 = 파랑
    if (recommendedList.includes(seat.seatNo)) return '#16a34a' // 추천 = 초록
    if (alternativeNos.includes(seat.seatNo)) return '#86efac'  // 동률 대안 = 연초록
    return '#f1f5f9'                                            // 빈 자리 = 밝은 회색
  }

  return (
    <div style={{ marginTop: '20px' }}>
      <h3>좌석 배치도</h3>
      <div style={{ textAlign: 'right', marginBottom: '8px', color: '#64748b' }}>🚍 앞 (운전석)</div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', alignItems: 'center' }}>
        {sortedRows.map(([rowNum, rowSeats]) => {
          // column 위치로 좌석 찾기 (없으면 빈 칸 = 통로)
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
                const highlighted = recommendedList.includes(seat.seatNo) || selectedList.includes(seat.seatNo)
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
                      fontWeight: highlighted ? 'bold' : 'normal',
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
        {selectedList.length > 0 && <LegendItem color="#2563eb" label="선택한 좌석" />}
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