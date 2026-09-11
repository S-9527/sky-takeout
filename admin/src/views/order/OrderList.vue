<script setup lang="ts">
import { Refresh, Search } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  acceptOrder,
  cancelOrderByAdmin,
  completeOrder,
  countOrdersByStatus,
  pageOrders,
  rejectOrder,
  startOrderDelivery,
} from '@/api/order'
import type { Order, OrderStatus, OrderStatusCounts } from '@/types'
import {
  canAccept,
  canCancel,
  canComplete,
  canDeliver,
  canReject,
  ORDER_STATUS_OPTIONS,
  orderStatusLabel,
  orderStatusTag,
  payStatusLabel,
  payStatusTag,
} from '@/utils/dict'
import { formatDateTime } from '@/utils/datetime'
import { confirmDanger, showError, showSuccess } from '@/utils/feedback'
import { formatCents } from '@/utils/money'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const orders = ref<Order[]>([])
const counts = ref<OrderStatusCounts | null>(null)
const total = ref(0)

const filters = reactive({
  status: (route.query.status as OrderStatus | undefined) ?? undefined,
  dateRange: [] as string[],
  orderNo: '',
  phone: '',
  page: 1,
  pageSize: 20,
})

/** 自动刷新:来单提醒是提示信号,列表要自己拉最新(REST 才是权威) */
const autoRefresh = ref(false)
let timer: ReturnType<typeof setInterval> | null = null

function syncAutoRefresh(): void {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  if (autoRefresh.value) {
    timer = setInterval(() => {
      void load()
    }, 20_000)
  }
}

watch(autoRefresh, syncAutoRefresh)

async function load(): Promise<void> {
  loading.value = true
  try {
    const [page, statusCounts] = await Promise.all([
      pageOrders({
        page: filters.page,
        pageSize: filters.pageSize,
        sort: 'placedAt,desc',
        status: filters.status,
        beginDate: filters.dateRange?.[0],
        endDate: filters.dateRange?.[1],
        orderNo: filters.orderNo.trim() || undefined,
        phone: filters.phone.trim() || undefined,
      }),
      countOrdersByStatus().catch(() => null),
    ])
    orders.value = page.records
    total.value = page.total
    if (statusCounts) counts.value = statusCounts
  } catch (error) {
    showError(error, '订单加载失败')
  } finally {
    loading.value = false
  }
}

function search(): void {
  filters.page = 1
  void load()
  void router.replace({ query: filters.status ? { status: filters.status } : {} })
}

function reset(): void {
  filters.status = undefined
  filters.dateRange = []
  filters.orderNo = ''
  filters.phone = ''
  search()
}

/** 状态页签上的角标数量 */
function countOf(status: OrderStatus): number | undefined {
  if (!counts.value) return undefined
  const map: Record<OrderStatus, number> = {
    PENDING_PAYMENT: counts.value.pendingPayment,
    PENDING_ACCEPTANCE: counts.value.pendingAcceptance,
    ACCEPTED: counts.value.accepted,
    DELIVERING: counts.value.delivering,
    COMPLETED: counts.value.completed,
    CANCELLED: counts.value.cancelled,
  }
  return map[status]
}

/** 五个动作的共同套路:确认 → 调接口 → 重新拉列表(动作接口都是 204,没有响应体) */
async function runAction(
  action: () => Promise<void>,
  options: { confirm?: string; success: string },
): Promise<void> {
  if (options.confirm && !(await confirmDanger(options.confirm))) return
  try {
    await action()
    showSuccess(options.success)
    await load()
  } catch (error) {
    showError(error)
  }
}

function handleAccept(order: Order): void {
  void runAction(() => acceptOrder(order.id), { success: '已接单' })
}

function handleDeliver(order: Order): void {
  void runAction(() => startOrderDelivery(order.id), { success: '已开始派送' })
}

function handleComplete(order: Order): void {
  void runAction(() => completeOrder(order.id), {
    confirm: `确认订单 ${order.orderNo} 已送达?完成后不可取消、不可退款。`,
    success: '订单已完成',
  })
}

