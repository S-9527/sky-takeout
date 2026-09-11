import { describe, expect, it } from 'vitest'

import type { NotificationMessage } from '@/api/notificationSocket'

import { describeNotification, notificationOrderId, orderNewSummary, orderUrgeSummary } from './notification'

describe('来单提醒文案', () => {
  it('拼出金额、件数、收货人', () => {
    const summary = orderNewSummary({
      orderId: 4001,
      orderNo: '202501011200000001',
      payAmountCents: 10400,
      itemCount: 3,
      consignee: '张三',
    })
    expect(summary.title).toBe('来单提醒 · 202501011200000001')
    expect(summary.summary).toBe('¥104.00 · 3 件商品 · 张三')
  })

  it('缺字段时不出现空洞', () => {
    const summary = orderNewSummary({
      orderId: 1,
      orderNo: 'N1',
      payAmountCents: 500,
    })
    expect(summary.summary).toBe('¥5.00')
  })
})

describe('催单文案', () => {
  it('带顾客留言时展示留言', () => {
    expect(orderUrgeSummary({ orderId: 1, orderNo: 'N1', message: '请尽快派送' })).toEqual({
      title: '顾客催单 · N1',
      summary: '请尽快派送',
    })
  })

  it('没有留言时给一句兜底', () => {
    expect(orderUrgeSummary({ orderId: 1, orderNo: 'N1' }).summary).toBe('顾客催单,请尽快处理')
    expect(orderUrgeSummary({ orderId: 1, orderNo: 'N1', message: '   ' }).summary).toBe(
      '顾客催单,请尽快处理',
    )
  })
})

describe('describeNotification', () => {
  it('按类型分派', () => {
    const orderNew: NotificationMessage = {
      type: 'ORDER_NEW',
      messageId: 'm1',
      timestamp: '',
      payload: { orderId: 9, orderNo: 'N9', payAmountCents: 100 },
    }
    expect(describeNotification(orderNew).title).toBe('来单提醒 · N9')

    const urge: NotificationMessage = {
      type: 'ORDER_URGE',
      messageId: 'm2',
      timestamp: '',
      payload: { orderId: 9, orderNo: 'N9' },
    }
    expect(describeNotification(urge).title).toBe('顾客催单 · N9')

    const pong: NotificationMessage = { type: 'PONG', messageId: 'm3', timestamp: '', payload: {} }
    expect(describeNotification(pong)).toEqual({ title: '连接心跳', summary: '' })
  })
})

describe('notificationOrderId', () => {
  it('取出订单 id', () => {
    expect(
      notificationOrderId({ type: 'ORDER_NEW', messageId: 'm', timestamp: '', payload: { orderId: 7 } }),
    ).toBe(7)
  })

  it('载荷里没有数字 id 时返回 null(不去猜)', () => {
    expect(
      notificationOrderId({ type: 'PONG', messageId: 'm', timestamp: '', payload: { orderId: '7' } }),
    ).toBeNull()
  })
})
