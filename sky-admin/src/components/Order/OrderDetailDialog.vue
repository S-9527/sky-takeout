<template>
  <el-dialog
    v-model="state.detailVisible"
    title="订单信息"
    width="53%"
    :before-close="closeDetail"
    class="order-dialog"
  >
    <el-scrollbar style="height: 100%">
      <div class="order-top">
        <div>
          <div style="display: inline-block">
            <label style="font-size: 16px">订单号：</label>
            <div class="order-num">
              {{ state.detail.number }}
            </div>
          </div>
          <div
            style="display: inline-block"
            class="order-status"
            :class="{
              status3: isOrderStatus(
                state.detailStatus,
                OrderStatus.Confirmed,
                OrderStatus.DeliveryInProgress
              )
            }"
          >
            {{ statusText(state.detailStatus) }}
          </div>
        </div>
        <p><label>下单时间：</label>{{ state.detail.orderTime }}</p>
      </div>

      <div class="order-middle">
        <div class="user-info">
          <div class="user-info-box">
            <div class="user-name">
              <label>用户名：</label>
              <span>{{ state.detail.consignee }}</span>
            </div>
            <div class="user-phone">
              <label>手机号：</label>
              <span>{{ state.detail.phone }}</span>
            </div>
            <div
              v-if="
                isOrderStatus(
                  state.detailStatus,
                  OrderStatus.ToBeConfirmed,
                  OrderStatus.Confirmed,
                  OrderStatus.DeliveryInProgress,
                  OrderStatus.Completed
                )
              "
              class="user-getTime"
            >
              <label>{{
                state.detailStatus === OrderStatus.Completed ? '送达时间：' : '预计送达时间：'
              }}</label>
              <span>{{
                state.detailStatus === OrderStatus.Completed
                  ? state.detail.deliveryTime
                  : state.detail.estimatedDeliveryTime
              }}</span>
            </div>
            <div class="user-address">
              <label>地址：</label>
              <span>{{ state.detail.address }}</span>
            </div>
          </div>
          <div
            class="user-remark"
            :class="{ orderCancel: state.detailStatus === OrderStatus.Cancelled }"
          >
            <div>{{
              state.detailStatus === OrderStatus.Cancelled ? '取消原因' : '备注'
            }}</div>
            <span>{{
              state.detailStatus === OrderStatus.Cancelled
                ? state.detail.cancelReason || state.detail.rejectionReason
                : state.detail.remark
            }}</span>
          </div>
        </div>

        <div class="dish-info">
          <div class="dish-label">菜品</div>
          <div class="dish-list">
            <div
              v-for="(item, index) in state.detail.orderDetailList"
              :key="index"
              class="dish-item"
            >
              <div class="dish-item-box">
                <span class="dish-name">{{ item.name }}</span>
                <span class="dish-num">x{{ item.number }}</span>
              </div>
              <span class="dish-price"
                >￥{{ item.amount ? item.amount.toFixed(2) : '' }}</span
              >
            </div>
          </div>
          <div class="dish-all-amount">
            <label>菜品小计</label>
            <span>￥{{ dishSubtotal }}</span>
          </div>
        </div>
      </div>

      <div class="order-bottom">
        <div class="amount-info">
          <div class="amount-label">费用</div>
          <div class="amount-list">
            <div class="dish-amount">
              <span class="amount-name">菜品小计：</span>
              <span class="amount-price">￥{{ Number(dishSubtotal) * 100 / 100 }}</span>
            </div>
            <div class="send-amount">
              <span class="amount-name">派送费：</span>
              <span class="amount-price">￥{{ DELIVERY_FEE }}</span>
            </div>
            <div class="package-amount">
              <span class="amount-name">打包费：</span>
              <span class="amount-price"
                >￥{{
                  (state.detail.packAmount ?? 0)
                    ? (Number((state.detail.packAmount ?? 0).toFixed(2)) * 100) / 100
                    : ''
                }}</span
              >
            </div>
            <div class="all-amount">
              <span class="amount-name">合计：</span>
              <span class="amount-price"
                >￥{{
                  (state.detail.amount ?? 0)
                    ? (Number((state.detail.amount ?? 0).toFixed(2)) * 100) / 100
                    : ''
                }}</span
              >
            </div>
            <div class="pay-type">
              <span class="pay-name">支付渠道：</span>
              <span class="pay-value">{{ payMethodText(state.detail.payMethod) }}</span>
            </div>
            <div class="pay-time">
              <span class="pay-name">支付时间：</span>
              <span class="pay-value">{{ state.detail.checkoutTime }}</span>
            </div>
          </div>
        </div>
      </div>
    </el-scrollbar>
    <template #footer>
      <span v-if="state.detailStatus !== OrderStatus.Cancelled" class="dialog-footer">
        <el-checkbox
          v-if="
            state.detailStatus === OrderStatus.ToBeConfirmed &&
            listStatus === OrderStatus.ToBeConfirmed
          "
          v-model="state.autoNext"
          >处理完自动跳转下一条</el-checkbox
        >
        <el-button
          v-if="state.detailStatus === OrderStatus.ToBeConfirmed"
          @click="openReject(state.row, $event)"
          >拒 单</el-button
        >
        <el-button
          v-if="state.detailStatus === OrderStatus.ToBeConfirmed"
          type="primary"
          @click="accept(state.row, $event)"
          >接 单</el-button
        >

        <el-button
          v-if="
            isOrderStatus(
              state.detailStatus,
              OrderStatus.PendingPayment,
              OrderStatus.Confirmed,
              OrderStatus.DeliveryInProgress,
              OrderStatus.Completed
            )
          "
          @click="closeDetail"
          >返 回</el-button
        >
        <el-button
          v-if="state.detailStatus === OrderStatus.Confirmed"
          type="primary"
          @click="deliverOrComplete(state.row, $event)"
          >派 送</el-button
        >
        <el-button
          v-if="state.detailStatus === OrderStatus.DeliveryInProgress"
          type="primary"
          @click="deliverOrComplete(state.row, $event)"
          >完 成</el-button
        >
        <el-button
          v-if="state.detailStatus === OrderStatus.PendingPayment"
          type="primary"
          @click="openCancel(state.row, $event)"
          >取消订单</el-button
        >
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  DELIVERY_FEE,
  OrderStatus,
  isOrderStatus,
  payMethodText,
  statusText
} from '@/constants/order'
import type { OrderActions } from '@/composables/useOrderActions'

