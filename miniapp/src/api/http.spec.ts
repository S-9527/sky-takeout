import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { lastRequest, respondWith, respondWithFailure } from '@/tests/uni'
import type { TokenPair } from '@/types'

import { ApiError, http, onAuthExpired, refreshTokenPair, resetRefreshTransport, setRefreshTransport } from './http'
import { clearTokens, getAccessToken, getRefreshToken, resetTokenCacheForTest, setTokens } from './tokens'

const pair = (access: string, refresh: string): TokenPair => ({
  accessToken: access,
  refreshToken: refresh,
  expiresIn: 7200,
  refreshExpiresIn: 604800,
  tokenType: 'Bearer',
})

function bodyOf(request: { data?: unknown }): any {
  return request.data
}

beforeEach(() => {
  resetTokenCacheForTest()
  clearTokens()
  resetRefreshTransport()
  onAuthExpired(null)
})

afterEach(() => {
  resetRefreshTransport()
  onAuthExpired(null)
})

describe('请求与令牌', () => {
  it('有令牌时带 Bearer 头,响应体直接返回', async () => {
    setTokens(pair('access-1', 'refresh-1'))
    respondWith([{ status: 200, data: { nickname: '小明' } }])

    const profile = await http.get<{ nickname: string }>('/api/v1/customer/profile')

    expect(profile.nickname).toBe('小明')
    expect(lastRequest().header?.Authorization).toBe('Bearer access-1')
    expect(lastRequest().method).toBe('GET')
  })

  it('204 没有响应体时返回 undefined,不尝试解析', async () => {
    setTokens(pair('a', 'r'))
    respondWith([{ status: 204, data: '' }])
    await expect(http.delete('/api/v1/customer/cart/items')).resolves.toBeUndefined()
  })

  it('业务错误体归一成 ApiError,保留 code 与提示', async () => {
    setTokens(pair('a', 'r'))
    respondWith([
      {
        status: 422,
        data: { code: 'CART_QUANTITY_INVALID', message: '数量不合理,请重新输入', traceId: 't1' },
      },
    ])

    const error = (await http.put('/api/v1/customer/cart/items/1/quantity', { quantity: 999 }).catch((e) => e)) as ApiError
    expect(error).toBeInstanceOf(ApiError)
    expect(error.code).toBe('CART_QUANTITY_INVALID')
    expect(error.message).toBe('数量不合理,请重新输入')
    expect(error.traceId).toBe('t1')
  })

  it('拿不到响应(断网)→ status 0', async () => {
    setTokens(pair('a', 'r'))
    respondWithFailure('request:fail')
    const error = (await http.get('/api/v1/customer/cart/items').catch((e) => e)) as ApiError
    expect(error.status).toBe(0)
    expect(error.isNetworkError).toBe(true)
  })
})

