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

// 刷新令牌本地持久化,随每次轮换更新
let refreshToken = uni.getStorageSync('refreshToken') || ''
// 在途刷新单例,避免并发 401 重复刷新导致轮换竞争
let refreshPending: Promise<string> | null = null

function doRefresh(): Promise<string> {
  if (!refreshToken) {
    useAppStore().setToken('')
    return Promise.resolve('')
  }
  if (!refreshPending) {
    refreshPending = new Promise((resolve) => {
      uni.request({
        url: baseUrl + '/user/user/refresh',
        data: { refreshToken },
        method: 'POST',
        header: {
          Accept: 'application/json',
          'Content-Type': 'application/json'
        },
        success: (res) => {
          const data = res.data as Result<{ accessToken: string; refreshToken: string }>
          if (data.code === 200 && data.data && data.data.accessToken) {
            // 刷新令牌单次有效,后端会轮换出一个新 pair
            refreshToken = data.data.refreshToken || refreshToken
            uni.setStorageSync('refreshToken', refreshToken)
            const store = useAppStore()
            store.setToken(data.data.accessToken)
            resolve(store.token)
          } else {
            // 刷新失败(刷新令牌过期/失效),清空本地身份,下次进入页面自动静默登录
            const store = useAppStore()
            refreshToken = ''
            uni.setStorageSync('refreshToken', '')
            store.setToken('')
            resolve('')
          }
        },
        fail: () => resolve(''),
        complete: () => {
          setTimeout(() => {
            refreshPending = null
          }, 0)
        }
      })
    })
  }
  return refreshPending
}

// 参数：url:请求地址 params:请求参数 method:请求方式
export function request<T = any>(options: RequestOptions, retried = false): Promise<Result<T>> {
  const { url = '', params = {}, method = 'GET' } = options
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
        } else if (data.code === 401 && !retried) {
          // 访问令牌过期,刷新后重试一次
          doRefresh().then((newToken) => {
            if (newToken) {
              request<T>(options, true).then(resolve, reject)
            } else {
              reject(data)
            }
          })
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