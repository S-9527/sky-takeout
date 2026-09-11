import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import * as authApi from '@/api/employee'
import { clearTokens, getRefreshToken, getTokens, setTokens } from '@/api/tokens'
import type { Employee } from '@/types'

/**
 * 登录态。
 *
 * 职责边界:
 * - **令牌**由 `@/api/tokens` 持有(`http` 拦截器刷新时也写它),store 只镜像"有没有会话";
 * - **资料**在这里,进应用时用 `GET /admin/auth/me` 拉一次 ——
 *   登录接口只返回令牌对,不返回资料,这是契约决定的。
 */
export const useAuthStore = defineStore('auth', () => {
  const profile = ref<Employee | null>(null)
  const authenticated = ref(!!getTokens())
  const loading = ref(false)

  const isAdmin = computed(() => profile.value?.role === 'ADMIN')
  const displayName = computed(() => profile.value?.name ?? profile.value?.username ?? '')

  /** 登录:换令牌 → 立刻拉资料。资料拉不到不算登录成功 */
  async function login(username: string, password: string): Promise<void> {
    loading.value = true
    try {
      const pair = await authApi.login({ username, password })
      setTokens(pair)
      authenticated.value = true
      profile.value = await authApi.fetchCurrentEmployee()
    } catch (error) {
      clearTokens()
      authenticated.value = false
      profile.value = null
      throw error
    } finally {
      loading.value = false
    }
  }

  async function loadProfile(): Promise<Employee> {
    const employee = await authApi.fetchCurrentEmployee()
    profile.value = employee
    authenticated.value = true
    return employee
  }

  /** 会话在本地失效(令牌过期且刷新失败)时的清理;由 `http` 层的 onAuthExpired 触发 */
  function clearSession(): void {
    clearTokens()
    authenticated.value = false
    profile.value = null
  }

  /**
   * 登出。
   *
   * 先请后端撤销 refresh token,再清本地 —— 顺序反了的话,
   * 一旦撤销请求失败,服务端还留着一个有效的 refresh token。
   * 失败也照样清本地:用户点了登出就必须登出。
   */
  async function logout(): Promise<void> {
    const refreshToken = getRefreshToken()
    try {
      if (refreshToken) await authApi.logout(refreshToken)
    } catch {
      // 网络/令牌已失效都不阻塞登出
    } finally {
      clearSession()
    }
  }

  /** 改密码成功后后端会撤销所有 refresh token,所以本地必须重新登录 */
  async function changePassword(oldPassword: string, newPassword: string): Promise<void> {
    await authApi.changePassword({ oldPassword, newPassword })
    clearSession()
  }

  return {
    profile,
    authenticated,
    loading,
    isAdmin,
    displayName,
    login,
    logout,
    loadProfile,
    clearSession,
    changePassword,
  }
})
