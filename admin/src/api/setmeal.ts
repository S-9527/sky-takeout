import type { EnabledFlag, PagedSetmeal, QueryOf, SetmealDetail, SetmealItem } from '@/types'

import { del, get, patch, post, put } from './request'

export type SetmealPageQuery = QueryOf<'pageSetmeals'>

export function pageSetmeals(query: SetmealPageQuery): Promise<PagedSetmeal> {
  return get('/api/v1/admin/setmeals', { params: query })
}

export function getSetmeal(id: number): Promise<SetmealDetail> {
  return get(`/api/v1/admin/setmeals/${id}`)
}

export type SetmealCreateBody = {
  categoryId: number
  name: string
  priceCents: number
  imageUrl?: string
  description?: string
  status?: EnabledFlag
  items: SetmealItem[]
}

export function createSetmeal(body: SetmealCreateBody): Promise<SetmealDetail> {
  return post('/api/v1/admin/setmeals', body)
}

export type SetmealUpdateBody = {
  categoryId: number
  name: string
  priceCents: number
  imageUrl?: string
  description?: string
  status: EnabledFlag
  /** 出现即整体替换组成明细;空数组同样 422 `SETMEAL_ITEMS_EMPTY` */
  items?: SetmealItem[]
}

export function updateSetmeal(id: number, body: SetmealUpdateBody): Promise<SetmealDetail> {
  return put(`/api/v1/admin/setmeals/${id}`, body)
}

export function deleteSetmeals(ids: number[]): Promise<void> {
  return del<void>('/api/v1/admin/setmeals', { params: { ids: ids.join(',') } })
}

export function changeSetmealsStatus(ids: number[], status: EnabledFlag): Promise<void> {
  return patch<void>('/api/v1/admin/setmeals/status', { ids, status })
}
