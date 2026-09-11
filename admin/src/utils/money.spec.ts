import { describe, expect, it } from 'vitest'

import { centsToYuan, centsToYuanText, formatCents, yuanToCents } from './money'

describe('formatCents', () => {
  it('把分渲染成带两位小数的金额', () => {
    expect(formatCents(10400)).toBe('¥104.00')
    expect(formatCents(5)).toBe('¥0.05')
    expect(formatCents(99)).toBe('¥0.99')
    expect(formatCents(0)).toBe('¥0.00')
    expect(formatCents(123456789)).toBe('¥1234567.89')
  })

  it('空值显示为 - 而不是 0,避免"没有金额"被误读成 0 元', () => {
    expect(formatCents(null)).toBe('-')
    expect(formatCents(undefined)).toBe('-')
  })

  it('负数保留符号', () => {
    expect(formatCents(-250)).toBe('-¥2.50')
  })
})

describe('yuanToCents', () => {
  it('接受整数与一/两位小数', () => {
    expect(yuanToCents('48')).toBe(4800)
    expect(yuanToCents('48.5')).toBe(4850)
    expect(yuanToCents('48.55')).toBe(4855)
    expect(yuanToCents('0.1')).toBe(10)
    expect(yuanToCents(12)).toBe(1200)
    expect(yuanToCents('0')).toBe(0)
  })

  it('用字符串拆分,不受浮点误差影响', () => {
    // 48.55 * 100 === 4854.999999999999,先乘后取整的写法会踩坑
    expect(yuanToCents('48.55')).toBe(4855)
    expect(yuanToCents('1.003')).toBeNull()
  })

  it('非法输入返回 null', () => {
    expect(yuanToCents('')).toBeNull()
    expect(yuanToCents(null)).toBeNull()
    expect(yuanToCents(undefined)).toBeNull()
    expect(yuanToCents('abc')).toBeNull()
    expect(yuanToCents('1.234')).toBeNull()
    expect(yuanToCents('1,5')).toBeNull()
  })

  it('支持负数(退款/冲正场景预留)', () => {
    expect(yuanToCents('-1.5')).toBe(-150)
  })
})

describe('centsToYuanText / centsToYuan', () => {
  it('表单回填用的字符串不丢位数', () => {
    expect(centsToYuanText(4800)).toBe('48.00')
    expect(centsToYuanText(4855)).toBe('48.55')
    expect(centsToYuanText(5)).toBe('0.05')
    expect(centsToYuanText(null)).toBe('')
  })

  it('数字形式只给图表用', () => {
    expect(centsToYuan(4855)).toBe(48.55)
    expect(centsToYuan(null)).toBe(0)
  })

  it('与 yuanToCents 互为逆运算', () => {
    for (const cents of [0, 1, 99, 100, 4855, 10400, 123456]) {
      expect(yuanToCents(centsToYuanText(cents))).toBe(cents)
    }
  })
})
