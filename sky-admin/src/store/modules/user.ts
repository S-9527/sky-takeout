import { ref } from 'vue'
import { defineStore } from 'pinia'
import { login, userLogout } from '@/api/employee'
import {
  getToken,
  setToken,
  removeToken,
  getStoreId,
  setStoreId,
  getUserInfo,
  setUserInfo,
  removeUserInfo
} from '@/utils/cookies'
import type { EmployeeLoginVO } from '@/api/types/employee'

interface StoreUserInfo extends EmployeeLoginVO {
  avatar?: string
  roles?: string[]
  introduction?: string
  applicant?: string
  storeManagerName?: string
}

// pinia 是唯一读入口,cookie 只负责跨刷新保留;这里启动时一次性水合
function loadUserInfo(): Partial<StoreUserInfo> {
  const saved = getUserInfo()
  if (!saved) return {}
  try {
    return JSON.parse(saved) as StoreUserInfo
  } catch {
    return {}
  }
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(getToken() || '')
  const storeId = ref<string>(getStoreId() || '')
  const userInfo = ref<Partial<StoreUserInfo>>(loadUserInfo())
  const roles = ref<string[]>([])
  const name = ref('')
  const avatar = ref('')
  const introduction = ref('')
  const username = ref('')

  // 派生字段统一从 userInfo 同步,避免各处各写一遍
  function syncFromUserInfo() {
    const info = userInfo.value
    roles.value = info.roles ?? []
    name.value = info.name ?? ''
    avatar.value = info.avatar ?? ''
    introduction.value = info.introduction ?? ''
  }
  syncFromUserInfo()

  async function Login(params: { username: string; password: string }) {
    const { password } = params
    const uname = params.username.trim()
    username.value = uname
    // 拦截器已剥离 Result 外壳并统一处理失败，成功时 data 即业务数据
    const data = await login({ username: uname, password })
    token.value = data.token
    setToken(data.token)
    userInfo.value = { ...data }
    syncFromUserInfo()
    setUserInfo(JSON.stringify(data))
    return data
  }

  // 清空登录态:cookie 与内存状态一起清
  function ResetToken() {
    removeToken()
    removeUserInfo()
    token.value = ''
    username.value = ''
    userInfo.value = {}
    syncFromUserInfo()
  }

  async function changeStore(data: { data: string; authorization: string }) {
    storeId.value = data.data
    token.value = data.authorization
    setStoreId(data.data)
    setToken(data.authorization)
  }

  async function LogOut() {
    await userLogout()
    ResetToken()
  }

  return {
    token,
    name,
    avatar,
    storeId,
    introduction,
    userInfo,
    roles,
    username,
    Login,
    ResetToken,
    changeStore,
    LogOut
  }
})
