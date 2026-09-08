<template>
  <div class="dashboard-container home">
    <!-- 营业数据 -->
    <Overview :overviewData="overviewData" />
    <!-- end -->
    <!-- 订单管理 -->
    <Orderview :orderviewData="orderviewData" />
    <!-- end -->
    <div class="homeMain">
      <!-- 菜品总览 -->
      <CuisineStatistics :dishesData="dishesData" />
      <!-- end -->
      <!-- 套餐总览 -->
      <SetMealStatistics :setMealData="setMealData" />
      <!-- end -->
    </div>
    <!-- 订单信息 -->
    <OrderList
      :order-statics="orderStatics"
      @getOrderListBy3Status="getOrderListBy3Status"
    />
    <!-- end -->
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getBusinessData as getBusinessDataApi,
  getOrderData, //订单管理今日订单
  getOverviewDishes, //菜品总览
  getSetMealStatistics, //套餐总览
} from '@/api/index'
import { getOrderListBy } from '@/api/order'
// 组件
// 营业数据
import Overview from './components/overview.vue'
// 订单管理
import Orderview from './components/orderview.vue'
// 菜品总览
import CuisineStatistics from './components/cuisineStatistics.vue'
// 套餐总览
import SetMealStatistics from './components/setMealStatistics.vue'
// 订单列表
import OrderList from './components/orderList.vue'

const overviewData = ref({})
const orderviewData = ref({} as any)
const dishesData = ref({} as any)
const setMealData = ref({})
const orderStatics = ref({} as any)

init()
function init() {
  nextTick(() => {
    getBusinessData()
    getOrderStatisticsData()
    getOverStatisticsData()
    getSetMealStatisticsData()
  })
}
// 获取营业数据
async function getBusinessData() {
  const data = await getBusinessDataApi()
  overviewData.value = data.data.data
}
// 获取今日订单
async function getOrderStatisticsData() {
  const data = await getOrderData()
  orderviewData.value = data.data.data
}
// 获取菜品总览数据
async function getOverStatisticsData() {
  const data = await getOverviewDishes()
  dishesData.value = data.data.data
}
// 获取套餐总览数据
async function getSetMealStatisticsData() {
  const data = await getSetMealStatistics()
  setMealData.value = data.data.data
}
//获取待处理，待派送，派送中数量
function getOrderListBy3Status() {
  getOrderListBy({})
    .then((res) => {
      if (res.data.code === 200) {
        orderStatics.value = res.data.data
      } else {
        ElMessage.error(res.data.msg)
      }
    })
    .catch((err) => {
      ElMessage.error('请求出错了：' + err.message)
    })
}
</script>

