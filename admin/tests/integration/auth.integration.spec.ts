import axios from 'axios'
import { afterEach, describe, expect, it } from 'vitest'

import * as authApi from '@/api/employee'
import { ApiError, http, refreshTokenPair } from '@/api/http'
import { pageOrders } from '@/api/order'
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from '@/api/tokens'

import {
  ADMIN,
  BACKEND_BASE_URL,
  expectApiError,
  expectOk,
  loginAsAdmin,
  loginAsStaff,
} from './helpers'

/**
 * 认证链路集成测试:登录、资料、刷新旋转、登出、越权。
 *
 * 这些是最不该出错的一环 —— access token 过期后前端能不能自动续上、
 * refresh 旋转后旧令牌是否真的失效,只有打真后端(真 Redis 里的 refresh 记录)才验证得了。
 */

afterEach(() => {
  clearTokens()
})

describe('登录与资料', () => {
  it('管理员登录:令牌对 + 资料不含密码', async () => {
    const pair = await expectOk(() => loginAsAdmin())
    expect(pair.tokenType).toBe('Bearer')
    expect(pair.expiresIn).toBeGreaterThan(0)
    expect(pair.refreshExpiresIn).toBeGreaterThan(pair.expiresIn)

    const me = await expectOk(() => authApi.fetchCurrentEmployee())
    expect(me.username).toBe(ADMIN.username)
    expect(me.role).toBe('ADMIN')
    expect(me.status).toBe(1)
    expect(JSON.stringify(me)).not.toContain('password')
  })

  it('普通员工登录后角色是 STAFF', async () => {
    await expectOk(() => loginAsStaff())
    const me = await expectOk(() => authApi.fetchCurrentEmployee())
    expect(me.role).toBe('STAFF')
  })

  it('密码错误 → 401 AUTH_BAD_CREDENTIALS,且提示不区分用户不存在', async () => {
    const wrongPassword = await expectApiError(
      () => authApi.login({ username: ADMIN.username, password: 'definitely-wrong' }),
      { status: 401, code: 'AUTH_BAD_CREDENTIALS' },
    )
    const unknownUser = await expectApiError(
      () => authApi.login({ username: 'no-such-user', password: 'whatever' }),
      { status: 401, code: 'AUTH_BAD_CREDENTIALS' },
    )
    expect(wrongPassword.message).toBe(unknownUser.message)
  })

  it('缺参数 → 400 COMMON_VALIDATION_FAILED', async () => {
    await expectApiError(() => authApi.login({ username: '', password: '' }), {
      status: 400,
      code: 'COMMON_VALIDATION_FAILED',
    })
  })

  it('改密码时旧密码错误 → 401,且不影响后续登录', async () => {
    await expectOk(() => loginAsAdmin())
    await expectApiError(
      () => authApi.changePassword({ oldPassword: 'wrong-old', newPassword: 'whatever123' }),
      { status: 401, code: 'AUTH_BAD_CREDENTIALS' },
    )
    clearTokens()
    await expectOk(() => loginAsAdmin())
  })
})

describe('令牌刷新', () => {
  it('刷新会旋转 refresh token,旧 refresh 立即失效', async () => {
    const pair = await expectOk(() => loginAsAdmin())
    const rotated = await expectOk(() => authApi.refresh(pair.refreshToken))

    expect(rotated.refreshToken).not.toBe(pair.refreshToken)
    expect(rotated.accessToken).not.toBe(pair.accessToken)

    await expectApiError(() => authApi.refresh(pair.refreshToken), {
      status: 401,
      code: 'AUTH_REFRESH_TOKEN_INVALID',
    })
  })

  it('access token 失效时,http 拦截器自动刷新并重放原请求', async () => {
    const pair = await expectOk(() => loginAsAdmin())
    // 故意把 access 换成无效值,refresh 保持有效
    setTokens({ ...pair, accessToken: 'not-a-real-token' })

    const page = await expectOk(() => pageOrders({ page: 1, pageSize: 1 }))

    expect(page.records.length).toBeLessThanOrEqual(1)
    // 拦截器刷新后令牌仓库里已经是新的 access
    expect(getAccessToken()).toBeTruthy()
    expect(getAccessToken()).not.toBe('not-a-real-token')
  })

  it('refresh 也失效时:清空令牌并抛错(onAuthExpired 会跳登录)', async () => {
    const pair = await expectOk(() => loginAsAdmin())
    setTokens({ ...pair, accessToken: 'not-a-real-token', refreshToken: 'not-a-real-refresh' })

    const error = await expectApiError(() => pageOrders({ page: 1 }), { status: 401 })
    expect(error.code.startsWith('AUTH_')).toBe(true)
    expect(getRefreshToken()).toBeNull()
  })

  it('并发请求同时过期只刷新一次,不会把彼此挤下线', async () => {
    const pair = await expectOk(() => loginAsAdmin())
    setTokens({ ...pair, accessToken: 'not-a-real-token' })

    const [orders, employees] = await Promise.all([
      expectOk(() => pageOrders({ page: 1, pageSize: 1 })),
      expectOk(() => authApi.pageEmployees({ page: 1, pageSize: 1 })),
    ])

    expect(orders.page).toBe(1)
    expect(employees.page).toBe(1)
  })
})

