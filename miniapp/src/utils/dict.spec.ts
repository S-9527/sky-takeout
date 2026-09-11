import { describe, expect, it } from 'vitest'

import {
  canCancelOrder,
  canPayOrder,
  canRemindOrder,
  canReorder,
  orderStatusLabel,
  orderStatusTone,
  ORDER_STATUS_TABS,
  payStatusLabel,
} from './dict'

describe('顾客端状态字典', () => {
  it('中文名与管理端一致', () => {
    expect(orderStatusLabel('PENDING_ACCEPTANCE')).toBe('待接单')
    expect(orderStatusLabel('DELIVERING')).toBe('派送中')
    expect(orderStatusTone('CANCELLED')).toBe('muted')
    expect(orderStatusLabel('WHAT')).toBe('未知状态')
    expect(payStatusLabel('REFUNDED')).toBe('已退款')
  })

  it('订单页签覆盖待付款到已完成', () => {
    expect(ORDER_STATUS_TABS.map((tab) => tab.value)).toEqual([
      'ALL',
      'PENDING_PAYMENT',
      'PENDING_ACCEPTANCE',
      'ACCEPTED',
      'DELIVERING',
      'COMPLETED',
    ])
  })

  it('可执行动作与后端状态机一致', () => {
    expect(canCancelOrder('PENDING_PAYMENT')).toBe(true)
    expect(canCancelOrder('DELIVERING')).toBe(false)
    expect(canPayOrder({ status: 'PENDING_PAYMENT', payStatus: 'UNPAID' })).toBe(true)
    expect(canPayOrder({ status: 'PENDING_PAYMENT', payStatus: 'PAID' })).toBe(false)
    expect(canRemindOrder('ACCEPTED')).toBe(true)
    expect(canRemindOrder('PENDING_PAYMENT')).toBe(false)
    expect(canReorder('COMPLETED')).toBe(true)
    expect(canReorder('DELIVERING')).toBe(false)
  })
})
