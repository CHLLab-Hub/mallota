import { request } from './httpClient'
import type { ConversationSearchResult } from '../features/conversation/types'

export function searchConversation(text: string, signal?: AbortSignal) {
  return request<ConversationSearchResult>('/api/conversation/search', {
    method: 'POST',
    json: { text },
    signal,
  })
}
