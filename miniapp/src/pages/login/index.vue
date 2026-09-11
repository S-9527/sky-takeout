<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

import { ApiError } from '@/api/http'
import { useSessionStore } from '@/stores/session'

/**
 * 登录页。
 *
 * 微信小程序里 `code` 只能由 `wx.login()` 现取(单次有效);
 * **H5 开发期没有微信环境**,后端在 mock 模式下接受任意 code,
 * 所以这里给一个稳定的开发用 code,方便本地把整条链路跑通。
 */
const session = useSessionStore()
const submitting = ref(false)

async function loginWithWechat(): Promise<void> {
  submitting.value = true
  try {
    const code = await resolveCode()
    await session.loginWithCode(code)
    uni.showToast({ title: '登录成功', icon: 'success' })
    const pages = getCurrentPages()
    if (pages.length > 1) uni.navigateBack()
    else uni.switchTab({ url: '/pages/menu/index' })
  } catch (error) {
    const message = error instanceof ApiError ? error.message : '登录失败,请稍后重试'
    uni.showToast({ title: message, icon: 'none' })
  } finally {
    submitting.value = false
  }
}

function resolveCode(): Promise<string> {
  const wxLogin = (globalThis as { wx?: { login?: (options: unknown) => void } }).wx?.login
  if (!wxLogin) {
    // H5 / 开发环境:后端 mock 换 openid,固定 code 即可复用同一个顾客
    return Promise.resolve('dev-h5-customer')
  }
  return new Promise<string>((resolve, reject) => {
    wxLogin({
      success: (result: { code?: string }) =>
        result.code ? resolve(result.code) : reject(new Error('微信未返回 code')),
      fail: () => reject(new Error('微信登录取消或失败')),
    })
  })
}

onLoad(() => {
  // 已登录就直接回点餐页,避免重复登录
  if (session.authenticated) uni.switchTab({ url: '/pages/menu/index' })
})
</script>

<template>
  <view class="flex min-h-screen flex-col items-center justify-center bg-slate-50 px-8">
    <view class="mb-10 text-center">
      <view class="text-2xl font-semibold text-slate-800">苍穹外卖</view>
      <view class="mt-2 text-sm text-slate-500">登录后即可点餐、下单、查看订单</view>
    </view>

    <button
      class="w-full rounded-full bg-blue-600 py-3 text-base text-white"
      :disabled="submitting"
      data-testid="login-submit"
      @click="loginWithWechat"
    >
      {{ submitting ? '登录中…' : '微信一键登录' }}
    </button>

    <view class="mt-6 text-center text-xs text-slate-400">
      H5 开发环境使用固定 code(mock 换 openid);
      小程序端会调用 wx.login 取真实 code。
    </view>
  </view>
</template>
