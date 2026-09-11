<!--配送信息-->
<template>
  <view class="box">
    <view class="orderBaseInfo">
      <view>
        <view>期望时间</view>
        <view>{{
          orderDetailsData.deliveryStatus === 1
          ? "立即送出"
          : orderDetailsData.estimatedDeliveryTime
        }}</view>
      </view>
      <view>
        <view>配送地址</view>
        <view>
          <view class="nameInfo">
            <text>{{ cryptoName }}</text>
            {{ orderDetailsData.phone }}
          </view>
          <view>{{ orderDetailsData.address }}</view>
        </view>
      </view>
    </view>
  </view>
</template>
<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  orderDetailsData?: Record<string, any>
}>(), {
  orderDetailsData: () => ({})
})

const cryptoName = computed(() => {
  if (!props.orderDetailsData.consignee) return ''
  if (props.orderDetailsData.sex == 0) {
    return props.orderDetailsData.consignee.charAt(0) + '先生'
  } else {
    return props.orderDetailsData.consignee.charAt(0) + '女士'
  }
})
</script>
<style src="../../order/style.scss" lang="scss"></style>
