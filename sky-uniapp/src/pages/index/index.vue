<template>
  <view>
    <!-- 导航 -->
    <navBar></navBar>
    <!-- end -->
    <view
      class="home_content"
      :style="{ paddingTop: ht + 'px' }"
      <!-- #ifndef H5 -->
      @touchmove.stop.prevent="disabledScroll"
      <!-- #endif -->
    >
      <!-- 店铺基本信息 -->
      <view class="restaurant_info_box">
        <view class="restaurant_info">
          <!-- 上部 -->
          <view class="info_top">
            <view class="info_top_left">
              <image class="logo_ruiji" src="../../static/logo_ruiji.png"></image>
            </view>
            <view class="info_top_right">
              <view class="right_title">
                <text>苍穹外卖</text>
                <view class="businessStatus" v-if="shopStatus === 1">营业中</view>
                <view class="businessStatus close" v-else>休息中</view>
              </view>
              <view class="right_details">
                <!-- 中 -->
                <view class="details_flex">
                  <image class="top_icon" src="../../static/money.png"></image>
                  <text class="icon_text">配送费{{ deliveryFee }}元</text>
                </view>
              </view>
            </view>
          </view>
          <!-- 下部---信息简介 -->
          <view class="info_bottom">
            <view>
              <view class="word">苍穹餐厅为顾客打造专业的大众化美食外送餐饮</view>
              <view class="address" v-if="shopInfo.shopAddress">
                <view class="addressIcon"></view>
                {{ shopInfo.shopAddress }}
              </view>
            </view>
            <view>
              <view class="phone" @click="handlePhone('bottom')">
                <view class="phoneIcon"></view>
              </view>
            </view>
          </view>
        </view>
      </view>
      <!-- end -->
      <!-- 菜单列表 -->
      <view class="restaurant_menu_list" v-if="shopStatus === 1">
        <view class="type_list">
          <scroll-view scroll-y scroll-with-animation class="u-tab-view menu-scroll-view" :scroll-top="scrollTop + 100"
            :scroll-into-view="itemId">
            <view class="type_item" id="target" :class="[typeIndex == index ? 'active' : '']"
              v-for="(item, index) in typeListData" :key="index" @tap.stop="swichMenu(item, index)">
              <view class="item" :class="item.name.length > 5 ? 'allLine' : ''">{{ item.name }}</view>
            </view>
            <view class="seize_seat"></view>
          </scroll-view>
        </view>
        <scroll-view class="vegetable_order_list" scroll-y="true" scroll-top="0rpx"
          v-if="dishListItems && dishListItems.length > 0">
          <view class="type_item" v-for="(item, index) in dishListItems" :key="index">
            <!-- 点击查看详情 -->
            <view class="dish_img" @click="openDetailHandle(item)">
              <image mode="aspectFill" :src="item.image" class="dish_img_url"></image>
            </view>
            <view class="dish_info">
              <view class="dish_name" @click="openDetailHandle(item)">{{
                item.name
              }}</view>
              <view class="dish_label" @click="openDetailHandle(item)">{{
                item.description || item.name
              }}</view>
              <view class="dish_label" @click="openDetailHandle(item)">月销量0</view>
              <view class="dish_price">
                <text class="ico">￥</text>
                {{ Number(item.price).toFixed(2) }}
              </view>
              <view class="dish_active" v-if="!item.flavors || item.flavors.length === 0">
                <!-- 减菜 -->
                <image v-if="item.dishNumber >= 1" src="../../static/btn_red.png" @click="redDishAction(item, '普通')"
                  class="dish_red"></image>
                <text v-if="item.dishNumber > 0" class="dish_number">{{
                  item.dishNumber
                }}</text>
                <!-- 加菜 -->
                <image src="../../static/btn_add.png" class="dish_add" @click="addDishAction(item, '普通')"></image>
              </view>
              <view class="dish_active_btn" v-else>
                <view class="check_but" @click="moreNormDataesHandle(item)">选择规格</view>
              </view>
            </view>
          </view>
          <view class="seize_seat"></view>
        </scroll-view>
        <view class="no_dish" v-else>
          <view v-if="typeListData.length > 0">该分类下暂无菜品</view>
        </view>
      </view>
      <view class="restaurant_close" v-if="shopStatus !== 1">店铺已打烊</view>
      <!-- end -->
      <view class="mask-box"></view>
      <!-- 底部去结算 -->
      <!-- 购物车里没有订单的状态 -->
      <view class="footer_order_buttom" v-if="orderListData.length === 0 || shopStatus !== 1">
        <view class="order_number">
          <image src="../../static/btn_waiter_nor.png" class="order_number_icon" mode=""></image>
        </view>
        <view class="order_price">
          <text class="ico">￥</text>
          0
        </view>
        <view class="order_but">去结算</view>
      </view>
      <!-- end -->
      <!-- 购物车里有订单结算 -->
      <view class="footer_order_buttom order_form" v-else>
        <view class="orderCar" @click="toggleCart">
          <view class="order_number">
            <image src="../../static/btn_waiter_sel.png" class="order_number_icon" mode=""></image>
            <view class="order_dish_num">{{ orderDishNumber }}</view>
          </view>
          <view class="order_price">
            <text class="ico">￥</text>
            {{ orderDishPrice.toFixed(2) }}
          </view>
        </view>
        <view class="order_but" @click="goOrder()">去结算</view>
      </view>
      <!-- end -->
      <!-- 选择多规格弹层 - start -->
      <view class="pop_mask" v-show="openMoreNormPop">
        <popMask :moreNormDishdata="moreNormDishdata" :moreNormdata="moreNormdata" :flavorDataes="flavorDataes"
          @checkMoreNormPop="checkMoreNormPop" @addShop="addShop" @closeMoreNorm="closeMoreNorm"></popMask>
      </view>
      <!-- 选择多规格 - end -->
      <!-- 菜品详情弹层 - start -->
      <view class="pop_mask" v-show="openDetailPop" style="z-index: 9999">
        <dishDetail :dishDetailes="dishDetailes" :openDetailPop="openDetailPop" :dishMealData="dishMealData"
          @redDishAction="redDishAction" @addDishAction="addDishAction" @moreNormDataesHandle="moreNormDataesHandle"
          @dishClose="dishClose"></dishDetail>
      </view>
      <!-- 菜品详情 - end -->
      <!-- 购物车弹框 - start -->
      <view class="pop_mask" v-show="openOrderCartList" @click="toggleCart">
        <popCart :openOrderCartLis="openOrderCartList" :orderAndUserInfo="orderAndUserInfo"
          @clearCardOrder="clearCardOrder" @addDishAction="addDishAction" @redDishAction="redDishAction"></popCart>
      </view>
      <!-- 购物车弹框 - end -->
      <view class="pop_mask" v-show="lodding">
        <view class="lodding">
          <image class="lodding_ico" src="../../static/lodding.gif" mode=""></image>
        </view>
      </view>
      <!-- 电话弹层 -->
      <phone ref="phone" :phoneData="phoneData" @closePopup="closePopup"></phone>
      <!-- end -->
      <!-- 店面打烊弹层 -->
      <view class="colseShop" v-if="shopStatus === 0">
        <view class="shop">本店已打样</view>
      </view>
      <!-- end -->
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, getCurrentInstance, nextTick, ref } from 'vue'
import { onLoad, onReady, onShow } from '@dcloudio/uni-app'
import { storeToRefs } from 'pinia'
import { useAppStore } from '@/stores'
import navBar from '../common/Navbar/navbar.vue'
import Phone from '@/components/uni-phone/index.vue'
import popMask from './components/popMask.vue'
import popCart from './components/popCart.vue'
import dishDetail from './components/dishDetail.vue'
import {
  userLogin,
  getCategoryList,
  dishListByCategoryId,
  querySetmeaList,
  getShoppingCartList,
  newAddShoppingCartAdd,
  newShoppingCartSub,
  delShoppingCart,
  querySetmealDishById,
  getShopStatus,
  getMerchantInfo as getMerchantInfoApi
} from '../api/api'
import type { Category, SetmealDish } from '../api/types'

