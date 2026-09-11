<template>
  <view>
    <uni-nav-bar
      @clickLeft="goBack"
     
      leftIcon="arrowleft"
      title="地址管理"
      statusBar="true"
      fixed="true"
      color="#ffffff"
      backgroundColor="#ffc200"
    ></uni-nav-bar>

    <view class="my-center">
      <!-- 头像展示部分 -->
      <head
        :psersonUrl="psersonUrl"
        :nickName="nickName"
        :gender="gender"
        :phoneNumber="phoneNumber"
        :getPhoneNum="getPhoneNum"
      ></head>

      <view class="container">
        <!-- 地址和历史订单 -->
        <order-info @goAddress="goAddress" @goOrder="goOrder"></order-info>
        <!-- 最近订单 -->
        <!-- 最近订单title -->
        <view
          class="recent"
          v-if="recentOrdersList && recentOrdersList.length > 0"
        >
          <text class="order_line">最近订单</text>
        </view>
        <order-list
          :scrollH="scrollH"
          @lower="lower"
          @goDetail="goDetail"
          @oneOrderFun="oneOrderFun"
          @getOvertime="getOvertime"
          @statusWord="statusWord"
          :loading="loading"
          :loadingText="loadingText"
          :recentOrdersList="recentOrdersList"
        ></order-list>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onReady } from '@dcloudio/uni-app'
import { storeToRefs } from 'pinia'
import { useAppStore } from '@/stores'
import { formatPhone } from '@/utils/index'
import { getOrderPage, repetitionOrder, delShoppingCart } from '../api/api'
import { statusWord as statusWordUtil, getOvertime as getOvertimeUtil } from '@/utils/index'

import HeadInfo from './components/headInfo.vue'
import OrderInfo from './components/orderInfo.vue'
import OrderList from './components/orderList.vue'

const store = useAppStore()
const { baseUserInfo, shopPhone } = storeToRefs(store)

const psersonUrl = ref('../../static/btn_waiter_sel.png')
const nickName = ref('')
const gender = ref('0')
const phoneNumber = ref('18500557668')
const recentOrdersList = ref<any[]>([])
const sumOrder = ref({ amount: 0, number: 0 })
const status = ref('')
const scrollH = ref(0)
const pageInfo = ref({ page: 1, pageSize: 10, total: 0 })
const loadingText = ref('')
const loading = ref(false)

function getPhoneNum(str: string): string {
  return formatPhone(str)
}

function statusWord(obj: { status: number; time?: number }): string {
  return statusWordUtil(obj.status, obj.time)
}

function getOvertime(time: string): number {
  return getOvertimeUtil(time)
}

function getList() {
  const params = {
    pageSize: 10,
    page: pageInfo.value.page
  }
  getOrderPage(params).then((res: any) => {
    if (res.code === 200) {
      recentOrdersList.value = recentOrdersList.value.concat(res.data.records)
      pageInfo.value.total = res.data.total
      loadingText.value = ''
      loading.value = false
    }
  })
}

function goAddress() {
  store.setAddressBackUrl('/pages/my/my')
  uni.redirectTo({
    url: '/pages/address/address?form=' + 'my'
  })
}

function goOrder() {
  uni.navigateTo({
    url: '/pages/historyOrder/historyOrder'
  })
}

async function oneOrderFun(id: number) {
  const pages = getCurrentPages()
  const routeIndex = pages.findIndex(
    (item: any) => item.route === 'pages/index/index'
  )
  await delShoppingCart()
  repetitionOrder(id).then((res: any) => {
    if (res.code === 200) {
      uni.navigateBack({
        delta: routeIndex > -1 ? pages.length - routeIndex : 1
      })
    }
  })
}

function goDetail(id: number) {
  store.setAddressBackUrl('/pages/my/my')
  uni.redirectTo({
    url: '/pages/details/index?orderId=' + id
  })
}

function dataAdd() {
  const pages = Math.ceil(pageInfo.value.total / 10)
  if (pageInfo.value.page === pages) {
    loadingText.value = '没有更多了'
    loading.value = true
  } else {
    pageInfo.value.page++
    getList()
  }
}

function lower() {
  loadingText.value = '数据加载中...'
  loading.value = true
  dataAdd()
}

function goBack() {
  uni.redirectTo({
    url: '/pages/index/index'
  })
}

onLoad(() => {
  const info = baseUserInfo.value as any
  psersonUrl.value = (info && info.avatarUrl) || '../../static/btn_waiter_sel.png'
  nickName.value = (info && info.nickName) || ''
  gender.value = (info && info.gender) || '0'
  phoneNumber.value = (shopPhone.value as string) || '18500557668'
  getList()
})

onReady(() => {
  uni.getSystemInfo({
    success: (res: any) => {
      scrollH.value = res.windowHeight - uni.upx2px(100)
    }
  })
})
</script>
<style lang="scss" scoped>
.my-center {
  background: #f6f6f6;
  height: 100%;

  .container {
    margin-top: 20rpx;
    height: calc(100% - 194rpx);
  }
}
::v-deep .uni-navbar--border {
  border-width: 0 !important;
}
</style>
