<template>
  <div>
    <div class="container homecon">
      <h2 class="homeTitle homeTitleBtn">
        订单信息
        <ul class="conTab">
          <li
            v-for="(item, index) in tabList"
            :key="index"
            :class="activeIndex === index ? 'active' : ''"
            @click="handleClass(index)"
          >
            <el-badge
              class="item"
              :class="item.num >= 10 ? 'badgeW' : ''"
              :value="item.num > 99 ? '99+' : item.num"
              :hidden="!(isOrderStatus(item.value, OrderStatus.ToBeConfirmed, OrderStatus.Confirmed) && item.num)"
              >{{ item.label }}</el-badge
            >
          </li>
        </ul>
      </h2>
      <div>
        <div v-if="orderData.length > 0">
          <el-table
            :data="orderData"
            stripe
            class="tableBox"
            style="width: 100%"
            @row-click="handleTable"
          >
            <el-table-column prop="number" label="订单号"> </el-table-column>
            <el-table-column label="订单菜品">
              <template #default="scope">
                <div class="ellipsisHidden">
                  <el-popover
                    placement="top-start"
                    title=""
                    width="200"
                    trigger="hover"
                    :content="scope.row.orderDishes"
                  >
                    <template #reference><span>{{ scope.row.orderDishes }}</span></template>
                  </el-popover>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              label="地址"
              :class-name="actions.state.detailStatus === OrderStatus.ToBeConfirmed ? 'address' : ''"
            >
              <template #default="scope">
                <div class="ellipsisHidden">
                  <el-popover
                    placement="top-start"
                    title=""
                    width="200"
                    trigger="hover"
                    :content="scope.row.address"
                  >
                    <template #reference><span>{{ scope.row.address }}</span></template>
                  </el-popover>
                </div>
              </template>
            </el-table-column>

            <el-table-column
              prop="estimatedDeliveryTime"
              label="预计送达时间"
              sortable
              class-name="orderTime"
              min-width="130"
            >
            </el-table-column>
            <el-table-column prop="amount" label="实收金额"> </el-table-column>
            <el-table-column label="备注">
              <template #default="scope">
                <div class="ellipsisHidden">
                  <el-popover
                    placement="top-start"
                    title=""
                    width="200"
                    trigger="hover"
                    :content="scope.row.remark"
                  >
                    <template #reference><span>{{ scope.row.remark }}</span></template>
                  </el-popover>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              v-if="status === OrderStatus.Confirmed"
              prop="tablewareNumber"
              label="餐具数量"
              min-width="80"
              align="center"
            >
            </el-table-column>
            <el-table-column
              label="操作"
              align="center"
              :class-name="actions.state.detailStatus === OrderStatus.All ? 'operate' : 'otherOperate'"
              :min-width="
                isOrderStatus(
                  actions.state.detailStatus,
                  OrderStatus.ToBeConfirmed,
                  OrderStatus.Confirmed
                )
                  ? 130
                  : actions.state.detailStatus === OrderStatus.All
                  ? 140
                  : 'auto'
              "
            >
              <template #default="{ row }">
                <div class="before">
                  <el-button
                    v-if="row.status === OrderStatus.ToBeConfirmed"
                    link
                    class="blueBug"
                    @click="actions.accept(row, $event, true)"
                  >
                    接单
                  </el-button>
                  <el-button
                    v-if="row.status === OrderStatus.Confirmed"
                    link
                    class="blueBug"
                    @click="actions.deliverOrComplete(row, $event)"
                  >
                    派送
                  </el-button>
                </div>
                <div class="middle">
                  <el-button
                    v-if="row.status === OrderStatus.ToBeConfirmed"
                    link
                    class="delBut"
                    @click="actions.openReject(row, $event, true)"
                  >
                    拒单
                  </el-button>
                  <el-button
                    v-if="
                      isOrderStatus(
                        row.status,
                        OrderStatus.PendingPayment,
                        OrderStatus.Confirmed,
                        OrderStatus.DeliveryInProgress,
                        OrderStatus.Completed
                      )
                    "
                    link
                    class="delBut"
                    @click="actions.openCancel(row, $event)"
                  >
                    取消
                  </el-button>
                </div>
                <div class="after">
                  <el-button
                    link
                    class="blueBug non"
                    @click="openDetail(row, $event)"
                  >
                    查看
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <Empty v-else :is-search="isSearch" />
        <Pagination
          :total="counts"
          :page="page"
          :page-size="pageSize"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>
    <!-- 订单详情 / 取消拒单弹窗 -->
    <OrderDetailDialog :actions="actions" :list-status="status" />
    <OrderCancelDialog :actions="actions" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import Empty from '@/components/Empty/index.vue'
