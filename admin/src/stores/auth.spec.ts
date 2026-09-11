import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as employeeApi from '@/api/employee'
import { clearTokens, getTokens, setTokens } from '@/api/tokens'
import type { Employee, TokenPair } from '@/types'

import { useAuthStore } from './auth'

vi.mock('@/api/employee', () => ({
  login: vi.fn(),
  logout: vi.fn(),
  fetchCurrentEmployee: vi.fn(),
  changePassword: vi.fn(),
  // 其余导出(分页/增删改)与本用例无关,但 store 的模块依赖需要它们存在
  pageEmployees: vi.fn(),
  getEmployee: vi.fn(),
  createEmployee: vi.fn(),
  updateEmployee: vi.fn(),
  changeEmployeeStatus: vi.fn(),
  refresh: vi.fn(),
}))

const mocked = vi.mocked(employeeApi)

const pair: TokenPair = {
  accessToken: 'access-1',
  refreshToken: 'refresh-1',
  expiresIn: 7200,
  refreshExpiresIn: 604800,
  tokenType: 'Bearer',
}

const admin: Employee = { id: 1, username: 'admin', name: '管理员', role: 'ADMIN', status: 1 }
const staff: Employee = { id: 2, username: 'zhangsan', name: '张三', role: 'STAFF', status: 1 }

beforeEach(() => {
  setActivePinia(createPinia())
  clearTokens()
  vi.clearAllMocks()
  localStorage.clear()
})

describe('登录', () => {
  it('登录成功:保存令牌 + 拉资料 + 标记已认证', async () => {
    mocked.login.mockResolvedValue(pair)
    mocked.fetchCurrentEmployee.mockResolvedValue(admin)

    const auth = useAuthStore()
    await auth.login('admin', '123456')

    expect(mocked.login).toHaveBeenCalledWith({ username: 'admin', password: '123456' })
    expect(getTokens()?.accessToken).toBe('access-1')
    expect(auth.authenticated).toBe(true)
    expect(auth.profile?.username).toBe('admin')
    expect(auth.isAdmin).toBe(true)
    expect(auth.displayName).toBe('管理员')
  })

  it('登录接口失败:不留下任何令牌', async () => {
    mocked.login.mockRejectedValue(new Error('AUTH_BAD_CREDENTIALS'))

    const auth = useAuthStore()
    await expect(auth.login('admin', 'wrong')).rejects.toThrow()

    expect(getTokens()).toBeNull()
    expect(auth.authenticated).toBe(false)
    expect(auth.profile).toBeNull()
  })

  it('令牌拿到了但资料拉不到:同样视为登录失败', async () => {
    mocked.login.mockResolvedValue(pair)
    mocked.fetchCurrentEmployee.mockRejectedValue(new Error('boom'))

    const auth = useAuthStore()
    await expect(auth.login('admin', '123456')).rejects.toThrow()

    expect(getTokens()).toBeNull()
    expect(auth.authenticated).toBe(false)
  })
})

describe('登出', () => {
  it('先请后端撤销 refresh token,再清本地', async () => {
    setTokens(pair)
    mocked.logout.mockResolvedValue(undefined)
    const auth = useAuthStore()
    auth.authenticated = true
    auth.profile = admin

    await auth.logout()

    expect(mocked.logout).toHaveBeenCalledWith('refresh-1')
    expect(getTokens()).toBeNull()
    expect(auth.authenticated).toBe(false)
    expect(auth.profile).toBeNull()
  })

  it('撤销请求失败也必须登出(用户点了登出就得登出)', async () => {
    setTokens(pair)
    mocked.logout.mockRejectedValue(new Error('network'))

    const auth = useAuthStore()
    auth.authenticated = true
    await auth.logout()

    expect(getTokens()).toBeNull()
    expect(auth.authenticated).toBe(false)
  })

  it('没有 refresh token 时不调接口', async () => {
    const auth = useAuthStore()
    auth.authenticated = true
    await auth.logout()
    expect(mocked.logout).not.toHaveBeenCalled()
  })
})

describe('资料与会话', () => {
  it('loadProfile 拉取当前员工并标记已认证', async () => {
    mocked.fetchCurrentEmployee.mockResolvedValue(staff)
    const auth = useAuthStore()
    await auth.loadProfile()
    expect(auth.profile?.role).toBe('STAFF')
    expect(auth.isAdmin).toBe(false)
    expect(auth.displayName).toBe('张三')
  })

  it('clearSession 清空令牌与资料', () => {
    setTokens(pair)
    const auth = useAuthStore()
    auth.profile = admin
    auth.authenticated = true

    auth.clearSession()

    expect(getTokens()).toBeNull()
    expect(auth.authenticated).toBe(false)
    expect(auth.profile).toBeNull()
  })

  it('改密码成功后会话被清掉(后端撤销了所有 refresh token)', async () => {
    setTokens(pair)
    mocked.changePassword.mockResolvedValue(undefined)
    const auth = useAuthStore()
    auth.authenticated = true
    auth.profile = admin

    await auth.changePassword('old', 'new')

    expect(mocked.changePassword).toHaveBeenCalledWith({ oldPassword: 'old', newPassword: 'new' })
    expect(getTokens()).toBeNull()
    expect(auth.authenticated).toBe(false)
  })
})
