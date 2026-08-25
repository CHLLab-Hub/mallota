import { useState } from 'react'
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
  const { buses, setSelectedBus, setSeat, setScreen, addMessage } = useAppState()
  const [loading, setLoading] = useState(false)

  function appSay(t: string) {
    addMessage('app', t)
    speak(t)
  }

  async function chooseBus(bus: BusSchedule) {
    setSelectedBus(bus)
    setLoading(true)
    try {
      const seatData = await recommendSeat({
        seatPreferences: [],
        accessibilityNeeds: [],
        busGrade: bus.grade,
      })
      setSeat(seatData)
      appSay(`${formatTime(bus.departureTime)} 출발 버스로 선택했어요. 좌석을 골라볼게요.`)
      setTimeout(() => setScreen('seat'), 800)
    } catch (e) {
      appSay('좌석 정보를 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }

  // 음성으로 버스 고르기
  function handleUserSpeak(text: string) {
    addMessage('user', text)
    if (buses.length === 0) return

    // 가장 저렴
    if (text.includes('저렴') || text.includes('싼') || text.includes('싸')) {
      const cheapest = [...buses].sort((a, b) => a.charge - b.charge)[0]
      chooseBus(cheapest)
    }
    // 가장 빠른/이른
    else if (text.includes('빠른') || text.includes('이른') || text.includes('빨리') || text.includes('첫')) {
      chooseBus(buses[0])
    }
    // 숫자 (첫번째, 두번째 등)
    else if (text.includes('두') || text.includes('2')) {
      chooseBus(buses[1] ?? buses[0])
    }
    else if (text.includes('세') || text.includes('3')) {
      chooseBus(buses[2] ?? buses[0])
    }
    else if (text.includes('첫') || text.includes('일') || text.includes('1')) {
      chooseBus(buses[0])
    }
    else {
      appSay('첫 번째, 저렴한 것, 빠른 것 중에 말씀해 주세요.')
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

        {/* 음성 + 대화 */}
        <VoicePanel onUserSpeak={handleUserSpeak} loading={loading} />
      </div>
    </div>
  )
}