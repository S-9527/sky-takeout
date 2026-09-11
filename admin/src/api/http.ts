import axios, { AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'

import type { ApiErrorBody, ErrorDetail, TokenPair } from '@/types'
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from './tokens'

/** 后端统一错误体 (`ErrorResponse`) 的前端表现 */
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

  /** 没拿到响应(断网、后端没起来、代理挂了) */
  get isNetworkError(): boolean {
    return this.status === 0
  }

  /** 401/403 —— 令牌或权限问题,通常需要重新登录或提示无权限 */
  get isAuthError(): boolean {
    return this.status === 401 || this.status === 403
  }

  /** 422/409 —— 业务规则不允许,提示文案直接来自后端 */
  get isBusinessError(): boolean {
    return this.status === 422 || this.status === 409
  }
}

export const LOGIN_PATH = '/api/v1/admin/auth/login'
export const REFRESH_PATH = '/api/v1/admin/auth/refresh'

/** 这两个错误码代表"access token 该换了",其余 401 直接走重新登录 */
const REFRESHABLE_CODES = new Set(['AUTH_TOKEN_EXPIRED', 'AUTH_TOKEN_INVALID'])

let apiBaseUrl = ''

/**
 * 设置 API 根地址。
 *
 * 浏览器里保持空串,靠 Vite 代理走同源;
 * Node 集成测试里设成 `http://localhost:8080`,因为 Node 没有"页面 origin"。
 */
export function setApiBaseUrl(url: string): void {
  apiBaseUrl = url
  http.defaults.baseURL = url
}

export function getApiBaseUrl(): string {
  return apiBaseUrl
}

type RefreshTransport = (refreshToken: string) => Promise<TokenPair>

/**
 * 刷新令牌的"传输层",默认用裸 axios 直连刷新接口。
 *
 * 之所以单独抽出来:刷新请求**不能**走 `http` 实例 —— 它自己也会带 401 拦截器,
 * 一旦 refresh token 也过期就会递归刷新直到栈溢出。
 * 抽成可注入的函数后,单元测试可以直接替换它,不用去 mock 整个 axios 模块。
 */
async function defaultRefreshTransport(refreshToken: string): Promise<TokenPair> {
  const response = await axios.post<TokenPair>(
    `${apiBaseUrl}${REFRESH_PATH}`,
    { refreshToken },
    { timeout: 15_000, headers: { 'Content-Type': 'application/json' } },
  )
  return response.data
}

let refreshTransport: RefreshTransport = defaultRefreshTransport

export function setRefreshTransport(transport: RefreshTransport): void {
  refreshTransport = transport
}

export function resetRefreshTransport(): void {
  refreshTransport = defaultRefreshTransport
}

let refreshPromise: Promise<TokenPair> | null = null

/**
 * 刷新令牌 —— **单飞**。
 *
 * 页面首屏往往并发好几个请求,若每个 401 都各自刷新,
 * 就会用同一个 refresh token 打多次:后端要旋转 refresh token,
 * 第二个请求会被判为"已失效",用户莫名其妙被踢下线。
 * 因此并发时共用同一个 Promise。
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

/** 注册"令牌彻底失效"回调(应用里指向跳登录页) */
export function onAuthExpired(handler: AuthExpiredHandler | null): void {
  authExpiredHandler = handler
}

function notifyAuthExpired(error: ApiError): void {
  clearTokens()
  authExpiredHandler?.(error)
}

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
  405: '请求方式不正确',
  409: '数据冲突,请刷新后重试',
  422: '该操作不被允许',
  500: '服务器开小差了,请稍后重试',
  502: '支付渠道暂时不可用,请稍后重试',
}

/** 把 axios 的异常统一成 `ApiError`:业务错误体优先,其次按状态码兜底 */
export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error

  if (error instanceof AxiosError) {
    const response = error.response
    if (!response) {
      const message =
        error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT'
          ? '请求超时,请稍后重试'
          : '无法连接服务器,请检查网络或后端是否已启动'
      return new ApiError({ status: 0, code: 'NETWORK_ERROR', message })
    }
    const status = response.status
    const traceId =
      (response.headers?.['x-trace-id'] as string | undefined) ??
      (isErrorBody(response.data) ? response.data.traceId : null)
    if (isErrorBody(response.data)) {
      return new ApiError({
        status,
        code: response.data.code,
        message: response.data.message,
        details: response.data.details,
        traceId,
      })
    }
    return new ApiError({
      status,
      code: 'COMMON_INTERNAL_ERROR',
      message: STATUS_FALLBACK_MESSAGE[status] ?? `请求失败(${status})`,
      traceId,
    })
  }

  if (error instanceof Error) {
    return new ApiError({ status: 0, code: 'UNKNOWN_ERROR', message: error.message })
  }
  return new ApiError({ status: 0, code: 'UNKNOWN_ERROR', message: '未知错误' })
}

interface RetryableConfig extends InternalAxiosRequestConfig {
  /** 已经重试过一次就不再重试,避免刷新成功后仍 401 时无限循环 */
  _skyRetried?: boolean
  /** 登录/刷新请求自身不参与刷新重试 */
  _skySkipRefresh?: boolean
}

function isRefreshable(error: ApiError, config: RetryableConfig | undefined): boolean {
  return (
    error.status === 401 &&
    REFRESHABLE_CODES.has(error.code) &&
    !!config &&
    !config._skyRetried &&
    !config._skySkipRefresh
  )
}

export const http: AxiosInstance = axios.create({
  baseURL: apiBaseUrl,
  timeout: 20_000,
  headers: { Accept: 'application/json' },
})

http.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) config.headers.set('Authorization', `Bearer ${token}`)
  const url = config.url ?? ''
  if (url.includes(REFRESH_PATH) || url.includes(LOGIN_PATH)) {
    ;(config as RetryableConfig)._skySkipRefresh = true
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    const apiError = toApiError(error)
    const config = error instanceof AxiosError ? (error.config as RetryableConfig | undefined) : undefined

    if (isRefreshable(apiError, config)) {
      try {
        const pair = await refreshTokenPair()
        const retryConfig = config as RetryableConfig
        retryConfig._skyRetried = true
        retryConfig.headers.set('Authorization', `Bearer ${pair.accessToken}`)
        return await http.request(retryConfig)
      } catch (refreshError) {
        const refreshApiError = toApiError(refreshError)
        notifyAuthExpired(refreshApiError)
        throw refreshApiError
      }
    }

    // 401 走到这里说明"刷新也救不回来":清空令牌并通知应用跳登录
    if (apiError.status === 401) {
      notifyAuthExpired(apiError)
    }
    throw apiError
  },
)
