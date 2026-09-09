import request from '@/utils/request'
import type {
  PageResult,
  SetmealDTO,
  SetmealPageQueryDTO,
  SetmealVO,
} from './types'
/**
 *
 * 套餐管理
 *
 **/

// 套餐分页查询
export const getSetmealPage = (params: SetmealPageQueryDTO) => {
  return request.get<PageResult<SetmealVO>>('/setmeal/page', { params })
}

// 批量删除套餐
export const deleteSetmeal = (ids: string | number) => {
  return request.delete('/setmeal', { params: { ids } })
}

// 编辑套餐
export const editSetmeal = (params: SetmealDTO) => {
  return request.put('/setmeal', params)
}

// 新增套餐
export const addSetmeal = (params: SetmealDTO) => {
  return request.post('/setmeal', params)
}

// 根据id查询套餐
export const querySetmealById = (id: number) => {
  return request.get<SetmealVO>(`/setmeal/${id}`)
}

// 批量起售禁售
export const setmealStatusByStatus = (params: {
  status: number
  ids: number | string
}) => {
  return request.post(`/setmeal/status/${params.status}`, undefined, {
    params: { id: params.ids }
  })
}
