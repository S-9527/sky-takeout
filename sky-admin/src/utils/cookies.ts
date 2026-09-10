import Cookies from 'js-cookie'

// 侧栏开合状态
const SIDEBAR_STATUS_KEY = 'sidebar_status'
export const setSidebarStatus = (status: string) =>
  Cookies.set(SIDEBAR_STATUS_KEY, status)

// 店铺 id
const STORE_ID_KEY = 'storeId'
export const getStoreId = () => Cookies.get(STORE_ID_KEY)
export const setStoreId = (id: string) => Cookies.set(STORE_ID_KEY, id)

// 登录 token
const TOKEN_KEY = 'token'
export const getToken = () => Cookies.get(TOKEN_KEY)
export const setToken = (token: string) => Cookies.set(TOKEN_KEY, token)
export const removeToken = () => Cookies.remove(TOKEN_KEY)

// 刷新 token
const REFRESH_TOKEN_KEY = 'refresh_token'
export const getRefreshToken = () => Cookies.get(REFRESH_TOKEN_KEY)
export const setRefreshToken = (token: string) => Cookies.set(REFRESH_TOKEN_KEY, token)
export const removeRefreshToken = () => Cookies.remove(REFRESH_TOKEN_KEY)

// 登录用户信息
const USER_INFO_KEY = 'user_info'
export const getUserInfo = () => Cookies.get(USER_INFO_KEY)
export const setUserInfo = (userInfo: string) =>
  Cookies.set(USER_INFO_KEY, userInfo)
export const removeUserInfo = () => Cookies.remove(USER_INFO_KEY)
