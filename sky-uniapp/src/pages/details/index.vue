<template>
  <view>
    <!-- 导航 -->
    <uni-nav-bar @clickLeft="goBack" leftIcon="arrowleft" title="订单详情" statusBar="true" fixed="true"
      color="#ffffff" backgroundColor="#333333"></uni-nav-bar>
    <!-- end -->
    <view class="order_content orderDetail">
      <view class="order_content_box" scroll-y="true" scroll-top="0rpx">
        <!-- 支付状态 -->
        <status ref="status" :timeout="timeout" :orderDetailsData="orderDetailsData" :rocallTime="rocallTime"
          @statusWord="statusWord" @paymentTime="paymentTime" @handlePay="handlePay" @handleReminder="handleReminder"
          @handleCancel="handleCancel" @handleRefund="handleRefund" @oneMoreOrder="oneMoreOrder"></status>
        <!-- end -->
        <!-- 订单详情 -->
        <order-detail :orderDataes="orderDataes" :orderDetailsData="orderDetailsData"
          :showDisplay="showDisplay"></order-detail>
        <!-- end -->
        <!-- 联系商家 -->
        <view class="box contactMerchant">
          <button @click="handlePhone('bottom', orderDetailsData.shopTelephone)">
            <view class="phoneIcon"></view>
            联系商家
          </button>
          <!-- 4 派送中 -->
          <button class="call-rider" v-if="orderDetailsData.status === 4"
            @click="handlePhone('bottom', orderDetailsData.courierTelephone)">
            <view class="phoneIcon"></view>
            联系骑手
          </button>
        </view>
        <!-- end -->
        <!-- 配送信息 -->
        <delivery-info :orderDetailsData="orderDetailsData"></delivery-info>
        <!-- end -->
        <!-- 订单信息 -->
        <order-info :orderDetailsData="orderDetailsData"></order-info>
        <!-- end -->
      </view>
      <!-- 联系商家弹层 -->
      <uni-popup ref="commonPopup" class="comPopupBox">
        <view class="popup-content">
          <view class="text">{{ textTip }}</view>
          <view class="btn" v-if="showConfirm">
            <view @click="closePopupInfo">确认</view>
          </view>
          <view class="btn" v-else>
            <view @click="closePopupInfo">先等等</view>
            <view @click="handlePhone('bottom')">拨打电话</view>
          </view>
        </view>
      </uni-popup>
      <!-- 拨打电话弹层 -->
      <view class="container phoneCon">
        <uni-popup ref="phonePopup" @change="change" class="popupBox">
          <view class="popup-content">
            <view>{{ phone }}</view>
            <view @click="call">呼叫</view>
            <view @click="closePopup" class="closePopup">取消</view>
          </view>
        </uni-popup>
      </view>
      <!-- end -->
    </view>
  </view>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { storeToRefs } from 'pinia'
import { useAppStore } from '@/stores'
import {
  getOrderDetail,
  repetitionOrder,
  delShoppingCart,
  reminderOrder,
  cancelOrder
} from '../api/api'
import { baseUrl } from '../../utils/env'
import { call as makePhoneCall } from '@/utils/index'
import Status from './components/status.vue' //订单状态
import OrderDetail from './components/orderDetail.vue' //菜品详情
import DeliveryInfo from './components/deliveryInfo.vue' //配送信息
import OrderInfo from './components/orderInfo.vue' //订单信息

const store = useAppStore()
const { shopInfo, orderListData } = storeToRefs(store)

const showDisplay = ref(false)
const rocallTime = ref('')
const textTip = ref('')
const showConfirm = ref(false)
const orderDetailsData = ref<Record<string, any>>({})
const timeout = ref(false)
const orderId = ref<any>(null)
const isPayment = ref(false)
const times = ref<any>(null)
const phone = ref('')

// 组件弹层引用
const status = ref<any>(null)
const commonPopup = ref<any>(null)
const phonePopup = ref<any>(null)

const orderListDataes = computed(() => orderListData.value)

// // 处理订单详情列表
const orderDataes = computed(() => {
  let testList: any[] = []
  if (showDisplay.value === false) {
    if (orderListDataes.value.length > 2) {
      for (let i = 0; i < 2; i++) {
        testList.push(orderListDataes.value[i])
      }
    } else {
      testList = orderListDataes.value
    }
    return testList
  } else {
    return orderListDataes.value
  }
})

onLoad((options: any) => {
  getBaseData(options.orderId)
})

