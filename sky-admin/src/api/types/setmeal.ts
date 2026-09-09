// 套餐相关类型

import type { PageQuery } from './common'

// 套餐菜品关系
export interface SetmealDish {
  id?: number
  setmealId?: number
  dishId: number
  name?: string
  price?: number
  // 份数
  copies: number
}

// 新增/编辑套餐
export interface SetmealDTO {
  id?: number
  categoryId: number
  name: string
  price: number
  // 0 停用 1 启用
  status?: number
  description?: string
  image: string
  setmealDishes: SetmealDish[]
}

// 套餐分页查询
export interface SetmealPageQueryDTO extends PageQuery {
  categoryId?: number
  // 0 停用 1 启用
  status?: number
}

// 套餐回显
export interface SetmealVO extends SetmealDTO {
  updateTime?: string
  categoryName?: string
}
