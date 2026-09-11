<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

import * as api from '@/api/customer'
import { ApiError } from '@/api/http'
import { useCartStore } from '@/stores/cart'
import type { OrderDetail } from '@/types'
import {
  canCancelOrder,
  canPayOrder,
  canRemindOrder,
  canReorder,
  orderStatusLabel,
  payStatusLabel,
} from '@/utils/dict'
import { formatCountdown, formatDateTime, remainingPayMillis } from '@/utils/datetime'
import { formatCents } from '@/utils/money'

/** 订单详情:含 15 分钟支付倒计时(R2,超时由后端定时任务关单) */
const cart = useCartStore()

const order = ref<OrderDetail | null>(null)
const orderId = ref(0)
const loading = ref(false)
const remainMs = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

const countdown = computed(() => formatCountdown(remainMs.value))
const showCountdown = computed(() => canPayOrder(order.value ?? {}) && remainMs.value > 0)

async function load(): Promise<void> {
  loading.value = true
  try {
    order.value = await api.getMyOrder(orderId.value)
    remainMs.value = remainingPayMillis(order.value.placedAt) ?? 0
    if (showCountdown.value && !timer) {
      timer = setInterval(() => {
        remainMs.value = remainingPayMillis(order.value?.placedAt) ?? 0
        if (remainMs.value === 0) void load()
      }, 1000)
    }
  } catch (error) {
    uni.showToast({ title: error instanceof ApiError ? error.message : '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function pay(): Promise<void> {
  if (!order.value) return
  try {
    await api.createPayment(order.value.id, 'MOCK')
    const status = await api.getPaymentStatus(order.value.id)
    uni.showToast({ title: status.payStatus === 'PAID' ? '支付成功' : '支付处理中', icon: 'none' })
    await load()
  } catch (error) {
    uni.showToast({ title: error instanceof ApiError ? error.message : '支付失败', icon: 'none' })
  }
}

function cancel(): void {
  if (!order.value) return
  const current = order.value
  uni.showModal({
    title: '取消订单',
    content: '确认取消这笔订单?',
    success: async (result) => {
      if (!result.confirm) return
      try {
        await api.cancelMyOrder(current.id, '顾客取消')
        await load()
      } catch (error) {
        uni.showToast({ title: error instanceof ApiError ? error.message : '取消失败', icon: 'none' })
      }
    },
  })
}

async function remind(): Promise<void> {
  if (!order.value) return
  try {
    await api.remindOrder(order.value.id, '请尽快派送')
    uni.showToast({ title: '已提醒商家', icon: 'none' })
  } catch (error) {
    uni.showToast({ title: error instanceof ApiError ? error.message : '催单失败', icon: 'none' })
  }
}

async function reorder(): Promise<void> {
  if (!order.value) return
  try {
    const result = await api.reorder(order.value.id)
    await cart.refresh()
    uni.showToast({ title: `已加入 ${result.addedCount} 件商品`, icon: 'none' })
  } catch (error) {
    uni.showToast({ title: error instanceof ApiError ? error.message : '操作失败', icon: 'none' })
  }
}

onLoad((query) => {
  orderId.value = Number((query as { id?: string } | undefined)?.id ?? 0)
  void load()
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  timer = null
})
</script>

<template>
  <view class="min-h-screen bg-slate-50 pb-24">
    <view v-if="order" class="bg-white p-4">
      <view class="flex items-center justify-between">
        <text class="text-base font-medium" data-testid="order-status">{{ orderStatusLabel(order.status) }}</text>
        <text v-if="showCountdown" class="text-sm text-red-500">剩余 {{ countdown }}</text>
      </view>
      <view class="mt-1 text-xs text-slate-400">{{ order.orderNo }}</view>
    </view>

    <view v-if="order" class="mt-3 bg-white p-4">
      <view class="mb-2 text-sm font-medium">收货信息</view>
      <view class="text-sm text-slate-700">{{ order.consignee }} {{ order.phone }}</view>
      <view class="mt-1 text-xs text-slate-500">
        {{ order.province }}{{ order.city }}{{ order.district }}{{ order.detail }}
      </view>
      <view v-if="order.remark" class="mt-1 text-xs text-slate-400">备注:{{ order.remark }}</view>
    </view>

    <view v-if="order" class="mt-3 bg-white p-4">
      <view class="mb-2 text-sm font-medium">商品明细</view>
      <view v-for="item in order.items" :key="item.id" class="mb-3 flex justify-between">
        <view class="flex-1">
          <view class="text-sm text-slate-800">{{ item.nameSnapshot }} × {{ item.quantity }}</view>
          <view v-if="item.flavorSnapshot?.length" class="text-xs text-slate-400">
            {{ item.flavorSnapshot.map((f) => `${f.name}:${f.option}`).join(' / ') }}
          </view>
        </view>
        <text class="text-sm">{{ formatCents(item.amountCents) }}</text>
      </view>

      <view class="mt-3 border-t border-slate-100 pt-3 text-sm">
        <view class="mb-1 flex justify-between"><text class="text-slate-500">商品合计</text><text>{{ formatCents(order.totalAmountCents) }}</text></view>
        <view class="mb-1 flex justify-between"><text class="text-slate-500">打包费</text><text>{{ formatCents(order.packAmountCents) }}</text></view>
        <view class="mb-1 flex justify-between"><text class="text-slate-500">配送费</text><text>{{ formatCents(order.deliveryAmountCents) }}</text></view>
        <view class="flex justify-between font-medium"><text>实付</text><text class="text-red-500">{{ formatCents(order.payAmountCents) }}</text></view>
      </view>
    </view>

    <view v-if="order" class="mt-3 bg-white p-4 text-sm">
      <view class="mb-1 flex justify-between"><text class="text-slate-500">支付状态</text><text>{{ payStatusLabel(order.payStatus) }}</text></view>
      <view class="mb-1 flex justify-between"><text class="text-slate-500">下单时间</text><text>{{ formatDateTime(order.placedAt) }}</text></view>
      <view v-if="order.paidAt" class="mb-1 flex justify-between"><text class="text-slate-500">支付时间</text><text>{{ formatDateTime(order.paidAt) }}</text></view>
      <view v-if="order.completedAt" class="flex justify-between"><text class="text-slate-500">完成时间</text><text>{{ formatDateTime(order.completedAt) }}</text></view>
    </view>

    <view v-if="order" class="fixed bottom-0 left-0 right-0 flex justify-end gap-2 border-t border-slate-200 bg-white px-4 py-3">
      <button v-if="canPayOrder(order)" class="rounded-full bg-blue-600 px-4 py-2 text-sm text-white" data-testid="pay-order" @click="pay">去支付</button>
      <button v-if="canCancelOrder(order.status)" class="rounded-full border border-slate-200 px-4 py-2 text-sm" @click="cancel">取消订单</button>
      <button v-if="canRemindOrder(order.status)" class="rounded-full border border-slate-200 px-4 py-2 text-sm" @click="remind">催单</button>
      <button v-if="canReorder(order.status)" class="rounded-full border border-slate-200 px-4 py-2 text-sm" @click="reorder">再来一单</button>
    </view>

    <view v-if="loading && !order" class="py-16 text-center text-sm text-slate-400">加载中…</view>
  </view>
</template>
