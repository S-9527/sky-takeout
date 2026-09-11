<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'

import * as api from '@/api/customer'
import { ApiError } from '@/api/http'
import type { UserAddress } from '@/types'

/** 收货地址:列表 + 新增/编辑表单(上限 20 条由后端判定) */
const addresses = ref<UserAddress[]>([])
const loading = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)

const form = reactive({
  consignee: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  label: '',
  isDefault: 0 as 0 | 1,
})

async function load(): Promise<void> {
  loading.value = true
  try {
    addresses.value = await api.listAddresses()
  } catch (error) {
    showError(error, '地址加载失败')
  } finally {
    loading.value = false
  }
}

function startCreate(): void {
  editingId.value = null
  Object.assign(form, {
    consignee: '',
    phone: '',
    province: '',
    city: '',
    district: '',
    detail: '',
    label: '',
    isDefault: 0,
  })
}

function startEdit(address: UserAddress): void {
  editingId.value = address.id
  Object.assign(form, {
    consignee: address.consignee,
    phone: address.phone,
    province: address.province,
    city: address.city,
    district: address.district,
    detail: address.detail,
    label: address.label ?? '',
    isDefault: address.isDefault,
  })
}

async function save(): Promise<void> {
  if (!form.consignee.trim() || !/^1[3-9]\d{9}$/.test(form.phone)) {
    uni.showToast({ title: '请填写收货人与正确的手机号', icon: 'none' })
    return
  }
  if (!form.province.trim() || !form.city.trim() || !form.district.trim() || !form.detail.trim()) {
    uni.showToast({ title: '请把地址填写完整', icon: 'none' })
    return
  }
  saving.value = true
  try {
    const body = {
      consignee: form.consignee.trim(),
      phone: form.phone,
      province: form.province.trim(),
      city: form.city.trim(),
      district: form.district.trim(),
      detail: form.detail.trim(),
      label: form.label.trim() || undefined,
      isDefault: form.isDefault,
    }
    if (editingId.value === null) await api.createAddress(body)
    else await api.updateAddress(editingId.value, body)
    uni.showToast({ title: '已保存', icon: 'success' })
    startCreate()
    await load()
  } catch (error) {
    showError(error, '保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(address: UserAddress): Promise<void> {
  uni.showModal({
    title: '删除地址',
    content: `确认删除「${address.consignee}」的地址?`,
    success: async (result) => {
      if (!result.confirm) return
      try {
        await api.deleteAddress(address.id)
        if (editingId.value === address.id) startCreate()
        await load()
      } catch (error) {
        showError(error, '删除失败')
      }
    },
  })
}

async function setDefault(address: UserAddress): Promise<void> {
  try {
    await api.setDefaultAddress(address.id)
    await load()
  } catch (error) {
    showError(error, '设置失败')
  }
}

function showError(error: unknown, fallback: string): void {
  uni.showToast({ title: error instanceof ApiError ? error.message : fallback, icon: 'none' })
}

onShow(() => {
  void load()
})
</script>

<template>
  <view class="min-h-screen bg-slate-50 p-3">
    <view v-for="address in addresses" :key="address.id" class="mb-3 rounded-lg bg-white p-4">
      <view class="flex items-center justify-between">
        <view class="text-sm font-medium text-slate-800">
          {{ address.consignee }} {{ address.phone }}
          <text v-if="address.isDefault === 1" class="ml-2 rounded bg-blue-50 px-1 text-xs text-blue-600">默认</text>
        </view>
        <text v-if="address.label" class="text-xs text-slate-400">{{ address.label }}</text>
      </view>
      <view class="mt-1 text-xs text-slate-500">
        {{ address.province }}{{ address.city }}{{ address.district }}{{ address.detail }}
      </view>
      <view class="mt-3 flex justify-end gap-3 text-xs text-slate-500">
        <text v-if="address.isDefault === 0" @click="setDefault(address)">设为默认</text>
        <text @click="startEdit(address)">编辑</text>
        <text class="text-red-500" @click="remove(address)">删除</text>
      </view>
    </view>

    <view v-if="addresses.length === 0 && !loading" class="py-10 text-center text-sm text-slate-400">
      还没有收货地址
    </view>

    <view class="rounded-lg bg-white p-4">
      <view class="mb-3 text-sm font-medium">{{ editingId === null ? '新增地址' : '编辑地址' }}</view>
      <input v-model="form.consignee" class="mb-2 w-full rounded bg-slate-50 px-3 py-2 text-sm" placeholder="收货人" />
      <input v-model="form.phone" class="mb-2 w-full rounded bg-slate-50 px-3 py-2 text-sm" placeholder="手机号" maxlength="11" />
      <view class="mb-2 flex gap-2">
        <input v-model="form.province" class="w-1/3 rounded bg-slate-50 px-3 py-2 text-sm" placeholder="省" />
        <input v-model="form.city" class="w-1/3 rounded bg-slate-50 px-3 py-2 text-sm" placeholder="市" />
        <input v-model="form.district" class="w-1/3 rounded bg-slate-50 px-3 py-2 text-sm" placeholder="区" />
      </view>
      <input v-model="form.detail" class="mb-2 w-full rounded bg-slate-50 px-3 py-2 text-sm" placeholder="详细地址" />
      <view class="mb-3 flex items-center gap-2">
        <input v-model="form.label" class="flex-1 rounded bg-slate-50 px-3 py-2 text-sm" placeholder="标签:家 / 公司" />
        <view class="flex items-center gap-2 text-xs text-slate-500" @click="form.isDefault = form.isDefault === 1 ? 0 : 1">
          <view
            class="h-5 w-5 rounded border"
            :class="form.isDefault === 1 ? 'border-blue-600 bg-blue-600' : 'border-slate-300 bg-white'"
          />
          设为默认
        </view>
      </view>
      <button
        class="w-full rounded-full bg-blue-600 py-2 text-sm text-white"
        :disabled="saving"
        data-testid="save-address"
        @click="save"
      >
        {{ saving ? '保存中…' : '保存地址' }}
      </button>
    </view>
  </view>
</template>
