import type { ApiErrorBody, ErrorDetail, TokenPair } from '@/types'

import { clearTokens, getAccessToken, getRefreshToken, setTokens } from './tokens'

/**
 * 顾客端 HTTP 客户端。
 *
 * 用的是 `uni.request` 而不是 axios:小程序没有 XHR/fetch,只有 uni 的跨端 API。
 * 于是这里自己实现管理端那套 axios 拦截器做的事:
 * 令牌注入、401 单飞刷新重试、错误归一成 `ApiError`。
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly details: ErrorDetail[]
  readonly traceId: string | null

  constructor(init: {
    status: number
    code: string
    message: string
    details?: ErrorDetail[]
    traceId?: string | null
  }) {
    super(init.message)
    this.name = 'ApiError'
    this.status = init.status
    this.code = init.code
    this.details = init.details ?? []
    this.traceId = init.traceId ?? null
  }

  get isNetworkError(): boolean {
    return this.status === 0
  }

  /** 401/403:令牌或权限问题,通常要重新登录 */
  get isAuthError(): boolean {
    return this.status === 401 || this.status === 403
  }
}

export const LOGIN_PATH = '/api/v1/customer/auth/wechat-login'
export const REFRESH_PATH = '/api/v1/customer/auth/refresh'

/** 这两个错误码代表"access token 该换了" */
const REFRESHABLE_CODES = new Set(['AUTH_TOKEN_EXPIRED', 'AUTH_TOKEN_INVALID'])

/**
 * API 根地址。
 *
 * - H5 开发期留空,走 Vite 的 `/api` 代理;
 * - 小程序必须给完整地址(没有代理概念),用 `VITE_API_BASE_URL` 配,例如 `http://localhost:8080`。
 */
let apiBaseUrl = (import.meta.env?.VITE_API_BASE_URL as string | undefined) ?? ''

export function setApiBaseUrl(url: string): void {
  apiBaseUrl = url.replace(/\/$/, '')
}

export function getApiBaseUrl(): string {
  return apiBaseUrl
}

/** 把相对路径补成 uni.request 能用的地址 */
export function resolveUrl(path: string): string {
  if (/^https?:\/\//.test(path)) return path
  return `${apiBaseUrl}${path}`
}

/* ------------------------------------------------------------------ uni.request 适配 */

export type UniMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

interface UniRequestSuccess {
  statusCode: number
  data: unknown
  header?: Record<string, string>
}

interface UniRequestFail {
  errMsg?: string
}

interface UniRequestTask {
  abort?: () => void
}

interface UniLike {
  request: (options: {
    url: string
    method?: UniMethod
    data?: unknown
    header?: Record<string, string>
    timeout?: number
    success?: (response: UniRequestSuccess) => void
    fail?: (error: UniRequestFail) => void
  }) => UniRequestTask
}

function uniApi(): UniLike {
  const candidate = (globalThis as { uni?: Partial<UniLike> }).uni
  if (!candidate?.request) {
    throw new ApiError({
      status: 0,
      code: 'UNI_NOT_AVAILABLE',
      message: '当前环境没有 uni.request(单元测试请注入 uni 桩)',
    })
  }
  return candidate as UniLike
}

/* ------------------------------------------------------------------ 错误归一 */

function isErrorBody(value: unknown): value is ApiErrorBody {
  if (typeof value !== 'object' || value === null) return false
  const body = value as Record<string, unknown>
  return typeof body.code === 'string' && typeof body.message === 'string'
}

const STATUS_FALLBACK_MESSAGE: Record<number, string> = {
  400: '请求参数有误,请检查后重试',
  401: '登录状态无效,请重新登录',
  403: '没有该操作权限',
  404: '请求的资源不存在',
  409: '数据冲突,请刷新后重试',
  422: '该操作不被允许',
  500: '服务器开小差了,请稍后重试',
  502: '支付渠道暂时不可用,请稍后重试',
}

function toApiError(status: number, data: unknown, traceId?: string | null): ApiError {
  if (isErrorBody(data)) {
    return new ApiError({
      status,
      code: data.code,
      message: data.message,
      details: data.details,
      traceId: data.traceId ?? traceId ?? null,
    })
  }
  return new ApiError({
    status,
    code: status === 0 ? 'NETWORK_ERROR' : 'COMMON_INTERNAL_ERROR',
    message:
      status === 0
        ? '无法连接服务器,请检查网络或后端是否已启动'
        : (STATUS_FALLBACK_MESSAGE[status] ?? `请求失败(${status})`),
    traceId: traceId ?? null,
  })
}

/* ------------------------------------------------------------------ 刷新(单飞) */

type RefreshTransport = (refreshToken: string) => Promise<TokenPair>

async function uniPost<T>(url: string, data: unknown): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    uniApi().request({
      url: resolveUrl(url),
      method: 'POST',
      data,
      header: { 'Content-Type': 'application/json' },
      timeout: 15_000,
      success: (response) => {
        if (response.statusCode >= 200 && response.statusCode < 300) {
          resolve(response.data as T)
          return
        }
        reject(toApiError(response.statusCode, response.data, response.header?.['x-trace-id']))
      },
      fail: (error) => reject(toApiError(0, null, error?.errMsg)),
    })
  })
}

