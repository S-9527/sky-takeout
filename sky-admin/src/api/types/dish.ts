// 菜品相关类型

import type { PageQuery } from './common'

// 菜品口味
export interface DishFlavor {
  id?: number
  dishId?: number
  name: string
  value: string
}

// 新增/编辑菜品
export interface DishDTO {
  id?: number
  name: string
  categoryId: number
  price: number
  image: string
  // 商品码
  code?: string
  description?: string
  // 0 停售 1 起售
  status?: number
  flavors: DishFlavor[]
}

// 菜品分页查询
export interface DishPageQueryDTO extends PageQuery {
  categoryId?: number
  // 0 停售 1 起售
  status?: number
}

// 菜品回显
export interface DishVO extends DishDTO {
  updateTime?: string
  categoryName?: string
}

// 菜品实体（列表数据）
export interface Dish {
  id: number
  name: string
  categoryId: number
  price: number
  image: string
  // 商品码
  code?: string
  description?: string
  // 0 停售 1 起售
  status: number
  createTime?: string
  updateTime?: string
  createUser?: number
  updateUser?: number
}
