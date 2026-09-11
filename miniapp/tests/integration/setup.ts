import { createPinia, setActivePinia } from 'pinia'
import { afterAll, beforeAll } from 'vitest'

import { setApiBaseUrl } from '@/api/http'

/**
 * 集成测试前置:把小程序端代码指向**真后端**,并给 `uni.*` 一份 Node 版实现。
 *
 * Node 里没有小程序运行时,`uni.request` 用 `fetch` 兜底 —— 除了这一层适配,
 * 被测代码(api 层、store)与小程序里跑的是同一份。
 */
export const BACKEND_BASE_URL = process.env.SKY_BACKEND ?? 'http://localhost:8080'

setApiBaseUrl(BACKEND_BASE_URL)

interface UniRequestOptions {
  url: string
  method?: string
  data?: unknown
  header?: Record<string, string>
  success?: (response: { statusCode: number; data: unknown; header?: Record<string, string> }) => void
  fail?: (error: { errMsg?: string }) => void
}

const storage = new Map<string, unknown>()

;(globalThis as { uni?: unknown }).uni = {
  request(options: UniRequestOptions) {
    const method = (options.method ?? 'GET').toUpperCase()
    const url = new URL(options.url)
    if (method === 'GET' && options.data) {
      for (const [key, value] of Object.entries(options.data as Record<string, unknown>)) {
        if (value !== undefined && value !== null) url.searchParams.set(key, String(value))
      }
    }
    const hasBody = method !== 'GET' && options.data !== undefined
    fetch(url, {
      method,
      headers: { ...(options.header ?? {}), ...(hasBody ? { 'Content-Type': 'application/json' } : {}) },
      body: hasBody ? JSON.stringify(options.data) : undefined,
    })
      .then(async (response) => {
        const text = await response.text()
        let data: unknown = text
        if (text) {
          try {
            data = JSON.parse(text)
          } catch {
            // 非 JSON 就原样返回
          }
        } else {
          data = ''
        }
        const header: Record<string, string> = {}
        const traceId = response.headers.get('x-trace-id')
        if (traceId) header['x-trace-id'] = traceId
        options.success?.({ statusCode: response.status, data, header })
      })
      .catch((error: unknown) => options.fail?.({ errMsg: String(error) }))
    return {}
  },
  getStorageSync: (key: string) => storage.get(key) ?? '',
  setStorageSync: (key: string, value: unknown) => {
    storage.set(key, value)
  },
  removeStorageSync: (key: string) => {
    storage.delete(key)
  },
}

beforeAll(async () => {
  // store 用例直接调 useXxxStore(),Node 环境里要自己装一个 pinia
  setActivePinia(createPinia())

  let response: Response
  try {
    response = await fetch(`${BACKEND_BASE_URL}/v3/api-docs`, { signal: AbortSignal.timeout(5000) })
  } catch (error) {
    throw new Error(
      `集成测试需要后端在 ${BACKEND_BASE_URL} 运行(先启动 backend/ 与 MySQL/Redis)。` +
        `原始错误:${error instanceof Error ? error.message : String(error)}`,
    )
  }
  if (!response.ok) throw new Error(`后端健康检查失败:GET /v3/api-docs → ${response.status}`)
})

const cleanups: (() => Promise<void>)[] = []

export function onCleanup(action: () => Promise<void>): void {
  cleanups.push(action)
}

afterAll(async () => {
  for (const cleanup of [...cleanups].reverse()) {
    try {
      await cleanup()
    } catch (error) {
      console.warn('[integration] 清理失败:', error)
    }
  }
  cleanups.length = 0
})
