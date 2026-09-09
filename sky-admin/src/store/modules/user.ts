import { ref } from 'vue'
import { defineStore } from 'pinia'
import { login, userLogout } from '@/api/employee'
import {
  getToken,
  setToken,
  removeToken,
  getStoreId,
  setStoreId,
  getUsername,
  setUsername,
  removeUsername,
  getUserInfo,
  setUserInfo,
  removeUserInfo
} from '@/utils/cookies'
import type { EmployeeLoginVO } from '@/api/types'

interface StoreUserInfo extends EmployeeLoginVO {
  avatar?: string
  roles?: string[]
  introduction?: string
  applicant?: string
  storeManagerName?: string
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(getToken() || '')
  const name = ref('')
  const avatar = ref('')
  const storeId = ref<string>(getStoreId() || '')
  const introduction = ref('')
  const userInfo = ref<Partial<StoreUserInfo>>({})
  const roles = ref<string[]>([])
  const username = ref<string>(getUsername() || '')

  async function Login(params: { username: string; password: string }) {
    let { username: uname, password } = params
    uname = uname.trim()
    username.value = uname
    setUsername(uname)
    // 拦截器已剥离 Result 外壳并统一处理失败，成功时 data 即业务数据
    const data = await login({ username: uname, password })
    token.value = data.token
    setToken(data.token)
    userInfo.value = { ...data }
    setUserInfo(JSON.stringify(data))
    return data
  }

  // 清空登录态:cookie 与内存状态一起清
  function ResetToken() {
    removeToken()
    removeUserInfo()
    removeUsername()
    token.value = ''
    username.value = ''
    userInfo.value = {}
    name.value = ''
    avatar.value = ''
    introduction.value = ''
    roles.value = []
  }

  async function changeStore(data: { data: string; authorization: string }) {
    storeId.value = data.data
    token.value = data.authorization
    setStoreId(data.data)
    setToken(data.authorization)
  }

  async function GetUserInfo() {
    if (token.value === '') {
      throw Error('GetUserInfo: token is undefined!')
    }

    const data = JSON.parse(getUserInfo() as string) as StoreUserInfo
    if (!data) {
      throw Error('Verification failed, please Login again.')
    }

    const {
      roles: roleList,
      name: userName,
      avatar: userAvatar,
      introduction: userIntroduction,
      applicant,
      storeManagerName
    } = data
    if (!roleList || roleList.length <= 0) {
      throw Error('GetUserInfo: roles must be a non-null array!')
    }

    roles.value = roleList
    userInfo.value = { ...data }
    name.value = userName || applicant || storeManagerName || ''
    avatar.value = userAvatar || ''
    introduction.value = userIntroduction || ''
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
    GetUserInfo,
    LogOut
  }
})
