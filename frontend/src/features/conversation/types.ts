export type TimePreference = 'MORNING' | 'AFTERNOON' | 'EVENING' | 'ANY'

export type SeatPreference = 'FRONT' | 'WINDOW' | 'AISLE' | 'ADJACENT'

export type AccessibilityNeed =
  | 'WALKING_DIFFICULTY'
  | 'MOTION_SICKNESS'
  | 'ELDERLY_CARE'
  | 'PREGNANCY'
  | 'VISUAL_IMPAIRMENT'

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
  labels: string[]
}

export interface BusRecommendResponse {
  recommendations: BusRecommendation[]
  routeExists: boolean // false면 조건이 아니라 이 출발지-도착지 사이에 직행 노선 자체가 없다는 뜻
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
  // 출발지-도착지 사이에 직행 노선 자체가 없다는 1회성 신호 — true면 세션을 초기화해야 한다.
  routeNotFound: boolean
  // LLM이 STT 오인식을 문맥으로 교정한 원문(예: "이런 트렌치" -> "이런 센트럴시티"). 세션에
  // 남지 않는 1회성 신호 — 방금 사용자 말풍선으로 보여준 원본 STT 텍스트를 이 값으로 갱신해서
  // 실제로 이해한 내용과 화면에 보이는 게 다르게 보이지 않게 한다.
  correctedText: string | null
}
