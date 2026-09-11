import type { OrderStatus, PayStatus } from '@/types'

/**
 * 顾客端状态字典。
 *
 * 与后端 `docs/01-domain.md` 的用词保持一致,也与管理端同一套中文名 ——
 * 顾客看到的"待接单"和商家看到的必须是同一个词,否则客服沟通时两边对不上。
 */

export interface DictItem {
  label: string
  /** 主题色,供页面自行决定怎么用(H5 用 class,小程序用 style) */
  tone: 'primary' | 'success' | 'warning' | 'danger' | 'muted'
}

export const ORDER_STATUS_DICT: Record<OrderStatus, DictItem> = {
  PENDING_PAYMENT: { label: '待付款', tone: 'warning' },
  PENDING_ACCEPTANCE: { label: '待接单', tone: 'danger' },
  ACCEPTED: { label: '已接单', tone: 'primary' },
  DELIVERING: { label: '派送中', tone: 'primary' },
  COMPLETED: { label: '已完成', tone: 'success' },
  CANCELLED: { label: '已取消', tone: 'muted' },
}

export const ORDER_STATUS_TABS: { value: OrderStatus | 'ALL'; label: string }[] = [
  { value: 'ALL', label: '全部' },
  { value: 'PENDING_PAYMENT', label: '待付款' },
  { value: 'PENDING_ACCEPTANCE', label: '待接单' },
  { value: 'ACCEPTED', label: '已接单' },
  { value: 'DELIVERING', label: '派送中' },
  { value: 'COMPLETED', label: '已完成' },
]

export const PAY_STATUS_DICT: Record<PayStatus, DictItem> = {
  UNPAID: { label: '未支付', tone: 'warning' },
  PAID: { label: '已支付', tone: 'success' },
  REFUNDED: { label: '已退款', tone: 'muted' },
  PARTIAL_REFUNDED: { label: '部分退款', tone: 'warning' },
}

export function orderStatusLabel(status: string | null | undefined): string {
  if (!status) return '未知状态'
  return ORDER_STATUS_DICT[status as OrderStatus]?.label ?? '未知状态'
}

export function orderStatusTone(status: string | null | undefined): DictItem['tone'] {
  if (!status) return 'muted'
  return ORDER_STATUS_DICT[status as OrderStatus]?.tone ?? 'muted'
}

export function payStatusLabel(status: string | null | undefined): string {
  if (!status) return '-'
  return PAY_STATUS_DICT[status as PayStatus]?.label ?? '-'
}

/** 订单是否还能取消:仅待付款/待接单/已接单(派送中已出餐,只能走售后) */
export function canCancelOrder(status: string | null | undefined): boolean {
  return status === 'PENDING_PAYMENT' || status === 'PENDING_ACCEPTANCE' || status === 'ACCEPTED'
}

/** 订单是否还需要支付 */
export function canPayOrder(order: { status?: string | null; payStatus?: string | null }): boolean {
  return order.status === 'PENDING_PAYMENT' && order.payStatus === 'UNPAID'
}

/** 催单:待付款与终态不支持 */
export function canRemindOrder(status: string | null | undefined): boolean {
  return status === 'PENDING_ACCEPTANCE' || status === 'ACCEPTED' || status === 'DELIVERING'
}

/** 再来一单:已完成/已取消才有意义 */
export function canReorder(status: string | null | undefined): boolean {
  return status === 'COMPLETED' || status === 'CANCELLED'
}