import Pagination from '@/components/Pagination/index.vue'
import OrderDetailDialog from '@/components/Order/OrderDetailDialog.vue'
import OrderCancelDialog from '@/components/Order/OrderCancelDialog.vue'
import { getOrderDetailPage, getOrderListBy } from '@/api/order'
import { useOrderActions } from '@/composables/useOrderActions'
import { useTablePage } from '@/composables/useTablePage'
import {
  OrderStatus,
  ORDER_STATUS_TEXT,
  isOrderStatus
} from '@/constants/order'
import type { OrderStatisticsVO, OrderVO } from '@/api/types/order'

const activeIndex = ref(0)
const isSearch = ref(false)
const counts = ref(0)
const status = ref<OrderStatus>(OrderStatus.ToBeConfirmed)
const { page, pageSize, handleSizeChange, handleCurrentChange } = useTablePage(
  () => getOrderListData(status.value)
)
const orderData = ref<OrderVO[]>([])
const orderStatistics = ref<OrderStatisticsVO>()

const actions = useOrderActions({
  onSuccess: () => getOrderListData(status.value)
})

const tabList = computed(() => [
  {
    label: ORDER_STATUS_TEXT[OrderStatus.ToBeConfirmed],
    value: OrderStatus.ToBeConfirmed,
    num: orderStatistics.value?.toBeConfirmed ?? 0
  },
  {
    label: ORDER_STATUS_TEXT[OrderStatus.Confirmed],
    value: OrderStatus.Confirmed,
    num: orderStatistics.value?.confirmed ?? 0
  }
])

getOrderListData(status.value)

// 获取订单数据
async function getOrderListData(val: number) {
  const params = {
    page: page.value,
    pageSize: pageSize.value,
    status: val
  }
  const data = await getOrderDetailPage(params)
  orderData.value = data.records
  counts.value = data.total
  refreshStatistics()
  if (
    actions.state.detailStatus === OrderStatus.ToBeConfirmed &&
    status.value === OrderStatus.ToBeConfirmed &&
    actions.state.autoNext &&
    !actions.state.tableOperated &&
    data.records.length > 1
  ) {
    const firstRow = data.records[0]
    void actions.openDetail(firstRow.id, firstRow.status, firstRow)
  }
}

// 各状态订单数量,用于 tab 角标
function refreshStatistics() {
  getOrderListBy()
    .then((res) => {
      orderStatistics.value = res
    })
    .catch(() => {
      // 拦截器已统一提示,角标保留上一次的值
    })
}

// 查看详情
function openDetail(row: OrderVO, event?: Event) {
  void actions.openDetail(row.id, row.status, row, event)
}

// tab切换
function handleClass(index: number) {
  activeIndex.value = index
  if (index === 0) {
    status.value = OrderStatus.ToBeConfirmed
    getOrderListData(OrderStatus.ToBeConfirmed)
  } else {
    status.value = OrderStatus.Confirmed
    getOrderListData(OrderStatus.Confirmed)
  }
}

// 触发table某一行
function handleTable(row: OrderVO, _column: unknown, event: Event) {
  openDetail(row, event)
}
</script>
<style lang="scss" scoped>
.dashboard-container.home .homecon {
  margin-bottom: 0;
}
</style>
<style lang="scss">
// 图标操作列的通用样式见 styles/component/order-table.scss
.dashboard-container {
  .cancelTime {
    padding-left: 30px;
  }
  .orderTime {
    padding-left: 30px;
  }
}
</style>
