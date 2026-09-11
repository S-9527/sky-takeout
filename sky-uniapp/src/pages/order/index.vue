<!--提交订单-->
<template>
  <view>
    <!-- 导航 -->
    <uni-nav-bar
      @clickLeft="goBack"
     
      leftIcon="arrowleft"
      title="提交订单"
      :statusBar="true"
      :fixed="true"
      color="#ffffff"
      backgroundColor="#333333"
    ></uni-nav-bar>
    <!-- end -->
    <view class="order_content" @touchstart="touchstart">
      <view class="order_content_box">
        <!-- 地址 -->
        <address-pop
          :address="address"
          :tagLabel="tagLabel"
          :addressLabel="addressLabel"
          :nickName="nickName"
          :phoneNumber="phoneNumber"
          :arrivalTime="arrivalTime"
          :popleft="popleft"
          :weeks="weeks"
          :newDateData="newDateData"
          :tabIndex="tabIndex"
          :selectValue="selectValue"
          @change="change"
          @goAddress="goAddress"
          @dateChange="dateChange"
          @timeClick="timeClick"
        ></address-pop>
        <!-- end -->
        <!-- 订单明细 -->
        <view class="order_list_cont">
          <!-- 菜品详情 -->
          <dish-detail
            :orderDataes="orderDataes"
            :showDisplay="showDisplay"
            :orderDishNumber="orderDishNumber"
            :orderListDataes="orderListDataes"
            :orderDishPrice="orderDishPrice"
          ></dish-detail>
          <!-- end -->
          <view class="boxPad">
            <!-- 备注、餐数数量、发票 -->
            <dish-info
              ref="dishinfo"
              :remark="remark"
              :tablewareData="tablewareData"
              :radioGroup="radioGroup"
              :activeRadio="activeRadio"
              :baseData="baseData"
              @goRemark="goRemark"
              @openPopuos="openPopuos"
              @change="change"
              @closePopup="closePopup"
              @handlePiker="handlePiker"
              @changeCont="changeCont"
              @handleRadio="handleRadio"
            ></dish-info>
            <!-- end -->
          </view>
        </view>
        <!-- end -->
      </view>
      <!-- 底部购物车、去支付 -->
      <view class="footer_order_buttom order_form">
        <view class="order_number">
          <image
            src="../../static/btn_waiter_sel.png"
            class="order_number_icon"
            mode=""
          ></image>
          <view class="order_dish_num"> {{ orderDishNumber }} </view>
        </view>
        <view class="order_price">
          <text class="ico">￥ </text> {{ orderDishPrice.toFixed(2) }}
        </view>
        <view class="order_but">
          <view v-if="isHandlePy" class="order_but_rit">去支付</view>
          <view v-else class="order_but_rit" @click="payOrderHandle()">
            去支付
          </view>
        </view>
      </view>
      <!-- end -->
    </view>
  </view>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onReady } from '@dcloudio/uni-app'
import { storeToRefs } from 'pinia'
import { useAppStore } from '@/stores'
import {
  // 提交订单
  submitOrderSubmit,
  // 查询默认地址
  getAddressBookDefault as getDefaultAddressApi,
  queryAddressBookList
} from '../api/api'
import { baseUrl } from '../../utils/env'
import { getLableVal, dateFormat, presentFormat, getWeekDate } from '../../utils/index'
import AddressPop from './components/address.vue'
import DishDetail from './components/dishDetail.vue'
import DishInfo from './components/dishInfo.vue'
import dayjs from 'dayjs'

const store = useAppStore()
const {
  orderListData,
  remarkData,
  addressData,
  deliveryFee,
  shopInfo,
  baseUserInfo
} = storeToRefs(store)