const store = useAppStore()
const { shopInfo, orderListData, lodding, token, deliveryFee } = storeToRefs(store)
const instance = getCurrentInstance()

const openOrderCartList = ref(false)
const typeListData = ref<Category[]>([])
const dishListData = ref<any[]>([])
const dishListItems = ref<any[]>([])
const dishDetailes = ref<any>({})
const openDetailPop = ref(false)
const openMoreNormPop = ref(false)
const moreNormdata = ref<any>(null)
const moreNormDishdata = ref<any>(null)
const dishMealData = ref<SetmealDish[]>([])
const typeIndex = ref(0)
const flavorDataes = ref<string[]>([])
const orderDishNumber = ref(0)
const orderDishPrice = ref(0)
const rightIdAndType = ref<any>({})
const phoneData = ref('')
const tablewareNumber = ref(0)
const shopStatus = ref<any>(null)
const scrollTop = ref(0)
const menuHeight = ref(0)
const menuItemHeight = ref(0)
const navHeight = ref(0)
const itemId = ref('')

const phone = ref<any>(null)

// 购物车信息列表
const orderListDataes = computed(() => orderListData.value)

// 计算购物车清单(按菜品分组)
const orderAndUserInfo = computed(() => {
  const orderData: any[] = []
  if (Array.isArray(orderListDataes.value)) {
    orderListDataes.value.forEach((n: any) => {
      const userData: any = {}
      userData.nickName = n.name ?? ''
      userData.avatarUrl = n.image ?? ''
      userData.dishList = [n]
      const num = orderData.findIndex((o) => o.nickName === userData.nickName)
      if (num !== -1) {
        orderData[num].dishList.push(n)
      } else {
        orderData.push(userData)
      }
    })
  }
  return orderData
})

