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
import {
  getBusinessData as getBusinessDataApi,
  getOrderData, //订单管理今日订单
  getOverviewDishes, //菜品总览
  getSetMealStatistics, //套餐总览
} from '@/api/index'
import { getOrderListBy } from '@/api/order'
import type {
  BusinessDataVO,
  DishOverViewVO,
  OrderOverViewVO,
  OrderStatisticsVO,
  SetmealOverViewVO,
} from '@/api/types'
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

const overviewData = ref<BusinessDataVO>()
const orderviewData = ref<OrderOverViewVO>()
const dishesData = ref<DishOverViewVO>()
const setMealData = ref<SetmealOverViewVO>()
const orderStatics = ref<OrderStatisticsVO>()

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
  overviewData.value = data
}
// 获取今日订单
async function getOrderStatisticsData() {
  const data = await getOrderData()
  orderviewData.value = data
}
// 获取菜品总览数据
async function getOverStatisticsData() {
  const data = await getOverviewDishes()
  dishesData.value = data
}
// 获取套餐总览数据
async function getSetMealStatisticsData() {
  const data = await getSetMealStatistics()
  setMealData.value = data
}
//获取待处理，待派送，派送中数量
function getOrderListBy3Status() {
  getOrderListBy()
    .then((res) => {
      orderStatics.value = res
    })
}
</script>

