<script setup lang="ts">
import { ArrowLeft, Refresh } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  acceptOrder,
  cancelOrderByAdmin,
  completeOrder,
  getOrder,
  rejectOrder,
  startOrderDelivery,
} from '@/api/order'
import { createRefund } from '@/api/refund'
import { useAuthStore } from '@/stores/auth'
import type { OrderDetail } from '@/types'
import {
  canAccept,
  canCancel,
  canComplete,
  canDeliver,
  canRefund,
  canReject,
  cancelSideLabel,
  orderStatusLabel,
  orderStatusTag,
  payStatusLabel,
  payStatusTag,
  paymentStatusLabel,
  refundReasonTypeLabel,
  refundStatusLabel,
  refundStatusTag,
} from '@/utils/dict'
import { formatDateTime } from '@/utils/datetime'
import { confirmDanger, showError, showSuccess } from '@/utils/feedback'
import { formatCents } from '@/utils/money'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const orderId = computed(() => Number(route.params.id))
const detail = ref<OrderDetail | null>(null)
const loading = ref(false)

const canRefundOrder = computed(() => {
  if (!detail.value) return false
  // 已有"处理中/成功"的退款就不给按钮,避免必然的 409(后端仍会兜底)
  const hasActiveRefund = (detail.value.refunds ?? []).some(
    (refund) => refund.status === 'PENDING' || refund.status === 'SUCCESS',
  )
  return canRefund(detail.value) && !hasActiveRefund
})

async function load(): Promise<void> {
  loading.value = true
  try {
    detail.value = await getOrder(orderId.value)
  } catch (error) {
    showError(error, '订单详情加载失败')
  } finally {
    loading.value = false
  }
}

async function runAction(action: () => Promise<void>, success: string, confirm?: string): Promise<void> {
  if (confirm && !(await confirmDanger(confirm))) return
  try {
    await action()
    showSuccess(success)
    await load()
  } catch (error) {
    showError(error)
  }
}

/** 各动作的包装:模板里避免写非空断言,统一在这里取一次当前详情 */
async function doAccept(): Promise<void> {
  const current = detail.value
  if (current) await runAction(() => acceptOrder(current.id), '已接单')
}

async function doDeliver(): Promise<void> {
  const current = detail.value
  if (current) await runAction(() => startOrderDelivery(current.id), '已开始派送')
}

async function doComplete(): Promise<void> {
  const current = detail.value
  if (!current) return
  await runAction(() => completeOrder(current.id), '订单已完成', '确认订单已送达?完成后不可取消、不可退款。')
}

async function handleReject(): Promise<void> {
  if (!detail.value) return
  let reason = ''
  try {
    const result = await ElMessageBox.prompt('请输入拒单原因(顾客可见)', '商家拒单', {
      inputValidator: (value: string) => (value && value.trim() ? true : '拒单原因必填'),
    })
    reason = result.value.trim()
  } catch {
    return
  }
  await runAction(() => rejectOrder(orderId.value, reason), '已拒单')
}

async function handleCancel(): Promise<void> {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt('请输入取消原因(可留空)', '商家取消订单', {
      inputValidator: () => true,
    })
    reason = result.value?.trim() ?? ''
  } catch {
    return
  }
  await runAction(() => cancelOrderByAdmin(orderId.value, reason || undefined), '订单已取消')
}

