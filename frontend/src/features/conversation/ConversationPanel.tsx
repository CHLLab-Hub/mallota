import { useState } from 'react'
import { searchConversation } from '../../api/httpClient'
import type { ConversationSearchResult } from './types'

export function ConversationPanel() {
  const [text, setText] = useState('내일 오전 서울에서 대전 가는데 다리가 불편하고 창가가 좋아요.')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<ConversationSearchResult | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function handleSearch() {
    setLoading(true)
    setError(null)
    setResult(null)
    try {
      const data = await searchConversation(text)
      setResult(data)
    } catch (e) {
      setError('검색 중 문제가 발생했습니다. 백엔드 서버가 켜져 있는지 확인해 주세요.')
    } finally {
      setLoading(false)
    }
  }

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
          {loading ? '검색 중...' : '버스 검색'}
        </button>
      </div>

      {error && <p style={{ color: 'red' }}>{error}</p>}

      {result && (
        <div style={{ marginTop: '20px' }}>
          <h3>추출된 조건</h3>
          <p>
            {result.condition.departure ?? '?'} → {result.condition.arrival ?? '?'}
            {' / '}
            {result.condition.date ?? '날짜 미정'}
          </p>

          {result.searched ? (
            <>
              <h3>운행편 ({result.buses.length}개)</h3>
              <ul>
                {result.buses.map((bus, i) => (
                  <li key={i}>
                    {bus.departureTime} · {bus.grade} · {bus.charge.toLocaleString()}원
                    {' '}({bus.departure} → {bus.arrival})
                  </li>
                ))}
              </ul>
            </>
          ) : (
            <p>
              필요한 정보가 부족해요: {result.condition.missingFields.join(', ')}
            </p>
          )}
        </div>
      )}
    </div>
  )
}