describe('登出与越权', () => {
  it('登出后 refresh token 立即失效;重复登出仍然返回成功(幂等)', async () => {
    const pair = await expectOk(() => loginAsAdmin())
    await expectOk(() => authApi.logout(pair.refreshToken))
    // 幂等:同一个 refresh token 再登出一次也是 204(此时 access token 仍有效)
    await expectOk(() => authApi.logout(pair.refreshToken))
    // 但用它换新令牌已经不行了
    await expectApiError(() => authApi.refresh(pair.refreshToken), {
      status: 401,
      code: 'AUTH_REFRESH_TOKEN_INVALID',
    })
  })

  it('未带令牌:请求 401,并且前端把会话判定为失效(令牌被清空)', async () => {
    clearTokens()
    const error = await expectApiError(() => authApi.fetchCurrentEmployee(), { status: 401 })
    // 客户端先拿到 AUTH_TOKEN_INVALID,尝试刷新;没有 refresh token,最终以
    // AUTH_REFRESH_TOKEN_INVALID 收口 —— 对用户来说都是"请重新登录"
    expect(['AUTH_TOKEN_INVALID', 'AUTH_REFRESH_TOKEN_INVALID', 'AUTH_SUBJECT_NOT_FOUND']).toContain(
      error.code,
    )
    expect(getRefreshToken()).toBeNull()
  })

  it('STAFF 能看订单,但不能管员工、不能发起退款', async () => {
    await expectOk(() => loginAsStaff())

    await expectOk(() => pageOrders({ page: 1, pageSize: 1 }))
    await expectApiError(() => authApi.pageEmployees({ page: 1 }), {
      status: 403,
      code: 'AUTH_PERMISSION_DENIED',
    })
    await expectApiError(
      () => import('@/api/refund').then((api) => api.createRefund({} as never)),
      { status: 403, code: 'AUTH_PERMISSION_DENIED' },
    )
  })

  it('顾客令牌打管理端接口 → 403 AUTH_AUDIENCE_MISMATCH', async () => {
    const { customerLogin } = await import('./helpers')
    const customerToken = await customerLogin('integration-audience')

    const response = await http
      .get('/api/v1/admin/orders', {
        headers: { Authorization: `Bearer ${customerToken}` },
      })
      .then(
        () => null,
        (error: unknown) => error,
      )

    expect(response).toBeInstanceOf(ApiError)
    expect((response as ApiError).status).toBe(403)
  })

  it('伪造的令牌签名无效 → 后端直接给 401 AUTH_TOKEN_INVALID', async () => {
    // 这里绕过前端拦截器:拦截器看到 401 会先去刷新,最终报的是刷新失败;
    // 要验证"后端怎么判伪造签名",用裸请求才能看到原始错误码。
    const response = await axios
      .get(`${BACKEND_BASE_URL}/api/v1/admin/auth/me`, {
        headers: { Authorization: 'Bearer eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiIxIn0.forged' },
        validateStatus: () => true,
      })
      .then((result) => result)

    expect(response.status).toBe(401)
    expect(response.data.code).toBe('AUTH_TOKEN_INVALID')
  })

  it('前端拦截器会把伪造令牌的原始错误码收敛成"会话失效"', async () => {
    setTokens({
      accessToken: 'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiIxIn0.forged',
      refreshToken: 'rt-forged',
      expiresIn: 7200,
      refreshExpiresIn: 604800,
      tokenType: 'Bearer',
    })
    const error = await expectApiError(() => authApi.fetchCurrentEmployee(), { status: 401 })
    expect(error.code).toBe('AUTH_REFRESH_TOKEN_INVALID')
    expect(getRefreshToken()).toBeNull()
  })

  it('refreshTokenPair 在没有 refresh token 时直接失败,不发无意义请求', async () => {
    clearTokens()
    await expect(refreshTokenPair()).rejects.toMatchObject({ status: 401 })
  })
})
