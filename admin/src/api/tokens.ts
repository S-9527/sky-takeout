import type { TokenPair } from '@/types'

/**
 * 令牌仓库 —— 唯一持有 access/refresh token 的地方。
 *
 * ## 为什么放 `localStorage`
 *
 * 后端签发的是 **Bearer 令牌**,不是 httpOnly Cookie,前端必须自己存。
 * 可选方案只有两个:
 * - `sessionStorage`:换标签页就要重新登录,管理端体验太差;
 * - `localStorage`:XSS 一旦发生,令牌可被读走。
 *
 * 这里选 `localStorage`,并把风险写进文档(admin/README.md「安全边界」):
 * 真正的防线是 CSP + 不引入不可信脚本 + access token 只活 2 小时 + refresh 旋转。
 *
 * ## 为什么单独成模块
 *
 * `http.ts` 需要"无循环依赖"地拿令牌(store → api → store 会成环),
 * 所以令牌读写放在这里,`auth` store 只是它的 UI 包装。
 *
 * 非浏览器环境(Node 集成测试)没有 `localStorage`,自动退化为进程内存储。
 */

const STORAGE_KEY = 'sky.admin.tokens'

let memory: TokenPair | null = null

function storage(): Storage | null {
  try {
    if (typeof localStorage === 'undefined') return null
    return localStorage
  } catch {
    // 隐私模式/被策略禁用时访问 localStorage 会抛异常
    return null
  }
}

function isTokenPair(value: unknown): value is TokenPair {
  if (typeof value !== 'object' || value === null) return false
  const pair = value as Record<string, unknown>
  return typeof pair.accessToken === 'string' && typeof pair.refreshToken === 'string'
}

/** 从持久化介质读取(只在模块首次使用时调用一次) */
function readFromStorage(): TokenPair | null {
  const store = storage()
  if (!store) return null
  const raw = store.getItem(STORAGE_KEY)
  if (!raw) return null
  try {
    const parsed: unknown = JSON.parse(raw)
    return isTokenPair(parsed) ? parsed : null
  } catch {
    return null
  }
}

let loaded = false

function ensureLoaded(): void {
  if (loaded) return
  loaded = true
  memory = readFromStorage()
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
  storage()?.setItem(STORAGE_KEY, JSON.stringify(pair))
}

export function clearTokens(): void {
  ensureLoaded()
  memory = null
  storage()?.removeItem(STORAGE_KEY)
}

/** 仅供测试:重置模块级缓存,让下一次读取重新走 storage */
export function resetTokenCacheForTest(): void {
  loaded = false
  memory = null
}
