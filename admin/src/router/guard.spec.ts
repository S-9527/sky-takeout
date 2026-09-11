import type { RouteLocationNormalized } from 'vue-router'
import { describe, expect, it, vi } from 'vitest'

import type { Employee } from '@/types'

import { resolveGuard, type GuardAuthState } from './guard'

function route(meta: RouteLocationNormalized['meta'], fullPath = '/orders'): RouteLocationNormalized {
  return { meta, fullPath, name: 'orders' } as unknown as RouteLocationNormalized
}

function loginRoute(): RouteLocationNormalized {
  return { meta: { public: true, title: '登录' }, fullPath: '/login', name: 'login' } as unknown as RouteLocationNormalized
}

function authState(partial: Partial<GuardAuthState> = {}): GuardAuthState {
  return {
    authenticated: false,
    profile: null,
    loadProfile: vi.fn(async () => employee('STAFF')),
    clearSession: vi.fn(),
    ...partial,
  }
}

function employee(role: Employee['role']): Employee {
  return {
    id: 1,
    username: role === 'ADMIN' ? 'admin' : 'zhangsan',
    name: role === 'ADMIN' ? '管理员' : '张三',
    role,
    status: 1,
  }
}

describe('免登录页面', () => {
  it('未登录可以直接进登录页', async () => {
    await expect(resolveGuard(loginRoute(), authState())).resolves.toBe(true)
  })

  it('已登录再点登录页会被送回工作台', async () => {
    const result = await resolveGuard(loginRoute(), authState({ authenticated: true }))
    expect(result).toEqual({ name: 'workbench' })
  })

  it('404 等公开页面不拦截', async () => {
    const notFound = route({ public: true, title: '页面不存在' }, '/nope')
    await expect(resolveGuard(notFound, authState())).resolves.toBe(true)
  })
})

describe('登录校验', () => {
  it('未登录访问业务页 → 跳登录并记住原地址', async () => {
    const result = await resolveGuard(
      route({ title: '订单管理' }, '/orders?status=ACCEPTED'),
      authState(),
    )
    expect(result).toEqual({ name: 'login', query: { redirect: '/orders?status=ACCEPTED' } })
  })

  it('已登录但内存里没有资料(刷新页面)→ 先拉资料', async () => {
    const loadProfile = vi.fn(async () => employee('STAFF'))
    const result = await resolveGuard(
      route({ title: '订单管理' }),
      authState({ authenticated: true, loadProfile }),
    )
    expect(loadProfile).toHaveBeenCalledTimes(1)
    expect(result).toBe(true)
  })

  it('拉资料失败(令牌失效且刷新失败)→ 清会话回登录页', async () => {
    const clearSession = vi.fn()
    const loadProfile = vi.fn(async () => {
      throw new Error('401')
    })
    const result = await resolveGuard(
      route({ title: '订单管理' }, '/dishes'),
      authState({ authenticated: true, loadProfile, clearSession }),
    )
    expect(clearSession).toHaveBeenCalledTimes(1)
    expect(result).toEqual({ name: 'login', query: { redirect: '/dishes' } })
  })
})

describe('角色校验', () => {
  it('ADMIN 页面拒绝 STAFF', async () => {
    const result = await resolveGuard(
      route({ title: '员工管理', roles: ['ADMIN'] }),
      authState({ authenticated: true, profile: employee('STAFF') }),
    )
    expect(result).toEqual({ name: 'forbidden' })
  })

  it('ADMIN 可以进', async () => {
    const result = await resolveGuard(
      route({ title: '员工管理', roles: ['ADMIN'] }),
      authState({ authenticated: true, profile: employee('ADMIN') }),
    )
    expect(result).toBe(true)
  })

  it('没有声明 roles 的页面所有员工都能进', async () => {
    const result = await resolveGuard(
      route({ title: '订单管理' }),
      authState({ authenticated: true, profile: employee('STAFF') }),
    )
    expect(result).toBe(true)
  })

  it('刷新后直接深链管理员页面:资料是守卫里才拉到的,也要放行', async () => {
    // 回归用例:曾经这里用的是进函数时的快照(profile=null),导致管理员刷新 /employees 被拦到 403
    const result = await resolveGuard(
      route({ title: '员工管理', roles: ['ADMIN'] }, '/employees'),
      authState({
        authenticated: true,
        profile: null,
        loadProfile: vi.fn(async () => employee('ADMIN')),
      }),
    )
    expect(result).toBe(true)
  })

  it('资料里没有角色时也按无权限处理', async () => {
    const result = await resolveGuard(
      route({ title: '员工管理', roles: ['ADMIN'] }),
      authState({ authenticated: true, profile: { ...employee('STAFF'), role: undefined as never } }),
    )
    expect(result).toEqual({ name: 'forbidden' })
  })
})