const platform = ref<string>('ios')
const orderDishPrice = ref<number>(0)
const openPayType = ref<boolean>(false)
const psersonUrl = ref<string>('../../static/btn_waiter_sel.png')
const nickName = ref<string>('')
const gender = ref<number>(0)
const phoneNumber = ref<string>('')
const address = ref<string>('')
const remark = ref<string>('')
const arrivalTime = ref<string>('')
const orderTime = ref<any>('')
const addressBookId = ref<any>('')
const addressLabel = ref<string>('')
const tagLabel = ref<string>('')
// 加入购物车数量
const orderDishNumber = ref<number>(0)
const showDisplay = ref<boolean>(false)
const type = ref<string>('center')
const expirationTime = ref<string>('')
const tablewareData = ref<string>('无需餐具')
const tableware = ref<string>('')
const packAmount = ref<number>(0)
const value = ref<number[]>([0, 0])
const timeValue = ref<number[]>([0, 0])
const indicatorStyle = ref<string>(`height: 44px;color:#333`)
const tabIndex = ref<number>(0)
const scrollinto = ref<string>('tab0')
const scrollH = ref<number>(0)
const popleft = ref<string[]>(['今天', '明天'])
const visible = ref<boolean>(true)
const baseData = ref<string[]>([
  '无需餐具', '1', '2', '3', '4', '5', '6', '7', '8', '9', '10'
])
const activeRadio = ref<string>('无需餐具')
const radioGroup = ref<string[]>(['依据餐量提供', '无需餐具'])
const popright = ref<string[]>([
  '立即派送', '09:00', '09:30', '10:00', '10:30', '11:00', '11:30', '12:00', '12:30', '13:00',
  '13:30', '14:00', '14:30', '15:00', '15:30', '16:00', '16:30', '17:00', '17:30', '18:00', '18:30',
  '19:00', '19:30', '20:00', '20:30', '21:00', '21:30', '22:00', '22:30', '23:00'
])
const newDateData = ref<string[]>([])
const textTip = ref<string>('')
const showConfirm = ref<boolean>(false)
const phoneData = ref<string>('15200000001')
const toDate = ref<any>(null)
const tomorrowStart = ref<any>(null)
const newDate = ref<any>(null)
const selectValue = ref<number>(0)
const selectDateValue = ref<number>(0)
const timeout = ref<boolean>(false)
const isTomorrow = ref<boolean>(false)
const status = ref<number>(0)
const num = ref<number>(0)
const weeks = ref<string[]>([])
const scrollTop = ref<number>(0)
const addressList = ref<any[]>([])
const isHandlePy = ref<boolean>(false)
const testValue = ref<boolean>(false)

const dishinfo = ref<any>(null)
const popup = ref<any>(null)

const time = new Date()
toDate.value = new Date(time.toLocaleDateString()).getTime()
tomorrowStart.value = toDate.value + 3600 * 24 * 1000
newDate.value = time.getHours() * 3600 + time.getMinutes() * 60

const weekDay = [toDate.value, tomorrowStart.value]

weekDay.forEach((date) => {
  weeks.value.push(getWeekDate(date))
})

getAddressList()

// 菜品数据
const orderListDataes = computed(() => orderListData.value)

// 菜品数据
const orderDataes = computed(() => {
  const testList: any[] = []
  if (showDisplay.value === false) {
    if (orderListDataes.value.length > 3) {
      for (let i = 0; i < 3; i++) {
        testList.push(orderListDataes.value[i])
      }
    } else {
      return orderListDataes.value
    }
    return testList
  } else {
    return orderListDataes.value
  }
})

onLoad(async (options: any) => {
  initPlatform()
  const baseInfo = baseUserInfo.value as any
  psersonUrl.value = baseInfo && baseInfo.avatarUrl
  nickName.value = baseInfo && baseInfo.nickName
  gender.value = baseInfo && baseInfo.gender
  remark.value = remarkData.value
  init()
  // 存在options说明换地址了
  if (addressData.value && addressData.value.detail) {
    addressBookId.value = ''
    const newAddress = addressData.value
    address.value = newAddress.provinceName + newAddress.cityName + newAddress.districtName + newAddress.detail
    phoneNumber.value = newAddress.phone
    nickName.value = newAddress.consignee
    gender.value = newAddress.sex

    addressBookId.value = newAddress.id
    addressLabel.value = getLableVal(newAddress.label)
  } else {
    // 默认地址查询
    await getAddressBookDefault()
  }

  await getEstimatedDeliveryTime()
  getDateDate()
  store.setArrivalTime(arrivalTime.value)
  store.setGender(gender.value)
})

