import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as employeeApi from '@/api/employee'
import { clearTokens, getTokens } from '@/api/tokens'
import type { Employee, TokenPair } from '@/types'

import Login from './Login.vue'

const replace = vi.fn()
const query: Record<string, unknown> = {}

vi.mock('vue-router', () => ({
  useRouter: () => ({ replace, push: vi.fn() }),
  useRoute: () => ({ query }),
}))

vi.mock('@/api/employee', () => ({
  login: vi.fn(),
  logout: vi.fn(),
  fetchCurrentEmployee: vi.fn(),
  changePassword: vi.fn(),
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

function mountLogin() {
  return mount(Login, {
    global: { plugins: [createPinia(), ElementPlus] },
  })
}

beforeEach(() => {
  setActivePinia(createPinia())
  clearTokens()
  localStorage.clear()
  replace.mockReset()
  vi.clearAllMocks()
  for (const key of Object.keys(query)) delete query[key]
})

describe('登录页', () => {
  it('渲染用户名、密码与提交按钮', () => {
    const wrapper = mountLogin()
    expect(wrapper.find('input[name="username"]').exists()).toBe(true)
    expect(wrapper.find('input[name="password"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="login-submit"]').exists()).toBe(true)
  })

  it('空表单提交不会打接口,并给出校验提示', async () => {
    const wrapper = mountLogin()

    await wrapper.find('[data-testid="login-submit"]').trigger('click')
    await flushPromises()

    expect(mocked.login).not.toHaveBeenCalled()
    expect(replace).not.toHaveBeenCalled()

    // Element Plus 的错误提示经过 100ms 防抖才渲染,等它一下
    await new Promise((resolve) => setTimeout(resolve, 200))
    await flushPromises()
    expect(wrapper.text()).toContain('请输入用户名')
  })

  it('填对表单后登录并跳转工作台', async () => {
    mocked.login.mockResolvedValue(pair)
    mocked.fetchCurrentEmployee.mockResolvedValue(admin)

    const wrapper = mountLogin()
    await wrapper.find('input[name="username"]').setValue('admin')
    await wrapper.find('input[name="password"]').setValue('123456')
    await wrapper.find('[data-testid="login-submit"]').trigger('click')
    await flushPromises()

    expect(mocked.login).toHaveBeenCalledWith({ username: 'admin', password: '123456' })
    expect(getTokens()?.accessToken).toBe('access-1')
    expect(replace).toHaveBeenCalledWith({ name: 'workbench' })
  })

  it('带 redirect 参数时回到原来要去的页面', async () => {
    query.redirect = '/orders?status=ACCEPTED'
    mocked.login.mockResolvedValue(pair)
    mocked.fetchCurrentEmployee.mockResolvedValue(admin)

    const wrapper = mountLogin()
    await wrapper.find('input[name="username"]').setValue('admin')
    await wrapper.find('input[name="password"]').setValue('123456')
    await wrapper.find('[data-testid="login-submit"]').trigger('click')
    await flushPromises()

    expect(replace).toHaveBeenCalledWith('/orders?status=ACCEPTED')
  })

  it('密码错误时展示后端文案,不跳转、不留令牌', async () => {
    const { ApiError } = await import('@/api/http')
    mocked.login.mockRejectedValue(
      new ApiError({ status: 401, code: 'AUTH_BAD_CREDENTIALS', message: '用户名或密码错误' }),
    )

    const wrapper = mountLogin()
    await wrapper.find('input[name="username"]').setValue('admin')
    await wrapper.find('input[name="password"]').setValue('badpass')
    await wrapper.find('[data-testid="login-submit"]').trigger('click')
    await flushPromises()

    expect(replace).not.toHaveBeenCalled()
    expect(getTokens()).toBeNull()
    expect(document.body.textContent).toContain('用户名或密码错误')
  })
})
