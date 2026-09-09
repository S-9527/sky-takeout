import request from '@/utils/request'
import type { Category, CategoryDTO, CategoryPageQueryDTO, PageResult } from './types'
/**
 *
 * 分类管理
 *
 **/

// 分类分页查询
export const getCategoryPage = (params: CategoryPageQueryDTO) => {
  return request.get<PageResult<Category>>('/category/page', { params })
}

// 删除分类
export const deleCategory = (id: number) => {
  return request.delete('/category', { params: { id } })
}

// 修改分类
export const editCategory = (params: CategoryDTO) => {
  return request.put('/category', params)
}

// 新增分类
export const addCategory = (params: CategoryDTO) => {
  return request.post('/category', params)
}

// 分类下拉数据(按类型取启用的分类)
export const getCategoryList = (params: { type: number }) => {
  return request.get<Category[]>('/category/list', { params })
}

// 启用禁用分类
export const enableOrDisableCategory = (params: { status: number; id: number }) => {
  return request.post(`/category/status/${params.status}`, undefined, {
    params: { id: params.id }
  })
}