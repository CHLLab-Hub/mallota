const apiBaseUrl = process.env.REACT_APP_API_BASE_URL ?? 'http://localhost:8081'

export async function getHealth(): Promise<{ status: string; service: string }> {
  const response = await fetch(`${apiBaseUrl}/api/health`)

  if (!response.ok) {
    throw new Error('백엔드 상태를 확인할 수 없습니다.')
  }

  return response.json() as Promise<{ status: string; service: string }>
}

export async function searchConversation(text: string) {
  const response = await fetch(`${apiBaseUrl}/api/conversation/search`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ text }),
  })

  if (!response.ok) {
    throw new Error('검색 요청에 실패했습니다.')
  }

  return response.json()
}