import { describe, expect, it } from 'vitest'

import {
  formatCountdown,
  formatDate,
  formatDateTime,
  formatDateTimeOrEmpty,
  formatTime,
  recentDaysRange,
  remainingPayMillis,
  toDateParam,
  today,
} from './datetime'

describe('门店时区渲染', () => {
  it('永远按 Asia/Shanghai 展示,不随运行机器的时区变化', () => {
    // 2025-01-01T16:30:00Z 在东八区是 2025-01-02 00:30
    expect(formatDateTime('2025-01-01T16:30:00Z')).toBe('2025-01-02 00:30:00')
    // 带偏移的 ISO 串直接换算到门店时区
    expect(formatDateTime('2025-01-01T12:00:00+08:00')).toBe('2025-01-01 12:00:00')
    // 2025-06-01T20:00:00+08:00 → 同日 20:00
    expect(formatDateTime('2025-06-01T20:00:00+08:00')).toBe('2025-06-01 20:00:00')
  })

  it('午夜渲染为 00 而不是 24', () => {
    expect(formatDateTime('2025-01-01T00:00:00+08:00')).toBe('2025-01-01 00:00:00')
  })

  it('空值/非法值返回占位符', () => {
    expect(formatDateTime(null)).toBe('-')
    expect(formatDateTime(undefined)).toBe('-')
    expect(formatDateTime('')).toBe('-')
    expect(formatDateTime('not-a-date')).toBe('-')
    expect(formatDateTimeOrEmpty(null)).toBe('')
  })
})

describe('formatDate / formatTime / toDateParam', () => {
  it('拆出日期与时间', () => {
    expect(formatDate('2025-01-01T12:00:00+08:00')).toBe('2025-01-01')
    expect(formatTime('2025-01-01T09:05:00+08:00')).toBe('09:05')
    expect(formatTime(null)).toBe('-')
  })

  it('Date → YYYY-MM-DD 时按门店时区取年月日', () => {
    expect(toDateParam(new Date('2025-01-01T16:00:00Z'))).toBe('2025-01-02')
    expect(today(new Date('2025-03-09T02:00:00Z'))).toBe('2025-03-09')
  })
})

describe('recentDaysRange', () => {
  it('含首含尾的最近 7 天', () => {
    const now = new Date('2025-01-07T10:00:00+08:00')
    expect(recentDaysRange(7, now)).toEqual(['2025-01-01', '2025-01-07'])
  })

  it('跨月/跨年也不会算错', () => {
    expect(recentDaysRange(3, new Date('2025-03-01T10:00:00+08:00'))).toEqual([
      '2025-02-27',
      '2025-03-01',
    ])
    expect(recentDaysRange(2, new Date('2025-01-01T10:00:00+08:00'))).toEqual([
      '2024-12-31',
      '2025-01-01',
    ])
  })

  it('days 至少为 1', () => {
    const now = new Date('2025-01-07T10:00:00+08:00')
    expect(recentDaysRange(0, now)).toEqual(['2025-01-07', '2025-01-07'])
  })
})

describe('支付倒计时', () => {
  const placedAt = '2025-01-01T12:00:00+08:00'

  it('15 分钟窗口内返回剩余毫秒', () => {
    const now = new Date('2025-01-01T12:05:00+08:00')
    expect(remainingPayMillis(placedAt, 15, now)).toBe(10 * 60 * 1000)
  })

  it('超时返回 0,不返回负数', () => {
    const now = new Date('2025-01-01T12:30:00+08:00')
    expect(remainingPayMillis(placedAt, 15, now)).toBe(0)
  })

  it('没有下单时间时返回 null', () => {
    expect(remainingPayMillis(null)).toBeNull()
  })

  it('毫秒格式化为 mm:ss', () => {
    expect(formatCountdown(0)).toBe('00:00')
    expect(formatCountdown(9_000)).toBe('00:09')
    expect(formatCountdown(65_000)).toBe('01:05')
    expect(formatCountdown(900_000)).toBe('15:00')
    expect(formatCountdown(-1)).toBe('00:00')
  })
})
