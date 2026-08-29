import { getHealth } from './healthApi'

describe('getHealth', () => {
  const fetchMock = jest.fn()

  beforeEach(() => {
    fetchMock.mockReset()
    Object.defineProperty(globalThis, 'fetch', {
      configurable: true,
      value: fetchMock,
      writable: true,
    })
  })

  it('백엔드 상태 확인 경로를 호출한다', async () => {
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      headers: {
        get: () => 'application/json',
      },
      json: jest.fn().mockResolvedValue({
        status: 'UP',
        service: 'mallota-backend',
      }),
    } as unknown as Response)

    await expect(getHealth()).resolves.toEqual({
      status: 'UP',
      service: 'mallota-backend',
    })

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/health$/),
      expect.any(Object),
    )
  })
})
