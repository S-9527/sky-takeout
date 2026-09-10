import axios, {
  type AxiosError,
  type AxiosResponse,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig
} from 'axios'
import { useUserStore } from '@/store/modules/user'
import { ElMessage } from 'element-plus'
import pinia from '@/store'
import {
  getRequestKey,
  pending,
  checkPending,
  removePending
} from './requestOptimize'
import router from '@/router'

const service = axios.create({
  baseURL: import.meta.env.VITE_BASE_API,
  'timeout': 600000
})

// 后端统一返回结果
interface Result<T = unknown> {
  code: number
  msg: string
  data: T
}

// 单次刷新锁：多个并发 401 只触发一次 refresh
let refreshPromise: Promise<string | null> | null = null

/**
 * 尝试使用 refreshToken 换取新的令牌对，成功返回 accessToken
 * 同一时间只发起一次 refresh 请求
 */
async function tryRefresh(): Promise<string | null> {
  const userStore = useUserStore(pinia)
  if (!userStore.refreshToken) {
    return null
  }
  refreshPromise = (async () => {
    try {
      // 响应拦截器已剥离 Result 外壳，实际返回业务数据（非 AxiosResponse）
      const res = await service.post(
        '/employee/refresh',
        { refreshToken: userStore.refreshToken },
        { headers: { 'X-Silent': 'true' } }
      ) as unknown as { accessToken: string; refreshToken: string }
      userStore.setTokens(res.accessToken, res.refreshToken)
      return res.accessToken
    } catch {
      return null
    } finally {
      refreshPromise = null
    }
  })()
  return refreshPromise
}

/**
 * 判断是否为刷新请求本身，避免自动刷新陷入无限循环
 */
function isRefreshRequest(config?: InternalAxiosRequestConfig): boolean {
  return !!config?.url?.includes('/employee/refresh')
}

// Request interceptors
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig): InternalAxiosRequestConfig => {
    const UserStore = useUserStore(pinia)
    if (UserStore.token) {
      config.headers.set('Authorization', `Bearer ${UserStore.token}`)
    }

    // 计算当前请求key值，相同请求在途时直接中止，防止重复提交
    const key = getRequestKey(config)
    if (checkPending(key)) {
      const controller = new AbortController()
      config.signal = controller.signal
      controller.abort()
    } else {
      // 加入请求字典
      pending[key] = true
    }
    return config
  },
  (error: AxiosError): Promise<never> => {
    return Promise.reject(error)
  }
)

// Response interceptors
// 拦截器剥离 Result 外壳后直接返回业务数据，返回值按 AxiosResponse 类型弱化约束
service.interceptors.response.use(
  (res: AxiosResponse): AxiosResponse | Promise<AxiosResponse> => {
    //请求响应中的config的url会带上代理的api需要去掉
    res.config.url = res.config.url?.replace('/api', '')
    // 请求完成，删除请求中状态
    const key = getRequestKey(res.config)
    removePending(key)
    const body = res.data as unknown
    // 二进制响应(blob/arraybuffer 等)直接返回，不做业务码校验
    if (
      res.config.responseType === 'blob' ||
      res.config.responseType === 'arraybuffer'
    ) {
      return body as unknown as AxiosResponse
    }
    // 业务失败(HTTP 仍为 200):统一提示并拒绝，调用方无需再判断 code
    if (body && typeof body === 'object' && isResult(body)) {
      const result = body as Result
      if (result.code !== 200) {
        // silent 模式下不弹 toast（用于刷新请求等内部调用）
        if (!(res.config as InternalAxiosRequestConfig).headers?.get('X-Silent')) {
          const message = result.msg || '操作失败'
          ElMessage.error(message)
        }
        return Promise.reject(new Error(result.msg || '操作失败'))
      }
      // 业务成功：剥离 Result 外壳，直接返回业务数据
      return result.data as unknown as AxiosResponse
    }
    // 非 Result 结构原样返回
    return body as unknown as AxiosResponse
  },
  async (error: AxiosError): Promise<AxiosResponse> => {
    if (error.response) {
      const status = error.response.status
      const userStore = useUserStore(pinia)

      if (status === 401 && !isRefreshRequest(error.config)) {
        // 尝试刷新令牌（single-flight）
        const newToken = await tryRefresh()
        if (newToken) {
          // 刷新成功：重放原请求
          return service(error.config!)
        }
        // 刷新失败：清空登录态并跳转登录页
        userStore.ResetToken()
        router.push('/login')
        return Promise.reject(error) as any
      }

      if (status === 401 && isRefreshRequest(error.config)) {
        // 刷新请求本身也失败（refresh token 已过期）→ 强制登出
        userStore.ResetToken()
        router.push('/login')
        return Promise.reject(error) as any
      }

      switch (status) {
        case 405:
          error.message = '请求错误'
      }
    }
    //请求响应中的config的url会带上代理的api需要去掉
    if (error.config?.url) {
      error.config.url = error.config.url.replace('/api', '')
    }
    // 请求完成，删除请求中状态
    if (error.config) {
      const key = getRequestKey(error.config)
      removePending(key)
    }
    return Promise.reject(error)
  }
)

// 判断响应是否为后端 Result 结构
function isResult(body: object): boolean {
  return 'code' in body || 'msg' in body
}

// 请求方法封装：拦截器已剥离 Result 外壳，成功时 resolve 业务数据，失败时 reject
const request = {
  get<T>(url: string, config?: AxiosRequestConfig) {
    return service.get<unknown, T>(url, config)
  },
  delete<T>(url: string, config?: AxiosRequestConfig) {
    return service.delete<unknown, T>(url, config)
  },
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
    return service.post<unknown, T>(url, data, config)
  },
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
    return service.put<unknown, T>(url, data, config)
  }
}

export default request
