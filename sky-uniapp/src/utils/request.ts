import { useAppStore } from '@/stores'
import { baseUrl } from './env'

export interface Result<T = any> {
  code: number
  msg: string
  data: T
}

interface RequestOptions {
  url: string
  params?: any
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
}

// 参数：url:请求地址 params:请求参数 method:请求方式
export function request<T = any>({ url = '', params = {}, method = 'GET' }: RequestOptions): Promise<Result<T>> {
  const store = useAppStore()
  store.setLodding(false)
  const header = {
    Accept: 'application/json',
    'Content-Type': 'application/json',
    Authorization: 'Bearer ' + (store.token || '')
  }

  return new Promise((resolve, reject) => {
    uni.request({
      url: baseUrl + url,
      data: params,
      header,
      method,
      success: (res) => {
        const data = res.data as Result<T>
        if (data.code === 200) {
          resolve(data)
        } else {
          reject(data)
        }
      },
      fail: (err) => {
        reject({ code: -1, msg: err.errMsg, data: null } as Result<T>)
      }
    })
  })
}
