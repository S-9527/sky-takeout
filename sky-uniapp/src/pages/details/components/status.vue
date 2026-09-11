<!-- 订单状态 -->
<template>
  <view>
    <view class="box">
      <view class="orderInfoTip">
        <view class="tit">{{ statusWord(orderDetailsData.status) }} <text class="smw"
            v-if="timeout && orderDetailsData.status === 1"> ( 已经超时)</text></view>
        <view class="rejectionReason" v-if="orderDetailsData.status === 6">
          <text v-if="orderDetailsData.payStatus === 1 || orderDetailsData.payStatus === 2">退款成功</text>
          <text v-else-if="orderDetailsData.cancelReason">{{ orderDetailsData.cancelReason }}</text>
          <text v-else-if="orderDetailsData.rejectionReason">{{ orderDetailsData.rejectionReason }}</text>
        </view>
        <view v-if="!timeout && orderDetailsData.status === 1">
          <view class="time">
            <view class="timeIcon"></view>
            等待支付：
            <text>{{ rocallTime }}</text>
            <text>{{ paymentTime }}</text>
          </view>
        </view>
        <view class="againBtn">
          <button class="new_btn" type="default" @click="handleCancel('center', orderDetailsData)" v-if="(!timeout && orderDetailsData.status === 1) ||
            orderDetailsData.status === 2 ||
            orderDetailsData.status === 3 ||
            orderDetailsData.status === 4
            ">
            取消订单
          </button>
          <button class="new_btn btn" type="default" @click="handlePay(orderDetailsData.id)"
            v-if="!timeout && orderDetailsData.status === 1">
            立即支付
          </button>
          <button class="new_btn btn" type="default" @click="handleReminder('center', orderDetailsData.id)"
            v-if="orderDetailsData.status === 2">
            催单
          </button>
          <button class="new_btn" type="default" @click="handleRefund('center')" v-if="orderDetailsData.status === 5">
            申请退款
          </button>
          <button class="new_btn" type="default" @click="oneMoreOrder(orderDetailsData.id)"
            v-if="orderDetailsData.status === 5 || orderDetailsData.status === 6">
            再来一单
          </button>
        </view>
      </view>
    </view>
    <view class="box timeTip" v-if="!timeout && orderDetailsData.status === 1">
      <view class="icon newIcon"></view>
      请在15分钟内完成支付，超时将自动取消。
    </view>
    <view class="box timeTip" v-if="orderDetailsData.status === 6 && orderDetailsData.payStatus === 2">
      <view class="icon moneyIcon"></view>
      您的订单已
      <text>退款成功</text>
      。
    </view>
  </view>
</template>
<script setup lang="ts">
import { statusWord as statusWordUtil } from '@/utils/index'

const paymentTime = ''

withDefaults(defineProps<{
  orderDetailsData?: Record<string, any>
  timeout?: boolean
  rocallTime?: string
}>(), {
  orderDetailsData: () => ({}),
  timeout: false,
  rocallTime: ''
})

const emit = defineEmits<{
  (e: 'statusWord', status: number): void
  (e: 'handlePay', id: number): void
  (e: 'handleReminder', payload: { type: string; id: number }): void
  (e: 'handleCancel', payload: { type: string; obj: Record<string, any> }): void
  (e: 'handleRefund', type: string): void
  (e: 'oneMoreOrder', id: number): void
}>()

function statusWord(status: number) {
  emit('statusWord', status)
  return statusWordUtil(status)
}

function handleCancel(type: string, obj: Record<string, any>) {
  emit('handleCancel', { type, obj })
}

function handlePay(id: number) {
  emit('handlePay', id)
}

function handleReminder(type: string, id: number) {
  emit('handleReminder', { type, id })
}

function handleRefund(type: string) {
  emit('handleRefund', type)
}

function oneMoreOrder(id: number) {
  emit('oneMoreOrder', id)
}
</script>
<style src="../../order/style.scss" lang="scss"></style>
