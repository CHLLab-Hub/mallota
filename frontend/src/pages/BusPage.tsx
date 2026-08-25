import { useState, useEffect, useRef } from 'react'
import { recommendSeat } from '../api/seatApi'
import { useAppState } from '../features/conversation/AppState'
import { VoicePanel, speak } from '../features/conversation/VoicePanel'
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
  const { recommendations, setSelectedBus, setSeat, setScreen, addMessage, seatPreferences, accessibilityNeeds } = useAppState()
  const [loading, setLoading] = useState(false)
  const announced = useRef(false)

  function appSay(t: string) {
    addMessage('app', t)
    speak(t)
  }

  // 화면 뜰 때 3개 추천 음성 안내
  useEffect(() => {
    if (recommendations.length > 0 && !announced.current) {
      announced.current = true
      const text = recommendations
        .map((r) => `${r.reason} ${formatTime(r.bus.departureTime)} 출발, ${r.bus.charge.toLocaleString()}원.`)
        .join(' ')
      appSay('추천 버스를 안내해드릴게요. ' + text + ' 어떤 버스로 하시겠어요?')
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function chooseBus(bus: BusSchedule) {
    setSelectedBus(bus)
    setLoading(true)
    try {
      const seatData = await recommendSeat({
        seatPreferences: seatPreferences as any,
        accessibilityNeeds: accessibilityNeeds as any,
        busGrade: bus.grade,
      })
      setSeat(seatData)
      appSay(`${formatTime(bus.departureTime)} 출발 버스를 선택했어요. 추천 좌석은 ${seatData.bestSeat?.seatNo ?? ''}번입니다. 이 좌석으로 결제할까요?`)
      setTimeout(() => setScreen('seat'), 3000)
    } catch (e) {
      appSay('좌석 정보를 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }

  // 음성으로 버스 고르기
  function handleUserSpeak(text: string) {
    addMessage('user', text)
    if (recommendations.length === 0) return

    if (text.includes('저렴') || text.includes('싼') || text.includes('싸')) {
      const found = recommendations.find((r) => r.label.includes('최저가'))
      chooseBus((found ?? recommendations[0]).bus)
    } else if (text.includes('빠른') || text.includes('이른') || text.includes('첫') || text.includes('추천')) {
      const found = recommendations.find((r) => r.label.includes('추천'))
      chooseBus((found ?? recommendations[0]).bus)
    } else if (text.includes('두') || text.includes('2')) {
      chooseBus((recommendations[1] ?? recommendations[0]).bus)
    } else if (text.includes('세') || text.includes('3')) {
      chooseBus((recommendations[2] ?? recommendations[0]).bus)
    } else if (text.includes('첫') || text.includes('1')) {
      chooseBus(recommendations[0].bus)
    } else {
      appSay('저렴한 것, 추천 시간, 또는 몇 번째인지 말씀해 주세요.')
    }
  }

  return (
    <div className="phone-frame">
      <header className="home-header">
        <button type="button" className="info-button" onClick={() => setScreen('home')}>
          ← 뒤로
        </button>
      </header>

      <h1 className="home-title" style={{ fontSize: '1.4rem' }}>추천 버스를 골라주세요</h1>

      <div className="home-body">
        {recommendations.length === 0 ? (
          <p>추천할 버스가 없습니다.</p>
        ) : (
          recommendations.map((rec, i) => (
            <button
              key={i}
              type="button"
              onClick={() => chooseBus(rec.bus)}
              disabled={loading}
              style={{
                width: '100%',
                textAlign: 'left',
                background: '#fff',
                border: '2px solid #f0e6d8',
                borderRadius: '16px',
                padding: '18px',
                marginBottom: '12px',
                cursor: 'pointer',
                position: 'relative',
              }}
            >
              {/* 라벨 뱃지 */}
              <span style={{
                display: 'inline-block',
                background: '#f07f21',
                color: '#fff',
                fontSize: '0.85rem',
                fontWeight: 700,
                padding: '4px 12px',
                borderRadius: '999px',
                marginBottom: '8px',
              }}>
                {rec.label}
              </span>
              <div style={{ fontSize: '1.2rem', fontWeight: 800, color: '#2b2320' }}>
                {rec.bus.departure} → {rec.bus.arrival}
              </div>
              <div style={{ fontSize: '1.1rem', color: '#f07f21', marginTop: '6px' }}>
                {formatTime(rec.bus.departureTime)} 출발 · {formatTime(rec.bus.arrivalTime)} 도착
              </div>
              <div style={{ fontSize: '1rem', color: '#58665f', marginTop: '4px' }}>
                {rec.bus.grade} · {rec.bus.charge.toLocaleString()}원
              </div>
            </button>
          ))
        )}

        <VoicePanel onUserSpeak={handleUserSpeak} loading={loading} />
      </div>
    </div>
  )
}