<!--最近订单-->
<template>
  <scroll-view scroll-y="true" :style="{ height: scrollH + 'px' }" @scrolltolower="lower">
    <view class="main recent_orders">
      <!-- 最近订单列表 -->
      <view class="box order_lists" v-for="(item, index) in recentOrdersList" :key="index">
        <!-- 时间和支付状态 -->
        <view class="date_type">
          <!-- 时间 -->
          <text class="time">{{ item.orderTime }} {{ item.id }}</text>
          <!-- 支付状态 -->
          <text class="type status" :class="{ status: item.status == 2 }">{{
            statusWord(item.status)
          }}</text>
        </view>
        <!-- 点菜的内容 -->
        <view class="orderBox" @click="goDetail(item.id)">
          <view class="food_num">
            <scroll-view scroll-x="true" class="pic" style="width: 100%; overflow: hidden; white-space: nowrap">
              <view class="food_num_item" v-for="(num, y) in item.orderDetailList" :key="y">
                <view class="img">
                  <image :src="num.image"></image>
                  <!-- <image src="../../static/img2.jpg"></image> -->
                </view>
                <view class="food">{{ num.name }}</view>
              </view>
            </scroll-view>
          </view>
          <view class="numAndAum">
            <view><text>￥{{ item.amount.toFixed(2) }}</text></view>
            <view><text>共{{ numes(item.orderDetailList).count }}件</text></view>
          </view>
        </view>

        <view class="againBtn">
          <button class="new_btn" type="default" @click="oneOrderFun(item.id)">
            再来一单
          </button>
          <button class="new_btn btn" type="default" @click="goDetail(item.id)"
            v-if="item.status === 1 && getOvertime(item.orderTime) > 0">
            去支付
          </button>
        </view>
      </view>
    </view>
    <reach-bottom v-if="loading" :loadingText="loadingText"></reach-bottom>
  </scroll-view>
</template>
<script setup lang="ts">
// @ts-nocheck
import { statusWord as statusWordUtil, getOvertime as getOvertimeUtil } from '@/utils/index'
import ReachBottom from '@/components/reach-bottom/reach-bottom.vue'

withDefaults(
  defineProps<{
    scrollH?: number
    loading?: boolean
    loadingText?: string
    recentOrdersList?: any[]
  }>(),
  {
    scrollH: 0,
    loading: false,
    loadingText: '',
    recentOrdersList: () => []
  }
)

const emit = defineEmits<{
  (e: 'lower'): void
  (e: 'goDetail', id: number): void
  (e: 'oneOrderFun', id: number): void
  (e: 'getOvertime', time: string): void
  (e: 'statusWord', data: { status: number; time?: number }): void
}>()

function lower() {
  emit('lower')
}

function goDetail(id: number) {
  emit('goDetail', id)
}

function numes(list: any[]) {
  let count = 0
  let total = 0
  list.length > 0 &&
    list.forEach((obj: any) => {
      count += Number(obj.number)
      total += Number(obj.number) * Number(obj.amount)
    })
  return { count: count, total: total }
}

function oneOrderFun(id: number) {
  emit('oneOrderFun', id)
}

function getOvertime(time: string) {
  emit('getOvertime', time)
  return getOvertimeUtil(time)
}

function statusWord(status: number, time?: number) {
  emit('statusWord', { status: status, time: time })
  return statusWordUtil(status, time)
}
</script>
