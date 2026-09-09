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
        ElMessage.error(result.msg || '操作失败')
        return Promise.reject(result)
      }
      // 业务成功：剥离 Result 外壳，直接返回业务数据
      return result.data as unknown as AxiosResponse
    }
    // 非 Result 结构原样返回
    return body as unknown as AxiosResponse
  },
  (error: AxiosError): Promise<AxiosError> => {
    if (error.response) {
      switch (error.response.status) {
        case 401:
          router.push('/login')
          break
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