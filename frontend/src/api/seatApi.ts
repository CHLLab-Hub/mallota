import { request } from './httpClient'
import type {
  AccessibilityNeed,
  SeatPreference,
  SeatRecommendation,
} from '../features/conversation/types'

export interface SeatRecommendRequest {
  busGrade: string
  seatPreferences: SeatPreference[]
  accessibilityNeeds: AccessibilityNeed[]
}

export function recommendSeat(
  requestBody: SeatRecommendRequest,
  signal?: AbortSignal,
) {
  return request<SeatRecommendation>('/api/seats/recommend', {
    method: 'POST',
    json: requestBody,
    signal,
  })
}
