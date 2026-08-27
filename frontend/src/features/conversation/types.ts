export type TimePreference = 'MORNING' | 'AFTERNOON' | 'EVENING' | 'ANY'

export type SeatPreference = 'FRONT' | 'WINDOW' | 'AISLE' | 'ADJACENT'

export type AccessibilityNeed = 'WALKING_DIFFICULTY' | 'MOTION_SICKNESS'

export interface SearchCondition {
  departure?: string
  arrival?: string
  date?: string
  timePreference?: TimePreference
  passengers: number
  seatPreferences: SeatPreference[]
  accessibilityNeeds: AccessibilityNeed[]
  missingFields: Array<'departure' | 'arrival' | 'date' | 'departureTime'>
}

export interface BusSchedule {
  routeId: string
  grade: string
  departure: string
  arrival: string
  departureTime: string
  arrivalTime: string
  charge: number
}
export interface BusRecommendation {
  bus: BusSchedule
  reason: string
  label: string
}

// 백엔드 PostgreSQL에 저장되는 예매 내역 형식
export interface Booking {
  id: string
  bus: BusSchedule
  seatNo: string
  passengers: number
  totalFare: number
  createdAt: string
}
export interface ConversationSearchResult {
  condition: SearchCondition
  buses: BusSchedule[]
  searched: boolean
}

export interface Seat {
  seatNo: string
  row: number
  column: number
  position: string
  side: string
  available: boolean
}

export interface SeatRecommendation {
  bestSeat: Seat | null
  score: number
  reasons: string[]
  alternatives: Seat[]
  adjacentPair: boolean   // alternatives가 bestSeat과 함께 배정된 그룹 좌석인지 - 동률 대안 구분 용도
  allSeats: Seat[]
  tiedAlternativeSeats: Seat[] // 추천(그룹 포함)과 점수가 동률인 다른 좌석/그룹 — "같은 조건 좌석" 표시용
}

export type ConversationStateValue =
  | 'COLLECTING_CONDITIONS'
  | 'READY_TO_SEARCH'
  | 'BUS_SELECTED'
  | 'SEAT_RECOMMENDED'
  | 'AWAITING_CONFIRMATION'
  | 'BOOKED'

export interface ConversationSessionResult {
  sessionId: string
  state: ConversationStateValue
  departure: string | null
  arrival: string | null
  date: string | null
  departureTime: string | null
  timePreference: string | null
  servicePreference: string | null
  busGradePreference: string | null
  passengers: number
  clarificationPrompt: string | null
  seatPreferences: SeatPreference[]
  accessibilityNeeds: AccessibilityNeed[]
  selectedBusId: string | null
  recommendedSeatNo: string | null
  bookingId: string | null
  // 세션에 계속 남는 값이 아니라 "이번 발화"에 한해서만 오는 1회성 신호
  // (예: "더 빠른/더 늦은 거 없어?" → 방금 보여준 버스보다 이르거나 늦은 시간을 찾아달라는 요청)
  wantsEarlierBus: boolean
  wantsLaterBus: boolean
}