async function handleReject(order: Order): Promise<void> {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt('请输入拒单原因(顾客可见)', '商家拒单', {
      inputPlaceholder: '如:菜品已售完',
      inputValidator: (value: string) => (value && value.trim().length > 0 ? true : '拒单原因必填'),
    })
    reason = result.value.trim()
  } catch {
    return
  }
  try {
    // 已支付订单会连带强制退款;退款受理失败时后端返回 502,订单保持原状态
    await rejectOrder(order.id, reason)
    showSuccess('已拒单')
    await load()
  } catch (error) {
    showError(error)
  }
}

async function handleCancel(order: Order): Promise<void> {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt('请输入取消原因(可留空)', '商家取消订单', {
      inputPlaceholder: '如:顾客电话要求取消',
      inputValidator: () => true,
    })
    reason = result.value?.trim() ?? ''
  } catch {
    return
  }
  try {
    await cancelOrderByAdmin(order.id, reason || undefined)
    showSuccess('订单已取消')
    await load()
  } catch (error) {
    showError(error)
  }
}

onMounted(() => {
  void load()
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="space-y-4">
    <el-card shadow="never">
      <el-form :inline="true" @submit.prevent="search">
        <el-form-item label="订单状态">
          <el-select v-model="filters.status" clearable placeholder="全部" style="width: 160px">
            <el-option
              v-for="option in ORDER_STATUS_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            >
              <span>{{ option.label }}</span>
              <span v-if="countOf(option.value) !== undefined" class="float-right text-slate-400">
                {{ countOf(option.value) }}
              </span>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="下单时间">
          <el-date-picker
            v-model="filters.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            :clearable="true"
          />
        </el-form-item>

        <el-form-item label="订单号">
          <el-input v-model="filters.orderNo" placeholder="精确匹配" clearable style="width: 200px" />
        </el-form-item>

        <el-form-item label="手机号">
          <el-input v-model="filters.phone" placeholder="收货人手机号" clearable style="width: 160px" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
          <el-button :icon="Refresh" @click="load">刷新</el-button>
          <el-checkbox v-model="autoRefresh" class="ml-2">自动刷新(20s)</el-checkbox>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="orders" empty-text="没有符合条件的订单" data-testid="order-table">
        <el-table-column label="订单号" min-width="180">
          <template #default="{ row }">
            <el-link type="primary" @click="router.push({ name: 'order-detail', params: { id: row.id } })">
              {{ row.orderNo }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="orderStatusTag(row.status)" size="small">
              {{ orderStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="支付" width="110">
          <template #default="{ row }">
            <el-tag :type="payStatusTag(row.payStatus)" size="small" effect="plain">
              {{ payStatusLabel(row.payStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">
            <span class="sky-amount">{{ formatCents(row.payAmountCents) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="收货人" min-width="160">
          <template #default="{ row }">
            <div>{{ row.consignee || '-' }}</div>
            <div class="text-xs text-slate-400">{{ row.phone }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="detail" label="地址" min-width="200" show-overflow-tooltip />
        <el-table-column label="下单时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.placedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="canAccept(row)"
              type="primary"
              size="small"
              data-testid="accept-order"
              @click="handleAccept(row)"
            >
              接单
            </el-button>
            <el-button v-if="canReject(row)" type="danger" size="small" @click="handleReject(row)">
              拒单
            </el-button>
            <el-button v-if="canDeliver(row)" type="primary" size="small" @click="handleDeliver(row)">
              派送
            </el-button>
            <el-button v-if="canComplete(row)" type="success" size="small" @click="handleComplete(row)">
              完成
            </el-button>
            <el-button v-if="canCancel(row)" size="small" @click="handleCancel(row)">取消</el-button>
            <el-button size="small" text @click="router.push({ name: 'order-detail', params: { id: row.id } })">
              详情
            </el-button>
          </template>
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
