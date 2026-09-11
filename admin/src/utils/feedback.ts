import { ElMessage, ElMessageBox } from 'element-plus'

import { ApiError } from '@/api/http'

/**
 * 统一的错误提示。
 *
 * 后端 `ErrorResponse.message` 本身就是**面向用户的中文提示**(契约 2.1),
 * 所以正常路径直接展示它,而不是自己再编一句"操作失败"——
 * 那样会把"该分类下仍有商品,无法删除"这类有用信息丢掉。
 */
export function showError(error: unknown, fallback = '操作失败,请稍后重试'): void {
  const message = error instanceof ApiError ? error.message : fallback
  ElMessage.error(message)
}

/** 成功提示的薄封装,统一时长 */
export function showSuccess(message: string): void {
  ElMessage.success(message)
}

/** 危险操作二次确认;返回 `true` 才继续 */
export async function confirmDanger(message: string, title = '请确认'): Promise<boolean> {
  try {
    await ElMessageBox.confirm(message, title, {
      type: 'warning',
      confirmButtonText: '确认',
      cancelButtonText: '取消',
    })
    return true
  } catch {
    return false
  }
}
