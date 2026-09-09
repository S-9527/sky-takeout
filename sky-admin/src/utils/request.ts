import axios, { type AxiosRequestConfig } from 'axios'
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
const CancelToken = axios.CancelToken;

const service = axios.create({
  baseURL: import.meta.env.VITE_BASE_API,
  'timeout': 600000
})

// Request interceptors
service.interceptors.request.use(
  (config: any) => {
    const UserStore = useUserStore(pinia)
    if (UserStore.token) {
      config.headers['Authorization'] = `Bearer ${UserStore.token}`
    }

    // get请求映射params参数
    if (config.method === 'get' && config.params) {
      let url = config.url + '?';
      for (const propName of Object.keys(config.params)) {
        const value = config.params[propName];
        var part = encodeURIComponent(propName) + '=';
        if (value !== null && typeof (value) !== 'undefined') {
          if (typeof value === 'object') {
            for (const key of Object.keys(value)) {
              let params = propName + '[' + key + ']';
              var subPart = encodeURIComponent(params) + '=';
              url += subPart + encodeURIComponent(value[key]) + '&';
            }
          } else {
            url += part + encodeURIComponent(value) + '&';
          }
        }
      }
      url = url.slice(0, -1);
      config.params = {};
      config.url = url;
    }
    // 计算当前请求key值
    const key = getRequestKey(config);
    if (checkPending(key)) {
      // 重复请求则取消当前请求
      const source = CancelToken.source();
      config.cancelToken = source.token;
      source.cancel('重复请求');
    } else {
      // 加入请求字典
      pending[key] = true;
    }
    return config
  },
  (error: any) => {
    Promise.reject(error)
  }
)

// Response interceptors
service.interceptors.response.use(
  (response: any) => {
    //请求响应中的config的url会带上代理的api需要去掉
    response.config.url = response.config.url.replace('/api', '')
    // 请求完成，删除请求中状态
    const key = getRequestKey(response.config);
    removePending(key);
    const body = response.data
    // 二进制响应(blob/arraybuffer 等)直接返回，不做业务码校验
    if (
      response.config.responseType === 'blob' ||
      response.config.responseType === 'arraybuffer'
    ) {
      return body
    }
    // 业务失败(HTTP 仍为 200):统一提示并拒绝，调用方无需再判断 code
    if (body && body.code !== 200) {
      ElMessage.error(body.msg || '操作失败')
      return Promise.reject(body)
    }
    // 业务成功：剥离 Result 外壳，直接返回业务数据
    return isResult(body) ? body.data : body
  },
  (error: any) => {
    if (error && error.response) {
      switch (error.response.status) {
        case 401:
          router.push('/login')
          break;
        case 405:
          error.message = '请求错误'
      }
    }
    //请求响应中的config的url会带上代理的api需要去掉
    error.config.url = error.config.url.replace('/api', '')
    // 请求完成，删除请求中状态
    const key = getRequestKey(error.config);
    removePending(key);
    return Promise.reject(error)
  }
)

// 判断响应是否为后端 Result 结构
function isResult(body: any) {
  return body && typeof body === 'object' && 'code' in body
}

// 请求函数：拦截器已剥离 Result 外壳，成功时 resolve 业务数据，失败时 reject
function request<T = any>(config: AxiosRequestConfig): Promise<T> {
  return service.request(config) as unknown as Promise<T>
}

export default request