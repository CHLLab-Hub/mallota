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
  position: string
  side: string
  available: boolean
}

export interface SeatRecommendation {
  bestSeat: Seat | null
  score: number
  reasons: string[]
  alternatives: Seat[]
}