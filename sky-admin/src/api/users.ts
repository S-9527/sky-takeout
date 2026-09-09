import request from '@/utils/request'
import type { PasswordEditDTO, ShopStatus } from './types'
// 修改密码
export const editPassword = (data: PasswordEditDTO) =>
  request.put('/employee/editPassword', data)
// 获取营业状态
export const getStatus = () =>
  request.get<ShopStatus>('/shop/status')
// 设置营业状态
export const setStatus = (status: ShopStatus) =>
  request.put(`/shop/${status}`)