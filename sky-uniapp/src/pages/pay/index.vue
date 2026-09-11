<!--购买页-->
<template>
  <view class="customer-box">
    <view class="wrap">
      <view class="contion">
        <view class="orderPay">
          <view>
            <view v-if="timeout">订单已超时</view>
            <view v-else
              >支付剩余时间<text>{{ rocallTime }}</text></view
            >
          </view>
          <view class="money"
            >￥<text>{{ orderDataInfo.orderAmount }}</text></view
          >
          <view>{{ shopInfo().shopName }}-{{ orderDataInfo.orderNumber }}</view>
        </view>
      </view>
      <view class="box payBox">
        <view class="contion">
          <view class="example-body">
            <radio-group class="uni-list" @change="styleChange">
              <view class="uni-list-item">
                <view
                  class="uni-list-item__container"
                  v-for="(item, index) in payMethodList"
                  :key="item"
                >
                  <view class="uni-list-item__content">
                    <icon class="wechatIcon"></icon
                    ><text class="uni-list-item__content-title">{{
                      item
                    }}</text>
                  </view>
                  <view class="uni-list-item__extra">
                    <radio
                      :value="item"
                      color="#FFC200"
                      :checked="index == activeRadio"
                      class="radioIcon"
                    />
                  </view>
                </view>
              </view>
            </radio-group>
          </view>
        </view>
      </view>
      <view class="bottomBox btnBox">
        <button class="add_btn" type="primary" plain="true" @click="handleSave">
          确认支付
        </button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { storeToRefs } from 'pinia'
import { useAppStore } from '@/stores'
import { paymentOrder, cancelOrder } from '@/pages/api/api'

const store = useAppStore()
const { orderData } = storeToRefs(store)
// 模板以 shopInfo() 函数调用方式访问,保持模板不变
const shopInfo = () => store.shopInfo

const timeout = ref(false)
const rocallTime = ref('')
const orderId = ref<any>(null)
const orderDataInfo = ref<Record<string, any>>({})
const activeRadio = ref(0)
const payMethodList = ref(['微信支付'])
const times = ref<any>(null)

// created:初始化支付信息
orderDataInfo.value = orderData.value

onMounted(() => {
  runTimeBack()
})

onLoad((options: any) => {
  orderId.value = options.orderId
})

// 支付详情
function handleSave() {
  if (timeout.value) {
    cancelOrder(orderId.value).then((res) => {})
    uni.redirectTo({
      url: '/pages/details/index?orderId=' + orderId.value
    })
  } else {
    // 如果支付成功进入成功页
    clearTimeout(times.value)
    const params = {
      orderNumber: orderDataInfo.value.orderNumber,
      payMethod: activeRadio.value === 0 ? 1 : 2
    }
    paymentOrder(params).then(async (res) => {
      if (res.code === 200) {
        const [err, payRes] = await uni.requestPayment({
          ...res.data,
          package: res.data.packageStr // package 为微信支付必须的字段
        })
        console.log(err, payRes)
        if (err) {
          await uni.showToast({ title: '支付失败', icon: 'error' })
          setTimeout(() => {
            // 下单失败!!
            uni.redirectTo({
              url: '/pages/details/index?orderId=' + orderId.value
            })
          }, 1500)
        } else {
          await uni.showToast({ title: '支付成功', icon: 'success' })
          setTimeout(() => {
            // 下单成功!!
            uni.redirectTo({
              url: '/pages/success/index?orderId=' + orderId.value
            })
          }, 1500)
        }
      } else {
        uni.showToast({
          title: res.msg,
          duration: 1000,
          icon: 'none'
        })
      }
    })
  }
}

// // 订单倒计时
function runTimeBack() {
  const end = Date.parse(String(orderDataInfo.value.orderTime).replace(/-/g, '/'))
  const now = Date.parse(new Date())
  const m15 = 15 * 60 * 1000
  const msec = m15 - (now - end)
  if (msec < 0) {
    timeout.value = true
    clearTimeout(times.value)
  } else {
    let min: any = parseInt((msec / 1000 / 60) % 60)
    let sec: any = parseInt((msec / 1000) % 60)
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
        return
      }
      times.value = setTimeout(function () {
        runTimeBack()
      }, 1000)
    }
  }
}

function styleChange() {}
</script>
<style src="./../common/Navbar/navbar.scss" lang="scss" scoped></style>
<style src="./../order/style.scss" lang="scss"></style>
<style>
</style>