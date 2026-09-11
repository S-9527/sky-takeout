import type { PagedRefund, QueryOf, Refund } from '@/types'

import { get, post } from './request'

export type RefundPageQuery = QueryOf<'pageRefunds'>

export function pageRefunds(query: RefundPageQuery): Promise<PagedRefund> {
  return get('/api/v1/admin/refunds', { params: query })
}

export type RefundCreateBody = {
  /** 订单号(顾客可见的业务单号),必填 */
  orderNo: string
  reason: string
  reasonType: 'MERCHANT_REJECT' | 'MERCHANT_CANCEL' | 'CUSTOMER_APPLY' | 'OTHER'
  /** 可选:与订单实付不一致时后端返回 422 `PAY_REFUND_AMOUNT_EXCEEDED` */
  expectedAmountCents?: number
}

/**
 * 管理端发起退款(整单全额退,**仅 ADMIN**)。
 *
 * 金额由服务端按订单 `payAmountCents` 决定,前端不传金额 ——
 * 传了也只是"对账",对不上反而报错,没有收益。
 */
export function createRefund(body: RefundCreateBody): Promise<Refund> {
  return post<Refund>('/api/v1/admin/refunds', body)
}
