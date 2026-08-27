import { useState, useEffect, useRef } from 'react'
import { recommendSeat } from '../api/seatApi'
import { useAppState } from '../features/conversation/AppState'
import { VoicePanel, speak } from '../features/conversation/VoicePanel'
import type { BusSchedule, BusRecommendation } from '../features/conversation/types'
import './HomePage.css'

function formatTime(raw: string): string {
  if (!raw || raw.length < 12) return raw
  const hour = parseInt(raw.substring(8, 10), 10)
  const minute = raw.substring(10, 12)
  const period = hour < 12 ? '오전' : '오후'
  const h = hour === 0 ? 12 : hour > 12 ? hour - 12 : hour
  return minute === '00' ? `${period} ${h}시` : `${period} ${h}시 ${minute}분`
}

// 3개 추천 중, 방금 고른 버스보다 출발 시각이 이른/늦은 것 중 가장 가까운 것을 고른다.
// departureTime은 "yyyyMMddHHmm" 형식이라 문자열 비교로 시간 순서를 알 수 있다.
function findEarlierRecommendation(recs: BusRecommendation[], current: BusSchedule): BusRecommendation | null {
  const earlier = recs.filter((r) => r.bus.departureTime < current.departureTime)
  if (earlier.length === 0) return null
  return earlier.reduce((latest, r) => (r.bus.departureTime > latest.bus.departureTime ? r : latest))
}

function findLaterRecommendation(recs: BusRecommendation[], current: BusSchedule): BusRecommendation | null {
  const later = recs.filter((r) => r.bus.departureTime > current.departureTime)
  if (later.length === 0) return null
  return later.reduce((earliest, r) => (r.bus.departureTime < earliest.bus.departureTime ? r : earliest))
}

export function BusPage() {
  const {
    recommendations, selectedBus, setSelectedBus, setSeat, setScreen, addMessage,
    seatPreferences, accessibilityNeeds, passengers,
  } = useAppState()
  const [loading, setLoading] = useState(false)
  const announced = useRef(false)

  const totalFare = (bus: BusSchedule) => bus.charge * passengers

  function appSay(t: string) {
    addMessage('app', t)
    speak(t)
  }

  // 화면 뜰 때 3개 추천 음성 안내
  useEffect(() => {
    if (recommendations.length > 0 && !announced.current) {
      announced.current = true
      const text = recommendations
        .map((r) => `${r.reason} ${formatTime(r.bus.departureTime)} 출발, ${passengers}인 총 ${totalFare(r.bus).toLocaleString()}원.`)
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
        passengers,
      })
      setSeat(seatData)
      // 2인 이상 연석 추천은 bestSeat 하나만 읽으면 동행 좌석을 알 수 없다.
      // adjacentPair일 때 alternatives에는 함께 추천된 나머지 좌석만 들어온다.
      const recommendedSeats = seatData.adjacentPair && seatData.bestSeat
        ? [seatData.bestSeat, ...seatData.alternatives].map((seat) => `${seat.seatNo}번`).join(', ')
        : seatData.bestSeat ? `${seatData.bestSeat.seatNo}번` : ''
      appSay(`${formatTime(bus.departureTime)} 출발 버스를 선택했어요. ${passengers}인 총 요금은 ${totalFare(bus).toLocaleString()}원이고, 추천 좌석은 ${recommendedSeats}입니다. 이 좌석으로 결제할까요?`)
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

    // "더 빠른/이른 거 없어?"는 목록 중 아무거나가 아니라, 방금 고른 버스보다 더 이른 시간을 찾아달라는
    // 뜻이다 (아직 고른 버스가 없으면 일반 "추천" 선택으로 자연스럽게 넘어간다).
    if ((text.includes('더 빠른') || text.includes('더 이른')) && selectedBus) {
      const earlier = findEarlierRecommendation(recommendations, selectedBus)
      if (earlier) {
        chooseBus(earlier.bus)
      } else {
        appSay('죄송해요, 이미 조건에 맞는 가장 이른 시간의 버스예요.')
      }
    } else if ((text.includes('더 늦은') || text.includes('더 나중')) && selectedBus) {
      const later = findLaterRecommendation(recommendations, selectedBus)
      if (later) {
        chooseBus(later.bus)
      } else {
        appSay('죄송해요, 이미 조건에 맞는 가장 늦은 시간의 버스예요.')
      }
    } else if (text.includes('저렴') || text.includes('싼') || text.includes('싸')) {
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
                {rec.bus.grade} · 1인 {rec.bus.charge.toLocaleString()}원 · <b>총 {totalFare(rec.bus).toLocaleString()}원</b>
              </div>
            </button>
          ))
        )}

        <VoicePanel onUserSpeak={handleUserSpeak} loading={loading} />
      </div>
    </div>
  )
}
