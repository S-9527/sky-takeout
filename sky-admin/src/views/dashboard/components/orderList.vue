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
              :hidden="!([2, 3].includes(item.value) && item.num)"
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
              :class-name="dialogOrderStatus === 2 ? 'address' : ''"
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
              v-if="status === 3"
            >
            </el-table-column>
            <el-table-column
              label="操作"
              align="center"
              :class-name="dialogOrderStatus === 0 ? 'operate' : 'otherOperate'"
              :min-width="
                [2, 3].includes(dialogOrderStatus)
                  ? 130
                  : [0].includes(dialogOrderStatus)
                  ? 140
                  : 'auto'
              "
            >
              <template #default="{ row }">
                <!-- <el-divider direction="vertical" /> -->
                <div class="before">
                  <el-button
                    v-if="row.status === 2"
                    link
                    class="blueBug"
                    @click="
                      orderAccept(row, $event), (isTableOperateBtn = true)
                    "
                  >
                    接单
                  </el-button>
                  <el-button
                    v-if="row.status === 3"
                    link
                    class="blueBug"
                    @click="cancelOrDeliveryOrComplete(3, row.id, $event)"
                  >
                    派送
                  </el-button>
                </div>
                <div class="middle">
                  <el-button
                    v-if="row.status === 2"
                    link
                    class="delBut"
                    @click="
                      orderReject(row, $event), (isTableOperateBtn = true)
                    "
                  >
                    拒单
                  </el-button>
                  <el-button
                    v-if="[1, 3, 4, 5].includes(row.status)"
                    link
                    class="delBut"
                    @click="cancelOrder(row, $event)"
                  >
                    取消
                  </el-button>
                </div>
                <div class="after">
                  <el-button
                    link
                    class="blueBug non"
                    @click="goDetail(row.id, row.status, row, $event)"
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
    <!-- 查看弹框部分 -->
    <el-dialog
      title="订单信息"
      v-model="dialogVisible"
      width="53%"
      :before-close="handleClose"
      class="order-dialog"
    >
      <el-scrollbar style="height: 100%">
        <div class="order-top">
          <div>
            <div style="display: inline-block">
              <label style="font-size: 16px">订单号：</label>
              <div class="order-num">
                {{ diaForm.number }}
              </div>
            </div>
            <div
              style="display: inline-block"
              class="order-status"
              :class="{ status3: [3, 4].includes(dialogOrderStatus) }"
            >
              {{
                orderList.filter((item) => item.value === dialogOrderStatus)[0]
                  .label
              }}
            </div>
          </div>
          <p><label>下单时间：</label>{{ diaForm.orderTime }}</p>
        </div>

        <div class="order-middle">
          <div class="user-info">
            <div class="user-info-box">
              <div class="user-name">
                <label>用户名：</label>
                <span>{{ diaForm.consignee }}</span>
              </div>
              <div class="user-phone">
                <label>手机号：</label>
                <span>{{ diaForm.phone }}</span>
              </div>
              <div
                v-if="[2, 3, 4, 5].includes(dialogOrderStatus)"
                class="user-getTime"
              >
                <label>{{
                  dialogOrderStatus === 5 ? '送达时间：' : '预计送达时间：'
                }}</label>
                <span>{{
                  dialogOrderStatus === 5
                    ? diaForm.deliveryTime
                    : diaForm.estimatedDeliveryTime
                }}</span>
              </div>
              <div class="user-address">
                <label>地址：</label>
                <span>{{ diaForm.address }}</span>
              </div>
            </div>
            <div
              class="user-remark"
              :class="{ orderCancel: dialogOrderStatus === 6 }"
            >
              <div>{{ dialogOrderStatus === 6 ? '取消原因' : '备注' }}</div>
              <span>{{
                dialogOrderStatus === 6
                  ? diaForm.cancelReason || diaForm.rejectionReason
                  : diaForm.remark
              }}</span>
            </div>
          </div>

          <div class="dish-info">
            <div class="dish-label">菜品</div>
            <div class="dish-list">
              <div
                v-for="(item, index) in diaForm.orderDetailList"
                :key="index"
                class="dish-item"
              >
                <span class="dish-name">{{ item.name }}</span>
                <span class="dish-num">x{{ item.number }}</span>
                <span class="dish-price"
                  >￥{{ item.amount ? item.amount.toFixed(2) : '' }}</span
                >
              </div>
            </div>
            <div class="dish-all-amount">
              <label>菜品小计</label>
              <span
                >￥{{
                  (diaForm.amount - 6 - diaForm.packAmount).toFixed(2)
                }}</span
              >
            </div>
          </div>
        </div>

        <div class="order-bottom">
          <div class="amount-info">
            <div class="amount-label">费用</div>
            <div class="amount-list">
              <div class="dish-amount">
                <span class="amount-name">菜品小计：</span>
                <span class="amount-price"
                  >￥{{
                    (Number((Number(diaForm.amount) - 6 - Number(diaForm.packAmount)).toFixed(2)) *
                      100) /
                    100
                  }}</span
                >
              </div>
              <div class="send-amount">
                <span class="amount-name">派送费：</span>
                <span class="amount-price">￥{{ 6 }}</span>
              </div>
              <div class="package-amount">
                <span class="amount-name">打包费：</span>
                <span class="amount-price"
                  >￥{{
                    diaForm.packAmount
                      ? (diaForm.packAmount.toFixed(2) * 100) / 100
                      : ''
                  }}</span
                >
              </div>
              <div class="all-amount">
                <span class="amount-name">合计：</span>
                <span class="amount-price"
                  >￥{{
                    diaForm.amount
                      ? (diaForm.amount.toFixed(2) * 100) / 100
                      : ''
                  }}</span
                >
              </div>
              <div class="pay-type">
                <span class="pay-name">支付渠道：</span>
                <span class="pay-value">{{
                  diaForm.payMethod === 1 ? '微信支付' : '支付宝支付'
                }}</span>
              </div>
              <div class="pay-time">
                <span class="pay-name">支付时间：</span>
                <span class="pay-value">{{ diaForm.checkoutTime }}</span>
              </div>
            </div>
          </div>
        </div>
      </el-scrollbar>
      <template v-if="dialogOrderStatus !== 6" #footer class="dialog-footer">
        <el-checkbox
          v-if="dialogOrderStatus === 2 && status === 2"
          v-model="isAutoNext"
          >处理完自动跳转下一条</el-checkbox
        >
        <el-button
          v-if="dialogOrderStatus === 2"
          @click="orderReject(row, $event), (isTableOperateBtn = false)"
          >拒 单</el-button
        >
        <el-button
          v-if="dialogOrderStatus === 2"
          type="primary"
          @click="orderAccept(row, $event), (isTableOperateBtn = false)"
          >接 单</el-button
        >

        <el-button
          v-if="[1, 3, 4, 5].includes(dialogOrderStatus)"
          @click="dialogVisible = false"
          >返 回</el-button
        >
        <el-button
          v-if="dialogOrderStatus === 3"
          type="primary"
          @click="cancelOrDeliveryOrComplete(3, row.id, $event)"
          >派 送</el-button
        >
        <el-button
          v-if="dialogOrderStatus === 4"
          type="primary"
          @click="cancelOrDeliveryOrComplete(4, row.id, $event)"
          >完 成</el-button
        >
        <el-button
          v-if="[1].includes(dialogOrderStatus)"
          type="primary"
          @click="cancelOrder(row, $event)"
          >取消订单</el-button
        >
      </template>
    </el-dialog>
    <!-- end -->
    <!-- 拒单，取消弹窗 -->
    <el-dialog
      :title="cancelDialogTitle + '原因'"
      v-model="cancelDialogVisible"
      width="42%"
      :before-close="() => ((cancelDialogVisible = false), (cancelReason = ''))"
      class="cancelDialog"
    >
      <el-form label-width="90px">
        <el-form-item :label="cancelDialogTitle + '原因：'">
          <el-select
            v-model="cancelReason"
            :placeholder="'请选择' + cancelDialogTitle + '原因'"
          >
            <el-option
              v-for="(item, index) in cancelDialogTitle === '取消'
                ? cancelrReasonList
                : cancelOrderReasonList"
              :key="index"
              :label="item.label"
              :value="item.label"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="cancelReason === '自定义原因'" label="原因：">
          <el-input
            v-model.trim="remark"
            type="textarea"
            :placeholder="'请填写您' + cancelDialogTitle + '的原因（限20字内）'"
            maxlength="20"
          />
        </el-form-item>
      </el-form>
      <template #footer class="dialog-footer">
        <el-button @click=";(cancelDialogVisible = false), (cancelReason = '')"
          >取 消</el-button
        >
        <el-button type="primary" @click="confirmCancel">确 定</el-button>
      </template>
    </el-dialog>
    <!-- end -->
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import Empty from '@/components/Empty/index.vue'
import {
  getOrderDetailPage,
  queryOrderDetailById,
  completeOrder,
  deliveryOrder,
  orderCancel,
  orderReject as apiOrderReject,
  orderAccept as apiOrderAccept,
} from '@/api/order'

