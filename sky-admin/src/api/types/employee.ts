// 员工相关类型

import type { PageQuery } from './common'

// 员工登录
export interface EmployeeLoginDTO {
  username: string
  password: string
}

export interface EmployeeLoginVO {
  id: number
  userName: string
  name: string
  accessToken: string
  refreshToken: string
}

// 新增/编辑员工
export interface EmployeeDTO {
  id?: number
  username: string
  name: string
  phone: string
  sex: string
  idNumber: string
}

// 员工分页查询
export type EmployeePageQueryDTO = PageQuery

// 修改密码
export interface PasswordEditDTO {
  empId: number
  oldPassword: string
  newPassword: string
}

// 员工实体
export interface Employee {
  id: number
  username: string
  name: string
  password?: string
  phone: string
  sex: string
  idNumber: string
  // 0 禁用 1 启用
  status: number
  createTime?: string
  updateTime?: string
  createUser?: number
  updateUser?: number
}
