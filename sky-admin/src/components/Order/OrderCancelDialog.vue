<template>
  <el-dialog
    v-model="state.cancelVisible"
    :title="state.cancelTitle + '原因'"
    width="42%"
    :before-close="closeCancelDialog"
    class="cancelDialog"
  >
    <el-form label-width="90px">
      <el-form-item :label="state.cancelTitle + '原因：'">
        <el-select
          v-model="state.cancelReason"
          :placeholder="'请选择' + state.cancelTitle + '原因'"
        >
          <el-option
            v-for="(item, index) in
              state.cancelTitle === '取消' ? CANCEL_REASONS : REJECTION_REASONS"
            :key="index"
            :label="item.label"
            :value="item.label"
          />
        </el-select>
      </el-form-item>
      <el-form-item v-if="state.cancelReason === CUSTOM_REASON" label="原因：">
        <el-input
          v-model.trim="state.remark"
          type="textarea"
          :placeholder="'请填写您' + state.cancelTitle + '的原因（限20字内）'"
          maxlength="20"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="closeCancelDialog">取 消</el-button>
        <el-button type="primary" @click="confirmCancel">确 定</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import type { OrderActions } from '@/composables/useOrderActions'
import { CANCEL_REASONS, CUSTOM_REASON, REJECTION_REASONS } from '@/composables/useOrderActions'

const props = defineProps<{
  actions: OrderActions
}>()

const { state, closeCancelDialog, confirmCancel } = props.actions
</script>

<style lang="scss">
.cancelDialog {
  .el-dialog__body {
    padding-left: 64px;
  }
  .el-select,
  .el-textarea {
    width: 293px;
  }
  .el-textarea textarea {
    height: 114px;
  }
}
</style>
