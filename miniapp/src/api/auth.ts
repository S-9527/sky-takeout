import type { Customer, TokenPair } from '@/types'

import { http } from './http'

export interface WechatLoginBody {
  /** 小程序 `wx.login` 拿到的临时凭证;开发期后端用 mock 换 openid,任意字符串即可 */
  code: string
  nickname?: string
  avatarUrl?: string
}

/**
 * 微信登录。
 *
 * 和其它登录接口一样:**只返回令牌对**,资料要另外调 `getProfile()`。
 */
export function wechatLogin(body: WechatLoginBody): Promise<TokenPair> {
  return http.post<TokenPair>('/api/v1/customer/auth/wechat-login', body, { auth: false })
}

export function refresh(refreshToken: string): Promise<TokenPair> {
  return http.post<TokenPair>('/api/v1/customer/auth/refresh', { refreshToken }, { auth: false })
}

export function logout(refreshToken: string): Promise<void> {
  return http.post<void>('/api/v1/customer/auth/logout', { refreshToken })
}

export function getProfile(): Promise<Customer> {
  return http.get<Customer>('/api/v1/customer/profile')
}

export function updateProfile(body: {
  nickname?: string
  avatarUrl?: string
  phone?: string
}): Promise<Customer> {
  return http.put<Customer>('/api/v1/customer/profile', body)
}