onReady(() => {
  uni.getSystemInfo({
    success: (res: any) => {
      scrollH.value = res.windowHeight - uni.upx2px(100)
    }
  })
})

function init() {
  computOrderInfo()
}

function initPlatform() {
  const res = uni.getSystemInfoSync()
  platform.value = res.platform
}

// 获取用户送餐期望时间（后端暂无该接口，本地按下单时间+30分钟估算）
async function getEstimatedDeliveryTime() {
  const estimated = dayjs().add(30, 'minute')
  arrivalTime.value = estimated.format('HH:mm')
  orderTime.value = estimated
}

// 根据系统派送时间 格式化时间  [16:00,16:30]
function getDateDate() {
  let currentDayjs = dayjs(orderTime.value)
  const list = ['立即派送']
  if (!(currentDayjs.hour() >= 22 && currentDayjs.minute() > 30)) {
    if (currentDayjs.minute() > 30) {
      currentDayjs = currentDayjs.add(1, 'hour').set('minute', 0)
    } else {
      currentDayjs = currentDayjs.set('minute', 30)
    }
    while (true) {
      if (currentDayjs.hour() === 23 && currentDayjs.minute() === 30) {
        break
      }
      const start = `${currentDayjs.format('HH')}:${currentDayjs.format('mm')}`
      list.push(`${start}`)
      currentDayjs = currentDayjs.add(30, 'minute')
    }
  }
  newDateData.value = list
}

// 获取地址
function getAddressList() {
  testValue.value = false
  queryAddressBookList().then((res) => {
    if (res.code === 200) {
      testValue.value = true
      addressList.value = res.data
    }
  })
}

// 默认地址查询
async function getAddressBookDefault() {
  return getDefaultAddressApi().then((res) => {
    if (res.code === 200) {
      const data: any = res.data
      addressBookId.value = ''
      address.value = data.provinceName + data.cityName + data.districtName + data.detail
      phoneNumber.value = data.phone
      nickName.value = data.consignee
      gender.value = data.sex
      addressBookId.value = data.id
      addressLabel.value = getLableVal(data.label)
      tagLabel.value = data.label
    }
  }).catch(() => {})
}

// 去地址页面
function goAddress() {
  store.setAddressBackUrl('/pages/order/index')
  if (addressList.value.length === 0) {
    uni.redirectTo({
      url: '/pages/addOrEditAddress/addOrEditAddress'
    })
  } else {
    uni.redirectTo({
      url: '/pages/address/address'
    })
  }
}

// // 重新拼装image
function getNewImage(image: string) {
  return `${baseUrl}/common/download?name=${image}`
}

// 订单里和总订单价格计算
function computOrderInfo() {
  const oriData = orderListDataes.value
  orderDishNumber.value = orderDishPrice.value = 0
  orderDishPrice.value = 0
  oriData.map((n: any, i: number) => {
    // this.orderDishPrice += n.number * n.price
    orderDishPrice.value += n.number * n.amount
    orderDishNumber.value += n.number
    console.log(n)
  })
  orderDishPrice.value = orderDishPrice.value + deliveryFee.value + orderDishNumber.value
}

// 返回上一级
function goBack() {
  uni.navigateBack({ delta: 1 })
}

function closeMask() {
  openPayType.value = false
}

