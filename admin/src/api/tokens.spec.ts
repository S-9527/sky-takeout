import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { TokenPair } from '@/types'

import {
  clearTokens,
  getAccessToken,
  getRefreshToken,
  getTokens,
  resetTokenCacheForTest,
  setTokens,
} from './tokens'

const pair: TokenPair = {
  accessToken: 'access-1',
  refreshToken: 'refresh-1',
  expiresIn: 7200,
  refreshExpiresIn: 604800,
  tokenType: 'Bearer',
}

beforeEach(() => {
  localStorage.clear()
  resetTokenCacheForTest()
  vi.unstubAllGlobals()
})

describe('令牌持久化', () => {
  it('写入后能从 localStorage 读回(刷新页面不掉登录)', () => {
    setTokens(pair)
    expect(JSON.parse(localStorage.getItem('sky.admin.tokens') ?? '{}')).toMatchObject({
      accessToken: 'access-1',
    })

    // 模拟"刷新页面":清掉进程内缓存,只能靠持久化介质
    resetTokenCacheForTest()
    expect(getTokens()?.refreshToken).toBe('refresh-1')
    expect(getAccessToken()).toBe('access-1')
    expect(getRefreshToken()).toBe('refresh-1')
  })

  it('没有存过时返回 null', () => {
    expect(getTokens()).toBeNull()
    expect(getAccessToken()).toBeNull()
  })

  it('存储内容被损坏时当作未登录,而不是抛异常', () => {
    localStorage.setItem('sky.admin.tokens', '{not json')
    resetTokenCacheForTest()
    expect(getTokens()).toBeNull()
  })

  it('存储内容结构不对(缺 refreshToken)也当作未登录', () => {
    localStorage.setItem('sky.admin.tokens', JSON.stringify({ accessToken: 'a' }))
    resetTokenCacheForTest()
    expect(getTokens()).toBeNull()
  })

  it('clearTokens 同时清内存与持久化', () => {
    setTokens(pair)
    clearTokens()
    expect(getTokens()).toBeNull()
    expect(localStorage.getItem('sky.admin.tokens')).toBeNull()

    resetTokenCacheForTest()
    expect(getTokens()).toBeNull()
  })

  it('没有 localStorage 的环境(Node 集成测试)退化为内存存储', () => {
    vi.stubGlobal('localStorage', undefined)
    resetTokenCacheForTest()

    setTokens(pair)
    expect(getAccessToken()).toBe('access-1')

    clearTokens()
    expect(getAccessToken()).toBeNull()
  })
})