describe('401 刷新与重试', () => {
  it('access 过期 → 刷新一次并用新令牌重放', async () => {
    setTokens(pair('access-1', 'refresh-1'))
    const refreshSpy = vi.fn(async () => pair('access-2', 'refresh-2'))
    setRefreshTransport(refreshSpy)

    respondWith([
      { status: 401, data: { code: 'AUTH_TOKEN_EXPIRED', message: '登录状态已过期', traceId: 't' } },
      { status: 200, data: { totalQuantity: 2 } },
    ])

    const cart = await http.get<{ totalQuantity: number }>('/api/v1/customer/cart/items')

    expect(cart.totalQuantity).toBe(2)
    expect(refreshSpy).toHaveBeenCalledWith('refresh-1')
    expect(getAccessToken()).toBe('access-2')
  })

  it('并发 401 只刷新一次(单飞)', async () => {
    setTokens(pair('access-1', 'refresh-1'))
    let count = 0
    setRefreshTransport(async () => {
      count += 1
      await new Promise((resolve) => setTimeout(resolve, 5))
      return pair('access-2', 'refresh-2')
    })

    const attempts = new Map<string, number>()
    const { uniTestDouble } = await import('@/tests/setup')
    uniTestDouble.handler = (options) => {
      const path = options.url
      const seen = attempts.get(path) ?? 0
      attempts.set(path, seen + 1)
      if (seen === 0) {
        options.success?.({ statusCode: 401, data: { code: 'AUTH_TOKEN_EXPIRED', message: '过期' } })
      } else {
        options.success?.({ statusCode: 200, data: { ok: true } })
      }
    }

    await Promise.all([http.get('/api/v1/customer/cart/items'), http.get('/api/v1/customer/profile')])
    expect(count).toBe(1)
  })

  it('刷新失败 → 清令牌并通知应用跳登录', async () => {
    setTokens(pair('access-1', 'refresh-1'))
    setRefreshTransport(async () => {
      throw new ApiError({ status: 401, code: 'AUTH_REFRESH_TOKEN_INVALID', message: '登录已失效' })
    })
    const expired = vi.fn()
    onAuthExpired(expired)
    respondWith([{ status: 401, data: { code: 'AUTH_TOKEN_EXPIRED', message: '过期' } }])

    await expect(http.get('/api/v1/customer/cart/items')).rejects.toMatchObject({
      code: 'AUTH_REFRESH_TOKEN_INVALID',
    })
    expect(getRefreshToken()).toBeNull()
    expect(expired).toHaveBeenCalledTimes(1)
  })

  it('重放后仍 401 不再重试(防无限循环)', async () => {
    setTokens(pair('access-1', 'refresh-1'))
    setRefreshTransport(async () => pair('access-2', 'refresh-2'))
    respondWith([{ status: 401, data: { code: 'AUTH_TOKEN_EXPIRED', message: '过期' } }])

    await expect(http.get('/api/v1/customer/cart/items')).rejects.toMatchObject({ status: 401 })
  })

  it('登录/刷新接口自身不参与刷新重试', async () => {
    const refreshSpy = vi.fn()
    setRefreshTransport(refreshSpy)
    respondWith([{ status: 401, data: { code: 'AUTH_BAD_CREDENTIALS', message: '登录失败' } }])

    const { wechatLogin } = await import('./auth')
    await expect(wechatLogin({ code: 'x' })).rejects.toMatchObject({ code: 'AUTH_BAD_CREDENTIALS' })
    expect(refreshSpy).not.toHaveBeenCalled()
  })
})

describe('refreshTokenPair', () => {
  it('没有 refresh token 时直接失败,不发请求', async () => {
    clearTokens()
    await expect(refreshTokenPair()).rejects.toMatchObject({ status: 401 })
  })

  it('失败后不会把失败结果缓存住', async () => {
    setTokens(pair('a', 'r1'))
    setRefreshTransport(async () => {
      throw new Error('boom')
    })
    await expect(refreshTokenPair()).rejects.toThrow()
    setRefreshTransport(async () => pair('a2', 'r2'))
    await expect(refreshTokenPair()).resolves.toMatchObject({ accessToken: 'a2' })
  })
})

describe('参数与请求体', () => {
  it('GET 的查询参数由 uni.request 的 data 承载', async () => {
    setTokens(pair('a', 'r'))
    respondWith([{ status: 200, data: { records: [] } }])
    await http.get('/api/v1/customer/orders', { data: { page: 1, pageSize: 10 } })
    expect(bodyOf(lastRequest())).toEqual({ page: 1, pageSize: 10 })
    expect(lastRequest().url).toContain('/api/v1/customer/orders')
  })

  it('POST 会带 Content-Type', async () => {
    setTokens(pair('a', 'r'))
    respondWith([{ status: 201, data: {} }])
    await http.post('/api/v1/customer/cart/items', { itemType: 'DISH', dishId: 1, quantity: 1 })
    expect(lastRequest().header?.['Content-Type']).toBe('application/json')
  })
})
