// 分类相关类型

import type { PageQuery } from './common'

// 新增/编辑分类
export interface CategoryDTO {
  id?: number
  // 1 菜品分类 2 套餐分类
  type: number
  name: string
  sort?: number
}

// 分类分页查询
export interface CategoryPageQueryDTO extends PageQuery {
  // 分类类型 1菜品分类 2套餐分类
  type?: number
}

// 分类实体
export interface Category {
  id: number
  // 1 菜品分类 2 套餐分类
  type: number
  name: string
  sort: number
  // 0 禁用 1 启用
  status: number
  createTime?: string
  updateTime?: string
  createUser?: number
  updateUser?: number
}
