import type { APIRequestContext } from '@playwright/test'

/**
 * E2E 的"造数据"夹具。
 *
 * 走的是**顾客端**接口:管理端代码里没有下单能力,而验证来单提醒必须真有一张已支付订单。
 * 这里用 Playwright 的 `request`(Node 侧 HTTP)直连后端,属于夹具,不是被测代码。
 */

export const BACKEND = process.env.SKY_BACKEND ?? 'http://localhost:8080'

export const ADMIN = { username: 'admin', password: '123456' }
export const STAFF = { username: 'zhangsan', password: '123456' }

interface ApiResult<T> {
  status: number
  json: T
}

async function call<T = unknown>(
  request: APIRequestContext,
  method: 'get' | 'post' | 'put' | 'delete',
  path: string,
  options: { token?: string; data?: unknown } = {},
): Promise<ApiResult<T>> {
  const response = await request[method](`${BACKEND}${path}`, {
    headers: options.token ? { Authorization: `Bearer ${options.token}` } : {},
    ...(options.data === undefined ? {} : { data: options.data }),
  })
  let json: unknown = null
  const text = await response.text()
  if (text) {
    try {
      json = JSON.parse(text)
    } catch {
      json = text
    }
  }
  return { status: response.status(), json: json as T }
}

export interface E2ePaidOrder {
  orderId: number
  orderNo: string
  /** 用例结束前调用:清购物车与地址,别给库里留垃圾 */
  cleanup: () => Promise<void>
}

/**
 * 真下一单并支付(mock 渠道发起即成功)。
 *
 * 门店必须处于营业状态(R1),否则先打开,收尾时还原。
 */
export async function createPaidOrder(request: APIRequestContext): Promise<E2ePaidOrder> {
  const code = `e2e-notify-${Date.now().toString(36)}`

  const shop = await call<{ isOpen: boolean }>(request, 'get', '/api/v1/admin/shop/status')
  let restoreShop = false
  if (shop.status === 200 && !shop.json.isOpen) {
    await call(request, 'put', '/api/v1/admin/shop/status', { data: { isOpen: true } })
    restoreShop = true
  }

  const login = await call<{ accessToken: string }>(request, 'post', '/api/v1/customer/auth/wechat-login', {
    data: { code },
  })
  if (login.status !== 200) throw new Error(`顾客登录失败:${login.status} ${JSON.stringify(login.json)}`)
  const token = login.json.accessToken

  const address = await call<{ id: number }>(request, 'post', '/api/v1/customer/addresses', {
    token,
    data: {
      consignee: 'E2E 收货人',
      phone: '13800138000',
      province: '北京市',
      city: '北京市',
      district: '朝阳区',
      detail: 'E2E 街道 1 号',
      label: '公司',
      isDefault: 1,
    },
  })
  if (address.status !== 201) throw new Error(`建地址失败:${address.status} ${JSON.stringify(address.json)}`)

  await call(request, 'delete', '/api/v1/customer/cart/items', { token })
  const added = await call(request, 'post', '/api/v1/customer/cart/items', {
    token,
    data: { itemType: 'DISH', dishId: 112, quantity: 1 },
  })
  if (added.status !== 201) throw new Error(`加购失败:${added.status} ${JSON.stringify(added.json)}`)

  const submitted = await call<{ id: number; orderNo: string }>(request, 'post', '/api/v1/customer/orders', {
    token,
    data: { addressId: address.json.id, remark: 'E2E 来单提醒' },
  })
  if (submitted.status !== 201) throw new Error(`下单失败:${submitted.status} ${JSON.stringify(submitted.json)}`)

  const paid = await call(request, 'post', `/api/v1/customer/orders/${submitted.json.id}/payments`, {
    token,
    data: { channel: 'MOCK' },
  })
  if (paid.status !== 201) throw new Error(`支付失败:${paid.status} ${JSON.stringify(paid.json)}`)

  return {
    orderId: submitted.json.id,
    orderNo: submitted.json.orderNo,
    cleanup: async () => {
      await call(request, 'delete', `/api/v1/customer/addresses/${address.json.id}`, { token })
      await call(request, 'delete', '/api/v1/customer/cart/items', { token })
      if (restoreShop) {
        await call(request, 'put', '/api/v1/admin/shop/status', { data: { isOpen: false } })
      }
    },
  }
}
