const defaultApiBaseUrl = 'http://localhost:8081'

const apiBaseUrl = (
  process.env.REACT_APP_API_BASE_URL ?? defaultApiBaseUrl
).replace(/\/+$/, '')

export interface FieldViolation {
  field: string
  message: string
}

export interface ApiErrorResponse {
  timestamp: string
  status: number
  code: string
  message: string
  path: string
  errors: FieldViolation[]
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly path: string
  readonly errors: FieldViolation[]

  constructor({ status, code, message, path, errors }: ApiErrorResponse) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.path = path
    this.errors = errors

    Object.setPrototypeOf(this, ApiError.prototype)
  }
}

export type ApiRequestOptions = Omit<RequestInit, 'body' | 'headers'> & {
  body?: BodyInit | null
  headers?: Record<string, string>
  json?: unknown
}

function buildApiUrl(path: string) {
  return `${apiBaseUrl}/${path.replace(/^\/+/, '')}`
}

// AbortSignal.timeout은 비교적 최근에 추가된 API라, 이 앱의 대상인 고령자/디지털 취약계층이
// 쓸 수 있는 오래된 브라우저나 저사양 기기의 구형 웹뷰에는 없을 수 있다. 없으면 매 요청마다
// 여기서 곧바로 예외가 터져 fetch 자체가 시도되지도 않으므로(테스트 환경의 jsdom도 마찬가지로
// 이 메서드가 없어서 매번 이 예외로 실패했다), AbortController + setTimeout으로 직접 구현한
// 폴백을 둔다.
function createTimeoutSignal(ms: number): AbortSignal | undefined {
  if (typeof AbortSignal.timeout === 'function') {
    return AbortSignal.timeout(ms)
  }
  if (typeof AbortController === 'undefined') {
    return undefined
  }
  const controller = new AbortController()
  setTimeout(() => controller.abort(), ms)
  return controller.signal
}

function isFieldViolation(value: unknown): value is FieldViolation {
  if (!value || typeof value !== 'object') {
    return false
  }

  const candidate = value as Record<string, unknown>
  return typeof candidate.field === 'string' && typeof candidate.message === 'string'
}

function isApiErrorResponse(value: unknown): value is ApiErrorResponse {
  if (!value || typeof value !== 'object') {
    return false
  }

  const candidate = value as Record<string, unknown>
  return (
    typeof candidate.timestamp === 'string' &&
    typeof candidate.status === 'number' &&
    typeof candidate.code === 'string' &&
    typeof candidate.message === 'string' &&
    typeof candidate.path === 'string' &&
    Array.isArray(candidate.errors) &&
    candidate.errors.every(isFieldViolation)
  )
}

async function createApiError(response: Response, path: string) {
  const contentType = response.headers.get('content-type') ?? ''

  if (contentType.includes('application/json')) {
    try {
      const body: unknown = await response.json()

      if (isApiErrorResponse(body)) {
        return new ApiError(body)
      }
    } catch {
      // JSON이 손상된 경우 아래의 안전한 기본 오류로 변환합니다.
    }
  }

  return new ApiError({
    timestamp: new Date().toISOString(),
    status: response.status,
    code: `HTTP_${response.status}`,
    message: '요청을 처리하는 중 오류가 발생했습니다.',
    path,
    errors: [],
  })
}

export async function request<T>(
  path: string,
  options: ApiRequestOptions = {},
): Promise<T> {
  const { body, headers, json, ...requestInit } = options

  if (body !== undefined && json !== undefined) {
    throw new TypeError('body와 json은 동시에 지정할 수 없습니다.')
  }

  const requestHeaders: Record<string, string> = {
    Accept: 'application/json',
    ...(json === undefined ? {} : { 'Content-Type': 'application/json' }),
    ...headers,
  }

  const url = buildApiUrl(path)
  let response: Response

  // 호출하는 쪽에서 signal을 직접 넘기지 않은 요청은 기본 30초 타임아웃을 건다. 안 그러면 서버가
  // 응답 없이 멈췄을 때(예: 음성 인식 API가 느려질 때) 화면이 "인식 중..."에서 영영 멈춰버린다.
  const timeoutSignal = requestInit.signal ? undefined : createTimeoutSignal(30_000)

  try {
    response = await fetch(url, {
      ...requestInit,
      signal: requestInit.signal ?? timeoutSignal,
      body: json === undefined ? body : JSON.stringify(json),
      headers: requestHeaders,
    })
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') {
      if (timeoutSignal) {
        throw new ApiError({
          timestamp: new Date().toISOString(),
          status: 0,
          code: 'TIMEOUT',
          message: '서버 응답이 너무 오래 걸려요. 잠시 후 다시 시도해 주세요.',
          path,
          errors: [],
        })
      }
      throw error
    }

    throw new ApiError({
      timestamp: new Date().toISOString(),
      status: 0,
      code: 'NETWORK_ERROR',
      message: '서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.',
      path,
      errors: [],
    })
  }

  if (!response.ok) {
    throw await createApiError(response, path)
  }

  if (response.status === 204) {
    return undefined as T
  }

  const contentType = response.headers.get('content-type') ?? ''

  if (!contentType.includes('application/json')) {
    return (await response.text()) as T
  }

  return response.json() as Promise<T>
}