// 导航栏高度占位
let ht = computed(() => navHeight.value)
// #ifdef MP-WEIXIN
ht = computed(() => {
  const res = uni.getMenuButtonBoundingClientRect()
  return res.top + res.height + 7
})
// #endif

onReady(() => {
  // #ifndef MP-WEIXIN
  // H5 没有胶囊按钮，直接量出导航栏高度作为内容区顶部留白
  const navEl = document.querySelector('.navBar')
  if (navEl) {
    navHeight.value = (navEl as HTMLElement).offsetHeight
  }
  // #endif
})

onLoad((options) => {
  uni.onNetworkStatusChange((res) => {
    if (res.isConnected === false) {
      uni.navigateTo({
        url: '/pages/nonet/index'
      })
    }
  })
  if (options) {
    if (!options.status && !options.formOrder) {
      getData()
    }
  }
})

onShow(() => {
  if (token.value) {
    init()
  }
})

// 登录并保存令牌
function doLogin(params: { code?: string; location?: string }) {
  userLogin(params)
    .then((success) => {
      if (success.code === 200) {
        // 后端返回 accessToken / refreshToken
        store.setToken(success.data.accessToken)
        uni.setStorageSync('refreshToken', success.data.refreshToken || '')
        store.setShopInfo({
          shopName: success.data.shopName || '',
          shopAddress: success.data.shopAddress || '',
          shopId: success.data.shopId || ''
        })
        init()
      }
    })
    .catch(() => {})
}

// 获取用户信息
function getData() {
  // 获取店铺状态
  getShopInfo()
  // 无令牌则直接静默登录,不再弹提示窗
  if (token.value === '') {
    silentLogin()
  }
}

// 静默登录:wx.login 拿 code 直接换令牌,全程不弹授权窗
// (getUserProfile 自 2022-10-25 起对新发布的小程序不再弹窗,回调会直接进 fail,登录链整体中断)
function silentLogin() {
  // #ifdef H5
  // H5 没有微信授权环境，直接以固定体验账号登录
  store.setBaseUserInfo({ nickName: '体验用户', avatarUrl: '' })
  doLogin({ code: 'h5-dev-user' })
  return
  // #endif
  uni.login({
    provider: 'weixin',
    success: (loginRes) => {
      if (loginRes.errMsg !== 'login:ok') {
        uni.showToast({ title: '登录失败', icon: 'none' })
        return
      }
      const params: { code?: string; location?: string } = {
        code: loginRes.code
      }
      // 定位失败不阻塞登录
      uni
        .getLocation({ type: 'gcj02', isHighAccuracy: true })
        .then((res) => {
          const [err, result] = res as unknown as [any, any]
          if (err) {
            uni.showToast({
              title: '获取地理位置失败',
              icon: 'none'
            })
          } else {
            params.location = `${result.longitude},${result.latitude}`
          }
        })
        .finally(() => {
          doLogin(params)
        })
    },
    fail: () => {
      uni.showToast({ title: '登录失败', icon: 'none' })
    }
  })
}

