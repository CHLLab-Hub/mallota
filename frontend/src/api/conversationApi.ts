import { request } from './httpClient'
import type {
  ConversationSearchResult,
  ConversationSessionResult,
  BusSchedule,
  BusRecommendation,
} from '../features/conversation/types'

export function searchConversation(text: string, signal?: AbortSignal) {
  return request<ConversationSearchResult>('/api/conversation/search', {
    method: 'POST',
    json: { text },
    signal,
  })
}

// 대화하며 조건 누적 (세션)
export function parseConversation(text: string, sessionId: string | null, signal?: AbortSignal) {
  return request<ConversationSessionResult>('/api/conversation/parse', {
    method: 'POST',
    json: { text, sessionId },
    signal,
  })
}

// 조건이 다 모이면 버스 조회
export function searchBuses(
  body: {
    departure: string
    arrival: string
    date: string
    departureTime?: string | null
    timePreference?: string | null
    servicePreference?: string | null
    busGradePreference?: string | null
  },
  signal?: AbortSignal,
) {
  return request<BusSchedule[]>('/api/buses/search', {
    method: 'POST',
    json: body,
    signal,
  })
}
export function recommendBuses(
  body: {
    departure: string
    arrival: string
    date: string
    departureTime?: string | null
    timePreference?: string | null
    servicePreference?: string | null
    busGradePreference?: string | null
  },
  signal?: AbortSignal,
) {
  return request<BusRecommendation[]>('/api/buses/recommend', {
    method: 'POST',
    json: body,
    signal,
  })
}