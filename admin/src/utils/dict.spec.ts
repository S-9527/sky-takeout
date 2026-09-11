import { describe, expect, it } from 'vitest'

import {
  canAccept,
  canCancel,
  canComplete,
  canDeliver,
  canRefund,
  canReject,
  cancelSideLabel,
  employeeRoleLabel,
  orderStatusLabel,
  orderStatusTag,
  ORDER_STATUS_OPTIONS,
  payStatusLabel,
  refundReasonTypeLabel,
  refundStatusLabel,
} from './dict'

describe('状态字典', () => {
  it('订单状态覆盖状态机的全部六个状态', () => {
    expect(ORDER_STATUS_OPTIONS.map((item) => item.value)).toEqual([
      'PENDING_PAYMENT',
      'PENDING_ACCEPTANCE',
      'ACCEPTED',
      'DELIVERING',
      'COMPLETED',
      'CANCELLED',
    ])
  })

  it('中文名与标签色', () => {
    expect(orderStatusLabel('PENDING_ACCEPTANCE')).toBe('待接单')
    expect(orderStatusTag('PENDING_ACCEPTANCE')).toBe('danger')
    expect(orderStatusLabel('COMPLETED')).toBe('已完成')
    expect(orderStatusTag('COMPLETED')).toBe('success')
  })

  it('未知状态不抛异常,降级为"未知状态"', () => {
    expect(orderStatusLabel('WHAT')).toBe('未知状态')
    expect(orderStatusTag('WHAT')).toBe('info')
    expect(orderStatusLabel(null)).toBe('未知状态')
  })

  it('支付/退款/取消方/员工角色', () => {
    expect(payStatusLabel('PARTIAL_REFUNDED')).toBe('部分退款')
    expect(refundStatusLabel('SUCCESS')).toBe('退款成功')
    expect(cancelSideLabel('SYSTEM')).toBe('系统关闭')
    expect(employeeRoleLabel('ADMIN')).toBe('管理员')
    expect(refundReasonTypeLabel('MERCHANT_REJECT')).toBe('商家拒单')
    expect(refundReasonTypeLabel(undefined)).toBe('-')
  })
})

describe('可执行动作 —— 与后端状态机一致', () => {
  it('接单/拒单仅限待接单', () => {
    expect(canAccept({ status: 'PENDING_ACCEPTANCE' })).toBe(true)
    expect(canAccept({ status: 'ACCEPTED' })).toBe(false)
    expect(canReject({ status: 'PENDING_ACCEPTANCE' })).toBe(true)
    expect(canReject({ status: 'DELIVERING' })).toBe(false)
  })

  it('派送/完成各自只认前一个状态', () => {
    expect(canDeliver({ status: 'ACCEPTED' })).toBe(true)
    expect(canDeliver({ status: 'DELIVERING' })).toBe(false)
    expect(canComplete({ status: 'DELIVERING' })).toBe(true)
    expect(canComplete({ status: 'COMPLETED' })).toBe(false)
  })

  it('派送中不可取消(已出餐,只能走售后)', () => {
    expect(canCancel({ status: 'PENDING_ACCEPTANCE' })).toBe(true)
    expect(canCancel({ status: 'ACCEPTED' })).toBe(true)
    expect(canCancel({ status: 'DELIVERING' })).toBe(false)
    expect(canCancel({ status: 'COMPLETED' })).toBe(false)
  })

  it('退款要求已支付且未完成(R8)', () => {
    expect(canRefund({ status: 'CANCELLED', payStatus: 'PAID' })).toBe(true)
    expect(canRefund({ status: 'PENDING_ACCEPTANCE', payStatus: 'PAID' })).toBe(true)
    expect(canRefund({ status: 'COMPLETED', payStatus: 'PAID' })).toBe(false)
    expect(canRefund({ status: 'CANCELLED', payStatus: 'UNPAID' })).toBe(false)
    expect(canRefund({ status: 'CANCELLED', payStatus: 'REFUNDED' })).toBe(false)
  })
})
