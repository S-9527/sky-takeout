import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type RouteRecordRaw } from 'vue-router'

import { fetchCurrentEmployee } from '@/api/employee'
import { clearTokens, setTokens } from '@/api/tokens'
import type { Employee } from '@/types'

import { installGuards } from './guard'

vi.mock('@/api/employee', () => ({
  fetchCurrentEmployee: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  changePassword: vi.fn(),
  pageEmployees: vi.fn(),
  getEmployee: vi.fn(),
  createEmployee: vi.fn(),
  updateEmployee: vi.fn(),
  changeEmployeeStatus: vi.fn(),
  refresh: vi.fn(),
}))

const mockedFetch = vi.mocked(fetchCurrentEmployee)

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: { template: '<div />' },
    meta: { title: '登录', public: true },
  },
  { path: '/workbench', name: 'workbench', component: { template: '<div />' }, meta: { title: '工作台' } },
  {
    path: '/employees',
    name: 'employees',
    component: { template: '<div />' },
    meta: { title: '员工管理', roles: ['ADMIN'] },
  },
  { path: '/forbidden', name: 'forbidden', component: { template: '<div />' }, meta: { title: '无访问权限' } },
]

function createTestRouter() {
  const router = createRouter({ history: createMemoryHistory(), routes })
  installGuards(router)
  return router
}

function login(role: Employee['role']): void {
  setTokens({
    accessToken: 'a',
    refreshToken: 'r',
    expiresIn: 7200,
    refreshExpiresIn: 604800,
    tokenType: 'Bearer',
  })
  mockedFetch.mockResolvedValue({ id: 1, username: 'u', name: '某人', role, status: 1 })
}

beforeEach(() => {
  setActivePinia(createPinia())
  clearTokens()
  localStorage.clear()
  document.title = ''
  vi.clearAllMocks()
})

describe('installGuards', () => {
  it('未登录访问业务页被送去登录页,并带上原地址', async () => {
    const router = createTestRouter()
    await router.push('/workbench')

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/workbench')
  })

  it('公开页面直接放行', async () => {
    const router = createTestRouter()
    await router.push('/login')
    expect(router.currentRoute.value.name).toBe('login')
  })

  it('登录后补齐资料、设置浏览器标题', async () => {
    login('ADMIN')
    const router = createTestRouter()
    await router.push('/workbench')

    expect(mockedFetch).toHaveBeenCalled()
    expect(router.currentRoute.value.name).toBe('workbench')
    expect(document.title).toBe('工作台 · 苍穹外卖管理端')
  })

  it('STAFF 访问管理员页面被送到 403,不是白屏也不是死循环', async () => {
    login('STAFF')
    const router = createTestRouter()
    await router.push('/employees')

    expect(router.currentRoute.value.name).toBe('forbidden')
  })

  it('资料拉不到时清会话并回登录页', async () => {
    setTokens({
      accessToken: 'a',
      refreshToken: 'r',
      expiresIn: 7200,
      refreshExpiresIn: 604800,
      tokenType: 'Bearer',
    })
    mockedFetch.mockRejectedValue(new Error('401'))

    const router = createTestRouter()
    await router.push('/workbench')

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/workbench')
  })
})
