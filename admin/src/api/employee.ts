import type { Employee, PagedEmployee, QueryOf, TokenPair } from '@/types'

import { get, patch, post, put } from './request'

type LoginBody = {
  username: string
  password: string
}

/**
 * 员工登录。
 *
 * 注意:本接口**只返回令牌对**,不返回员工资料 ——
 * 资料要另外调 `fetchCurrentEmployee()`(`GET /admin/auth/me`)。
 * 早年的实现把资料塞进登录响应,结果是"资料字段一变,登录接口就得跟着动"。
 */
export function login(body: LoginBody): Promise<TokenPair> {
  return post<TokenPair>('/api/v1/admin/auth/login', body)
}

/** 登出:撤销 refresh token(幂等) */
export function logout(refreshToken: string): Promise<void> {
  return post<void>('/api/v1/admin/auth/logout', { refreshToken })
}

/** 刷新令牌(应用正常流程走 http 拦截器的单飞刷新,这里主要给"主动续期"用) */
export function refresh(refreshToken: string): Promise<TokenPair> {
  return post<TokenPair>('/api/v1/admin/auth/refresh', { refreshToken })
}

export function fetchCurrentEmployee(): Promise<Employee> {
  return get<Employee>('/api/v1/admin/auth/me')
}

export function changePassword(body: { oldPassword: string; newPassword: string }): Promise<void> {
  return put<void>('/api/v1/admin/auth/password', body)
}

/* ------------------------------------------------------------------ 员工管理 */

export type EmployeePageQuery = QueryOf<'pageEmployees'>

export function pageEmployees(query: EmployeePageQuery): Promise<PagedEmployee> {
  return get('/api/v1/admin/employees', { params: query })
}

export function getEmployee(id: number): Promise<Employee> {
  return get(`/api/v1/admin/employees/${id}`)
}

export type EmployeeCreateBody = {
  username: string
  password: string
  name: string
  phone?: string
  role: Employee['role']
}

export function createEmployee(body: EmployeeCreateBody): Promise<Employee> {
  return post('/api/v1/admin/employees', body)
}

export type EmployeeUpdateBody = {
  name: string
  phone?: string
  role: Employee['role']
  status: Employee['status']
}

export function updateEmployee(id: number, body: EmployeeUpdateBody): Promise<Employee> {
  return put(`/api/v1/admin/employees/${id}`, body)
}

export function changeEmployeeStatus(id: number, status: Employee['status']): Promise<void> {
  return patch<void>(`/api/v1/admin/employees/${id}/status`, { status })
}
