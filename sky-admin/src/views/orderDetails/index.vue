<template>
  <div class="dashboard-container">
    <TabChange
      :order-statics="orderStatics"
      :default-activity="defaultActivity"
      @tabChange="change"
    />
    <div class="container" :class="{ hContainer: tableData.length }">
      <!-- 搜索项 -->
      <div class="tableBar">
        <label style="margin-right: 10px">订单号：</label>
        <el-input
          v-model="input"
          placeholder="请填写订单号"
          style="width: 15%"
          clearable
          @clear="init(orderStatus)"
          @keyup.enter="initFun(orderStatus)"
        />
        <label style="margin-left: 20px">手机号：</label>
        <el-input
          v-model="phone"
          placeholder="请填写手机号"
          style="width: 15%"
          clearable
          @clear="init(orderStatus)"
          @keyup.enter="initFun(orderStatus)"
        />
        <label style="margin-left: 20px">下单时间：</label>
        <el-date-picker
          v-model="valueTime"
          clearable
          value-format="yyyy-MM-dd HH:mm:ss"
          range-separator="至"
          :default-time="defaultTime"
          type="daterange"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 25%; margin-left: 10px"
          @clear="init(orderStatus)"
        />
        <el-button class="normal-btn continue" @click="init(orderStatus, true)">
          查询
        </el-button>
      </div>
      <el-table
        v-if="tableData.length"
        :data="tableData"
        stripe
        class="tableBox"
      >
        <el-table-column key="number" prop="number" label="订单号" />
        <el-table-column
          v-if="isOrderStatus(orderStatus, OrderStatus.ToBeConfirmed, OrderStatus.Confirmed, OrderStatus.DeliveryInProgress)"
          key="orderDishes"
          prop="orderDishes"
          label="订单菜品"
        />
        <el-table-column
          v-if="isOrderStatus(orderStatus, OrderStatus.All)"
          key="status"
          prop="订单状态"
          label="订单状态"
        >
          <template #default="{ row }">
            <span>{{ getOrderType(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          v-if="isOrderStatus(orderStatus, OrderStatus.All, OrderStatus.Completed, OrderStatus.Cancelled)"
          key="consignee"
          prop="consignee"
          label="用户名"
          show-overflow-tooltip
        />
        <el-table-column
          v-if="isOrderStatus(orderStatus, OrderStatus.All, OrderStatus.Completed, OrderStatus.Cancelled)"
          key="phone"
          prop="phone"
          label="手机号"
        />
        <el-table-column
          v-if="isOrderStatus(orderStatus, OrderStatus.All, OrderStatus.ToBeConfirmed, OrderStatus.Confirmed, OrderStatus.DeliveryInProgress, OrderStatus.Completed, OrderStatus.Cancelled)"
          key="address"
          prop="address"
          label="地址"
          :class-name="orderStatus === OrderStatus.Cancelled ? 'address' : ''"
        />
        <el-table-column
          v-if="isOrderStatus(orderStatus, OrderStatus.All, OrderStatus.Cancelled)"
          key="orderTime"
          prop="orderTime"
          label="下单时间"
          class-name="orderTime"
          min-width="110"
        />
        <el-table-column
          v-if="isOrderStatus(orderStatus, OrderStatus.Cancelled)"
          key="cancelTime"
          prop="cancelTime"
          class-name="cancelTime"
          label="取消时间"
          min-width="110"
        />
        <el-table-column
          v-if="isOrderStatus(orderStatus, OrderStatus.Cancelled)"
          key="cancelReason"
          prop="cancelReason"
          label="取消原因"
          class-name="cancelReason"
          :min-width="isOrderStatus(orderStatus, OrderStatus.Cancelled) ? 80 : 'auto'"
        />
        <el-table-column
          v-if="isOrderStatus(orderStatus, OrderStatus.Completed)"
          key="deliveryTime"
          prop="deliveryTime"
          label="送达时间"
        />
        <el-table-column
          v-if="isOrderStatus(orderStatus, OrderStatus.ToBeConfirmed, OrderStatus.Confirmed, OrderStatus.DeliveryInProgress)"
          key="estimatedDeliveryTime"
          prop="estimatedDeliveryTime"
          label="预计送达时间"
          min-width="110"
        />
        <el-table-column
          v-if="isOrderStatus(orderStatus, OrderStatus.All, OrderStatus.ToBeConfirmed, OrderStatus.Completed)"
          key="amount"
          prop="amount"
          label="实收金额"
          align="center"
        >
          <template #default="{ row }">
            <span>￥{{ ((row.amount ?? 0).toFixed(2) * 100) / 100 }}</span>
          </template>
        </el-table-column>
        <el-table-column
          v-if="isOrderStatus(orderStatus, OrderStatus.ToBeConfirmed, OrderStatus.Confirmed, OrderStatus.DeliveryInProgress, OrderStatus.Completed)"
          key="remark"
          prop="remark"
          label="备注"
          align="center"
        />
        <el-table-column
          v-if="isOrderStatus(orderStatus, OrderStatus.ToBeConfirmed, OrderStatus.Confirmed, OrderStatus.DeliveryInProgress)"
          key="tablewareNumber"
          prop="tablewareNumber"
          label="餐具数量"
          align="center"
          min-width="80"
        />
        <el-table-column
          prop="btn"
          label="操作"
          align="center"
          :class-name="orderStatus === OrderStatus.All ? 'operate' : 'otherOperate'"
          :min-width="
            isOrderStatus(orderStatus, OrderStatus.ToBeConfirmed, OrderStatus.Confirmed, OrderStatus.DeliveryInProgress)
              ? 130
              : isOrderStatus(orderStatus, OrderStatus.All)
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
              <el-button
                v-if="row.status === OrderStatus.DeliveryInProgress"
                link
                class="blueBug"
                @click="actions.deliverOrComplete(row, $event)"
              >
                完成
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
                @click="goDetail(row.id, row.status, row)"
              >
                查看
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
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

    <!-- 订单详情 / 取消拒单弹窗 -->
    <OrderDetailDialog :actions="actions" :list-status="orderStatus" />
    <OrderCancelDialog :actions="actions" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TabChange from './tabChange.vue'
import Empty from '@/components/Empty/index.vue'
import OrderDetailDialog from '@/components/Order/OrderDetailDialog.vue'
import OrderCancelDialog from '@/components/Order/OrderCancelDialog.vue'
import { getOrderDetailPage, getOrderListBy } from '@/api/order'
import { useOrderActions } from '@/composables/useOrderActions'
import {
  OrderStatus,
  ORDER_STATUS_TEXT,
  isOrderStatus,
  type OrderStatisticsVO,
  type OrderVO,
  type OrderStatus as OrderStatusCode
} from '@/api/types'

const route = useRoute()
const router = useRouter()

const defaultActivity = ref<number>(OrderStatus.All)
const orderStatics = ref<OrderStatisticsVO>()
const input = ref('') //搜索条件的订单号
const phone = ref('') //搜索条件的手机号
const valueTime = ref<string[]>([])
const defaultTime = [new Date(2000, 0, 1, 0, 0, 0), new Date(2000, 0, 1, 23, 59, 59)]
const counts = ref(0)
const page = ref(1)
const pageSize = ref(10)
const tableData = ref<OrderVO[]>([])
const isSearch = ref(false)
const orderStatus = ref<number>(OrderStatus.All) //列表字段展示所需订单状态,用于分页请求数据

const actions = useOrderActions({
  onSuccess: () => init(orderStatus.value)
})

function initFun(orderStatus: number) {
  page.value = 1
  init(orderStatus)
}

function change(activeIndex: number) {
  if (activeIndex === orderStatus.value) return
  init(activeIndex)
  input.value = ''
  phone.value = ''
  valueTime.value = []
  actions.state.detailStatus = OrderStatus.All
  router.push('/order')
}

//获取待处理，待派送，派送中数量
function getOrderListBy3Status() {
  getOrderListBy()
    .then((res) => {
      orderStatics.value = res
    })
}

function init(activeIndex: number = 0, isSearchVal?: boolean) {
  isSearch.value = isSearchVal ?? false
  const params = {
    page: page.value,
    pageSize: pageSize.value,
    number: input.value || undefined,
    phone: phone.value || undefined,
    beginTime:
      valueTime.value && valueTime.value.length > 0
        ? valueTime.value[0]
        : undefined,
    endTime:
      valueTime.value && valueTime.value.length > 0
        ? valueTime.value[1]
        : undefined,
    status: activeIndex || undefined,
  }
  getOrderDetailPage({ ...params })
    .then((res) => {
      tableData.value = res.records
      orderStatus.value = activeIndex
      counts.value = Number(res.total)
      getOrderListBy3Status()
      if (
        actions.state.detailStatus === OrderStatus.ToBeConfirmed &&
        orderStatus.value === OrderStatus.ToBeConfirmed &&
        actions.state.autoNext &&
        !actions.state.tableOperated &&
        res.records.length > 1
      ) {
        const firstRow = res.records[0]
        goDetail(firstRow.id, firstRow.status, firstRow)
      }
    })
}

function getOrderType(row: OrderVO) {
  return ORDER_STATUS_TEXT[row.status as OrderStatusCode] ?? '退款'
}

// 查看详情
function goDetail(id: number, status: number, rowData?: OrderVO, event?: Event) {
  void actions.openDetail(id, status, rowData, event)
  // 从消息通知深链进入时,清除地址栏上的 orderId
  if (route.query.orderId) {
    router.push('/order')
  }
}

function handleSizeChange(val: number) {
  pageSize.value = val
  init(orderStatus.value)
}

function handleCurrentChange(val: number) {
  page.value = val
  init(orderStatus.value)
}

init(Number(route.query.status) || OrderStatus.All)

onMounted(() => {
  //如果有值说明是消息通知点击进来的
  if (route.query.orderId && route.query.orderId !== 'undefined') {
    goDetail(Number(route.query.orderId), OrderStatus.ToBeConfirmed)
  }
  if (route.query.status) {
    defaultActivity.value = Number(route.query.status)
  }
})
</script>

<style lang="scss" scoped>
.dashboard {
  &-container {
    margin: 30px;
    min-height: 700px;

    .container {
      background: #fff;
      position: relative;
      z-index: 1;
      padding: 30px 28px;
      border-radius: 4px;
      height: calc(100% - 55px);

      .tableBar {
        margin-bottom: 20px;
        justify-content: space-between;
      }
      .tableBox {
        width: 100%;
        border: 1px solid $gray-5;
        border-bottom: 0;
      }
      .pageList {
        text-align: center;
        margin-top: 30px;
      }
      //查询黑色按钮样式
      .normal-btn {
        background: #333333;
        color: white;
        margin-left: 20px;
      }
    }
    .hContainer {
      height: auto !important;
    }
  }
}
</style>

<style lang="scss">
.dashboard-container {
  .cancelReason {
    padding-left: 40px;
  }
  .cancelTime {
    padding-left: 50px;
  }
  .orderTime {
    padding-left: 50px;
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
