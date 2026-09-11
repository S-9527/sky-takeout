import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import * as authApi from '@/api/auth'
import { clearTokens, getRefreshToken, getTokens, setTokens } from '@/api/tokens'
import type { Customer } from '@/types'

/**
 * 顾客会话。
 *
 * 令牌在 `@/api/tokens`(http 层刷新时也写它),这里只镜像"有没有会话"并持有资料 ——
 * 登录接口只给令牌,资料要另外拉,这一点与管理端一致。
 */
export const useSessionStore = defineStore('session', () => {
  const profile = ref<Customer | null>(null)
  const authenticated = ref(!!getTokens())
  const loading = ref(false)

  const displayName = computed(() => profile.value?.nickname?.trim() || '微信用户')

  /**
   * 微信登录。
   *
   * `code` 由调用方给:小程序里是 `wx.login()` 的结果;
   * H5 开发期后端用 mock 换 openid,直接传一个稳定字符串即可(见 README)。
   */
  async function loginWithCode(code: string, extra: { nickname?: string; avatarUrl?: string } = {}) {
    loading.value = true
    try {
      const pair = await authApi.wechatLogin({ code, ...extra })
      setTokens(pair)
      authenticated.value = true
      profile.value = await authApi.getProfile()
    } catch (error) {
      clearTokens()
      authenticated.value = false
      profile.value = null
      throw error
    } finally {
      loading.value = false
    }
  }

  async function loadProfile(): Promise<Customer> {
    const customer = await authApi.getProfile()
    profile.value = customer
    authenticated.value = true
    return customer
  }

  function clearSession(): void {
    clearTokens()
    authenticated.value = false
    profile.value = null
  }

  async function logout(): Promise<void> {
    const refreshToken = getRefreshToken()
    try {
      if (refreshToken) await authApi.logout(refreshToken)
    } catch {
      // 撤销失败也要让用户退出
    } finally {
      clearSession()
    }
  }

  return {
    profile,
    authenticated,
    loading,
    displayName,
    loginWithCode,
    loadProfile,
    clearSession,
    logout,
  }
})
