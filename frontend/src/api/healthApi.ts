import { request } from './httpClient'

export interface HealthResponse {
  status: string
  service: string
}

export function getHealth(signal?: AbortSignal) {
  return request<HealthResponse>('/api/health', { signal })
}
