import type { ShopStatus } from '@/types'

import { get, put } from './request'

export function getShopStatus(): Promise<ShopStatus> {
  return get<ShopStatus>('/api/v1/admin/shop/status')
}

export type ShopStatusUpdateBody = {
  isOpen: boolean
  openTime?: string | null
  closeTime?: string | null
  notice?: string | null
}

export function updateShopStatus(body: ShopStatusUpdateBody): Promise<ShopStatus> {
  return put<ShopStatus>('/api/v1/admin/shop/status', body)
}
