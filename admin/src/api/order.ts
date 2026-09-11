import type {
  Order,
  OrderDetail,
  OrderStatusCounts,
  PagedOrder,
  QueryOf,
} from '@/types'

import { get, post } from './request'

export type OrderPageQuery = QueryOf<'pageOrders'>

export function pageOrders(query: OrderPageQuery): Promise<PagedOrder> {
  return get('/api/v1/admin/orders', { params: query })
}

/** 各状态订单数量:订单页角标与工作台都用它 */
export function countOrdersByStatus(): Promise<OrderStatusCounts> {
  return get('/api/v1/admin/orders/status-counts')
}

/** 管理端详情:含明细快照、支付与退款记录(不受顾客归属限制) */
export function getOrder(id: number): Promise<OrderDetail> {
  return get(`/api/v1/admin/orders/${id}`)
}

/**
 * 商家动作。
 *
 * 五个动作都是 `POST` + `204 No Content`,没有响应体 ——
 * 调用方拿到结果后需要**重新拉一次订单**(通知通道不可作为状态依据,契约 4.4)。
 */
export function acceptOrder(id: number): Promise<void> {
  return post<void>(`/api/v1/admin/orders/${id}/acceptance`)
}

/** 拒单:已支付订单会**强制退款**(R6),退款受理失败则整单保持原状态并返回 502 */
export function rejectOrder(id: number, reason: string): Promise<void> {
  return post<void>(`/api/v1/admin/orders/${id}/rejection`, { reason })
}

export function startOrderDelivery(id: number): Promise<void> {
  return post<void>(`/api/v1/admin/orders/${id}/delivery`)
}

export function completeOrder(id: number): Promise<void> {
  return post<void>(`/api/v1/admin/orders/${id}/completion`)
}

/** 商家取消:仅 `PENDING_ACCEPTANCE` / `ACCEPTED`;`DELIVERING` 不可取消 */
export function cancelOrderByAdmin(id: number, reason?: string): Promise<void> {
  return post<void>(`/api/v1/admin/orders/${id}/cancellation`, { reason })
}

export type { Order, OrderDetail }
