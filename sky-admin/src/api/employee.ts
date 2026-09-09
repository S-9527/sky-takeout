import request from '@/utils/request'
import type {
  Employee,
  EmployeeDTO,
  EmployeeLoginDTO,
  EmployeeLoginVO,
  EmployeePageQueryDTO,
  PageResult,
} from './types'
/**
 *
 * 员工管理
 *
 **/
// 登录
export const login = (data: EmployeeLoginDTO) =>
  request.post<EmployeeLoginVO>('/employee/login', data)
// 退出
export const userLogout = () => request.post<string>('/employee/logout')

// 员工分页查询
export const getEmployeeList = (params: EmployeePageQueryDTO) =>
  request.get<PageResult<Employee>>('/employee/page', { params })

// 启用禁用员工账号
export const enableOrDisableEmployee = (params: {
  status: number
  id: number
}) =>
  request.post(`/employee/status/${params.status}`, undefined, {
    params: { id: params.id }
  })

// 新增员工
export const addEmployee = (params: EmployeeDTO) =>
  request.post('/employee', params)

// 编辑员工
export const editEmployee = (params: EmployeeDTO) =>
  request.put('/employee', params)

// 根据id查询员工信息
export const queryEmployeeById = (id: number) =>
  request.get<Employee>(`/employee/${id}`)