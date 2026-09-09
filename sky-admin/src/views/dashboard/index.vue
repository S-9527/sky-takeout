<template>
  <div class="dashboard-container home" v-loading="loading">
    <div v-if="error" class="workspace-error">
      <span>{{ error }}</span>
      <el-button type="primary" link @click="refresh">重试</el-button>
    </div>
    <!-- 营业数据 -->
    <Overview :overview-data="overviewData" />
    <!-- end -->
    <!-- 订单管理 -->
    <Orderview :orderview-data="orderviewData" />
    <!-- end -->
    <div class="homeMain">
      <!-- 菜品总览 -->
      <CuisineStatistics :dishes-data="dishesData" />
      <!-- end -->
      <!-- 套餐总览 -->
      <SetMealStatistics :set-meal-data="setMealData" />
      <!-- end -->
    </div>
    <!-- 订单信息 -->
    <OrderList />
    <!-- end -->
  </div>
</template>

<script setup lang="ts">
import Overview from './components/overview.vue'
import Orderview from './components/orderview.vue'
import CuisineStatistics from './components/cuisineStatistics.vue'
import SetMealStatistics from './components/setMealStatistics.vue'
import OrderList from './components/orderList.vue'
import { useWorkspaceData } from '@/composables/useWorkspaceData'

const { overviewData, orderviewData, dishesData, setMealData, loading, error, refresh } =
  useWorkspaceData()
</script>

<style lang="scss" scoped>
.workspace-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  padding: 10px 16px;
  background: #fffbf0;
  border: 1px solid #fbe396;
  border-radius: 4px;
  color: #f2a402;
  font-size: 13px;
}
</style>
