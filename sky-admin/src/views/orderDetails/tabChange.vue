<template>
  <div class="tab-change">
    <div v-for="item in changedOrderList"
         :key="item.value"
         class="tab-item"
         :class="{ active: item.value === activeIndex }"
         @click="tabChange(item.value)">
      <el-badge :class="{'special-item':(item.num ?? 0)<10}"
                class="item"
                :value="(item.num ?? 0) > 99 ? '99+' : (item.num ?? 0)"
                :hidden="!(isOrderStatus(item.value, OrderStatus.ToBeConfirmed, OrderStatus.Confirmed, OrderStatus.DeliveryInProgress) && item.num)">
        {{ item.label }}
      </el-badge>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { OrderStatus, ORDER_STATUS_TEXT, isOrderStatus } from '@/constants/order'
import type { OrderStatisticsVO } from '@/api/types/order'

const props = withDefaults(
  defineProps<{
    orderStatics?: OrderStatisticsVO
    defaultActivity?: number
  }>(),
  {
    orderStatics: () => ({ toBeConfirmed: 0, confirmed: 0, deliveryInProgress: 0 }),
    defaultActivity: 0
  }
)
const emit = defineEmits<{ tabChange: [value: number] }>()

const activeIndex = ref<number>(props.defaultActivity ?? OrderStatus.All)

watch(
  () => props.defaultActivity,
  (val) => {
    activeIndex.value = val ?? OrderStatus.All
  }
)

const changedOrderList = computed(() => {
  return [
    {
      label: ORDER_STATUS_TEXT[OrderStatus.All],
      value: OrderStatus.All
    },
    {
      label: ORDER_STATUS_TEXT[OrderStatus.ToBeConfirmed],
      value: OrderStatus.ToBeConfirmed,
      num: props.orderStatics.toBeConfirmed
    },
    {
      label: ORDER_STATUS_TEXT[OrderStatus.Confirmed],
      value: OrderStatus.Confirmed,
      num: props.orderStatics.confirmed
    },
    {
      label: ORDER_STATUS_TEXT[OrderStatus.DeliveryInProgress],
      value: OrderStatus.DeliveryInProgress,
      num: props.orderStatics.deliveryInProgress
    },
    {
      label: ORDER_STATUS_TEXT[OrderStatus.Completed],
      value: OrderStatus.Completed
    },
    {
      label: ORDER_STATUS_TEXT[OrderStatus.Cancelled],
      value: OrderStatus.Cancelled
    }
  ]
})

function tabChange(val: number) {
  activeIndex.value = val
  emit('tabChange', val)
}
</script>
<style lang="scss" scoped>
.tab-change {
  display: flex;
  border-radius: 4px;
  margin-bottom: 20px;

  .tab-item {
    width: 120px;
    height: 40px;
    text-align: center;
    line-height: 40px;
    color: #333;
    border: 1px solid #e5e4e4;
    background-color: white;
    border-left: none;
    cursor: pointer;
    /* EP2 的 .el-badge 默认 vertical-align: middle,文字会比原版低 2px;
       Element UI 为 top,改回 top 对齐原版 */
    .item {
      vertical-align: top;

      :deep(.el-badge__content) {
        background-color: #fd3333 !important;
        line-height: 18px;
        height: auto;
        min-width: 18px;
        min-height: 18px;
        // border-radius: 50%;
      }
      :deep(.el-badge__content.is-fixed) {
        top: 14px;
        right: 2px;
      }
    }
    .special-item {
      :deep(.el-badge__content) {
        width: 20px;
        padding: 0 5px;
      }
    }
  }
  .active {
    background-color: #ffc200;
    font-weight: bold;
  }
  .tab-item:first-child {
    border-left: 1px solid #e5e4e4;
  }
}
</style>
