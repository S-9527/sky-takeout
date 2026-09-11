import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/api/http'

import { confirmDanger, showError, showSuccess } from './feedback'

const error = vi.fn()
const success = vi.fn()
const confirm = vi.fn()

vi.mock('element-plus', () => ({
  ElMessage: {
    error: (message: string) => error(message),
    success: (message: string) => success(message),
  },
  ElMessageBox: {
    confirm: (...args: unknown[]) => confirm(...args),
  },
}))

beforeEach(() => {
  error.mockReset()
  success.mockReset()
  confirm.mockReset()
})

describe('showError', () => {
  it('ApiError 直接把后端文案给用户(契约里 message 就是面向用户的)', () => {
    showError(
      new ApiError({ status: 422, code: 'CATEGORY_IN_USE', message: '该分类下仍有商品,无法删除' }),
    )
    expect(error).toHaveBeenCalledWith('该分类下仍有商品,无法删除')
  })

  it('非 ApiError 用兜底文案,不把 JS 异常暴露给用户', () => {
    showError(new TypeError('x is not a function'))
    expect(error).toHaveBeenCalledWith('操作失败,请稍后重试')

    showError(new Error('boom'), '删除失败')
    expect(error).toHaveBeenLastCalledWith('删除失败')
  })
})

describe('showSuccess', () => {
  it('透传文案', () => {
    showSuccess('已接单')
    expect(success).toHaveBeenCalledWith('已接单')
  })
})

describe('confirmDanger', () => {
  it('用户确认返回 true', async () => {
    confirm.mockResolvedValue('confirm')
    await expect(confirmDanger('确认删除?')).resolves.toBe(true)
    expect(confirm).toHaveBeenCalledWith('确认删除?', '请确认', expect.objectContaining({ type: 'warning' }))
  })

  it('用户取消(ElMessageBox reject)返回 false,不抛异常', async () => {
    confirm.mockRejectedValue('cancel')
    await expect(confirmDanger('确认删除?', '删除菜品')).resolves.toBe(false)
    expect(confirm).toHaveBeenCalledWith('确认删除?', '删除菜品', expect.anything())
  })
})
