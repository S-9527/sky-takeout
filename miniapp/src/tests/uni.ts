import { uniTestDouble, type UniRequestOptions } from './setup'

/** 用例里驱动 `uni.request` 的快捷方式:按顺序给每次请求一个响应 */
export function respondWith(
  responses: { status: number; data?: unknown; headers?: Record<string, string> }[],
): UniRequestOptions[] {
  let index = 0
  uniTestDouble.handler = (options) => {
    const response = responses[Math.min(index, responses.length - 1)]
    index += 1
    options.success?.({
      statusCode: response.status,
      data: response.data ?? {},
      header: response.headers ?? {},
    })
  }
  return uniTestDouble.requests
}

export function respondWithFailure(errMsg: string): void {
  uniTestDouble.handler = (options) => {
    options.fail?.({ errMsg })
  }
}

export function lastRequest(): UniRequestOptions {
  const request = uniTestDouble.requests[uniTestDouble.requests.length - 1]
  if (!request) throw new Error('没有发出任何请求')
  return request
}
