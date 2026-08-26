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
  missingFields: Array<'departure' | 'arrival' | 'date'>
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
  adjacentPair: boolean   // alternatives가 bestSeat과 나란히 붙은 연석인지
  allSeats: Seat[]
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
}
