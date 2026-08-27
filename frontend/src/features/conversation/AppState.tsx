import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { fetchBookings, getBookingOwnerId } from '../../api/bookingApi'
import type { Booking, BusSchedule, SeatRecommendation, BusRecommendation } from './types'
export type { Booking } from './types'
// 화면 종류
export type Screen = 'home' | 'bus' | 'seat' | 'confirm' | 'history' | 'mypage'

// 대화 메시지
export type ChatMessage = { role: 'app' | 'user'; text: string }

// 공유 상태 타입
interface AppStateValue {
  screen: Screen
  setScreen: (s: Screen) => void

  sessionId: string | null
  setSessionId: (id: string | null) => void

  messages: ChatMessage[]
  addMessage: (role: 'app' | 'user', text: string) => void
  resetMessages: () => void

  buses: BusSchedule[]
  setBuses: (b: BusSchedule[]) => void

  recommendations: BusRecommendation[]
  setRecommendations: (r: BusRecommendation[]) => void

  selectedBus: BusSchedule | null
  setSelectedBus: (b: BusSchedule | null) => void

  seat: SeatRecommendation | null
  setSeat: (s: SeatRecommendation | null) => void

  selectedSeatNo: string | null
  setSelectedSeatNo: (no: string | null) => void

    seatPreferences: string[]
  setSeatPreferences: (p: string[]) => void

  accessibilityNeeds: string[]
  setAccessibilityNeeds: (n: string[]) => void

  // 예매 인원 (2/3/4인 좌석 묶음 추천에 필요)
  passengers: number
  setPassengers: (n: number) => void

  bookings: Booking[]
  setBookings: (b: Booking[]) => void
  addBooking: (b: Booking) => void
  removeBooking: (id: string) => void
}

const AppStateContext = createContext<AppStateValue | null>(null)

const initialMessage: ChatMessage = {
  role: 'app',
  text: '어디에서 출발하시는지 큰 목소리로 말씀해 주세요.',
}

export function AppStateProvider({ children }: { children: ReactNode }) {
  const [screen, setScreen] = useState<Screen>('home')
  const [sessionId, setSessionId] = useState<string | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([initialMessage])
  const [buses, setBuses] = useState<BusSchedule[]>([])
  const [recommendations, setRecommendations] = useState<BusRecommendation[]>([])
  const [selectedBus, setSelectedBus] = useState<BusSchedule | null>(null)
  const [seat, setSeat] = useState<SeatRecommendation | null>(null)
  const [selectedSeatNo, setSelectedSeatNo] = useState<string | null>(null)
  const [seatPreferences, setSeatPreferences] = useState<string[]>([])
  const [accessibilityNeeds, setAccessibilityNeeds] = useState<string[]>([])
  const [passengers, setPassengers] = useState<number>(1)
  const [bookings, setBookings] = useState<Booking[]>([])

  // 예매 내역의 기준은 프런트 메모리가 아니라 PostgreSQL이다.
  // 앱을 새로 열거나 화면을 이동해도 과거의 임시 상태가 남아 DB 건수와 달라지지 않게 한다.
  useEffect(() => {
    let mounted = true
    fetchBookings(getBookingOwnerId())
      .then((items) => {
        if (mounted) setBookings(items)
      })
      .catch(() => {
        // 백엔드가 아직 실행 전이면 기존 화면 흐름은 유지하고, 예매 내역 화면에서 오류를 안내한다.
      })
    return () => { mounted = false }
  }, [])

  function addMessage(role: 'app' | 'user', text: string) {
    setMessages((prev) => [...prev, { role, text }])
  }
  function resetMessages() {
    setMessages([initialMessage])
  }
  function addBooking(b: Booking) {
    setBookings((prev) => [...prev, b])
  }


  function removeBooking(id: string) {
    setBookings((prev) => prev.filter((x) => x.id !== id))
  }

  return (
    <AppStateContext.Provider
      value={{
        screen, setScreen,
        sessionId, setSessionId,
        messages, addMessage, resetMessages,
        buses, setBuses,
        recommendations, setRecommendations,
        selectedBus, setSelectedBus,
        seat, setSeat,
        selectedSeatNo, setSelectedSeatNo,
        seatPreferences, setSeatPreferences,
        accessibilityNeeds, setAccessibilityNeeds,
        passengers, setPassengers,
        bookings, setBookings, addBooking, removeBooking,
      }}
    >
      {children}
    </AppStateContext.Provider>
  )
}

// 어느 화면에서든 상태 꺼내 쓰기
export function useAppState() {
  const ctx = useContext(AppStateContext)
  if (!ctx) throw new Error('useAppState must be used within AppStateProvider')
  return ctx
}
