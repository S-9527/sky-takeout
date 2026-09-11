<script setup lang="ts">
import { Refresh, Search } from '@element-plus/icons-vue'
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { pageRefunds } from '@/api/refund'
import type { Refund, RefundStatus } from '@/types'
import { refundReasonTypeLabel, refundStatusLabel, refundStatusTag, REFUND_STATUS_DICT } from '@/utils/dict'
import { formatDateTime } from '@/utils/datetime'
import { showError } from '@/utils/feedback'
import { formatCents } from '@/utils/money'

const router = useRouter()

const loading = ref(false)
const refunds = ref<Refund[]>([])
const total = ref(0)

const filters = reactive({
  status: undefined as RefundStatus | undefined,
  orderNo: '',
  refundNo: '',
  dateRange: [] as string[],
  page: 1,
  pageSize: 20,
})

const statusOptions = Object.entries(REFUND_STATUS_DICT).map(([value, item]) => ({
  value: value as RefundStatus,
  label: item.label,
}))

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await pageRefunds({
      page: filters.page,
      pageSize: filters.pageSize,
      sort: 'createdAt,desc',
      status: filters.status,
      orderNo: filters.orderNo.trim() || undefined,
      refundNo: filters.refundNo.trim() || undefined,
      beginDate: filters.dateRange?.[0],
      endDate: filters.dateRange?.[1],
    })
    refunds.value = page.records
    total.value = page.total
  } catch (error) {
    showError(error, '退款记录加载失败')
  } finally {
    loading.value = false
  }
}

function search(): void {
  filters.page = 1
  void load()
}

function reset(): void {
  filters.status = undefined
  filters.orderNo = ''
  filters.refundNo = ''
  filters.dateRange = []
  search()
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <el-alert
      type="info"
      :closable="false"
      title="v2 只支持整单全额退;退款金额恒等于订单实付金额。管理端发起退款仅限管理员。"
    />

    <el-card shadow="never">
      <el-form :inline="true" @submit.prevent="search">
        <el-form-item label="退款状态">
          <el-select v-model="filters.status" clearable placeholder="全部" style="width: 140px">
            <el-option
              v-for="option in statusOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="订单号">
          <el-input v-model="filters.orderNo" clearable placeholder="精确匹配" style="width: 200px" />
        </el-form-item>
        <el-form-item label="退款单号">
          <el-input v-model="filters.refundNo" clearable placeholder="精确匹配" style="width: 200px" />
        </el-form-item>
        <el-form-item label="申请时间">
          <el-date-picker
            v-model="filters.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
          <el-button :icon="Refresh" @click="load">刷新</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="refunds" empty-text="没有退款记录" data-testid="refund-table">
        <el-table-column prop="refundNo" label="退款单号" min-width="190" />
        <el-table-column label="订单号" min-width="190">
          <template #default="{ row }">
            <el-link
              v-if="row.orderId"
              type="primary"
              @click="router.push({ name: 'order-detail', params: { id: row.orderId } })"
            >
              {{ row.orderNo || row.orderId }}
            </el-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="refundStatusTag(row.status)" size="small">
              {{ refundStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">
            <span class="sky-amount">{{ formatCents(row.amountCents) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="原因类型" width="120">
          <template #default="{ row }">{{ refundReasonTypeLabel(row.reasonType) }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="180" show-overflow-tooltip />
        <el-table-column label="申请时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="退款时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.refundedAt) }}</template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="mt-4 justify-end"
        layout="total, sizes, prev, pager, next"
        :total="total"
        :current-page="filters.page"
        :page-size="filters.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="
          (page: number) => {
            filters.page = page
            load()
          }
        "
        @size-change="
          (size: number) => {
            filters.pageSize = size
            filters.page = 1
            load()
          }
        "
      />
    </el-card>
  </div>
</template>
