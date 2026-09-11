<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'

import * as authApi from '@/api/auth'
import { ApiError } from '@/api/http'
import { useSessionStore } from '@/stores/session'
import { formatDateTime } from '@/utils/datetime'

/** 我的:资料、常用入口、退出登录 */
const session = useSessionStore()
const editing = ref(false)
const nickname = ref('')
const saving = ref(false)

const profile = computed(() => session.profile)

function startEdit(): void {
  nickname.value = profile.value?.nickname ?? ''
  editing.value = true
}

async function saveProfile(): Promise<void> {
  saving.value = true
  try {
    await authApi.updateProfile({ nickname: nickname.value.trim() })
    await session.loadProfile()
    editing.value = false
    uni.showToast({ title: '已保存', icon: 'success' })
  } catch (error) {
    uni.showToast({ title: error instanceof ApiError ? error.message : '保存失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}

function goOrders(): void {
  // order/list 是 tabBar 页面,只能用 switchTab(navigateTo 会报 can not navigateTo a tabbar page)
  uni.switchTab({ url: '/pages/order/list' })
}

function goAddresses(): void {
  uni.navigateTo({ url: '/pages/address/list' })
}

function logout(): void {
  uni.showModal({
    title: '退出登录',
    content: '确认退出当前账号?',
    success: async (result) => {
      if (!result.confirm) return
      await session.logout()
      uni.reLaunch({ url: '/pages/login/index' })
    },
  })
}

onShow(() => {
  if (session.authenticated && !session.profile) {
    void session.loadProfile().catch(() => session.clearSession())
  }
})
</script>

<template>
  <view class="min-h-screen bg-slate-50">
    <view class="flex items-center bg-white p-4">
      <image
        v-if="profile?.avatarUrl"
        :src="profile.avatarUrl"
        class="mr-3 h-14 w-14 rounded-full"
        mode="aspectFill"
      />
      <view v-else class="mr-3 flex h-14 w-14 items-center justify-center rounded-full bg-slate-100 text-slate-400">
        头像
      </view>
      <view class="flex-1">
        <view class="text-base font-medium text-slate-800">{{ session.displayName }}</view>
        <view class="mt-1 text-xs text-slate-400">
          {{ profile?.phone || '未绑定手机号' }}
        </view>
      </view>
      <text class="text-xs text-blue-600" @click="startEdit">修改昵称</text>
    </view>

    <view v-if="editing" class="mt-3 bg-white p-4">
      <input v-model="nickname" class="mb-3 w-full rounded bg-slate-50 px-3 py-2 text-sm" placeholder="昵称" maxlength="32" />
      <view class="flex gap-2">
        <button class="flex-1 rounded-full bg-slate-100 py-2 text-sm" @click="editing = false">取消</button>
        <button class="flex-1 rounded-full bg-blue-600 py-2 text-sm text-white" :disabled="saving" @click="saveProfile">
          保存
        </button>
      </view>
    </view>

    <view class="mt-3 bg-white">
      <view class="flex items-center justify-between border-b border-slate-100 p-4 text-sm" @click="goOrders">
        <text>我的订单</text>
        <text class="text-slate-300">›</text>
      </view>
      <view class="flex items-center justify-between p-4 text-sm" @click="goAddresses">
        <text>收货地址</text>
        <text class="text-slate-300">›</text>
      </view>
    </view>

    <view class="mt-3 bg-white p-4 text-xs text-slate-400">
      <view>注册时间:{{ formatDateTime(profile?.createdAt) }}</view>
      <view class="mt-1">最近登录:{{ formatDateTime(profile?.lastLoginAt) }}</view>
    </view>

    <view class="p-4">
      <button class="w-full rounded-full border border-slate-200 bg-white py-2 text-sm text-red-500" data-testid="logout" @click="logout">
        退出登录
      </button>
    </view>
  </view>
</template>
