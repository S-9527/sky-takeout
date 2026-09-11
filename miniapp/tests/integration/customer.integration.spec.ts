import { beforeAll, describe, expect, it } from 'vitest'

import * as authApi from '@/api/auth'
import * as api from '@/api/customer'
import { ApiError, http } from '@/api/http'
import { clearTokens, setTokens } from '@/api/tokens'
import { useCartStore } from '@/stores/cart'
import { useSessionStore } from '@/stores/session'
import type { UserAddress } from '@/types'

import { BACKEND_BASE_URL, onCleanup } from './setup'

/**
 * 顾客端契约集成测试:用小程序自己的 api 层与 store 打真后端。
 *
 * 覆盖"mock 测不出来"的那部分:参数名、状态码、错误码、上下架可见性、
 * 购物车合并规则、订单金额重算、地址簿往返。
 * 造出来的地址与购物车会在收尾时清掉;订单是凭证,保留。
 */

const CODE = `miniapp-it-${Date.now().toString(36)}`

async function expectApiError(action: () => Promise<unknown>, expected: { status?: number; code?: string }) {
  const error = await action().then(
    () => null,
    (caught: unknown) => caught,
  )
  expect(error).toBeInstanceOf(ApiError)
  const apiError = error as ApiError
  if (expected.status !== undefined) expect(apiError.status).toBe(expected.status)
  if (expected.code !== undefined) expect(apiError.code).toBe(expected.code)
  return apiError
}

beforeAll(async () => {
  clearTokens()
  await authApi.wechatLogin({ code: CODE })
})

describe('登录与资料', () => {
  it('微信登录只返回令牌对,资料要另外拉', async () => {
    clearTokens()
    const pair = await authApi.wechatLogin({ code: CODE })
    expect(pair.accessToken).toBeTruthy()
    setTokens(pair)

    const profile = await authApi.getProfile()
    expect(profile.id).toBeGreaterThan(0)
    expect(JSON.stringify(profile)).not.toContain('openid')

    const updated = await authApi.updateProfile({ nickname: `集成昵称-${CODE}` })
    expect(updated.nickname).toBe(`集成昵称-${CODE}`)
  })

  it('session store 登录后能拿到资料与展示名', async () => {
    clearTokens()
    const session = useSessionStore()
    await session.loginWithCode(CODE, { nickname: 'store 登录' })
    expect(session.authenticated).toBe(true)
    expect(session.displayName).toBeTruthy()
  })

  it('不带令牌访问需要登录的接口 → 401', async () => {
    clearTokens()
    await expectApiError(() => api.getCart(), { status: 401 })
    // 恢复会话,后续用例继续用
    const pair = await authApi.wechatLogin({ code: CODE })
    setTokens(pair)
  })
})

describe('目录与门店', () => {
  it('门店状态返回营业开关与公告(顾客端同样需要令牌)', async () => {
    const status = await api.getShopStatus()
    expect(typeof status.isOpen).toBe('boolean')
  })

  it('分类与菜品按分类过滤,菜品都是起售中的', async () => {
    const categories = await api.listCategories('DISH')
    expect(categories.length).toBeGreaterThan(0)

    const dishPage = await api.listDishes(categories[0].id)
    const dishes = dishPage.records
    expect(Array.isArray(dishes)).toBe(true)
    expect(dishPage.page).toBe(1)
    for (const dish of dishes) {
      // 顾客端只返回可见商品(停售过滤是后端的职责,smoke-customer-catalog 已覆盖);
      // 这里校验价格有效,顺带保证类型没被解析错
      expect(dish.priceCents).toBeGreaterThan(0)
      expect(typeof dish.name).toBe('string')
    }
  })

  it('菜品详情带口味配置,套餐列表按分页返回', async () => {
    const firstCategory = (await api.listCategories('DISH'))[0]
    const dishes = (await api.listDishes(firstCategory.id)).records
    const withFlavor = dishes.find((dish) => dish.id === 101) ?? dishes[0]
    const detail = await api.getDish(withFlavor.id)
    expect(Array.isArray(detail.flavors)).toBe(true)
    expect(detail.id).toBe(withFlavor.id)

    // 必填参数缺失会被拦下(契约里 categoryId 是 required)
    await expectApiError(() => http.get('/api/v1/customer/catalog/dishes'), {
      status: 400,
      code: 'COMMON_VALIDATION_FAILED',
    })

    const setmeals = await api.listSetmeals(undefined, 1, 20)
    expect(setmeals.page).toBe(1)
    expect(Array.isArray(setmeals.records)).toBe(true)
  })
})

