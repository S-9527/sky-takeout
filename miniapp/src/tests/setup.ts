import { afterEach } from 'vitest'

/**
 * `uni.*` 桩实现。
 *
 * jsdom 里没有小程序运行时,但被测代码会调 `uni.request` / `uni.getStorageSync`。
 * 这里给一份内存实现,让单元测试直接驱动真实代码路径(而不是到处 mock 模块)。
 */
export interface UniRequestOptions {
  url: string
  method?: string
  data?: unknown
  header?: Record<string, string>
  success?: (response: { statusCode: number; data: unknown; header?: Record<string, string> }) => void
  fail?: (error: { errMsg?: string }) => void
}

const storage = new Map<string, unknown>()

export const uniTestDouble = {
  storage,
  requests: [] as UniRequestOptions[],
  /** 由用例设置:收到请求后怎么回 */
  handler: null as null | ((options: UniRequestOptions) => void),
  reset(): void {
    storage.clear()
    uniTestDouble.requests.length = 0
    uniTestDouble.handler = null
  },
}

import { setRuntimeForTest } from '@/api/runtime'

const runtime = {
  request(options: UniRequestOptions) {
    uniTestDouble.requests.push(options)
    if (uniTestDouble.handler) {
      uniTestDouble.handler(options)
      return {}
    }
    options.success?.({ statusCode: 200, data: {}, header: {} })
    return {}
  },
  getStorageSync: (key: string) => storage.get(key) ?? '',
  setStorageSync: (key: string, value: unknown) => {
    storage.set(key, value)
  },
  removeStorageSync: (key: string) => {
    storage.delete(key)
  },
  showToast: () => undefined,
  showModal: () => undefined,
  showActionSheet: () => undefined,
  navigateTo: () => undefined,
  navigateBack: () => undefined,
  reLaunch: () => undefined,
  switchTab: () => undefined,
  redirectTo: () => undefined,
  stopPullDownRefresh: () => undefined,
}

// 单元测试通过 runtime 注入替身:生产代码里永远是直接的 uni.xxx 调用
setRuntimeForTest({
  request: (options) => runtime.request(options as UniRequestOptions),
  storage: {
    get: (key) => storage.get(key) ?? '',
    set: (key, value) => {
      storage.set(key, value)
    },
    remove: (key) => {
      storage.delete(key)
    },
  },
})

;(globalThis as { uni?: unknown }).uni = runtime

afterEach(() => {
  uniTestDouble.reset()
})
