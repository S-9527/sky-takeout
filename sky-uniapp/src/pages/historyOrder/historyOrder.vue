<!--历史订单-->
<template>
  <view class="history_order">
    <uni-nav-bar @clickLeft="goBack" leftIcon="arrowleft" title="历史订单" :statusBar="true" :fixed="true"
      color="#ffffff" backgroundColor="#333333"></uni-nav-bar>
    <!-- 根据scrollinto和:id="'tab'+index"切换下方轮播 -->
    <scroll-view scroll-x class="scroll-row" :scroll-into-view="scrollinto" :scroll-with-animation="true" enable-flex>
      <view v-for="(item, index) in tabBars" :key="index" :id="'tab' + index" class="scroll-row-item"
        @click="changeTab(index)">
        <view :class="tabIndex == index ? 'scroll-row-item-act' : ''"><text class="line"></text>{{ item }}</view>
      </view>
    </scroll-view>
    <!--  滑块内容 对应的是顶部选项卡的切换 :current="tabIndex"  设置的是y方向上可以滚动-->
    <swiper :current="tabIndex" @change="onChangeSwiperTab" :style="{ height: scrollH + 'px' }">
      <swiper-item v-for="(item, index) in tabBars" :key="index">
        <!-- 垂直滚动区域  scroll和swiper的高度都要给且是一样的高度-->
        <scroll-view scroll-y="true" :style="{ height: scrollH + 'px' }" @scrolltolower="lower">
          <!-- 可垂直滚动区域 显示真正内容-->
          <view class="main recent_orders" v-if="recentOrdersList && recentOrdersList.length > 0">
            <!-- 历史订单列表 -->
            <view class="box order_lists" v-for="(item, index) in recentOrdersList" :key="index" :class="{
              'item-last': Number(index) + 1 === recentOrdersList.length,
            }">
              <!-- 时间和支付状态 -->
              <view class="date_type">
                <!-- 时间 -->
                <text class="time">{{ item.orderTime }}</text>
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
                      </view>
                      <view class="food">{{ num.name }}</view>
                    </view>
                  </scroll-view>
                </view>
                <!-- 商品数量及金额 -->
                <view class="numAndAum">
                  <view><text>￥{{ item.amount.toFixed(2) }}</text></view>
                  <view><text>共{{ numes(item.orderDetailList).count }}件</text></view>
                </view>
              </view>
              <view class="againBtn">
                <button class="new_btn" type="default" @click="oneMoreOrder(item.id)">
                  再来一单
                </button>
                <button class="new_btn btn" type="default" @click="goDetail(item.id)"
                  v-if="item.status === 1 && getOvertime(item.orderTime) > 0">
                  去支付
                </button>
                <button class="new_btn btn" type="default" @click="handleReminder('center', item.id)"
                  v-if="item.status === 2">
                  催单
                </button>
              </view>
            </view>
          </view>
        </scroll-view>
      </swiper-item>
    </swiper>
    <uni-popup ref="commonPopup" class="comPopupBox">
      <view class="popup-content">
        <view class="text">{{ textTip }}</view>
        <view class="btn" v-if="showConfirm">
          <view @click="closePopup">确认</view>
        </view>
      </view>
    </uni-popup>
  </view>
</template>