// 获取订单详情
function getBaseData(id: any) {
  getOrderDetail(id).then((res) => {
    if (res.code === 200) {
      orderDetailsData.value = res.data
      store.initdishListMut(orderDetailsData.value.orderDetailList)
      if (orderDetailsData.value.status === 1) {
        runTimeBack(orderDetailsData.value.orderTime)
      }
    }
  })
}

// 催单
function handleReminder(val: any) {
  reminderOrder(val.id).then((res) => {
    if (res.code === 200) {
      showConfirm.value = true
      textTip.value = '您的催单信息已发出！'
      commonPopup.value?.open(val.type)
      orderId.value = val.id
    }
  })
}

// 取消订单接口
function cancel(type: any, obj: any) {
  cancelOrder(obj.id).then((res) => {
    if (res.code === 200) {
      isPayment.value = true
      showConfirm.value = true
      textTip.value = '您的订单已取消！'
      commonPopup.value?.open(type)
      orderId.value = obj.id
    }
  })
}

// 取消订单
function handleCancel(val: any) {
  if (val.obj.status === 1 || val.obj.status === 2) {
    cancel(val.type, val.obj)
  } else {
    showConfirm.value = false
    commonPopup.value?.open(val.type)
    textTip.value = '请联系商家进行取消！'
  }
}

// 再来一单
async function oneMoreOrder(id: any) {
  // 先清空购物车
  await delShoppingCart()
  repetitionOrder(id).then((res) => {
    if (res.code === 200) {
      uni.redirectTo({
        url: '/pages/index/index'
      })
    }
  })
}

// 处理状态
function statusWord(status: any) {
  if (timeout.value && status === 1 || orderDetailsData.value.status === 6) {
    return '订单已取消'
  }
  switch (status) {
    case 2:
      return '等待商户接单'
    case 3:
      return '商家已接单'
    case 4:
      return '订单派送中'
    case 5:
      return '订单已完成'
  }
}

// 订单倒计时
function runTimeBack(time: any) {
  const end = Date.parse(String(time).replace(/-/g, '/'))

  const now = Date.now()
  const m15 = 15 * 60 * 1000
  const msec = m15 - (now - end)
  if (msec < 0) {
    timeout.value = true
    clearTimeout(times.value)
    cancel('center', orderDetailsData.value) //超时的时候取消订单
  } else {
    let min: any = parseInt(String(msec / 1000 / 60 % 60))
    let sec: any = parseInt(String(msec / 1000 % 60))
    if (min < 10) {
      min = '0' + min
    } else {
      min = min
    }
    if (sec < 10) {
      sec = '0' + sec
    } else {
      sec = sec
    }
    rocallTime.value = min + ':' + sec
    if (min >= 0 && sec >= 0) {
      if (min === 0 && sec === 0) {
        timeout.value = true
        clearTimeout(times.value)
        cancel('center', orderDetailsData.value) //超时的时候取消订单
        return
      }
      times.value = setTimeout(function () {
        runTimeBack(time)
      }, 1000)
    }
  }
}

// 重新拼装image
function getNewImage(image: string) {
  return `${baseUrl}/common/download?name=${image}`
}

// 返回上一级
function goBack() {
  uni.redirectTo({
    url: '/pages/historyOrder/historyOrder'
  })
}

function openPopuos(type: any) {
  commonPopup.value?.open(type)
}

// 联系商家进行退款弹层
function handleRefund(type: any) {
  showConfirm.value = false
  openPopuos(type)
  textTip.value = '请联系商家进行退款！'
}

// 拨打电话弹层
function handlePhone(type: any, phoneData?: any) {
  // 暂时关闭打电话
  phonePopup.value?.open(type)
  phone.value = phoneData
}

// 关闭弹层
function closePopup(type: any) {
  phonePopup.value?.close(type)
}

// closePopupInfo
function closePopupInfo(type: any) {
  commonPopup.value?.close(type)
  getBaseData(orderId.value)
}

// 立即支付
function handlePay(id: any) {
  const obj = {
    orderNumber: orderDetailsData.value.number,
    orderAmount: orderDetailsData.value.amount,
    orderTime: orderDetailsData.value.orderTime
  }
  store.setOrderData(obj)
  uni.redirectTo({
    url: '/pages/pay/index?orderId=' + id
  })
}

// 拨打号码
function call() {
  makePhoneCall(phone.value)
}

// 状态时间展示(占位,保持模板监听绑定)
function paymentTime() {}

// uni-popup change 事件(占位)
function change() {}
</script>
<style src="./../common/Navbar/navbar.scss" lang="scss" scoped></style>
<style src="../order/style.scss" lang="scss"></style>