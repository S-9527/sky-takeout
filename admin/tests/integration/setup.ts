import { afterAll, beforeAll } from 'vitest'

import { setApiBaseUrl } from '@/api/http'

/**
 * 集成测试前置:把前端代码指向**真后端**。
 *
 * 这里不做任何 mock —— 用例失败只有两种可能:契约变了,或者后端没起来。
 * 后者必须给出人能看懂的一句话,而不是一串 `ECONNREFUSED`。
 */
export const BACKEND_BASE_URL = process.env.SKY_BACKEND ?? 'http://localhost:8080'

setApiBaseUrl(BACKEND_BASE_URL)

const cleanups: (() => Promise<void>)[] = []

/** 注册收尾动作:即使断言失败也要跑,避免测试数据污染种子库 */
export function onCleanup(action: () => Promise<void>): void {
  cleanups.push(action)
}

beforeAll(async () => {
  let response: Response
  try {
    response = await fetch(`${BACKEND_BASE_URL}/v3/api-docs`, { signal: AbortSignal.timeout(5000) })
  } catch (error) {
    throw new Error(
      `集成测试需要后端在 ${BACKEND_BASE_URL} 运行(先启动 backend/ 与 MySQL/Redis)。` +
        `原始错误:${error instanceof Error ? error.message : String(error)}`,
    )
  }
  if (!response.ok) {
    throw new Error(`后端健康检查失败:GET /v3/api-docs → ${response.status}`)
  }
})

afterAll(async () => {
  // 后注册的先清理(与资源依赖顺序相反)
  for (const cleanup of [...cleanups].reverse()) {
    try {
      await cleanup()
    } catch (error) {
      console.warn('[integration] 清理失败:', error)
    }
  }
  cleanups.length = 0
})