/** 管理端整单全额退(仅 ADMIN) */
async function handleRefund(): Promise<void> {
  if (!detail.value) return
  let reason = ''
  try {
    const result = await ElMessageBox.prompt(
      `将按订单实付金额 ${formatCents(detail.value.payAmountCents)} 全额退款,请填写原因`,
      '发起退款',
      { inputValidator: (value: string) => (value && value.trim() ? true : '退款原因必填') },
    )
    reason = result.value.trim()
  } catch {
    return
  }
  try {
    // 金额由服务端按订单实付决定,这里只传订单号与原因
    await createRefund({ orderNo: detail.value.orderNo, reason, reasonType: 'CUSTOMER_APPLY' })
    showSuccess('退款已受理')
    await load()
  } catch (error) {
    showError(error)
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="space-y-4">
    <div class="flex items-center justify-between">
      <el-button :icon="ArrowLeft" text @click="router.back()">返回</el-button>
      <div class="flex items-center gap-2">
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <el-button
          v-if="detail && canRefundOrder && auth.isAdmin"
          type="warning"
          data-testid="refund-order"
          @click="handleRefund"
        >
          全额退款
        </el-button>
      </div>
    </div>

    <el-card v-if="detail" shadow="never" data-testid="order-detail">
      <template #header>
        <div class="flex flex-wrap items-center gap-3">
          <span class="text-base font-semibold">订单 {{ detail.orderNo }}</span>
          <el-tag :type="orderStatusTag(detail.status)">{{ orderStatusLabel(detail.status) }}</el-tag>
          <el-tag :type="payStatusTag(detail.payStatus)" effect="plain">
            {{ payStatusLabel(detail.payStatus) }}
          </el-tag>
          <el-tag v-if="detail.cancelSide" type="info" effect="plain">
            {{ cancelSideLabel(detail.cancelSide) }}
          </el-tag>
        </div>
      </template>

      <div class="flex flex-wrap gap-2">
        <el-button v-if="canAccept(detail)" type="primary" data-testid="detail-accept" @click="doAccept">
          接单
        </el-button>
        <el-button v-if="canReject(detail)" type="danger" @click="handleReject">拒单</el-button>
        <el-button v-if="canDeliver(detail)" type="primary" @click="doDeliver">开始派送</el-button>
        <el-button v-if="canComplete(detail)" type="success" @click="doComplete">完成订单</el-button>
        <el-button v-if="canCancel(detail)" @click="handleCancel">取消订单</el-button>
      </div>

      <el-descriptions class="mt-4" :column="3" border>
        <el-descriptions-item label="下单时间">{{ formatDateTime(detail.placedAt) }}</el-descriptions-item>
        <el-descriptions-item label="支付时间">{{ formatDateTime(detail.paidAt) }}</el-descriptions-item>
        <el-descriptions-item label="接单时间">{{ formatDateTime(detail.acceptedAt) }}</el-descriptions-item>
        <el-descriptions-item label="派送时间">{{ formatDateTime(detail.deliveringAt) }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{ formatDateTime(detail.completedAt) }}</el-descriptions-item>
        <el-descriptions-item label="预计送达">
          {{ formatDateTime(detail.estimatedDeliveryAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="收货人">{{ detail.consignee || '-' }}</el-descriptions-item>
        <el-descriptions-item label="电话">{{ detail.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="餐具份数">{{ detail.tablewareCount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="地址" :span="3">
          {{ [detail.province, detail.city, detail.district, detail.detail].filter(Boolean).join(' ') || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ detail.remark || '无' }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.cancelledAt" label="取消时间">
          {{ formatDateTime(detail.cancelledAt) }}
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.cancelReason" label="取消原因">
          {{ detail.cancelReason }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card v-if="detail" shadow="never">
      <template #header><span class="font-medium">商品明细</span></template>
      <el-table :data="detail.items" size="small">
        <el-table-column prop="nameSnapshot" label="名称" min-width="160" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ row.itemType === 'SETMEAL' ? '套餐' : '菜品' }}</template>
        </el-table-column>
        <el-table-column label="单价" width="120" align="right">
          <template #default="{ row }">{{ formatCents(row.unitPriceCents) }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="80" align="right" />
        <el-table-column label="小计" width="120" align="right">
          <template #default="{ row }">
            <span class="sky-amount">{{ formatCents(row.amountCents) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="口味 / 套餐组成" min-width="180">
          <template #default="{ row }">
            <template v-if="row.comboSnapshot && row.comboSnapshot.length > 0">
              <el-tag v-for="combo in row.comboSnapshot" :key="combo.dishId" class="mr-1" size="small" effect="plain">
                {{ combo.name }} ×{{ combo.copies }}
              </el-tag>
            </template>
            <template v-else-if="row.flavorSnapshot && row.flavorSnapshot.length > 0">
              <el-tag
                v-for="flavor in row.flavorSnapshot"
                :key="flavor.name + flavor.option"
                class="mr-1"
                size="small"
                effect="plain"
              >
                {{ flavor.name }}:{{ flavor.option }}
              </el-tag>
            </template>
            <span v-else class="text-slate-400">-</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="mt-4 flex justify-end">
        <el-descriptions :column="1" border class="w-72">
          <el-descriptions-item label="商品合计">
            {{ formatCents(detail.totalAmountCents) }}
          </el-descriptions-item>
          <el-descriptions-item label="打包费">{{ formatCents(detail.packAmountCents) }}</el-descriptions-item>
          <el-descriptions-item label="配送费">
            {{ formatCents(detail.deliveryAmountCents) }}
          </el-descriptions-item>
          <el-descriptions-item label="优惠">
            -{{ formatCents(detail.discountAmountCents) }}
          </el-descriptions-item>
          <el-descriptions-item label="实付">
            <span class="sky-amount text-red-500">{{ formatCents(detail.payAmountCents) }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-card>

    <el-card v-if="detail" shadow="never">
      <template #header><span class="font-medium">支付与退款</span></template>
      <el-table :data="detail.payments ?? []" size="small" empty-text="暂无支付记录">
        <el-table-column prop="id" label="支付单号" width="120" />
        <el-table-column label="渠道" width="100">
          <template #default="{ row }">{{ row.channel }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">{{ paymentStatusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">{{ formatCents(row.amountCents) }}</template>
        </el-table-column>
        <el-table-column label="支付时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.paidAt) }}</template>
        </el-table-column>
      </el-table>

      <el-table class="mt-4" :data="detail.refunds ?? []" size="small" empty-text="暂无退款记录">
        <el-table-column prop="refundNo" label="退款单号" min-width="180" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="refundStatusTag(row.status)" size="small">{{ refundStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">{{ formatCents(row.amountCents) }}</template>
        </el-table-column>
        <el-table-column label="原因类型" width="120">
          <template #default="{ row }">{{ refundReasonTypeLabel(row.reasonType) }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="160" />
        <el-table-column label="退款时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.refundedAt) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
