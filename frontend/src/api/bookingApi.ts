import { request } from './httpClient'
import type { Booking, BusSchedule } from '../features/conversation/types'

const OWNER_ID_STORAGE_KEY = 'malrota-booking-owner-id'
let transientOwnerId: string | null = null

/**
 * 로그인 전 MVP에서는 브라우저마다 하나의 식별자를 보관해 다른 기기의 예매와 섞이지 않게 한다.
 * 인증을 도입하면 이 값을 로그인한 사용자 ID로 대체한다.
 */
export function getBookingOwnerId(): string {
  if (transientOwnerId) return transientOwnerId

  try {
    const existing = window.localStorage.getItem(OWNER_ID_STORAGE_KEY)
    if (existing) {
      transientOwnerId = existing
      return existing
    }

    const created = typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `browser-${Date.now()}-${Math.random().toString(36).slice(2)}`
    window.localStorage.setItem(OWNER_ID_STORAGE_KEY, created)
    transientOwnerId = created
    return created
  } catch {
    // 저장소가 차단된 환경에서는 해당 실행 중에만 유지되는 식별자를 사용한다.
    transientOwnerId = `temporary-${Date.now()}-${Math.random().toString(36).slice(2)}`
    return transientOwnerId
  }
}

export interface CreateBookingInput {
  ownerId: string
  bus: BusSchedule
  seatNo: string
  passengers: number
  totalFare: number
}

export function createBooking(input: CreateBookingInput) {
  const { ownerId, bus, seatNo, passengers, totalFare } = input
  return request<Booking>('/api/bookings', {
    method: 'POST',
    json: {
      ownerId,
      routeId: bus.routeId,
      grade: bus.grade,
      departure: bus.departure,
      arrival: bus.arrival,
      departureTime: bus.departureTime,
      arrivalTime: bus.arrivalTime,
      charge: bus.charge,
      seatNo,
      passengers,
      totalFare,
    },
  })
}

export function fetchBookings(ownerId: string) {
  return request<Booking[]>(`/api/bookings?ownerId=${encodeURIComponent(ownerId)}`)
}

export function cancelBooking(id: string, ownerId: string) {
  return request<void>(`/api/bookings/${encodeURIComponent(id)}?ownerId=${encodeURIComponent(ownerId)}`, {
    method: 'DELETE',
  })
}
