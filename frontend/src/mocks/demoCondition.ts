import type { SearchCondition } from '../features/conversation/types'

export const demoCondition: SearchCondition = {
  departure: '서울',
  arrival: '대전',
  date: '2026-08-23',
  timePreference: 'MORNING',
  passengers: 1,
  seatPreferences: ['FRONT', 'WINDOW'],
  accessibilityNeeds: ['WALKING_DIFFICULTY'],
  missingFields: [],
}
