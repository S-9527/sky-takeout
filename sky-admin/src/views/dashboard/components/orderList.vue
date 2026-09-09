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
      <div class="">
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
              prop="tablewareNumber"
              label="餐具数量"
              min-width="80"
              align="center"
              v-if="status === OrderStatus.Confirmed"
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
        <el-pagination
          v-if="counts > 10"
          class="pageList"
          :page-sizes="[10, 20, 30, 40]"
          :page-size="pageSize"
          layout="total, sizes, prev, pager, next, jumper"
          :total="counts"
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
import OrderDetailDialog from '@/components/Order/OrderDetailDialog.vue'
import OrderCancelDialog from '@/components/Order/OrderCancelDialog.vue'
import { getOrderDetailPage } from '@/api/order'
import { useOrderActions } from '@/composables/useOrderActions'
import {
  OrderStatus,
  ORDER_STATUS_TEXT,
  isOrderStatus,
  type OrderStatisticsVO,
  type OrderVO
} from '@/api/types'

const props = withDefaults(
  defineProps<{
    orderStatics?: OrderStatisticsVO
  }>(),
  {
    orderStatics: () => ({ toBeConfirmed: 0, confirmed: 0, deliveryInProgress: 0 })
  }
)
const emit = defineEmits(['getOrderListBy3Status'])

const activeIndex = ref(0)
const isSearch = ref(false)
const counts = ref(0)
const page = ref<number>(1)
const pageSize = ref<number>(10)
const status = ref<OrderStatus>(OrderStatus.ToBeConfirmed)
const orderData = ref<OrderVO[]>([])

const actions = useOrderActions({
  onSuccess: () => getOrderListData(status.value)
})

const tabList = computed(() => [
  {
    label: ORDER_STATUS_TEXT[OrderStatus.ToBeConfirmed],
    value: OrderStatus.ToBeConfirmed,
    num: props.orderStatics?.toBeConfirmed ?? 0
  },
  {
    label: ORDER_STATUS_TEXT[OrderStatus.Confirmed],
    value: OrderStatus.Confirmed,
    num: props.orderStatics?.confirmed ?? 0
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
  emit('getOrderListBy3Status')
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

// 分页
function handleSizeChange(val: number) {
  pageSize.value = val
  getOrderListData(status.value)
}

function handleCurrentChange(val: number) {
  page.value = val
  getOrderListData(status.value)
}
</script>
<style lang="scss" scoped>
.dashboard-container.home .homecon {
  margin-bottom: 0;
}
</style>
<style lang="scss">
.dashboard-container {
  .cancelTime {
    padding-left: 30px;
  }
  .orderTime {
    padding-left: 30px;
  }
  td.operate .cell {
    .before,
    .middle,
    .after {
      height: 39px;
      width: 48px;
    }
  }
  td.operate .cell,
  td.otherOperate .cell {
    display: flex;
    flex-wrap: nowrap;
    justify-content: center;
  }
}
</style>
