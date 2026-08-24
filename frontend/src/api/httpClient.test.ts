import { ApiError, request } from './httpClient'

const expectedApiBaseUrl = (
  process.env.REACT_APP_API_BASE_URL ?? 'http://localhost:8081'
).replace(/\/+$/, '')

function createResponse(status: number, body?: unknown): Response {
  const hasBody = body !== undefined

  return {
    ok: status >= 200 && status < 300,
    status,
    headers: {
      get: (name: string) =>
        name.toLowerCase() === 'content-type' && hasBody
          ? 'application/json'
          : null,
    },
    json: jest.fn().mockResolvedValue(body),
    text: jest
      .fn()
      .mockResolvedValue(typeof body === 'string' ? body : JSON.stringify(body)),
  } as unknown as Response
}

describe('request', () => {
  const fetchMock = jest.fn()

  beforeEach(() => {
    fetchMock.mockReset()
    Object.defineProperty(globalThis, 'fetch', {
      configurable: true,
      value: fetchMock,
      writable: true,
    })
  })

  it('기본 주소를 적용하고 JSON 응답을 반환한다', async () => {
    fetchMock.mockResolvedValue(createResponse(200, { status: 'UP' }))

    await expect(request<{ status: string }>('/api/health')).resolves.toEqual({
      status: 'UP',
    })

    expect(fetchMock).toHaveBeenCalledWith(
      `${expectedApiBaseUrl}/api/health`,
      expect.objectContaining({
        headers: expect.objectContaining({ Accept: 'application/json' }),
      }),
    )
  })

  it('json 옵션을 요청 본문으로 변환한다', async () => {
    fetchMock.mockResolvedValue(createResponse(200, { intent: 'BUS_SEARCH' }))

    await request('/api/conversation/parse', {
      method: 'POST',
      json: { text: '내일 서울에서 대전 가고 싶어요.' },
    })

    expect(fetchMock).toHaveBeenCalledWith(
      `${expectedApiBaseUrl}/api/conversation/parse`,
      expect.objectContaining({
        body: JSON.stringify({ text: '내일 서울에서 대전 가고 싶어요.' }),
        headers: expect.objectContaining({
          Accept: 'application/json',
          'Content-Type': 'application/json',
        }),
        method: 'POST',
      }),
    )
  })

  it('백엔드 공통 오류 응답을 ApiError로 변환한다', async () => {
    fetchMock.mockResolvedValue(
      createResponse(400, {
        timestamp: '2026-08-23T00:00:00Z',
        status: 400,
        code: 'VALIDATION_ERROR',
        message: '요청값을 확인해 주세요.',
        path: '/api/conversation/parse',
        errors: [{ field: 'text', message: '발화 내용을 입력해 주세요.' }],
      }),
    )

    const promise = request('/api/conversation/parse', {
      method: 'POST',
      json: {},
    })

    await expect(promise).rejects.toBeInstanceOf(ApiError)
    await expect(promise).rejects.toMatchObject({
      status: 400,
      code: 'VALIDATION_ERROR',
      path: '/api/conversation/parse',
      errors: [{ field: 'text', message: '발화 내용을 입력해 주세요.' }],
    })
  })

  it('네트워크 연결 실패를 구분한다', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))

    await expect(request('/api/health')).rejects.toMatchObject({
      status: 0,
      code: 'NETWORK_ERROR',
      path: '/api/health',
    })
  })

  it('본문이 없는 204 응답을 처리한다', async () => {
    fetchMock.mockResolvedValue(createResponse(204))

    await expect(request<void>('/api/session', { method: 'DELETE' })).resolves.toBeUndefined()
  })
})