// 支付下单
function payOrderHandle() {
  isHandlePy.value = true

  if (!address.value) {
    uni.showToast({
      title: '请选择收货地址',
      icon: 'none'
    })
    return false
  }
  const params = {
    payMethod: 1,
    addressBookId: addressBookId.value,
    remark: remark.value,
    estimatedDeliveryTime:
      arrivalTime.value === '立即派送' ? presentFormat() : dateFormat(isTomorrow.value, arrivalTime.value),
    deliveryStatus: arrivalTime.value === '立即派送' ? 1 : 0,
    tablewareStatus: status.value,
    tablewareNumber: num.value,
    packAmount: orderDishNumber.value,
    amount: orderDishPrice.value,
    shopId: shopInfo.value.shopId,
    deliveryFee: deliveryFee.value
  }

  submitOrderSubmit(params).then((res) => {
    if (res.code === 200) {
      isHandlePy.value = false
      store.setOrderData(res.data)
      store.setRemark('')

      uni.navigateTo({
        url: '/pages/pay/index?orderId=' + res.data.id
      })
    } else {
      uni.showToast({
        title: res.msg || '操作失败',
        icon: 'none'
      })
    }
  })
}

// 拨打电话
function call() {
  uni.makePhoneCall({
    phoneNumber: '114' //仅为示例
  })
}

// // 联系商家进行取消弹层
function handleContact(type: any) {
  showConfirm.value = false
  openPopuos(type)
  textTip.value = '请联系商家进行取消！'
}

// 联系商家进行退款弹层
function handleRefund(type: any) {
  showConfirm.value = false
  openPopuos(type)
  textTip.value = '请联系商家进行退款！'
}

// 进入备注页
function goRemark() {
  store.setAddressBackUrl('/pages/order/index')
  uni.redirectTo({
    url: '/pages/remark/index'
  })
}

// 打开参数数量弹层
function openPopuos(type?: any) {
  // open 方法传入参数 等同在 uni-popup 组件上绑定 type属性
  popup.value?.open(type)
}

// 关闭餐具弹层
function closePopup(type?: any) {
  popup.value?.close(type)
}

function change(e?: any) {}

// 确定本单餐具
function handlePiker() {
  if (tableware.value !== '') {
    num.value = Number(tableware.value)
    status.value = 0
    if (tableware.value === '无需餐具') {
      num.value = 0
      status.value = 0
    }
    if (tableware.value === '依据餐量提供') {
      num.value = orderDishNumber.value
      status.value = 1
    }

    if (String(tableware.value) !== '依据餐量提供' || String(tableware.value) !== '无需餐具') {
      tablewareData.value = tableware.value + '份'
    } else {
      tablewareData.value = tableware.value
    }
  } else {
    // 是默认值，在点击的时候抛出去
    const cont = baseData.value[dishinfo.value?.piker?.defaultValue?.[0] ?? 0]
    tablewareData.value = cont
    if (activeRadio.value === '依据餐量提供') {
      num.value = orderDishNumber.value
      status.value = 1
    } else {
      num.value = 0
      status.value = 0
    }
  }
}

// 确定本单餐具
function changeCont(val: any) {
  tableware.value = val
}

// 餐具数量的后续订单餐具设置
function handleRadio(e: any) {
  activeRadio.value = e.detail.value
}

function countdown() {
  const end = Date.parse(String(new Date()))
}

// 星期几选择
function dateChange(index: number) {
  if (index === 1) {
    newDateData.value = popright.value.slice(1)
    isTomorrow.value = true
  } else {
    isTomorrow.value = false
    newDateData.value = []
    getDateDate()
  }
  // 点击的还是当前数据的时候直接return
  if (tabIndex.value == index) {
    return
  }
  tabIndex.value = index
}

// 选中时间段
function timeClick(val: any) {
  selectValue.value = val.i
  setTime(val.val)
}

// 设置时间
function setTime(val: any) {
  if (val === '立即派送') {
    arrivalTime.value = dayjs(orderTime.value).format('HH:mm')
  } else {
    arrivalTime.value = val
  }

  store.setArrivalTime(arrivalTime.value)
}

function touchstart(e: any) {
  if (e.changedTouches[0].clientY > 400) {
  }
}
</script>
<style src="./../common/Navbar/navbar.scss" lang="scss" scoped></style>
<style src="./style.scss" lang="scss" scoped></style>