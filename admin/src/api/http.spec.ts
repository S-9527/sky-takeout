import { AxiosError, type AxiosAdapter, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { pageEmployees } from './employee'
import {
  ApiError,
  http,
  onAuthExpired,
  refreshTokenPair,
  resetRefreshTransport,
  setRefreshTransport,
  toApiError,
} from './http'
import { acceptOrder, pageOrders } from './order'
import { clearTokens, getAccessToken, getTokens, setTokens } from './tokens'
import type { TokenPair } from '@/types'

/**
 * 这些用例走的是**真的** axios 拦截器链,只把适配器换成内联实现。
 *
 * 比 `vi.mock('axios')` 更值:被验证的正是"我们自己写的拦截器"
 * —— 令牌注入、401 刷新重试、错误归一,这些都是 mock 掉 axios 后测不到的东西。
 */
interface StubResponse {
  status: number
  data?: unknown
  headers?: Record<string, string>
}

function makeAdapter(
  handler: (config: InternalAxiosRequestConfig, attempt: number) => StubResponse,
): { adapter: AxiosAdapter; requests: InternalAxiosRequestConfig[] } {
  const requests: InternalAxiosRequestConfig[] = []
  const adapter: AxiosAdapter = async (config) => {
    requests.push(config)
    const result = handler(config, requests.length)
    const response: AxiosResponse = {
      data: result.data,
      status: result.status,
      statusText: String(result.status),
      headers: result.headers ?? {},
      config,
    }
    if (result.status >= 200 && result.status < 300) return response
    throw new AxiosError(`Request failed with status code ${result.status}`, 'ERR_BAD_REQUEST', config, null, response)
  }
  return { adapter, requests }
}

function tokenPair(accessToken: string, refreshToken: string): TokenPair {
  return {
    accessToken,
    refreshToken,
    expiresIn: 7200,
    refreshExpiresIn: 604800,
    tokenType: 'Bearer',
  }
}

function errorBody(code: string, message: string) {
  return { code, message, traceId: 'trace-1' }
}

beforeEach(() => {
  clearTokens()
})

afterEach(() => {
  http.defaults.adapter = undefined
  clearTokens()
  resetRefreshTransport()
  onAuthExpired(null)
  vi.restoreAllMocks()
})

describe('请求拦截器', () => {
  it('有令牌时带上 Bearer 头,并把查询参数原样传出', async () => {
    setTokens(tokenPair('access-1', 'refresh-1'))
    const { adapter, requests } = makeAdapter(() => ({
      status: 200,
      data: { records: [], page: 1, pageSize: 20, total: 0 },
    }))
    http.defaults.adapter = adapter

    const result = await pageEmployees({ page: 1, pageSize: 20, sort: 'createdAt,desc' })

    expect(result.total).toBe(0)
    expect(requests).toHaveLength(1)
    expect(requests[0].headers.get('Authorization')).toBe('Bearer access-1')
    expect(requests[0].params).toEqual({ page: 1, pageSize: 20, sort: 'createdAt,desc' })
    expect(requests[0].url).toBe('/api/v1/admin/employees')
  })

  it('没令牌时不带 Authorization 头', async () => {
    const { adapter, requests } = makeAdapter(() => ({ status: 200, data: {} }))
    http.defaults.adapter = adapter
    await http.get('/api/v1/admin/orders/status-counts')
    expect(requests[0].headers.get('Authorization')).toBeUndefined()
  })

  it('204 响应没有响应体,不解析 JSON', async () => {
    setTokens(tokenPair('access-1', 'refresh-1'))
    const { adapter, requests } = makeAdapter(() => ({ status: 204 }))
    http.defaults.adapter = adapter

    await expect(acceptOrder(4001)).resolves.toBeUndefined()
    expect(requests[0].url).toBe('/api/v1/admin/orders/4001/acceptance')
  })
})

describe('401 刷新与重试', () => {
  it('access token 过期 → 刷新一次并用新令牌重放原请求', async () => {
    setTokens(tokenPair('access-1', 'refresh-1'))
    const refreshCalls: string[] = []
    setRefreshTransport(async (refreshToken) => {
      refreshCalls.push(refreshToken)
      return tokenPair('access-2', 'refresh-2')
    })

    const { adapter, requests } = makeAdapter((_config, attempt) => {
      if (attempt === 1) {
        return { status: 401, data: errorBody('AUTH_TOKEN_EXPIRED', '登录状态已过期') }
      }
      return { status: 200, data: { records: [], page: 1, pageSize: 20, total: 3 } }
    })
    http.defaults.adapter = adapter

    const result = await pageOrders({ page: 1 })

    expect(result.total).toBe(3)
    expect(refreshCalls).toEqual(['refresh-1'])
    expect(requests).toHaveLength(2)
    expect(requests[1].headers.get('Authorization')).toBe('Bearer access-2')
    expect(getAccessToken()).toBe('access-2')
    expect(getTokens()?.refreshToken).toBe('refresh-2')
  })

  it('并发请求同时 401 时只刷新一次(单飞)', async () => {
    setTokens(tokenPair('access-1', 'refresh-1'))
    let refreshCount = 0
    setRefreshTransport(async () => {
      refreshCount += 1
      await new Promise((resolve) => setTimeout(resolve, 5))
      return tokenPair('access-2', 'refresh-2')
    })

    const firstAttempts = new Set<string>()
    const { adapter } = makeAdapter((config) => {
      const url = config.url ?? ''
      if (!firstAttempts.has(url)) {
        firstAttempts.add(url)
        return { status: 401, data: errorBody('AUTH_TOKEN_EXPIRED', '登录状态已过期') }
      }
      return { status: 200, data: { records: [], page: 1, pageSize: 20, total: 1 } }
    })
    http.defaults.adapter = adapter

    await Promise.all([pageOrders({ page: 1 }), pageEmployees({ page: 1 })])

    expect(refreshCount).toBe(1)
    expect(firstAttempts.size).toBe(2)
  })

  it('刷新失败 → 清空令牌、通知应用跳登录,并抛出刷新错误', async () => {
    setTokens(tokenPair('access-1', 'refresh-1'))
    setRefreshTransport(async () => {
      throw new ApiError({
        status: 401,
        code: 'AUTH_REFRESH_TOKEN_EXPIRED',
        message: '登录已过期,请重新登录',
      })
    })
    const expired = vi.fn()
    onAuthExpired(expired)

    const { adapter } = makeAdapter(() => ({
      status: 401,
      data: errorBody('AUTH_TOKEN_EXPIRED', '登录状态已过期'),
    }))
    http.defaults.adapter = adapter

    await expect(pageOrders({ page: 1 })).rejects.toMatchObject({
      code: 'AUTH_REFRESH_TOKEN_EXPIRED',
      status: 401,
    })
    expect(getTokens()).toBeNull()
    expect(expired).toHaveBeenCalledTimes(1)
  })

  it('没有 refresh token 时不刷新,直接按会话失效处理', async () => {
    setTokens({ ...tokenPair('access-1', 'refresh-1'), refreshToken: '' })
    const refreshSpy = vi.fn()
    setRefreshTransport(refreshSpy)
    const expired = vi.fn()
    onAuthExpired(expired)

    const { adapter } = makeAdapter(() => ({
      status: 401,
      data: errorBody('AUTH_TOKEN_EXPIRED', '登录状态已过期'),
    }))
    http.defaults.adapter = adapter

    await expect(pageOrders({ page: 1 })).rejects.toBeInstanceOf(ApiError)
    expect(refreshSpy).not.toHaveBeenCalled()
    expect(expired).toHaveBeenCalled()
  })

  it('刷新接口自身返回 401 时不会递归刷新', async () => {
    setTokens(tokenPair('access-1', 'refresh-1'))
    const refreshSpy = vi.fn()
    setRefreshTransport(refreshSpy)
    const { adapter, requests } = makeAdapter(() => ({
      status: 401,
      data: errorBody('AUTH_REFRESH_TOKEN_INVALID', '登录状态已失效'),
    }))
    http.defaults.adapter = adapter

    const { refresh } = await import('./employee')
    await expect(refresh('refresh-1')).rejects.toMatchObject({ code: 'AUTH_REFRESH_TOKEN_INVALID' })
    expect(requests).toHaveLength(1)
    expect(refreshSpy).not.toHaveBeenCalled()
  })

  it('刷新成功后重放仍 401 时不再重试(避免无限循环)', async () => {
    setTokens(tokenPair('access-1', 'refresh-1'))
    setRefreshTransport(async () => tokenPair('access-2', 'refresh-2'))
    const { adapter, requests } = makeAdapter(() => ({
      status: 401,
      data: errorBody('AUTH_TOKEN_EXPIRED', '登录状态已过期'),
    }))
    http.defaults.adapter = adapter

    await expect(pageOrders({ page: 1 })).rejects.toMatchObject({ code: 'AUTH_TOKEN_EXPIRED' })
    expect(requests).toHaveLength(2)
  })

  it('业务类 401(用户名密码错误)不触发刷新', async () => {
    const refreshSpy = vi.fn()
    setRefreshTransport(refreshSpy)
    const { adapter } = makeAdapter(() => ({
      status: 401,
      data: errorBody('AUTH_BAD_CREDENTIALS', '用户名或密码错误'),
    }))
    http.defaults.adapter = adapter

    const { login } = await import('./employee')
    await expect(login({ username: 'admin', password: 'x' })).rejects.toMatchObject({
      code: 'AUTH_BAD_CREDENTIALS',
    })
    expect(refreshSpy).not.toHaveBeenCalled()
  })
})

describe('refreshTokenPair', () => {
  it('并发调用共用一个 Promise', async () => {
    setTokens(tokenPair('access-1', 'refresh-1'))
    let calls = 0
    setRefreshTransport(async () => {
      calls += 1
      await new Promise((resolve) => setTimeout(resolve, 5))
      return tokenPair('access-2', 'refresh-2')
    })

    const [a, b] = await Promise.all([refreshTokenPair(), refreshTokenPair()])
    expect(calls).toBe(1)
    expect(a.accessToken).toBe('access-2')
    expect(b.accessToken).toBe('access-2')
  })
})

describe('错误归一', () => {
  it('断网 → status 0 / NETWORK_ERROR', async () => {
    const { adapter } = makeAdapter(() => {
      throw new AxiosError('Network Error', 'ERR_NETWORK')
    })
    http.defaults.adapter = adapter

    const error = await pageOrders({ page: 1 }).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(0)
    expect((error as ApiError).code).toBe('NETWORK_ERROR')
    expect((error as ApiError).isNetworkError).toBe(true)
  })

  it('超时给出可读提示', async () => {
    const { adapter } = makeAdapter(() => {
      throw new AxiosError('timeout of 20000ms exceeded', 'ECONNABORTED')
    })
    http.defaults.adapter = adapter

    const error = (await pageOrders({ page: 1 }).catch((e: unknown) => e)) as ApiError
    expect(error.message).toContain('超时')
  })

  it('422 业务错误保留后端文案与字段细节', async () => {
    const { adapter } = makeAdapter(() => ({
      status: 422,
      data: {
        code: 'ORDER_INVALID_TRANSITION',
        message: 'DELIVERING 不能取消',
        details: [{ field: 'status', reason: 'DELIVERING 不能取消' }],
        traceId: 'trace-9',
      },
    }))
    http.defaults.adapter = adapter

    const error = (await pageOrders({ page: 1 }).catch((e: unknown) => e)) as ApiError
    expect(error.code).toBe('ORDER_INVALID_TRANSITION')
    expect(error.message).toBe('DELIVERING 不能取消')
    expect(error.details).toHaveLength(1)
    expect(error.traceId).toBe('trace-9')
    expect(error.isBusinessError).toBe(true)
  })

  it('后端返回非契约体(如网关 HTML)时按状态码兜底,并保留 X-Trace-Id', async () => {
    const { adapter } = makeAdapter(() => ({
      status: 500,
      data: '<html>oops</html>',
      headers: { 'x-trace-id': 'trace-html' },
    }))
    http.defaults.adapter = adapter

    const error = (await pageOrders({ page: 1 }).catch((e: unknown) => e)) as ApiError
    expect(error.code).toBe('COMMON_INTERNAL_ERROR')
    expect(error.message).toBe('服务器开小差了,请稍后重试')
    expect(error.traceId).toBe('trace-html')
  })

  it('toApiError 对普通异常也能收敛', () => {
    expect(toApiError(new Error('boom'))).toMatchObject({ code: 'UNKNOWN_ERROR', status: 0 })
    expect(toApiError('weird')).toMatchObject({ code: 'UNKNOWN_ERROR' })
    const apiError = new ApiError({ status: 404, code: 'X', message: 'y' })
    expect(toApiError(apiError)).toBe(apiError)
  })
})
