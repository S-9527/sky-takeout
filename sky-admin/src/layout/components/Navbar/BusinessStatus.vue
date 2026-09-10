<template>
  <el-dialog v-model="visible"
             class="business-status-dialog"
             title="营业状态设置"
             width="25%"
             :show-close="false">
    <el-radio-group v-model="setStatus">
      <el-radio :value="1">
        营业中
        <span>当前餐厅处于营业状态，自动接收任何订单，可点击打烊进入店铺打烊状态。</span>
      </el-radio>
      <el-radio :value="0">
        打烊中
        <span>当前餐厅处于打烊状态，仅接受营业时间内的预定订单，可点击营业中手动恢复营业状态。</span>
      </el-radio>
    </el-radio-group>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="visible = false">取 消</el-button>
        <el-button type="primary"
                   @click="handleSave">确 定</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { setStatus as setApiStatus } from '@/api/users'

const props = defineProps<{
  modelValue: boolean
  status: number
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'change', status: number): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const setStatus = ref(props.status)
watch(
  () => props.modelValue,
  (value) => {
    if (value) setStatus.value = props.status
  }
)

const handleSave = async () => {
  await setApiStatus(setStatus.value)
  ElMessage.success(setStatus.value === 1 ? '已切换为营业中' : '已切换为打烊中')
  visible.value = false
  emit('change', setStatus.value)
}
</script>

<style lang="scss">
.business-status-dialog {
  min-width: auto !important;

  .el-dialog__header {
    height: 61px;
    line-height: 60px;
    background: #fbfbfa;
    padding: 0 30px;
    font-size: 16px;
    color: #333;
    border: 0 none;
  }
  .el-dialog__body {
    padding: 10px 30px 30px;
    .el-radio,
    .el-radio__input {
      white-space: normal;
    }
    .el-radio__label {
      padding-left: 5px;
      color: #333;
      font-weight: 700;
      span {
        display: block;
        line-height: 20px;
        padding-top: 12px;
        color: #666;
        font-weight: normal;
      }
    }
    .el-radio__input.is-checked .el-radio__inner {
      &::after {
        background: #333;
      }
    }
    .el-radio-group {
      display: inline-block;
      width: 100%;
      & > .is-checked {
        border: 1px solid #ffc200;
      }
    }
    .el-radio {
      display: inline-block;
      line-height: 1;
      width: 100%;
      height: auto;
      margin-right: 0;
      background: #fbfbfa;
      border: 1px solid #e5e4e4;
      border-radius: 4px;
      padding: 14px 22px;
      margin-top: 20px;
    }
  }
}
</style>