const props = withDefaults(
  defineProps<{
    actions: OrderActions
    // 列表当前筛选状态,控制"处理完自动跳转下一条"是否显示
    listStatus?: number
  }>(),
  {
    listStatus: OrderStatus.All
  }
)

const { state, accept, closeDetail, deliverOrComplete, openCancel, openReject } = props.actions

const dishSubtotal = computed(() =>
  ((state.detail.amount ?? 0) - DELIVERY_FEE - (state.detail.packAmount ?? 0)).toFixed(2)
)
</script>

<style lang="scss" scoped>
.order-top {
  border-bottom: 1px solid #e7e6e6;
  padding-bottom: 26px;
  padding-left: 22px;
  padding-right: 22px;
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
      min-height: 43px;
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
      }
      span {
        color: #f2a402;
        line-height: 1.15;
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
    display: flex;
    flex-wrap: wrap;
    padding: 20px 40px;
    border-bottom: 1px solid #e7e6e6;

    .dish-label {
      color: #666;
      margin-right: 65px;
    }
    .dish-list {
      flex: 80%;
      display: flex;
      flex-wrap: wrap;

      .dish-item {
        flex: 50%;
        margin-bottom: 14px;
        color: #333;

        .dish-item-box {
          display: inline-block;
          width: 120px;
        }
      }
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

<style lang="scss">
.dashboard-container {
  .order-dialog {
    .el-dialog__body {
      height: 520px !important;
    }
  }
}
.el-dialog__body {
  padding-top: 34px;
  padding-left: 30px;
  padding-right: 30px;
}
.el-dialog__footer {
  .el-checkbox {
    float: left;
    margin-left: 40px;
  }
  .el-checkbox__label {
    color: #333333 !important;
  }
}
</style>
