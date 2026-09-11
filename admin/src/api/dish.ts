import type { DishDetail, DishFlavor, EnabledFlag, PagedDish, QueryOf } from '@/types'

import { del, get, patch, post, put } from './request'

export type DishPageQuery = QueryOf<'pageDishes'>

export function pageDishes(query: DishPageQuery): Promise<PagedDish> {
  return get('/api/v1/admin/dishes', { params: query })
}

/** 详情(含口味配置),编辑页回填用 */
export function getDish(id: number): Promise<DishDetail> {
  return get(`/api/v1/admin/dishes/${id}`)
}

export type DishCreateBody = {
  categoryId: number
  name: string
  priceCents: number
  imageUrl?: string
  description?: string
  status?: EnabledFlag
  sortOrder: number
  flavors?: DishFlavor[]
}

export function createDish(body: DishCreateBody): Promise<DishDetail> {
  return post('/api/v1/admin/dishes', body)
}

export type DishUpdateBody = {
  categoryId: number
  name: string
  priceCents: number
  imageUrl?: string
  description?: string
  status: EnabledFlag
  sortOrder?: number
  /** 出现即整体替换口味配置 */
  flavors?: DishFlavor[]
}

export function updateDish(id: number, body: DishUpdateBody): Promise<DishDetail> {
  return put(`/api/v1/admin/dishes/${id}`, body)
}

/** 批量删除;`ids` 是逗号分隔字符串,任一不存在返回 404 */
export function deleteDishes(ids: number[]): Promise<void> {
  return del<void>('/api/v1/admin/dishes', { params: { ids: ids.join(',') } })
}

/** 批量起售/停售;停售会连带停售包含它的套餐 */
export function changeDishesStatus(ids: number[], status: EnabledFlag): Promise<void> {
  return patch<void>('/api/v1/admin/dishes/status', { ids, status })
}
