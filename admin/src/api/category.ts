import type { Category, CategoryType, EnabledFlag, PagedCategory, QueryOf } from '@/types'

import { del, get, patch, post, put } from './request'

export type CategoryPageQuery = QueryOf<'pageCategories'>

export function pageCategories(query: CategoryPageQuery): Promise<PagedCategory> {
  return get('/api/v1/admin/categories', { params: query })
}

/** 下拉选项:不分页,默认只给启用中的分类 */
export function listCategoryOptions(
  type: CategoryType,
  includeDisabled = false,
): Promise<Category[]> {
  return get('/api/v1/admin/categories/options', { params: { type, includeDisabled } })
}

export function getCategory(id: number): Promise<Category> {
  return get(`/api/v1/admin/categories/${id}`)
}

export type CategoryCreateBody = {
  name: string
  type: CategoryType
  sortOrder: number
  status?: EnabledFlag
}

export function createCategory(body: CategoryCreateBody): Promise<Category> {
  return post('/api/v1/admin/categories', body)
}

export type CategoryUpdateBody = {
  name: string
  /** 类型不可改;不传表示保持不变,传了必须与当前一致 */
  type?: CategoryType
  sortOrder: number
  status: EnabledFlag
}

export function updateCategory(id: number, body: CategoryUpdateBody): Promise<Category> {
  return put(`/api/v1/admin/categories/${id}`, body)
}

/** 真删除;被菜品/套餐引用时 422 `CATEGORY_IN_USE` */
export function deleteCategory(id: number): Promise<void> {
  return del<void>(`/api/v1/admin/categories/${id}`)
}

export function changeCategoryStatus(id: number, status: EnabledFlag): Promise<void> {
  return patch<void>(`/api/v1/admin/categories/${id}/status`, { status })
}
