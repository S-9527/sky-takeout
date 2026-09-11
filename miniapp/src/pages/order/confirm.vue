<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

import * as api from '@/api/customer'
import { ApiError } from '@/api/http'
import { useCartStore } from '@/stores/cart'
import type { OrderPreview, UserAddress } from '@/types'
import { formatCents } from '@/utils/money'

/**
 * 确认订单页。
 *
 * 金额一律以**服务端试算结果**为准(`POST /orders/preview`),不拿购物车里的数字糊弄 ——
 * 试算会按当前库价重算,下架商品也会在这一步暴露出来。
 */
const cart = useCartStore()

const preview = ref<OrderPreview | null>(null)
const addresses = ref<UserAddress[]>([])
const addressId = ref<number | null>(null)
const remark = ref('')
const tablewareCount = ref(1)
const loading = ref(false)
const submitting = ref(false)

const selectedAddress = computed(
  () => addresses.value.find((item) => item.id === addressId.value) ?? null,
)

async function load(address?: number): Promise<void> {
  loading.value = true
  try {
    const [addressList, previewResult] = await Promise.all([
      api.listAddresses(),
      api.previewOrder(address),
    ])
    addresses.value = addressList
    preview.value = previewResult
    addressId.value = address ?? previewResult.address?.id ?? addressList[0]?.id ?? null
    if (!address && previewResult.address?.id && !addressId.value) {
      addressId.value = previewResult.address.id
    }
  } catch (error) {
    showError(error, '试算失败,请稍后重试')
  } finally {
    loading.value = false
  }
}

async function chooseAddress(): Promise<void> {
  const items = addresses.value.map((item) => `${item.consignee} ${item.phone}`)
  if (items.length === 0) {
    uni.navigateTo({ url: '/pages/address/list' })
    return
  }
  uni.showActionSheet({
    itemList: items,
    success: async (result) => {
      const picked = addresses.value[result.tapIndex]
      if (picked) await load(picked.id)
    },
  })
}

async function submit(): Promise<void> {
  if (!preview.value) return
  if (!addressId.value) {
    uni.showToast({ title: '请先选择收货地址', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    const result = await api.submitOrder({
      addressId: addressId.value,
      remark: remark.value.trim() || undefined,
      tablewareCount: tablewareCount.value,
      // 带上客户端看到的合计,不一致时后端返回 422 ORDER_PRICE_CHANGED
      expectedTotalAmountCents: preview.value.totalAmountCents,
    })
    // 下单成功后服务端已清空购物车,这里只清本地缓存
    cart.resetLocal()
    uni.showToast({ title: '下单成功', icon: 'success' })
    uni.redirectTo({ url: `/pages/order/detail?id=${result.id}` })
  } catch (error) {
    if (error instanceof ApiError && error.code === 'ORDER_PRICE_CHANGED') {
      // 价格变了:重新试算,让用户看到新价格再决定
      await load(addressId.value ?? undefined)
      uni.showToast({ title: '商品价格已变化,请确认后重新提交', icon: 'none' })
      return
    }
    showError(error, '下单失败,请稍后重试')
  } finally {
    submitting.value = false
  }
}

function showError(error: unknown, fallback: string): void {
  uni.showToast({ title: error instanceof ApiError ? error.message : fallback, icon: 'none' })
}

onLoad(() => {
  void load()
})
</script>

<template>
  <view class="min-h-screen bg-slate-50 pb-24">
    <!-- 地址 -->
    <view class="mb-3 bg-white p-4" data-testid="address-card" @click="chooseAddress">
      <view v-if="selectedAddress">
        <view class="text-sm font-medium text-slate-800">
          {{ selectedAddress.consignee }} {{ selectedAddress.phone }}
        </view>
        <view class="mt-1 text-xs text-slate-500">
          {{ selectedAddress.province }}{{ selectedAddress.city }}{{ selectedAddress.district
          }}{{ selectedAddress.detail }}
        </view>
      </view>
      <view v-else class="text-sm text-slate-500">请选择收货地址</view>
    </view>

    <!-- 明细 -->
    <view class="mb-3 bg-white p-4">
      <view class="mb-2 text-sm font-medium">商品明细</view>
      <view v-if="loading" class="py-6 text-center text-sm text-slate-400">试算中…</view>
      <view v-for="item in preview?.items ?? []" :key="`${item.itemType}-${item.dishId ?? item.setmealId}`" class="mb-3 flex justify-between">
        <view class="flex-1">
          <view class="text-sm text-slate-800">{{ item.name }} × {{ item.quantity }}</view>
          <view v-if="item.flavorChoice?.length" class="text-xs text-slate-400">
            {{ item.flavorChoice.map((f) => `${f.name}:${f.option}`).join(' / ') }}
          </view>
        </view>
        <text class="text-sm text-slate-700">{{ formatCents(item.amountCents) }}</text>
      </view>
    </view>

    <!-- 备注与餐具 -->
    <view class="mb-3 bg-white p-4">
      <view class="mb-2 flex items-center justify-between">
        <text class="text-sm">餐具份数</text>
        <view class="flex items-center">
          <button class="h-6 w-6 rounded-full bg-slate-100" @click="tablewareCount = Math.max(0, tablewareCount - 1)">−</button>
          <text class="mx-3 text-sm">{{ tablewareCount }}</text>
          <button class="h-6 w-6 rounded-full bg-blue-600 text-white" @click="tablewareCount += 1">+</button>
        </view>
      </view>
      <input
        v-model="remark"
        class="mt-2 w-full rounded bg-slate-50 px-3 py-2 text-sm"
        placeholder="备注:如不要香菜"
        maxlength="100"
      />
    </view>

    <!-- 金额 -->
    <view class="bg-white p-4 text-sm">
      <view class="mb-2 flex justify-between">
        <text class="text-slate-500">商品合计</text>
        <text>{{ formatCents(preview?.totalAmountCents) }}</text>
      </view>
      <view class="mb-2 flex justify-between">
        <text class="text-slate-500">打包费</text>
        <text>{{ formatCents(preview?.packAmountCents) }}</text>
      </view>
      <view class="mb-2 flex justify-between">
        <text class="text-slate-500">配送费</text>
        <text>{{ formatCents(preview?.deliveryAmountCents) }}</text>
      </view>
      <view class="flex justify-between font-medium">
        <text>实付</text>
        <text class="text-red-500">{{ formatCents(preview?.payAmountCents) }}</text>
      </view>
      <view v-if="preview && preview.shopOpen === false" class="mt-2 text-xs text-red-500">
        门店已打烊,暂时无法下单
      </view>
    </view>

    <view class="fixed bottom-0 left-0 right-0 flex items-center border-t border-slate-200 bg-white px-4 py-3">
      <view class="flex-1">
        <text class="text-xs text-slate-500">实付</text>
        <text class="ml-2 text-base font-semibold text-red-500">
          {{ formatCents(preview?.payAmountCents) }}
        </text>
      </view>
      <button
        class="rounded-full bg-blue-600 px-6 py-2 text-sm text-white"
        :disabled="submitting || loading"
        data-testid="submit-order"
        @click="submit"
      >
        {{ submitting ? '提交中…' : '提交订单' }}
      </button>
    </view>
  </view>
</template>
