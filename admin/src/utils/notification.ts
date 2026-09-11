import type { NotificationMessage, OrderNewPayload, OrderUrgePayload } from '@/api/notificationSocket'
import { formatCents } from './money'

/**
 * 通知文案。
 *
 * 单独抽出来是因为"通知里该显示什么"是纯函数,能不依赖浏览器就测;
 * store 那边只负责"什么时候弹、弹完记什么",两部分各自好测。
 */

/** `ORDER_NEW`:来单提醒。返回 `{ title, summary }`,由 UI 决定怎么呈现 */
export function orderNewSummary(payload: OrderNewPayload): { title: string; summary: string } {
  const parts = [formatCents(payload.payAmountCents)]
  if (payload.itemCount) parts.push(`${payload.itemCount} 件商品`)
  if (payload.consignee) parts.push(payload.consignee)
  return {
    title: `来单提醒 · ${payload.orderNo}`,
    summary: parts.join(' · '),
  }
}

/** `ORDER_URGE`:顾客催单 */
export function orderUrgeSummary(payload: OrderUrgePayload): { title: string; summary: string } {
  const message = payload.message?.trim()
  return {
    title: `顾客催单 · ${payload.orderNo}`,
    summary: message && message !== '' ? message : '顾客催单,请尽快处理',
  }
}

/** 通知的通用描述:通知中心列表里的一行 */
export function describeNotification(message: NotificationMessage): { title: string; summary: string } {
  if (message.type === 'ORDER_NEW') {
    return orderNewSummary(message.payload as unknown as OrderNewPayload)
  }
  if (message.type === 'ORDER_URGE') {
    return orderUrgeSummary(message.payload as unknown as OrderUrgePayload)
  }
  return { title: '连接心跳', summary: '' }
}

/** 从通知里取订单 id(点通知要跳订单详情) */
export function notificationOrderId(message: NotificationMessage): number | null {
  const value = (message.payload as { orderId?: unknown }).orderId
  return typeof value === 'number' ? value : null
}
