import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import * as customerApi from '@/api/customer'
import type { CartItemView, CartView, FlavorChoice, ItemType } from '@/types'

/**
 * 购物车。
 *
 * 与服务端的分工:行合并、口味归一化、数量上限这些规则**都在后端**(R2/R7),
 * 前端只做两件事:把服务端返回的视图缓存起来给界面用,以及"改完立刻重拉"。
 * 不做本地乐观累加 —— 那样一旦与服务端规则不一致(比如合并后超 99),
 * 界面就会显示一个并不存在的数量。
 */
export const useCartStore = defineStore('cart', () => {
  const view = ref<CartView | null>(null)
  const loading = ref(false)
  const mutating = ref(false)

  const groups = computed(() => view.value?.groups ?? [])
  const totalQuantity = computed(() => view.value?.totalQuantity ?? 0)
  const totalAmountCents = computed(() => view.value?.totalAmountCents ?? 0)
  const isEmpty = computed(() => totalQuantity.value === 0)

  /** 摊平所有行,便于按 id 查找/渲染 */
  const items = computed<CartItemView[]>(() => groups.value.flatMap((group) => group.items))

  async function refresh(): Promise<CartView> {
    loading.value = true
    try {
      view.value = await customerApi.getCart()
      return view.value
    } finally {
      loading.value = false
    }
  }

  /** 加购后重拉:让界面拿到服务端合并后的真实数量 */
  async function add(body: {
    itemType: ItemType
    dishId?: number
    setmealId?: number
    quantity?: number
    flavorChoice?: FlavorChoice[]
  }): Promise<CartItemView> {
    mutating.value = true
    try {
      const item = await customerApi.addCartItem({ quantity: 1, ...body })
      await refresh()
      return item
    } finally {
      mutating.value = false
    }
  }

  /** 覆盖式改量;数量为 0 时直接删行(后端对 0 的判定不作为交互手段) */
  async function setQuantity(id: number, quantity: number): Promise<void> {
    mutating.value = true
    try {
      await customerApi.updateCartItemQuantity(id, quantity)
      await refresh()
    } finally {
      mutating.value = false
    }
  }

  async function remove(id: number): Promise<void> {
    // 契约里没有"删一行"的接口,减到 0 即由后端删除该行
    await setQuantity(id, 0)
  }

  async function clear(): Promise<void> {
    mutating.value = true
    try {
      await customerApi.clearCart()
      await refresh()
    } finally {
      mutating.value = false
    }
  }

  /** 本地清空(下单成功后服务端已清空购物车,不需要再打一次接口) */
  function resetLocal(): void {
    view.value = { groups: [], totalQuantity: 0, totalAmountCents: 0 }
  }

  return {
    view,
    groups,
    items,
    totalQuantity,
    totalAmountCents,
    isEmpty,
    loading,
    mutating,
    refresh,
    add,
    setQuantity,
    remove,
    clear,
    resetLocal,
  }
})
