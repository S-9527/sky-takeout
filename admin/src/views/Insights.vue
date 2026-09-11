<script setup lang="ts">
import { Refresh } from '@element-plus/icons-vue'
import { computed, onMounted, ref, watch } from 'vue'

import { getOrderStats, getTopDishes, getTurnoverStats, getUserStats } from '@/api/insights'
import BaseChart from '@/components/charts/BaseChart.vue'
import { orderOption, topDishOption, turnoverOption, userOption } from '@/components/charts/options'
import type { OrderStats, TopDishSales, TurnoverStats, UserStats } from '@/types'
import { recentDaysRange } from '@/utils/datetime'
import { showError } from '@/utils/feedback'
import { formatCents } from '@/utils/money'

/**
 * 数据统计。
 *
 * 区间参数固定用 `beginDate`/`endDate`(含首含尾,格式 `YYYY-MM-DD`),
 * 与订单列表保持同名 —— 契约里特意统一了这两处参数名。
 */
const range = ref<[string, string]>(recentDaysRange(7))
const loading = ref(false)

const turnover = ref<TurnoverStats | null>(null)
const users = ref<UserStats | null>(null)
const orders = ref<OrderStats | null>(null)
const topDishes = ref<TopDishSales | null>(null)

/** 常用区间快捷选项(picker 的 shortcuts) */
const shortcuts = [
  { text: '最近 7 天', value: () => datesFromRange(7) },
  { text: '最近 30 天', value: () => datesFromRange(30) },
  { text: '最近 90 天', value: () => datesFromRange(90) },
]

function datesFromRange(days: number): [Date, Date] {
  const [begin, end] = recentDaysRange(days)
  return [new Date(`${begin}T00:00:00+08:00`), new Date(`${end}T00:00:00+08:00`)]
}

const validRatePercent = computed(() => {
  const rate = orders.value?.validOrderRate ?? 0
  return `${(rate * 100).toFixed(1)}%`
})

const averageOrderCents = computed(() => {
  const sum = turnover.value?.sum ?? 0
  const count = orders.value?.validOrderCount ?? 0
  return count > 0 ? Math.round(sum / count) : 0
})

async function load(): Promise<void> {
  const [beginDate, endDate] = range.value ?? []
  if (!beginDate || !endDate) return
  loading.value = true
  try {
    const query = { beginDate, endDate }
    const [turnoverData, userData, orderData, dishData] = await Promise.all([
      getTurnoverStats(query),
      getUserStats(query),
      getOrderStats(query),
      getTopDishes({ ...query, topNumber: 10 }),
    ])
    turnover.value = turnoverData
    users.value = userData
    orders.value = orderData
    topDishes.value = dishData
  } catch (error) {
    showError(error, '报表数据加载失败')
  } finally {
    loading.value = false
  }
}

watch(range, () => {
  void load()
})

onMounted(load)
</script>

<template>
  <div class="space-y-4" data-testid="insights">
    <el-card shadow="never">
      <div class="flex flex-wrap items-center gap-3">
        <span class="text-sm text-slate-600">统计区间</span>
        <el-date-picker
          v-model="range"
          type="daterange"
          value-format="YYYY-MM-DD"
          :shortcuts="shortcuts"
          :clearable="false"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          data-testid="insights-range"
        />
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <span class="text-xs text-slate-400">含首含尾;跨度上限 366 天</span>
      </div>
    </el-card>

    <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <el-card shadow="never">
        <div class="text-sm text-slate-500">区间营业额</div>
        <div class="sky-amount mt-2 text-2xl">{{ formatCents(turnover?.sum ?? 0) }}</div>
      </el-card>
      <el-card shadow="never">
        <div class="text-sm text-slate-500">客单价(按有效订单)</div>
        <div class="sky-amount mt-2 text-2xl">{{ formatCents(averageOrderCents) }}</div>
      </el-card>
      <el-card shadow="never">
        <div class="text-sm text-slate-500">订单总数 / 有效</div>
        <div class="mt-2 text-2xl font-semibold">
          {{ orders?.totalOrderCount ?? 0 }}
          <span class="text-sm font-normal text-slate-400">/ {{ orders?.validOrderCount ?? 0 }}</span>
        </div>
      </el-card>
      <el-card shadow="never">
        <div class="text-sm text-slate-500">有效订单占比</div>
        <div class="mt-2 text-2xl font-semibold">{{ validRatePercent }}</div>
      </el-card>
    </div>

    <el-card shadow="never">
      <template #header><span class="font-medium">营业额趋势</span></template>
      <BaseChart :option="turnoverOption(turnover?.daily ?? [])" :loading="loading" />
    </el-card>

    <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
      <el-card shadow="never">
        <template #header><span class="font-medium">用户统计</span></template>
        <div class="mb-2 text-sm text-slate-500">
          区间新增 {{ users?.newUserCount ?? 0 }} 人,累计 {{ users?.totalUserCount ?? 0 }} 人
        </div>
        <BaseChart :option="userOption(users?.daily ?? [])" :loading="loading" height="260px" />
      </el-card>

      <el-card shadow="never">
        <template #header><span class="font-medium">订单统计</span></template>
        <div class="mb-2 text-sm text-slate-500">
          有效订单 = 已完成订单;占比 {{ validRatePercent }}
        </div>
        <BaseChart :option="orderOption(orders?.daily ?? [])" :loading="loading" height="260px" />
      </el-card>
    </div>

    <el-card shadow="never">
      <template #header>
        <span class="font-medium">销量 Top {{ topDishes?.topNumber ?? 10 }}</span>
      </template>
      <BaseChart :option="topDishOption(topDishes?.items ?? [])" :loading="loading" height="360px" />
    </el-card>
  </div>
</template>
