import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as customerApi from '@/api/customer'
import type { CartView } from '@/types'

import { useCartStore } from './cart'

vi.mock('@/api/customer', () => ({
  getCart: vi.fn(),
  addCartItem: vi.fn(),
  updateCartItemQuantity: vi.fn(),
  clearCart: vi.fn(),
}))

const mocked = vi.mocked(customerApi)

function cartView(totalQuantity: number, totalAmountCents: number): CartView {
  return {
    totalQuantity,
    totalAmountCents,
    groups: [
      {
        categoryId: 10,
        categoryName: '川湘菜',
        items: [
          {
            id: 1,
            itemType: 'DISH',
            dishId: 101,
            name: '宫保鸡丁',
            quantity: totalQuantity,
            amountCents: totalAmountCents,
            available: true,
          },
        ],
      },
    ],
  }
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
})

describe('购物车 store', () => {
  it('刷新后按服务端返回计算总数与金额', async () => {
    mocked.getCart.mockResolvedValue(cartView(2, 7600))
    const cart = useCartStore()

    await cart.refresh()

    expect(cart.totalQuantity).toBe(2)
    expect(cart.totalAmountCents).toBe(7600)
    expect(cart.isEmpty).toBe(false)
    expect(cart.items).toHaveLength(1)
  })

  it('加购后重新拉取,不做本地累加(数量以服务端合并结果为准)', async () => {
    mocked.addCartItem.mockResolvedValue({} as never)
    mocked.getCart.mockResolvedValue(cartView(3, 11400))
    const cart = useCartStore()

    await cart.add({ itemType: 'DISH', dishId: 101 })

    expect(mocked.addCartItem).toHaveBeenCalledWith({ itemType: 'DISH', dishId: 101, quantity: 1 })
    expect(cart.totalQuantity).toBe(3)
  })

  it('改数量是覆盖式的,并带上口味不影响其它行', async () => {
    mocked.updateCartItemQuantity.mockResolvedValue(undefined)
    mocked.getCart.mockResolvedValue(cartView(5, 19000))
    const cart = useCartStore()

    await cart.setQuantity(1, 5)

    expect(mocked.updateCartItemQuantity).toHaveBeenCalledWith(1, 5)
    expect(cart.totalQuantity).toBe(5)
  })

  it('减到 0 即删行(契约没有单行删除接口)', async () => {
    mocked.updateCartItemQuantity.mockResolvedValue(undefined)
    mocked.getCart.mockResolvedValue(cartView(0, 0))
    const cart = useCartStore()

    await cart.remove(1)

    expect(mocked.updateCartItemQuantity).toHaveBeenCalledWith(1, 0)
    expect(cart.isEmpty).toBe(true)
  })

  it('清空后本地视图为空', async () => {
    mocked.clearCart.mockResolvedValue(undefined)
    mocked.getCart.mockResolvedValue(cartView(0, 0))
    const cart = useCartStore()

    await cart.clear()

    expect(mocked.clearCart).toHaveBeenCalled()
    expect(cart.totalQuantity).toBe(0)
  })

  it('下单成功后只清本地缓存,不再打接口', async () => {
    const cart = useCartStore()
    await cart.refresh().catch(() => undefined)
    cart.resetLocal()

    expect(cart.view).toEqual({ groups: [], totalQuantity: 0, totalAmountCents: 0 })
    expect(cart.isEmpty).toBe(true)
  })
})
