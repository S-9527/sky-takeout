import { defineStore } from 'pinia'
import { login, userLogout } from '@/api/employee'
import {
  getToken,
  setToken,
  removeToken,
  getStoreId,
  setStoreId,
  removeStoreId,
  setUserInfo,
  getUserInfo,
  removeUserInfo
} from '@/utils/cookies'
import Cookies from 'js-cookie'
import { ElMessage } from 'element-plus'

export interface IUserState {
  token: string
  name: string
  avatar: string
  storeId: string
  introduction: string
  userInfo: any
  roles: string[]
  username: string
}

export const useUserStore = defineStore('user', {
  state: (): IUserState => ({
    token: getToken() || '',
    name: '',
    avatar: '',
    storeId: getStoreId() || '',
    introduction: '',
    userInfo: {},
    roles: [],
    username: Cookies.get('username') || ''
  }),
  actions: {
    async Login(userInfo: { username: string; password: string }) {
      let { username, password } = userInfo
      username = username.trim()
      this.username = username
      Cookies.set('username', username)
      const { data } = await login({ username, password })
      if (String(data.code) === '1') {
        this.token = data.data.token
        setToken(data.data.token)
        this.userInfo = { ...data.data }
        Cookies.set('user_info', JSON.stringify(data.data))
        return data
      } else {
        return ElMessage.error(data.msg)
      }
    },
    ResetToken() {
      removeToken()
      this.token = ''
      this.roles = []
    },
    async changeStore(data: any) {
      this.storeId = data.data
      this.token = data.authorization
      setStoreId(data.data)
      setToken(data.authorization)
    },
    async GetUserInfo() {
      if (this.token === '') {
        throw Error('GetUserInfo: token is undefined!')
      }

      const data = JSON.parse(<string>getUserInfo())
      if (!data) {
        throw Error('Verification failed, please Login again.')
      }

      const {
        roles,
        name,
        avatar,
        introduction,
        applicant,
        storeManagerName,
        storeId = ''
      } = data
      if (!roles || roles.length <= 0) {
        throw Error('GetUserInfo: roles must be a non-null array!')
      }

      this.roles = roles
      this.userInfo = { ...data }
      this.name = name || applicant || storeManagerName
      this.avatar = avatar
      this.introduction = introduction
    },
    async LogOut() {
      const { data } = await userLogout({})
      removeToken()
      this.token = ''
      this.roles = []
      Cookies.remove('username')
      Cookies.remove('user_info')
      removeUserInfo()
    }
  }
})