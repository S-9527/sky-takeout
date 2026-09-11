<script setup lang="ts">
import { Refresh } from '@element-plus/icons-vue'
import { computed, onMounted, reactive, ref } from 'vue'

import { getShopStatus, updateShopStatus } from '@/api/shop'
import type { ShopStatus } from '@/types'
import { showError, showSuccess } from '@/utils/feedback'
import { formatDateTime } from '@/utils/datetime'

/**
 * 营业状态。
 *
 * `isOpen` 是**显式状态**,不由营业时间推导(领域文档 3.11):
 * 打烊后顾客端仍可浏览,但下单会返回 422 `ORDER_SHOP_CLOSED`(R1)。
 * 所以这个开关是门店的"总闸",切换时要给出明确反馈。
 */
const loading = ref(false)
const saving = ref(false)
const status = ref<ShopStatus | null>(null)

const form = reactive({
  isOpen: true,
  openTime: '',
  closeTime: '',
  notice: '',
})

const isDirty = computed(() => {
  if (!status.value) return false
  return (
    form.isOpen !== status.value.isOpen ||
    (form.openTime || '') !== (status.value.openTime ?? '') ||
    (form.closeTime || '') !== (status.value.closeTime ?? '') ||
    (form.notice || '') !== (status.value.notice ?? '')
  )
})

function fill(data: ShopStatus): void {
  status.value = data
  form.isOpen = data.isOpen
  form.openTime = data.openTime ?? ''
  form.closeTime = data.closeTime ?? ''
  form.notice = data.notice ?? ''
}

async function load(): Promise<void> {
  loading.value = true
  try {
    fill(await getShopStatus())
  } catch (error) {
    showError(error, '营业状态加载失败')
  } finally {
    loading.value = false
  }
}

async function save(): Promise<void> {
  saving.value = true
  try {
    const updated = await updateShopStatus({
      isOpen: form.isOpen,
      openTime: form.openTime || null,
      closeTime: form.closeTime || null,
      notice: form.notice.trim() === '' ? null : form.notice.trim(),
    })
    fill(updated)
    showSuccess(updated.isOpen ? '门店已开始营业' : '门店已打烊,顾客暂时无法下单')
  } catch (error) {
    showError(error, '营业状态保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="mx-auto max-w-2xl space-y-4">
    <el-card shadow="never">
      <template #header>
        <div class="flex items-center justify-between">
          <span class="font-medium">营业状态</span>
          <el-button :icon="Refresh" text @click="load">刷新</el-button>
        </div>
      </template>

      <el-form label-width="110px">
        <el-form-item label="当前状态">
          <el-switch
            v-model="form.isOpen"
            active-text="营业中"
            inactive-text="已打烊"
            inline-prompt
            data-testid="shop-open-switch"
          />
          <span class="ml-3 text-xs text-slate-500">
            打烊是显式开关:顾客仍可浏览商品,但不能下单
          </span>
        </el-form-item>

        <el-form-item label="营业时间">
          <div class="flex items-center gap-2">
            <el-time-picker
              v-model="form.openTime"
              arrow-control
              value-format="HH:mm:ss"
              placeholder="开始营业"
            />
            <span class="text-slate-400">至</span>
            <el-time-picker
              v-model="form.closeTime"
              arrow-control
              value-format="HH:mm:ss"
              placeholder="打烊时间"
            />
          </div>
        </el-form-item>

        <el-form-item label="门店公告">
          <el-input
            v-model="form.notice"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="展示在顾客端首页,如:本店今日 22:00 打烊,请提前下单"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="saving" :disabled="!isDirty" data-testid="shop-save" @click="save">
            保存
          </el-button>
          <el-button :disabled="!isDirty" @click="status && fill(status)">还原</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header><span class="font-medium">最近更新</span></template>
      <div class="text-sm text-slate-600">
        更新时间:{{ formatDateTime(status?.updatedAt) }}
      </div>
      <div class="mt-2 text-xs text-slate-400">
        门店公告会实时展示在顾客端;营业时间为约定信息,不参与下单校验。
      </div>
    </el-card>
  </div>
</template>
