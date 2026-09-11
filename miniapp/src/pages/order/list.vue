<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'

import * as api from '@/api/customer'
import { ApiError } from '@/api/http'
import { useCartStore } from '@/stores/cart'
import { useSessionStore } from '@/stores/session'
import type { Order, OrderStatus } from '@/types'
import { canCancelOrder, canPayOrder, canRemindOrder, canReorder, orderStatusLabel, ORDER_STATUS_TABS } from '@/utils/dict'
import { formatCents } from '@/utils/money'

/** 我的订单:状态页签 + 触底加载 */
const session = useSessionStore()
const cart = useCartStore()

const tabs = ORDER_STATUS_TABS
const activeStatus = ref<OrderStatus | 'ALL'>('ALL')
const orders = ref<Order[]>([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)

const hasMore = computed(() => orders.value.length < total.value)

async function load(reset = false): Promise<void> {
  if (!session.authenticated) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  if (loading.value) return
  if (reset) {
    page.value = 1
    orders.value = []
  }
  loading.value = true
  try {
    const result = await api.pageMyOrders({
      page: page.value,
      pageSize: 10,
      sort: 'placedAt,desc',
      status: activeStatus.value === 'ALL' ? undefined : activeStatus.value,
    })
    orders.value = reset ? result.records : [...orders.value, ...result.records]
    total.value = result.total
  } catch (error) {
    if (!(error instanceof ApiError && error.isAuthError)) {
      uni.showToast({ title: '订单加载失败', icon: 'none' })
    }
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

function switchStatus(status: OrderStatus | 'ALL'): void {
  activeStatus.value = status
  void load(true)
}

/** mock 渠道发起即成功;真实微信支付要换成 uni.requestPayment(字段已由契约给出) */
async function pay(order: Order): Promise<void> {
  try {
    await api.createPayment(order.id, 'MOCK')
    const status = await api.getPaymentStatus(order.id)
    if (status.payStatus === 'PAID') {
      uni.showToast({ title: '支付成功', icon: 'success' })
      await load(true)
    } else {
      uni.showToast({ title: '支付处理中,请稍后刷新', icon: 'none' })
    }
  } catch (error) {
    uni.showToast({ title: error instanceof ApiError ? error.message : '支付失败', icon: 'none' })
  }
}

async function cancel(order: Order): Promise<void> {
  uni.showModal({
    title: '取消订单',
    content: '确认取消这笔订单?',
    success: async (result) => {
      if (!result.confirm) return
      try {
        await api.cancelMyOrder(order.id, '顾客取消')
        uni.showToast({ title: '已取消', icon: 'none' })
        await load(true)
      } catch (error) {
        uni.showToast({ title: error instanceof ApiError ? error.message : '取消失败', icon: 'none' })
      }
    },
  })
}

async function remind(order: Order): Promise<void> {
  try {
    await api.remindOrder(order.id, '请尽快派送')
    uni.showToast({ title: '已提醒商家', icon: 'none' })
  } catch (error) {
    // 5 分钟内重复催单是 409,直接把后端文案给用户
    uni.showToast({ title: error instanceof ApiError ? error.message : '催单失败', icon: 'none' })
  }
}

async function reorder(order: Order): Promise<void> {
  try {
    const result = await api.reorder(order.id)
    await cart.refresh()
    const skipped = result.skippedItems.length
    uni.showToast({
      title: skipped > 0 ? `已加入 ${result.addedCount} 件,${skipped} 件已下架` : '已加入购物车',
      icon: 'none',
    })
    if (result.addedCount > 0) uni.switchTab({ url: '/pages/menu/index' })
  } catch (error) {
    uni.showToast({ title: error instanceof ApiError ? error.message : '操作失败', icon: 'none' })
  }
}

function openDetail(order: Order): void {
  uni.navigateTo({ url: `/pages/order/detail?id=${order.id}` })
}

onShow(() => {
  void load(true)
})

onPullDownRefresh(() => {
  void load(true)
})

onReachBottom(() => {
  if (hasMore.value) {
    page.value += 1
    void load()
  }
})
</script>

<template>
  <view class="min-h-screen bg-slate-50 pb-4">
    <scroll-view scroll-x class="whitespace-nowrap bg-white">
      <view
        v-for="tab in tabs"
        :key="tab.value"
        class="inline-block px-4 py-3 text-sm"
        :class="tab.value === activeStatus ? 'border-b-2 border-blue-600 font-medium text-blue-600' : 'text-slate-500'"
        @click="switchStatus(tab.value)"
      >
        {{ tab.label }}
      </view>
    </scroll-view>

    <view v-if="orders.length === 0 && !loading" class="py-16 text-center text-sm text-slate-400">
      还没有订单,去点一份吧
    </view>

    <view
      v-for="order in orders"
      :key="order.id"
      class="mb-3 bg-white p-4"
      data-testid="order-card"
      @click="openDetail(order)"
    >
      <view class="flex items-center justify-between">
        <text class="text-xs text-slate-400">{{ order.orderNo }}</text>
        <text class="text-xs font-medium text-blue-600">{{ orderStatusLabel(order.status) }}</text>
      </view>
      <view class="mt-2 flex items-center justify-between">
        <text class="text-sm text-slate-600">共 {{ order.itemCount ?? '-' }} 件商品</text>
        <text class="text-base font-semibold text-red-500">{{ formatCents(order.payAmountCents) }}</text>
      </view>
      <view class="mt-1 text-xs text-slate-400">{{ order.placedAt }}</view>

      <view class="mt-3 flex justify-end gap-2">
        <button
          v-if="canPayOrder(order)"
          class="rounded-full bg-blue-600 px-3 py-1 text-xs text-white"
          @click.stop="pay(order)"
        >
          去支付
        </button>
        <button
          v-if="canCancelOrder(order.status)"
          class="rounded-full border border-slate-200 px-3 py-1 text-xs"
          @click.stop="cancel(order)"
        >
          取消订单
        </button>
        <button
          v-if="canRemindOrder(order.status)"
          class="rounded-full border border-slate-200 px-3 py-1 text-xs"
          @click.stop="remind(order)"
        >
          催单
        </button>
        <button
          v-if="canReorder(order.status)"
          class="rounded-full border border-slate-200 px-3 py-1 text-xs"
          @click.stop="reorder(order)"
        >
          再来一单
        </button>
      </view>
    </view>

    <view v-if="loading" class="py-4 text-center text-xs text-slate-400">加载中…</view>
    <view v-else-if="orders.length > 0 && !hasMore" class="py-4 text-center text-xs text-slate-300">
      没有更多了
    </view>
  </view>
</template>