async function init() {
  // 获取菜品和套餐分类接口
  if (typeIndex.value !== 0) {
    typeIndex.value = 0
  }

  // 获取店铺联系方式
  loadMerchantInfo()
  getCategoryList().then((res) => {
    if (res && res.code === 200) {
      typeListData.value = [...res.data]
      if (res.data.length > 0) {
        getDishListDataes(res.data[typeIndex.value || 0])
      }
    }
  })
  // 调用一次购物车集合---初始化
  getTableOrderDishListes()
}

// 点击左边的栏目切换
function swichMenu(params: any, index: number) {
  if (index === typeIndex.value) return
  nextTick(() => {
    typeIndex.value = index
    leftMenuStatus(index)
  })
  getDishListDataes(params, index)
}

// 获取一个目标元素的高度
function getElRect(elClass: string, dataVal: 'menuHeight' | 'menuItemHeight') {
  return new Promise<void>((resolve) => {
    const query = uni.createSelectorQuery().in(instance?.proxy as any)
    query
      .select('.' + elClass)
      .fields({ size: true }, (res: any) => {
        // 如果节点尚未生成，res值为null，循环调用执行
        if (!res) {
          setTimeout(() => {
            getElRect(elClass, dataVal)
          }, 10)
          return
        }
        if (dataVal === 'menuHeight') {
          menuHeight.value = res.height
        } else {
          menuItemHeight.value = res.height
        }
        resolve()
      })
      .exec()
  })
}

// 设置左边菜单的滚动状态
async function leftMenuStatus(index: number) {
  typeIndex.value = index
  // 如果为0，意味着尚未初始化
  if (menuHeight.value === 0 || menuItemHeight.value === 0) {
    await getElRect('menu-scroll-view', 'menuHeight')
    await getElRect('type_item', 'menuItemHeight')
  }
  // 将菜单活动item垂直居中
  scrollTop.value = index * menuItemHeight.value + menuItemHeight.value / 2 - menuHeight.value / 2
}

// 获取菜品列表
async function getDishListDataes(params: any, index?: number) {
  rightIdAndType.value = {}
  rightIdAndType.value = {
    id: params.id,
    type: params.type
  }
  const param = {
    categoryId: params.id
  }
  // type：1是菜品、2是套餐
  if (params.type === 1) {
    await dishListByCategoryId(param)
      .then((res) => {
        if (res && res.code === 200) {
          // 添加一个字段去实时更新加入购物车number数量 ----- newCardNumber
          dishListData.value =
            res.data &&
            res.data.map((obj: any) => ({
              ...obj,
              type: 1,
              newCardNumber: 0
            }))
        }
      })
      .catch(() => {})
  } else {
    // 套餐
    await querySetmeaList(param)
      .then((success) => {
        if (success && success.code === 200) {
          dishListData.value =
            success.data &&
            success.data.map((obj: any) => ({
              ...obj,
              type: 2,
              newCardNumber: 0
            }))
        }
      })
      .catch(() => {})
  }
  if (index !== undefined) {
    typeIndex.value = index
  }
  setOrderNum()
}

// 获取首页店铺信息
async function getShopInfo() {
  await getShopStatus()
    .then((res) => {
      shopStatus.value = res.data
      store.setShopStatus(res.data)
    })
    .catch(() => {})
}

// 获取店铺电话
async function loadMerchantInfo() {
  await getMerchantInfoApi()
    .then((res) => {
      phoneData.value = res.data.phone
      store.setShopPhone(res.data)
    })
    .catch(() => {})
}

