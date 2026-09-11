import type { TokenPair } from '@/types'

import { runtimeStorageGet, runtimeStorageRemove, runtimeStorageSet, setRuntimeForTest } from './runtime'

/**
 * 令牌仓库(小程序端)。
 *
 * 与 `admin/src/api/tokens.ts` 同样的职责与理由,只是存储介质换成 `uni.setStorageSync` ——
 * 它在 H5 落到 localStorage、在小程序落到 `wx.setStorageSync`,两端都不需要 httpOnly Cookie
 * (后端签发的是 Bearer 令牌)。非 uni 环境(单元测试/Node 集成测试)退化为进程内存储。
 */

const STORAGE_KEY = 'sky.customer.tokens'

let memory: TokenPair | null = null
let loaded = false

export { setRuntimeForTest }

function isTokenPair(value: unknown): value is TokenPair {
  if (typeof value !== 'object' || value === null) return false
  const pair = value as Record<string, unknown>
  return typeof pair.accessToken === 'string' && typeof pair.refreshToken === 'string'
}

function ensureLoaded(): void {
  if (loaded) return
  loaded = true
  try {
    const raw = runtimeStorageGet(STORAGE_KEY)
    const parsed: unknown = typeof raw === 'string' ? JSON.parse(raw) : raw
    memory = isTokenPair(parsed) ? parsed : null
  } catch {
    memory = null
  }
}

export function getTokens(): TokenPair | null {
  ensureLoaded()
  return memory
}

export function getAccessToken(): string | null {
  return getTokens()?.accessToken ?? null
}

export function getRefreshToken(): string | null {
  return getTokens()?.refreshToken ?? null
}

export function setTokens(pair: TokenPair): void {
  ensureLoaded()
  memory = pair
  try {
    runtimeStorageSet(STORAGE_KEY, JSON.stringify(pair))
  } catch {
    // 存储写失败不影响本次会话(内存里还在)
  }
}

export function clearTokens(): void {
  ensureLoaded()
  memory = null
  try {
    runtimeStorageRemove(STORAGE_KEY)
  } catch {
    // 同上
  }
}

/** 仅供测试:重置模块级缓存,让下一次读取重新走 storage */
export function resetTokenCacheForTest(): void {
  loaded = false
  memory = null
}