async function defaultRefreshTransport(refreshToken: string): Promise<TokenPair> {
  return uniPost<TokenPair>(REFRESH_PATH, { refreshToken })
}

let refreshTransport: RefreshTransport = defaultRefreshTransport

/** 允许测试注入刷新传输层,避免去 mock 整个 uni 对象 */
export function setRefreshTransport(transport: RefreshTransport): void {
  refreshTransport = transport
}

export function resetRefreshTransport(): void {
  refreshTransport = defaultRefreshTransport
}

let refreshPromise: Promise<TokenPair> | null = null

/**
 * 刷新令牌 —— 单飞。
 *
 * 首页会并发好几个请求(门店状态、分类、购物车),同时过期时若各自刷新,
 * 后端旋转 refresh token 后第二次就会判失效,用户被莫名踢下线。
 */
export function refreshTokenPair(): Promise<TokenPair> {
  if (!refreshPromise) {
    const refreshToken = getRefreshToken()
    refreshPromise = (refreshToken
      ? refreshTransport(refreshToken)
      : Promise.reject(
          new ApiError({
            status: 401,
            code: 'AUTH_REFRESH_TOKEN_INVALID',
            message: '登录已失效,请重新登录',
          }),
        )
    )
      .then((pair) => {
        setTokens(pair)
        return pair
      })
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

type AuthExpiredHandler = (error: ApiError) => void

let authExpiredHandler: AuthExpiredHandler | null = null

export function onAuthExpired(handler: AuthExpiredHandler | null): void {
  authExpiredHandler = handler
}

function notifyAuthExpired(error: ApiError): void {
  clearTokens()
  authExpiredHandler?.(error)
}

/* ------------------------------------------------------------------ 请求入口 */

export interface RequestOptions {
  method?: UniMethod
  data?: unknown
  /** 是否附带令牌,默认 true */
  auth?: boolean
  /** 内部重试用:标记已经重放过,避免无限循环 */
  retried?: boolean
  timeout?: number
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET'
  const header: Record<string, string> = { Accept: 'application/json' }
  if (method !== 'GET' && options.data !== undefined) header['Content-Type'] = 'application/json'
  const token = options.auth === false ? null : getAccessToken()
  if (token) header.Authorization = `Bearer ${token}`

  const response = await new Promise<UniRequestSuccess>((resolve, reject) => {
    uniApi().request({
      url: resolveUrl(path),
      method,
      data: options.data,
      header,
      timeout: options.timeout ?? 20_000,
      success: resolve,
      fail: (error) => reject(toApiError(0, null, error?.errMsg)),
    })
  })

  if (response.statusCode >= 200 && response.statusCode < 300) {
    // 204 无响应体:uni.request 会给空字符串,这里统一成 undefined
    return (response.data === '' ? undefined : response.data) as T
  }

  const error = toApiError(response.statusCode, response.data, response.header?.['x-trace-id'])
  const refreshable =
    error.status === 401 &&
    REFRESHABLE_CODES.has(error.code) &&
    !options.retried &&
    options.auth !== false

  if (refreshable) {
    try {
      const pair = await refreshTokenPair()
      return await request<T>(path, { ...options, retried: true })
    } catch (refreshError) {
      const refreshApiError =
        refreshError instanceof ApiError ? refreshError : toApiError(0, null, String(refreshError))
      notifyAuthExpired(refreshApiError)
      throw refreshApiError
    }
  }

  if (error.status === 401) notifyAuthExpired(error)
  throw error
}

export const http = {
  // GET 的查询参数也走 `data`(uni.request 的约定)
  get: <T>(path: string, options: Omit<RequestOptions, 'method'> = {}) =>
    request<T>(path, { ...options, method: 'GET' }),
  post: <T>(path: string, data?: unknown, options: Omit<RequestOptions, 'method' | 'data'> = {}) =>
    request<T>(path, { ...options, method: 'POST', data }),
  put: <T>(path: string, data?: unknown, options: Omit<RequestOptions, 'method' | 'data'> = {}) =>
    request<T>(path, { ...options, method: 'PUT', data }),
  patch: <T>(path: string, data?: unknown, options: Omit<RequestOptions, 'method' | 'data'> = {}) =>
    request<T>(path, { ...options, method: 'PATCH', data }),
  delete: <T = void>(path: string, options: Omit<RequestOptions, 'method' | 'data'> = {}) =>
    request<T>(path, { ...options, method: 'DELETE' }),
  /** 带查询参数的删除(契约里批量删除走 `?ids=1,2`) */
  deleteWithQuery: <T = void>(path: string, data: unknown) =>
    request<T>(path, { method: 'DELETE', data }),
}
