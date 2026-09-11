<script setup lang="ts">
import { Refresh } from '@element-plus/icons-vue'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { getWorkbench } from '@/api/insights'
import { ORDER_STATUS_DICT } from '@/utils/dict'
import { formatCents } from '@/utils/money'
import { showError } from '@/utils/feedback'
import type { OverviewItem, Workbench } from '@/types'

/**
 * 工作台首页。
 *
 * 数据全部来自 `GET /admin/insights/workbench`("今日"按门店时区算,D8),
 * 前端不自己拼 SQL 口径 —— 页面上的"今日营业额"必须和报表页一致。
 */
const router = useRouter()
const data = ref<Workbench | null>(null)
const loading = ref(false)

/** 计数项点击后跳到带筛选的列表页 */
const overviewRoute: Record<string, string> = {
  pendingAcceptance: '/orders?status=PENDING_ACCEPTANCE',
  accepted: '/orders?status=ACCEPTED',
  delivering: '/orders?status=DELIVERING',
  completed: '/orders?status=COMPLETED',
  cancelledOrders: '/orders?status=CANCELLED',
}

async function load(): Promise<void> {
  loading.value = true
  try {
    data.value = await getWorkbench()
  } catch (error) {
    showError(error, '工作台数据加载失败')
  } finally {
    loading.value = false
  }
}

function openOverview(item: OverviewItem): void {
  const target = overviewRoute[item.name]
  if (target) void router.push(target)
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="space-y-4" data-testid="workbench">
    <div class="flex items-center justify-between">
      <h3 class="text-base font-semibold text-slate-800">今日概览</h3>
      <el-button :icon="Refresh" text @click="load">刷新</el-button>
    </div>

    <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-5">
      <el-card shadow="never" data-testid="today-turnover">
        <div class="text-sm text-slate-500">今日营业额</div>
        <div class="sky-amount mt-2 text-2xl text-slate-900">
          {{ formatCents(data?.today.turnoverCents ?? 0) }}
        </div>
      </el-card>
      <el-card shadow="never">
        <div class="text-sm text-slate-500">今日有效订单</div>
        <div class="mt-2 text-2xl font-semibold text-slate-900">
          {{ data?.today.validOrderCount ?? 0 }}
          <span class="text-sm font-normal text-slate-400">/ {{ data?.today.totalOrderCount ?? 0 }}</span>
        </div>
      </el-card>
      <el-card shadow="never">
        <div class="text-sm text-slate-500">今日新增顾客</div>
        <div class="mt-2 text-2xl font-semibold text-slate-900">{{ data?.today.newUserCount ?? 0 }}</div>
      </el-card>
      <el-card
        shadow="never"
        class="cursor-pointer"
        data-testid="pending-acceptance"
        @click="router.push('/orders?status=PENDING_ACCEPTANCE')"
      >
        <div class="text-sm text-slate-500">待接单</div>
        <div class="mt-2 text-2xl font-semibold text-red-500">
          {{ data?.today.pendingAcceptanceCount ?? 0 }}
        </div>
      </el-card>
      <el-card
        shadow="never"
        class="cursor-pointer"
        @click="router.push('/orders?status=ACCEPTED')"
      >
        <div class="text-sm text-slate-500">待派送</div>
        <div class="mt-2 text-2xl font-semibold text-blue-500">
          {{ data?.today.pendingDeliveryCount ?? 0 }}
        </div>
      </el-card>
    </div>

    <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
      <el-card shadow="never">
        <template #header>
          <span class="font-medium">订单概览</span>
        </template>
        <div class="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <div
            v-for="item in data?.orderOverview ?? []"
            :key="item.name"
            class="cursor-pointer rounded border border-slate-100 p-3 hover:border-blue-300"
            @click="openOverview(item)"
          >
            <div class="text-xs text-slate-500">{{ item.title ?? item.name }}</div>
            <div class="mt-1 text-xl font-semibold text-slate-800">{{ item.value }}</div>
          </div>
        </div>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <span class="font-medium">菜品概览</span>
        </template>
        <div class="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <div v-for="item in data?.dishOverview ?? []" :key="item.name" class="rounded border border-slate-100 p-3">
            <div class="text-xs text-slate-500">{{ item.title ?? item.name }}</div>
            <div class="mt-1 text-xl font-semibold text-slate-800">{{ item.value }}</div>
          </div>
        </div>
      </el-card>
    </div>

    <el-card shadow="never">
      <template #header>
        <span class="font-medium">状态说明</span>
      </template>
      <div class="flex flex-wrap gap-2">
        <el-tag
          v-for="(item, status) in ORDER_STATUS_DICT"
          :key="status"
          :type="item.tag"
          effect="plain"
        >
          {{ item.label }}
        </el-tag>
      </div>
    </el-card>
  </div>
</template>