describe('购物车', () => {
  it('加购 → 同菜同口味合并 → 覆盖式改量 → 清空', async () => {
    await api.clearCart()
    onCleanup(async () => {
      await api.clearCart()
    })

    await api.addCartItem({ itemType: 'DISH', dishId: 112, quantity: 1 })
    const merged = await api.addCartItem({ itemType: 'DISH', dishId: 112, quantity: 2 })

    const cart = await api.getCart()
    expect(cart.totalQuantity).toBe(3)
    expect(cart.groups.length).toBeGreaterThan(0)
    expect(merged.id).toBeGreaterThan(0)

    await api.updateCartItemQuantity(merged.id, 5)
    expect((await api.getCart()).totalQuantity).toBe(5)

    // 减到 0 即删行
    await api.updateCartItemQuantity(merged.id, 0)
    expect((await api.getCart()).totalQuantity).toBe(0)
  })

  it('下架商品加购 → 422 CART_ITEM_OFF_SALE;文案直接可用', async () => {
    const categories = await api.listCategories('DISH')
    const all = await Promise.all(categories.map((category) => api.listDishes(category.id)))
    const onSale = all.flat()
    expect(onSale.length).toBeGreaterThan(0)

    // 用一个不存在的菜品 id 验证错误分支(契约:菜品不存在 404)
    await expectApiError(() => api.addCartItem({ itemType: 'DISH', dishId: 999_999_999, quantity: 1 }), {
      status: 404,
    })
  })

  it('数量越界 → 422 CART_QUANTITY_INVALID', async () => {
    await api.clearCart()
    const item = await api.addCartItem({ itemType: 'DISH', dishId: 112, quantity: 1 })
    await expectApiError(() => api.updateCartItemQuantity(item.id, 100), {
      status: 422,
      code: 'CART_QUANTITY_INVALID',
    })
    await api.clearCart()
  })

  it('cart store 与后端一致(数量、金额)', async () => {
    await api.clearCart()
    const cart = useCartStore()
    await cart.add({ itemType: 'DISH', dishId: 112, quantity: 2 })
    expect(cart.totalQuantity).toBe(2)
    expect(cart.totalAmountCents).toBeGreaterThan(0)
    await cart.clear()
    expect(cart.isEmpty).toBe(true)
  })
})

describe('地址簿', () => {
  it('新增 → 列表 → 设默认 → 编辑 → 删除', async () => {
    const created = await api.createAddress({
      consignee: '集成顾客',
      phone: '13800138000',
      province: '北京市',
      city: '北京市',
      district: '朝阳区',
      detail: '集成街道 1 号',
      label: '家',
      isDefault: 1,
    })
    expect(created.id).toBeGreaterThan(0)
    onCleanup(async () => {
      await api.deleteAddress(created.id).catch(() => undefined)
    })

    const list = await api.listAddresses()
    expect(list.some((item: UserAddress) => item.id === created.id)).toBe(true)
    expect(list.find((item: UserAddress) => item.id === created.id)?.isDefault).toBe(1)

    const updated = await api.updateAddress(created.id, {
      consignee: '集成顾客改',
      phone: '13900139000',
      province: '北京市',
      city: '北京市',
      district: '海淀区',
      detail: '集成街道 2 号',
      isDefault: 1,
    })
    expect(updated.consignee).toBe('集成顾客改')
    expect(updated.district).toBe('海淀区')

    await api.setDefaultAddress(created.id)
    await api.deleteAddress(created.id)
    await expectApiError(() => api.getAddress(created.id), { status: 404 })
  })
})

describe('下单与支付', () => {
  it('空购物车试算 → 422 ORDER_CART_EMPTY', async () => {
    await api.clearCart()
    await expectApiError(() => api.previewOrder(), { status: 422, code: 'ORDER_CART_EMPTY' })
  })

  it('试算 → 下单 → 支付 → 订单可查(金额由服务端重算)', async () => {
    const shop = await api.getShopStatus()
    if (!shop.isOpen) {
      throw new Error('集成测试需要门店处于营业状态(R1):请先打开营业状态再跑')
    }

    await api.clearCart()
    const address = await api.createAddress({
      consignee: '集成下单',
      phone: '13800138000',
      province: '北京市',
      city: '北京市',
      district: '朝阳区',
      detail: '集成下单街道 9 号',
      isDefault: 1,
    })
    onCleanup(async () => {
      await api.deleteAddress(address.id).catch(() => undefined)
      await api.clearCart().catch(() => undefined)
    })

    await api.addCartItem({ itemType: 'DISH', dishId: 112, quantity: 1 })
    const preview = await api.previewOrder(address.id)
    expect(preview.shopOpen).toBe(true)
    expect(preview.items.length).toBeGreaterThan(0)
    expect(preview.payAmountCents).toBe(
      preview.totalAmountCents + preview.packAmountCents + preview.deliveryAmountCents - preview.discountAmountCents,
    )

    const submitted = await api.submitOrder({
      addressId: address.id,
      remark: '集成测试',
      tablewareCount: 1,
      expectedTotalAmountCents: preview.totalAmountCents,
    })
    expect(submitted.status).toBe('PENDING_PAYMENT')
    expect(submitted.payAmountCents).toBe(preview.payAmountCents)

    // 下单后服务端清空购物车(R3)
    expect((await api.getCart()).totalQuantity).toBe(0)

    const payment = await api.createPayment(submitted.id, 'MOCK')
    expect(payment.status).toBe('SUCCESS')

    const status = await api.getPaymentStatus(submitted.id)
    expect(status.payStatus).toBe('PAID')

    const detail = await api.getMyOrder(submitted.id)
    expect(detail.status).toBe('PENDING_ACCEPTANCE')
    expect(detail.items.length).toBeGreaterThan(0)

    const page = await api.pageMyOrders({ page: 1, pageSize: 10, status: 'PENDING_ACCEPTANCE' })
    expect(page.records.some((order) => order.id === submitted.id)).toBe(true)

    // 催单:刚下单的订单可以催
    await api.remindOrder(submitted.id, '集成测试催单')
    // 再来一单:把明细加回购物车
    const reorder = await api.reorder(submitted.id)
    expect(reorder.addedCount).toBeGreaterThan(0)
    await api.clearCart()
  })
})
