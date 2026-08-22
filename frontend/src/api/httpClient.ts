const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export async function getHealth(): Promise<{ status: string; service: string }> {
  const response = await fetch(`${apiBaseUrl}/api/health`)

  if (!response.ok) {
    throw new Error('백엔드 상태를 확인할 수 없습니다.')
  }

  return response.json() as Promise<{ status: string; service: string }>
}
