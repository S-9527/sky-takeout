import { expect } from 'vitest'

import * as authApi from '@/api/employee'
import { ApiError } from '@/api/http'
import { clearTokens, setTokens } from '@/api/tokens'
import type { TokenPair } from '@/types'

import { BACKEND_BASE_URL, onCleanup } from './setup'

// 让用例从一个地方取后端地址
export { BACKEND_BASE_URL }

/**
 * 集成测试夹具。
 *
 * 两个来源要分清:
 * - **管理端动作**一律走 `src/api/**`(那才是被测对象);
 * - **顾客端动作**(造一张已支付订单给管理端操作)前端没有对应代码,
 *   用原生 `fetch` 造数据 —— 它是夹具,不是被测代码。
 */

export const ADMIN = { username: 'admin', password: '123456' } as const
export const STAFF = { username: 'zhangsan', password: '123456' } as const

/** 用管理端 API 登录并写入令牌仓库(后续请求自动带上 Bearer) */
export async function loginAsAdmin(): Promise<TokenPair> {
  clearTokens()
  const pair = await authApi.login({ ...ADMIN })
  setTokens(pair)
  return pair
}

export async function loginAsStaff(): Promise<TokenPair> {
  clearTokens()
  const pair = await authApi.login({ ...STAFF })
  setTokens(pair)
  return pair
}

export interface RawCallResult<T> {
  status: number
  json: T
}

export async function rawCall<T = any>(
  method: string,
  path: string,
  options: { token?: string; body?: unknown } = {},
): Promise<{ status: number; json: T }> {
  const response = await fetch(`${BACKEND_BASE_URL}${path}`, {
    method,
    headers: {
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}),
    },
    body: options.body ? JSON.stringify(options.body) : undefined,
  })
  const text = await response.text()
  let json: unknown = null
  if (text) {
    try {
      json = JSON.parse(text)
    } catch {
      json = text
    }
  }
  return { status: response.status, json: json as T }
}

/** 顾客端登录:mock 渠道下 code 任意字符串都能换到令牌(每次同一 code 复用同一顾客) */
export async function customerLogin(code: string): Promise<string> {
  const response = await rawCall<{ accessToken: string }>('POST', '/api/v1/customer/auth/wechat-login', {
    body: { code },
  })
  expect(response.status, `顾客登录失败:${JSON.stringify(response.json)}`).toBe(200)
  return response.json.accessToken
}

export interface PaidOrderFixture {
  orderId: number
  orderNo: string
  payAmountCents: number
}

/**
 * 造一张"已支付、待接单"的订单。
 *
 * 顾客端流程:登录 → 建地址 → 加购 → 下单 → 用 MOCK 渠道支付。
 * mock 渠道发起即成功,所以订单支付后直接进入 `PENDING_ACCEPTANCE`。
 *
 * 会先确保门店是营业状态(R1:打烊时不能下单),用完后还原成原来的状态。
 */
export async function createPaidOrder(
  code: string,
  dish: { dishId: number; quantity: number } = { dishId: 112, quantity: 1 },
): Promise<PaidOrderFixture> {
  const token = await customerLogin(code)

  const shop = await rawCall<{ isOpen: boolean }>('GET', '/api/v1/admin/shop/status')
  if (shop.status === 200 && !shop.json.isOpen) {
    await rawCall('PUT', '/api/v1/admin/shop/status', { body: { isOpen: true } })
    onCleanup(async () => {
      await rawCall('PUT', '/api/v1/admin/shop/status', { body: { isOpen: false } })
    })
  }

  // 购物车可能是脏的,先清空(顾客自己造成的状态,夹具负责还原)
  await rawCall('DELETE', '/api/v1/customer/cart/items', { token })
  onCleanup(async () => {
    await rawCall('DELETE', '/api/v1/customer/cart/items', { token })
  })

  const address = await rawCall<{ id: number }>('POST', '/api/v1/customer/addresses', {
    token,
    body: {
      consignee: '集成测试收货人',
      phone: '13800138000',
      province: '北京市',
      city: '北京市',
      district: '朝阳区',
      detail: '集成测试街道 1 号',
      label: '公司',
      isDefault: 1,
    },
  })
  expect(address.status, `建地址失败:${JSON.stringify(address.json)}`).toBe(201)
  const addressId = address.json.id
  onCleanup(async () => {
    await rawCall('DELETE', `/api/v1/customer/addresses/${addressId}`, { token })
  })

  const added = await rawCall('POST', '/api/v1/customer/cart/items', {
    token,
    body: { itemType: 'DISH', dishId: dish.dishId, quantity: dish.quantity },
  })
  expect(added.status, `加购失败:${JSON.stringify(added.json)}`).toBe(201)

  const submitted = await rawCall<{ id: number; orderNo: string; payAmountCents: number }>(
    'POST',
    '/api/v1/customer/orders',
    { token, body: { addressId, remark: '集成测试订单' } },
  )
  expect(submitted.status, `下单失败:${JSON.stringify(submitted.json)}`).toBe(201)

  const orderId = submitted.json.id
  const paid = await rawCall('POST', `/api/v1/customer/orders/${orderId}/payments`, {
    token,
    body: { channel: 'MOCK' },
  })
  expect(paid.status, `发起支付失败:${JSON.stringify(paid.json)}`).toBe(201)

  return {
    orderId,
    orderNo: submitted.json.orderNo,
    payAmountCents: submitted.json.payAmountCents,
  }
}

/** 断言某个调用以指定的业务错误码失败 */
export async function expectApiError(
  action: () => Promise<unknown>,
  expected: { status?: number; code?: string },
): Promise<ApiError> {
  const error = await action().then(
    () => null,
    (caught: unknown) => caught,
  )
  expect(error, '期望抛错,但调用成功了').toBeInstanceOf(ApiError)
  const apiError = error as ApiError
  if (expected.status !== undefined) {
    expect(apiError.status, `错误码 ${apiError.code} 的 HTTP 状态不符:${apiError.message}`).toBe(
      expected.status,
    )
  }
  if (expected.code !== undefined) {
    expect(apiError.code).toBe(expected.code)
  }
  return apiError
}

/** 断言某个调用成功(失败时把后端 message 带进断言信息) */
export async function expectOk<T>(action: () => Promise<T>): Promise<T> {
  try {
    return await action()
  } catch (error) {
    const detail = error instanceof ApiError ? `${error.status} ${error.code} ${error.message}` : String(error)
    throw new Error(`期望成功,实际失败:${detail}`)
  }
}
