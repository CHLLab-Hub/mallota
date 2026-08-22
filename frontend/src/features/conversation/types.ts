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