// 获取购物车订单列表
async function getTableOrderDishListes() {
  // 调用获取购物车集合接口
  await getShoppingCartList({})
    .then((res) => {
      if (res.code === 200) {
        store.initdishListMut(res.data)
        computOrderInfo()
      }
    })
    .catch(() => {})
}

// 去订单页面
function goOrder() {
  uni.navigateTo({
    url: '/pages/order/index'
  })
}

// 加菜 - 添加菜品
async function addDishAction(item: any, form?: string) {
  // 规格
  if (openMoreNormPop.value && (!flavorDataes.value || flavorDataes.value.length <= 0)) {
    uni.showToast({
      title: '请选择规格',
      icon: 'none'
    })
    return false
  }
  openMoreNormPop.value = false
  // 实时更新obj.newCardNumber新添加的字段----加入购物车数量number
  tablewareNumber.value++
  dishDetailes.value.dishNumber++
  if (
    orderListDataes.value &&
    !orderListDataes.value.some((n: any) => n.id === item.dishId) &&
    flavorDataes.value.length > 0
  ) {
    item.flavorRemark = JSON.stringify(flavorDataes.value)
  }
  // 有sort字段是菜品
  let dishFlavorDatas = ''
  const flavorRemark: any[] = []
  if (item.flavorRemark) {
    flavorRemark.push(...JSON.parse(item.flavorRemark))
  }
  if (item.dishFlavor !== '' && item.dishFlavor) {
    dishFlavorDatas = item.dishFlavor
  } else if (flavorRemark.length > 0) {
    dishFlavorDatas = flavorRemark.join(',')
  } else {
    dishFlavorDatas = ''
  }
  let params: any = {
    dishFlavor: dishFlavorDatas || null
  }
  if (item.type === 1) {
    params = {
      ...params,
      dishId: item.id
    }
  } else if (item.type === 2) {
    params = {
      setmealId: item.id
    }
  } else if (form === '购物车') {
    if (item.dishId) {
      params = {
        ...params,
        dishId: item.dishId
      }
    } else {
      params = {
        setmealId: item.setmealId
      }
    }
  }
  newAddShoppingCartAdd(params)
    .then((res) => {
      if (res.code === 200) {
        // 调用一次购物车集合---初始化
        getTableOrderDishListes()
        // 重新调取刷新右侧具体菜品列表
        getDishListDataes(rightIdAndType.value)
        flavorDataes.value = []
      }
    })
    .catch(() => {})
}

// 加入购物车
function addShop(item: any) {
  dishDetailes.value = item
  addDishAction(item, '普通')
}

// 减菜 - 添加菜品
async function redDishAction(item: any, form?: string) {
  // 实时更新obj.newCardNumber新添加的字段----加入购物车数量number
  tablewareNumber.value--
  dishDetailes.value.dishNumber--
  let dishFlavorDatas = ''
  const flavorRemark: any[] = []
  if (item.flavorRemark) {
    flavorRemark.push(...JSON.parse(item.flavorRemark))
  }
  if (item.dishFlavor !== '' && item.dishFlavor) {
    dishFlavorDatas = item.dishFlavor
  } else if (flavorRemark.length > 0) {
    dishFlavorDatas = flavorRemark[0]
  } else {
    dishFlavorDatas = ''
  }
  let params: any = {
    dishFlavor: dishFlavorDatas || null
  }
  if (item.type === 1) {
    params = {
      ...params,
      dishId: item.id
    }
  } else if (item.type === 2) {
    params = {
      setmealId: item.id
    }
  } else if (form === '购物车') {
    if (item.dishId) {
      params = {
        ...params,
        dishId: item.dishId
      }
    } else {
      params = {
        setmealId: item.setmealId
      }
    }
  }
  await newShoppingCartSub(params)
    .then((res) => {
      if (res.code === 200) {
        // 调用一次购物车集合---初始化
        getTableOrderDishListes()
        // 重新调取刷新右侧具体菜品列表
        getDishListDataes(rightIdAndType.value)
      }
    })
    .catch(() => {})
}

