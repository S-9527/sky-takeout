import { describe, expect, it, vi } from 'vitest'

import { uniTestDouble } from '@/tests/setup'

import {
  DEV_LOGIN_CODE,
  runtimePlatform,
  runtimeRequest,
  runtimeStorageGet,
  runtimeStorageRemove,
  runtimeStorageSet,
  runtimeWechatLoginCode,
  setRuntimeForTest,
} from './runtime'

/**
 * 适配层的职责就两条:
 * 1. 生产调用点永远是裸 `uni.xxx`(由 uni-app 编译器按平台重写);
 * 2. 单测经 `setRuntimeForTest()` 注入替身,不依赖 jsdom 里的全局。
 */
describe('runtime 适配层', () => {
  it('请求与存储走注入的替身', () => {
    const request = vi.fn()
    const store = new Map<string, unknown>()
    setRuntimeForTest({
      request,
      storage: {
        get: (key) => store.get(key) ?? '',
        set: (key, value) => {
          store.set(key, value)
        },
        remove: (key) => {
          store.delete(key)
        },
      },
    })

    runtimeRequest({ url: '/api/v1/ping' })
    expect(request).toHaveBeenCalledWith({ url: '/api/v1/ping' })

    runtimeStorageSet('token', 'v1')
    expect(runtimeStorageGet('token')).toBe('v1')
    runtimeStorageRemove('token')
    expect(runtimeStorageGet('token')).toBe('')
  })

  it('没有替身时回落到全局 uni', () => {
    setRuntimeForTest(null)

    runtimeRequest({ url: '/fallback' })
    expect(uniTestDouble.requests.at(-1)?.url).toBe('/fallback')

    runtimeStorageSet('k', 'v')
    expect(runtimeStorageGet('k')).toBe('v')
    runtimeStorageRemove('k')
    expect(runtimeStorageGet('k')).toBe('')
  })

  it('平台可由替身伪造,优先于 uni.getSystemInfoSync', () => {
    setRuntimeForTest({ platform: 'mp-weixin' })
    expect(runtimePlatform()).toBe('mp-weixin')

    setRuntimeForTest({ platform: 'web' })
    expect(runtimePlatform()).toBe('web')

    setRuntimeForTest({ platform: null })
    expect(runtimePlatform()).toBeUndefined()
  })

  it('没伪造平台时读 uni.getSystemInfoSync().uniPlatform,读不到就算未知', () => {
    setRuntimeForTest(null)
    const uniStub = (globalThis as { uni?: { getSystemInfoSync?: () => unknown } }).uni
    expect(uniStub).toBeDefined()

    uniStub!.getSystemInfoSync = () => ({ uniPlatform: 'mp-weixin' })
    expect(runtimePlatform()).toBe('mp-weixin')

    uniStub!.getSystemInfoSync = () => {
      throw new Error('getSystemInfoSync:fail')
    }
    expect(runtimePlatform()).toBeUndefined()

    delete uniStub!.getSystemInfoSync
    expect(runtimePlatform()).toBeUndefined()
  })

  it('非小程序平台直接给开发 code', async () => {
    setRuntimeForTest({ platform: 'web' })
    await expect(runtimeWechatLoginCode()).resolves.toBe(DEV_LOGIN_CODE)

    // 平台未知(H5 发行摇树、单测默认环境)同样走开发 code,而不是误调 uni.login
    setRuntimeForTest({ platform: null })
    await expect(runtimeWechatLoginCode()).resolves.toBe(DEV_LOGIN_CODE)
  })

  it('微信小程序调 uni.login 取一次性 code', async () => {
    const login = vi.fn((options: { success?: (result: { code?: string }) => void }) => {
      options.success?.({ code: 'wx-code-1' })
    })
    setRuntimeForTest({ platform: 'mp-weixin', wechatLogin: login })

    await expect(runtimeWechatLoginCode()).resolves.toBe('wx-code-1')
    expect(login).toHaveBeenCalledOnce()
    expect(login.mock.calls[0][0]).toMatchObject({ provider: 'weixin' })
  })

  it('小程序登录失败或没返回 code 都要 reject,不静默降级', async () => {
    setRuntimeForTest({ platform: 'mp-weixin', wechatLogin: (options) => options.fail?.() })
    await expect(runtimeWechatLoginCode()).rejects.toThrow('微信登录取消或失败')

    setRuntimeForTest({ platform: 'mp-weixin', wechatLogin: (options) => options.success?.({}) })
    await expect(runtimeWechatLoginCode()).rejects.toThrow('微信未返回 code')
  })
})