<script setup lang="ts">
// @ts-nocheck
import { ref } from 'vue'
import { onLoad, onUnload, onReady, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import { storeToRefs } from 'pinia'
import { useAppStore } from '@/stores'
import {
  getOrderPage,
  repetitionOrder,
  reminderOrder,
  delShoppingCart
} from '../api/api'
import Empty from '@/components/empty/empty'
import { statusWord as statusWordUtil, getOvertime as getOvertimeUtil } from '@/utils/index'

const store = useAppStore()
const { setAddressBackUrl } = store

const recentOrdersList = ref<any[]>([])
const pageInfo = ref({ page: 1, pageSize: 10, total: 0 })
const status = ref('')
const loadingType = ref(0)
const showTitle = ref(false)
const scrollinto = ref('tab0')
const scrollH = ref(0)
const tabIndex = ref(0)
const tabBars = ref(['全部订单', '待付款'])
const urlMap: Record<number, { fn: any; key: string }> = {
  0: { fn: getOrderPage, key: 'status' },
  1: { fn: getOrderPage, key: 'status' }
}
const textTip = ref('')
const showConfirm = ref(false)
const isEmpty = ref(false)
const loadingText = ref('')
const loading = ref(false)

const commonPopup = ref<any>(null)

onLoad(() => {
  getList()
})

onUnload(() => {
  showTitle.value = false
})

onReady(() => {
  uni.getSystemInfo({
    success: (res: any) => {
      scrollH.value = res.windowHeight - uni.upx2px(100)
    }
  })
})

onPullDownRefresh(() => {
  pageInfo.value.page = 1
  loadingType.value = 0
  recentOrdersList.value = []
  getList()
  uni.stopPullDownRefresh()
  showTitle.value = true
})

onReachBottom(() => {
  if (recentOrdersList.value.length < Number(pageInfo.value.total)) {
    pageInfo.value.page++
    loading.value = true
    getList()
    showTitle.value = true
  }
})

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

function statusWord(status: number) {
  return statusWordUtil(status)
}

function getOvertime(time: string) {
  return getOvertimeUtil(time)
}

function getList() {
  const key = urlMap[tabIndex.value].key
  const fn = urlMap[tabIndex.value].fn
  const params: any = {
    pageSize: 10,
    page: pageInfo.value.page
  }
  params[key] = status.value
  uni.showLoading({ title: '加载中', mask: true })
  fn(params).then((res: any) => {
    if (res.code === 200) {
      setTimeout(function () {
        uni.hideLoading()
      }, 100)
      recentOrdersList.value = recentOrdersList.value.concat(res.data.records)
      pageInfo.value.total = res.data.total
      isEmpty.value = true
    }
  })
}

async function oneMoreOrder(id: number) {
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

function changeTab(index: number) {
  if (tabIndex.value == index) {
    return
  }
  tabIndex.value = index
  if (index === 1) {
    status.value = '1'
  } else {
    status.value = ''
  }
  pageInfo.value.page = 1
  recentOrdersList.value = []
  getList()
  scrollinto.value = 'tab' + index
}

function onChangeSwiperTab(e: any) {
  changeTab(e.detail.current)
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

function goDetail(id: number) {
  setAddressBackUrl('/pages/historyOrder/historyOrder')
  uni.navigateTo({ url: '/pages/details/index?orderId=' + id })
}

function handleReminder(type: string, id: number) {
  reminderOrder(id).then((res: any) => {
    if (res.code === 200) {
      showConfirm.value = true
      textTip.value = '您的催单信息已发出！'
      commonPopup.value?.open(type)
      getList()
    }
  })
}

function closePopup(type?: string) {
  commonPopup.value?.close(type)
}

function goBack() {
  uni.redirectTo({
    url: '/pages/my/my'
  })
}
</script>

<style lang="scss" scoped>
.history_order {
  height: 100%;

  .recent_orders {
    padding-top: 8rpx;
  }
}

.scroll-row {
  height: 88rpx;
  line-height: 88rpx;
  background-color: #fff;
  padding: 0 30rpx;
  box-shadow: 0 2px 4px 0 rgba(0, 0, 0, 0.06);
  width: 100vw;
  box-sizing: border-box;
  flex-wrap: nowrap;
  overflow: auto;
  display: flex;
}

.scroll-row-item {
  margin-right: 88rpx;
  color: #666;
  display: inline-block;
  font-size: 28rpx;
  flex-shrink: 0;
}

.scroll-row-item-act {
  color: #333;
  position: relative;
  font-weight: 600;

  .line {
    width: 32rpx;
    height: 8rpx;
    display: block;
    background: #ffc200;
    border-radius: 8rpx;
    transform: translate(-50%, -50%);
    position: absolute;
    bottom: -4rpx;
    left: 50%;
  }
}
</style>
