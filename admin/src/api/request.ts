import type { AxiosRequestConfig } from 'axios'

import { http } from './http'

/**
 * 薄薄一层 `response.data` 解包。
 *
 * 保留 axios 原样(不把拦截器改成"直接返回 data")是有意的:
 * 上传进度、响应头(`X-Trace-Id`)、超时这类信息在拦截器里被吃掉后就拿不回来了。
 * 代价是每个 api 函数多一次 `.then(r => r.data)`,这里统一收口。
 */
export async function get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const response = await http.get<T>(url, config)
  return response.data
}

export async function post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const response = await http.post<T>(url, data, config)
  return response.data
}

export async function put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const response = await http.put<T>(url, data, config)
  return response.data
}

export async function patch<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const response = await http.patch<T>(url, data, config)
  return response.data
}

export async function del<T = void>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const response = await http.delete<T>(url, config)
  return response.data
}
