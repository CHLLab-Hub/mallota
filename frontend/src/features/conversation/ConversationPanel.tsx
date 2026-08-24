import { useState } from 'react'
import { searchConversation, recommendSeat } from '../../api/httpClient'
import type { ConversationSearchResult, SeatRecommendation } from './types'

export function ConversationPanel() {
  const [text, setText] = useState('내일 오전 서울에서 대전 가는데 다리가 불편하고 창가가 좋아요.')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<ConversationSearchResult | null>(null)
  const [seat, setSeat] = useState<SeatRecommendation | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function handleSearch() {
    setLoading(true)
    setError(null)
    setResult(null)
    setSeat(null)
    try {
      // 1. 조건 추출 + 버스 조회
      const data: ConversationSearchResult = await searchConversation(text)
      setResult(data)

      // 2. 조회됐고 버스가 있으면 → 자동으로 첫 버스 선택 + 좌석 추천
      if (data.searched && data.buses.length > 0) {
        const chosenBus = data.buses[0]  // 제일 이른 버스 (나중에 규칙 정교화)
        const seatData: SeatRecommendation = await recommendSeat(
          data.condition.seatPreferences,
          data.condition.accessibilityNeeds,
          chosenBus.grade,
        )
        setSeat(seatData)
      }
    } catch (e) {
      setError('처리 중 문제가 발생했습니다. 백엔드 서버가 켜져 있는지 확인해 주세요.')
    } finally {
      setLoading(false)
    }
  }

  const chosenBus = result?.searched && result.buses.length > 0 ? result.buses[0] : null

  return (
    <div className="conversation-panel">
      <label htmlFor="travel-request">어디로 가실 예정인가요?</label>
      <textarea
        id="travel-request"
        value={text}
        onChange={(e) => setText(e.target.value)}
      />
      <div className="panel-actions">
        <button
          className="primary-button"
          type="button"
          onClick={handleSearch}
          disabled={loading}
        >
          {loading ? '추천하는 중...' : '버스 추천받기'}
        </button>
      </div>

      {error && <p style={{ color: 'red' }}>{error}</p>}

      {result && (
        <div style={{ marginTop: '20px' }}>
          <h3>말씀하신 조건</h3>
          <p>
            {result.condition.departure ?? '?'} → {result.condition.arrival ?? '?'}
            {' / '}
            {result.condition.date ?? '날짜 미정'}
          </p>

          {!result.searched && (
            <p>필요한 정보가 부족해요: {result.condition.missingFields.join(', ')}</p>
          )}
        </div>
      )}

      {chosenBus && (
        <div style={{ marginTop: '20px' }}>
          <h3>추천 버스</h3>
          <p>
            {chosenBus.departureTime} 출발 · {chosenBus.grade} · {chosenBus.charge.toLocaleString()}원
          </p>
        </div>
      )}

      {seat && seat.bestSeat && (
        <div style={{ marginTop: '20px' }}>
          <h3>추천 좌석: {seat.bestSeat.seatNo}</h3>
          <ul>
            {seat.reasons.map((reason, i) => (
              <li key={i}>{reason}</li>
            ))}
          </ul>
          {seat.alternatives.length > 0 && (
            <p>같은 조건의 다른 좌석: {seat.alternatives.map((s) => s.seatNo).join(', ')}</p>
          )}
        </div>
      )}
    </div>
  )
}