const props = defineProps({
  orderStatics: {
    type: Object,
    default: () => ({}),
  },
})
const emit = defineEmits(['getOrderListBy3Status'])

const orderId = ref('') //订单号
const dialogOrderStatus = ref(0) //弹窗所需订单状态，用于详情展示字段
const activeIndex = ref(0)

const dialogVisible = ref(false) //详情弹窗
const cancelDialogVisible = ref(false) //取消，拒单弹窗
const cancelDialogTitle = ref('') //取消，拒绝弹窗标题
const cancelReason = ref('')
const remark = ref('') //自定义原因
const diaForm = ref<any>([])
const row = ref<any>({})
const isAutoNext = ref(true)
const isSearch = ref(false)
const counts = ref(0)
const page = ref<number>(1)
const pageSize = ref<number>(10)
const status = ref(2)
const orderData = ref<any>([])
const isTableOperateBtn = ref(true)
const cancelOrderReasonList = [
  {
    value: 1,
    label: '订单量较多，暂时无法接单',
  },
  {
    value: 2,
    label: '菜品已销售完，暂时无法接单',
  },
  {
    value: 3,
    label: '餐厅已打烊，暂时无法接单',
  },
  {
    value: 0,
    label: '自定义原因',
  },
]

const cancelrReasonList = [
  {
    value: 1,
    label: '订单量较多，暂时无法接单',
  },
  {
    value: 2,
    label: '菜品已销售完，暂时无法接单',
  },
  {
    value: 3,
    label: '骑手不足无法配送',
  },
  {
    value: 4,
    label: '客户电话取消',
  },
  {
    value: 0,
    label: '自定义原因',
  },
]
const orderList = [
  {
    label: '全部订单',
    value: 0,
  },
  {
    label: '待付款',
    value: 1,
  },
  {
    label: '待接单',
    value: 2,
  },
  {
    label: '待派送',
    value: 3,
  },
  {
    label: '派送中',
    value: 4,
  },
  {
    label: '已完成',
    value: 5,
  },
  {
    label: '已取消',
    value: 6,
  },
]
const tabList = computed(() => [
  {
    label: '待接单',
    value: 2,
    num: props.orderStatics.toBeConfirmed,
  },
  {
    label: '待派送',
    value: 3,
    num: props.orderStatics.confirmed,
  },
])