// 清空购物车
function clearCardOrder() {
  delShoppingCart()
    .then(() => {
      openOrderCartList.value = false
      // 调用一次购物车集合---初始化
      getTableOrderDishListes()
      // 重新调取刷新右侧具体菜品列表
      getDishListDataes(rightIdAndType.value)
    })
    .catch(() => {})
}

// 打开菜品牌详情
function openDetailHandle(item: any) {
  dishDetailes.value = item
  if (item.type === 2) {
    querySetmealDishById({
      id: item.id
    })
      .then((res) => {
        if (res.code === 200) {
          openDetailPop.value = true
          dishMealData.value = res.data
        }
      })
      .catch(() => {})
  } else {
    openDetailPop.value = true
  }
}

// 关闭菜品详情
function dishClose() {
  openDetailPop.value = false
}

// 多规格数据处理
function moreNormDataesHandle(item: any) {
  flavorDataes.value.splice(0)
  moreNormDishdata.value = item
  openDetailPop.value = false
  openMoreNormPop.value = true
  moreNormdata.value = item.flavors.map((obj: any) => ({
    ...obj,
    value: JSON.parse(obj.value)
  }))
  moreNormdata.value.forEach((it: any) => {
    if (it.value && it.value.length > 0) {
      flavorDataes.value.push(it.value[0])
    }
  })
}

// 选规格 处理一行只能选择一种
function checkMoreNormPop(val: any) {
  const obj = val.obj
  const item = val.item
  let ind = -1
  const findst = obj.some((n: any) => {
    ind = flavorDataes.value.findIndex((o) => o === n)
    return ind !== -1
  })
  const num = flavorDataes.value.findIndex((it) => it === item)
  if (num === -1 && !findst) {
    flavorDataes.value.push(item)
  } else if (findst) {
    flavorDataes.value.splice(ind, 1)
    flavorDataes.value.push(item)
  } else {
    flavorDataes.value.splice(num, 1)
  }
}

// 关闭选规格弹窗
function closeMoreNorm(_moreNormDishdata: any) {
  flavorDataes.value.splice(0, flavorDataes.value.length)
  openMoreNormPop.value = false
}

// 订单里和总订单价格计算
function computOrderInfo() {
  const oriData = orderListDataes.value
  orderDishNumber.value = 0
  orderDishPrice.value = 0
  oriData.map((n: any) => {
    orderDishNumber.value += n.number
    orderDishPrice.value += n.number * n.amount
  })
}

// 处理点餐数量 - 更新菜品已点餐数量
function setOrderNum() {
  const ODate = dishListData.value
  const CData = orderListDataes.value
  ODate &&
    ODate.map((obj: any) => {
      obj.dishNumber = 0
      // 去除空的规格
      if (obj.flavors) {
        obj.flavors.forEach((value: any, i: number) => {
          if (value.name === '') {
            obj.flavors.splice(i, 1)
          }
        })
      }

      if (CData.length > 0) {
        CData.forEach((tg: any) => {
          if (obj.id === tg.dishId) {
            obj.dishNumber = tg.number
          }
          if (obj.id === tg.setmealId) {
            obj.dishNumber = tg.number
          }
        })
      }
    })
  if (dishListItems.value.length === 0) {
    dishListItems.value = ODate
  } else {
    dishListItems.value.splice(0, dishListItems.value.length, ...ODate)
  }
}

// 拨打电话弹层
function handlePhone(type: string) {
  phone.value?.popup?.open(type)
}

// 关闭电话弹层
function closePopup(type?: string) {
  phone.value?.popup?.close(type)
}

function disabledScroll() {
  return false
}

// 切换购物车弹层
function toggleCart() {
  openOrderCartList.value = !openOrderCartList.value
}
</script>

<style src="./style.scss" lang="scss" scoped></style>
<style scoped>
/* #ifdef MP-WEIXIN || APP-PLUS */
:deep(::-webkit-scrollbar) {
  display: none !important;
  width: 0 !important;
  height: 0 !important;
  -webkit-appearance: none;
  background: transparent;
  color: transparent;
}

/* #endif */
</style>
