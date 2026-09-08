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
  removeUserInfo
} from '@/utils/cookies'
import Cookies from 'js-cookie'
import { ElMessage } from 'element-plus'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(getToken() || '')
  const name = ref('')
  const avatar = ref('')
  const storeId = ref<string>(getStoreId() || '')
  const introduction = ref('')
  const userInfo = ref<any>({})
  const roles = ref<string[]>([])
  const username = ref<string>(Cookies.get('username') || '')

  async function Login(params: { username: string; password: string }) {
    let { username: uname, password } = params
    uname = uname.trim()
    username.value = uname
    Cookies.set('username', uname)
    const { data } = await login({ username: uname, password })
    if (String(data.code) === '200') {
      token.value = data.data.token
      setToken(data.data.token)
      userInfo.value = { ...data.data }
      Cookies.set('user_info', JSON.stringify(data.data))
      return data
    } else {
      return ElMessage.error(data.msg)
    }
  }

  function ResetToken() {
    removeToken()
    token.value = ''
    roles.value = []
  }

  async function changeStore(data: any) {
    storeId.value = data.data
    token.value = data.authorization
    setStoreId(data.data)
    setToken(data.authorization)
  }

  async function GetUserInfo() {
    if (token.value === '') {
      throw Error('GetUserInfo: token is undefined!')
    }

    const data = JSON.parse(getUserInfo() as string)
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
    name.value = userName || applicant || storeManagerName
    avatar.value = userAvatar
    introduction.value = userIntroduction
  }

  async function LogOut() {
    await userLogout({})
    removeToken()
    token.value = ''
    roles.value = []
    Cookies.remove('username')
    Cookies.remove('user_info')
    removeUserInfo()
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