getOrderListData(status.value)
// // 获取订单数据
async function getOrderListData(val: number) {
  const params = {
    page: page.value,
    pageSize: pageSize.value,
    status: val,
  }
  const data = await getOrderDetailPage(params)
  orderData.value = data.records
  counts.value = data.total
  emit('getOrderListBy3Status')
  if (
    dialogOrderStatus.value === 2 &&
    status.value === 2 &&
    isAutoNext.value &&
    !isTableOperateBtn.value &&
    data.records.length > 1
  ) {
    const row = data.records[0]
    goDetail(row.id, row.status, row, row)
  } else {
    return null
  }
}

//接单
function orderAccept(row: any, event: any) {
  event.stopPropagation()
  orderId.value = row.id
  dialogOrderStatus.value = row.status
  apiOrderAccept({ id: orderId.value })
    .then(() => {
      ElMessage.success('操作成功')
      orderId.value = ''
      dialogVisible.value = false
      getOrderListData(status.value)
    })
    .catch((err) => {
      ElMessage.error('请求出错了：' + err.message)
    })
}
//打开取消订单弹窗
function cancelOrder(row: any, event: any) {
  event.stopPropagation()
  cancelDialogVisible.value = true
  orderId.value = row.id
  dialogOrderStatus.value = row.status
  cancelDialogTitle.value = '取消'
  dialogVisible.value = false
  cancelReason.value = ''
}
//打开拒单弹窗
function orderReject(row: any, event: any) {
  event.stopPropagation()
  cancelDialogVisible.value = true
  orderId.value = row.id
  dialogOrderStatus.value = row.status
  cancelDialogTitle.value = '拒绝'
  dialogVisible.value = false
  cancelReason.value = ''
}
//确认取消或拒绝订单并填写原因
function confirmCancel(_type: any) {
  if (!cancelReason.value) {
    return ElMessage.error(`请选择${cancelDialogTitle.value}原因`)
  } else if (cancelReason.value === '自定义原因' && !remark.value) {
    return ElMessage.error(`请输入${cancelDialogTitle.value}原因`)
  }

  ;(cancelDialogTitle.value === '取消' ? orderCancel : apiOrderReject)({
    id: orderId.value,
    // eslint-disable-next-line standard/computed-property-even-spacing
    [cancelDialogTitle.value === '取消' ? 'cancelReason' : 'rejectionReason']:
      cancelReason.value === '自定义原因' ? remark.value : cancelReason.value,
  })
    .then(() => {
      ElMessage.success('操作成功')
      cancelDialogVisible.value = false
      orderId.value = ''
      getOrderListData(status.value)
    })
    .catch((err) => {
      ElMessage.error('请求出错了：' + err.message)
    })
}

