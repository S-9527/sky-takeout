import request from '@/utils/request'
import type {
  Category,
  Dish,
  DishDTO,
  DishPageQueryDTO,
  DishVO,
  PageResult,
} from './types'
/**
 *
 * 菜品管理
 *
 **/
// 菜品分页查询
export const getDishPage = (params: DishPageQueryDTO) => {
  return request.get<PageResult<DishVO>>('/dish/page', { params })
}

// 批量删除菜品
export const deleteDish = (ids: string | number) => {
  return request.delete('/dish', { params: { ids } })
}

// 编辑菜品
export const editDish = (params: DishDTO) => {
  return request.put('/dish', params)
}

// 新增菜品
export const addDish = (params: DishDTO) => {
  return request.post('/dish', params)
}

// 根据id查询菜品
export const queryDishById = (id: number) => {
  return request.get<DishVO>(`/dish/${id}`)
}

// 获取菜品分类列表
export const getCategoryList = (params: { type: number }) => {
  return request.get<Category[]>('/category/list', { params })
}

// 根据分类id/名称查询菜品列表
export const queryDishList = (params: { categoryId?: number; name?: string }) => {
  return request.get<Dish[]>('/dish/list', { params })
}

// 起售停售
export const dishStatusByStatus = (params: {
  status: number
  id: number | string
}) => {
  return request.post(`/dish/status/${params.status}`, undefined, {
    params: { id: params.id }
  })
}

// 菜品分类数据查询
export const dishCategoryList = (params: { type: number }) => {
  return request.get<Category[]>('/category/list', { params: { ...params } })
}