<template>
  <div class="dashboard-container home">
    <!-- 标题 -->
    <TitleIndex @sendTitleInd="getTitleNum" :flag="flag" :tateData="tateData" />
    <!-- end -->
    <div class="homeMain">
      <!-- 营业额统计 -->
      <TurnoverStatistics :turnoverdata="turnoverData" />
      <!-- end -->
      <!-- 用户统计 -->
      <UserStatistics :userdata="userData" />
      <!-- end -->
    </div>
    <div class="homeMain homecon">
      <!-- 订单统计 -->
      <OrderStatistics :orderdata="orderData" :overviewData="overviewData" />
      <!-- end -->
      <!-- 销量排名TOP10 -->
      <Top :top10data="top10Data" />
      <!-- end -->
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import {
  get1stAndToday,
  past7Day,
  past30Day,
  pastWeek,
  pastMonth,
} from '@/utils/formValidate'
import {
  getTurnoverStatistics,
  getUserStatistics,
  getOrderStatistics,
  getTop,
} from '@/api/index'
// 组件
// 标题
import TitleIndex from './components/titleIndex.vue'
// 营业额统计
import TurnoverStatistics from './components/turnoverStatistics.vue'
// 用户统计
import UserStatistics from './components/userStatistics.vue'
// 订单统计
import OrderStatistics from './components/orderStatistics.vue'
// 排名
import Top from './components/top10.vue'

const overviewData = ref<any>({})
const flag = ref(2)
const tateData = ref<any[]>([])
const turnoverData = ref<any>({})
const userData = ref<any>({})
const orderData = ref<any>({
  data: {},
})
const top10Data = ref<any>({})

// 获取基本数据
function init(begin: any, end: any) {
  nextTick(() => {
    getTurnoverStatisticsData(begin, end)
    getUserStatisticsData(begin, end)
    getOrderStatisticsData(begin, end)
    getTopData(begin, end)
  })
}

// 获取营业额统计数据
async function getTurnoverStatisticsData(begin: any, end: any) {
  const data = await getTurnoverStatistics({ begin: begin, end: end })
  const turnoverDataRes = data
  turnoverData.value = {
    dateList: turnoverDataRes.dateList.split(','),
    turnoverList: turnoverDataRes.turnoverList.split(','),
  }
}
// 获取用户统计数据
async function getUserStatisticsData(begin: any, end: any) {
  const data = await getUserStatistics({ begin: begin, end: end })
  const userDataRes = data
  userData.value = {
    dateList: userDataRes.dateList.split(','),
    totalUserList: userDataRes.totalUserList.split(','),
    newUserList: userDataRes.newUserList.split(','),
  }
}
// 获取订单统计数据
async function getOrderStatisticsData(begin: any, end: any) {
  const data = await getOrderStatistics({ begin: begin, end: end })
  const orderDataRes = data
  orderData.value = {
    data: {
      dateList: orderDataRes.dateList.split(','),
      orderCountList: orderDataRes.orderCountList.split(','),
      validOrderCountList: orderDataRes.validOrderCountList.split(','),
    },
    totalOrderCount: orderDataRes.totalOrderCount,
    validOrderCount: orderDataRes.validOrderCount,
    orderCompletionRate: orderDataRes.orderCompletionRate
  }
}
// 获取排行数据
async function getTopData(begin: any, end: any) {
  const data = await getTop({ begin: begin, end: end })
  const top10DataRes = data
  top10Data.value = {
    nameList: top10DataRes.nameList.split(',').reverse(),
    numberList: top10DataRes.numberList.split(',').reverse(),
  }
  console.log(top10Data.value)
}
// 获取当前选中的tab时间
function getTitleNum(data: number) {
  switch (data) {
    case 1:
      tateData.value = get1stAndToday()
      break
    case 2:
      tateData.value = past7Day()
      break
    case 3:
      tateData.value = past30Day()
      break
    case 4:
      tateData.value = pastWeek()
      break
    case 5:
      tateData.value = pastMonth()
      break
  }
  init(tateData.value[0], tateData.value[1])
}

getTitleNum(2)
</script>

