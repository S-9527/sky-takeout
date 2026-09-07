<!--  -->
<template>
  <div class="tab-change">
    <div v-for="item in changedOrderList"
         :key="item.value"
         class="tab-item"
         :class="{ active: item.value === activeIndex }"
         @click="tabChange(item.value)">
      <el-badge :class="{'special-item':item.num<10}"
                class="item"
                :value="item.num > 99 ? '99+' : item.num"
                :hidden="!([2, 3, 4].includes(item.value) && item.num)">
        {{ item.label }}
      </el-badge>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'

const props = defineProps({
  orderStatics: { type: Object, default: '' },
  defaultActivity: { type: [Number, String], default: '' },
})
const emit = defineEmits(['tabChange'])

const activeIndex = ref<number>(Number(props.defaultActivity) || 0)

watch(
  () => props.defaultActivity,
  (val) => {
    activeIndex.value = Number(val)
  }
)

const changedOrderList = computed(() => {
  return [
    {
      label: '全部订单',
      value: 0
    },
    {
      label: '待接单',
      value: 2,
      num: props.orderStatics.toBeConfirmed
    },
    {
      label: '待派送',
      value: 3,
      num: props.orderStatics.confirmed
    },
    {
      label: '派送中',
      value: 4,
      num: props.orderStatics.deliveryInProgress
    },
    {
      label: '已完成',
      value: 5
    },
    {
      label: '已取消',
      value: 6
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