// 派送，完成
function cancelOrDeliveryOrComplete(status: number, id: string, event: any) {
  event.stopPropagation()
  const params = {
    status,
    id,
  }
  ;(status === 3 ? deliveryOrder : completeOrder)(params)
    .then(() => {
      ElMessage.success('操作成功')
      orderId.value = ''
      dialogVisible.value = false
      getOrderListData(status)
    })
    .catch((err) => {
      ElMessage.error('请求出错了：' + err.message)
    })
}
// 查看详情
async function goDetail(id: any, status: number, rowData: any, event: any) {
  event.stopPropagation()
  // console.log(111, index, row)
  diaForm.value = []
  dialogVisible.value = true
  dialogOrderStatus.value = status
  const data = await queryOrderDetailById({ orderId: id })
  diaForm.value = data
  row.value = rowData
}
// 关闭弹层
function handleClose() {
  dialogVisible.value = false
}
// tab切换
function handleClass(index: any) {
  activeIndex.value = index
  if (index === 0) {
    status.value = 2
    getOrderListData(2)
  } else {
    status.value = 3
    getOrderListData(3)
  }
}
// 触发table某一行
function handleTable(row: any, _column: any, event: any) {
  event.stopPropagation()
  goDetail(row.id, row.status, row, event)
}
// 分页
function handleSizeChange(val: any) {
  pageSize.value = val
  getOrderListData(status.value)
}

