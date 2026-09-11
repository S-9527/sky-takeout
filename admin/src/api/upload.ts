import type { UploadResult } from '@/types'

import { http } from './http'

/** 允许的图片类型 —— 与后端 `UPLOAD_TYPE_NOT_ALLOWED` 的判定保持一致 */
export const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const

/** 单文件上限 5MB,后端同样限制;前端先拦一次只是为了让用户少等一次往返 */
export const MAX_IMAGE_BYTES = 5 * 1024 * 1024

export interface UploadOptions {
  /** 上传进度 0~100 */
  onProgress?: (percent: number) => void
}

/**
 * 上传图片(`multipart/form-data`,字段名固定 `file`)。
 *
 * 返回的 `url` 可以直接写进菜品/套餐的 `imageUrl`。
 */
export async function uploadImage(file: File, options: UploadOptions = {}): Promise<UploadResult> {
  const form = new FormData()
  form.append('file', file)
  const response = await http.post<UploadResult>('/api/v1/admin/uploads', form, {
    onUploadProgress: (event) => {
      if (!options.onProgress || !event.total) return
      options.onProgress(Math.round((event.loaded / event.total) * 100))
    },
  })
  return response.data
}

/** 前端侧的预校验;返回 `null` 表示通过 */
export function validateImageFile(file: File): string | null {
  if (file.size === 0) return '文件为空,请重新选择'
  if (file.size > MAX_IMAGE_BYTES) return '图片不能超过 5MB'
  if (file.type && !(ALLOWED_IMAGE_TYPES as readonly string[]).includes(file.type)) {
    return '只支持 jpg / jpeg / png / webp 格式'
  }
  return null
}