function handleCurrentChange(val: any) {
  page.value = val
  getOrderListData(status.value)
}
</script>
<style  lang="scss" scoped >
.dashboard-container.home .homecon {
  margin-bottom: 0;
}
.order-top {
  // height: 80px;
  border-bottom: 1px solid #e7e6e6;
  padding-bottom: 26px;
  padding-left: 22px;
  padding-right: 22px;
  // margin: 0 30px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  .order-status {
    width: 57.25px;
    height: 27px;
    background: #333333;
    border-radius: 13.5px;
    color: white;
    margin-left: 19px;
    text-align: center;
    line-height: 27px;
  }
  .status3 {
    background: #f56c6c;
  }
  p {
    color: #333;
    label {
      color: #666;
    }
  }
  .order-num {
    font-size: 16px;
    color: #2a2929;
    font-weight: bold;
    display: inline-block;
  }
}

.order-middle {
  .user-info {
    min-height: 140px;
    background: #fbfbfa;
    margin-top: 23px;

    padding: 20px 43px;
    color: #333;
    .user-info-box {
      min-height: 55px;
      display: flex;
      flex-wrap: wrap;
      .user-name {
        flex: 67%;
      }
      .user-phone {
        flex: 33%;
      }
      .user-getTime {
        margin-top: 14px;
        flex: 80%;
        label {
          margin-right: 3px;
        }
      }
      label {
        margin-right: 17px;
        color: #666;
      }

      .user-address {
        margin-top: 14px;
        flex: 80%;
        label {
          margin-right: 30px;
        }
      }
    }
    .user-remark {
      height: 43px;
      line-height: 43px;
      background: #fffbf0;
      border: 1px solid #fbe396;
      border-radius: 4px;
      margin-top: 10px;
      padding: 6px;
      display: flex;
      align-items: center;
      div {
        display: inline-block;
        min-width: 53px;
        height: 32px;
        background: #fbe396;
        border-radius: 4px;
        text-align: center;
        line-height: 32px;
        color: #333;
        margin-right: 30px;
        // padding: 12px 6px;
      }
      span {
        color: #f2a402;
      }
    }
    .orderCancel {
      background: #ffffff;
      border: 1px solid #b6b6b6;

      div {
        padding: 0 10px;
        background-color: #e5e4e4;
      }
      span {
        color: #f56c6c;
      }
    }
  }
  .dish-info {
    // min-height: 180px;
    display: flex;
    flex-wrap: wrap;
    padding: 20px 40px;
    border-bottom: 1px solid #e7e6e6;
    .dish-label {
      color: #666;
    }
    .dish-list {
      flex: 80%;
      display: flex;
      flex-wrap: wrap;
      .dish-item {
        flex: 50%;
        margin-bottom: 14px;
        color: #333;
        .dish-num {
          margin-right: 51px;
        }
      }
      // .dish-item:nth-child(odd) {
      //   flex: 60%;
      // }
      // .dish-item:nth-child(even) {
      //   flex: 40%;
      // }
    }
    .dish-label {
      margin-right: 65px;
    }
    .dish-all-amount {
      flex: 1;
      padding-left: 92px;
      margin-top: 10px;
      label {
        color: #333333;
        font-weight: bold;
        margin-right: 5px;
      }
      span {
        color: #f56c6c;
      }
    }
  }
}
.order-bottom {
  .amount-info {
    // min-height: 180px;
    display: flex;
    flex-wrap: wrap;
    padding: 20px 40px;
    padding-bottom: 0px;
    .amount-label {
      color: #666;
      margin-right: 65px;
    }
    .amount-list {
      flex: 80%;
      display: flex;
      flex-wrap: wrap;
      color: #333;
      // height: 65px;
      .dish-amount,
      .package-amount,
      .pay-type {
        display: inline-block;
        width: 300px;
        margin-bottom: 14px;
        flex: 50%;
      }
      .send-amount,
      .all-amount,
      .pay-time {
        display: inline-block;
        flex: 50%;
        padding-left: 10%;
      }
      .package-amount {
        .amount-name {
          margin-right: 14px;
        }
      }
      .all-amount {
        .amount-name {
          margin-right: 24px;
        }
        .amount-price {
          color: #f56c6c;
        }
      }
      .send-amount {
        .amount-name {
          margin-right: 10px;
        }
      }
    }
  }
}
</style>
<style  lang="scss">
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
