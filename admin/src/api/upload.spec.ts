import { AxiosError, type AxiosAdapter, type AxiosResponse } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { http } from './http'
import { ALLOWED_IMAGE_TYPES, MAX_IMAGE_BYTES, uploadImage, validateImageFile } from './upload'

function makePngFile(size = 1024, name = 'dish.png'): File {
  return new File([new Uint8Array(size)], name, { type: 'image/png' })
}

afterEach(() => {
  http.defaults.adapter = undefined
  vi.restoreAllMocks()
})

describe('validateImageFile', () => {
  it('允许 jpg/png/webp', () => {
    for (const type of ALLOWED_IMAGE_TYPES) {
      expect(validateImageFile(new File([new Uint8Array(10)], 'a', { type }))).toBeNull()
    }
  })

  it('空文件、超大文件、错误格式都要在本地拦下', () => {
    expect(validateImageFile(new File([], 'empty.png', { type: 'image/png' }))).toBe(
      '文件为空,请重新选择',
    )
    expect(
      validateImageFile(new File([new Uint8Array(MAX_IMAGE_BYTES + 1)], 'big.png', { type: 'image/png' })),
    ).toBe('图片不能超过 5MB')
    expect(validateImageFile(new File([new Uint8Array(10)], 'a.gif', { type: 'image/gif' }))).toContain(
      '只支持',
    )
  })

  it('浏览器没给出 MIME 类型时交给后端判定,不误杀', () => {
    expect(validateImageFile(new File([new Uint8Array(10)], 'unknown'))).toBeNull()
  })
})

describe('uploadImage', () => {
  it('用 multipart/form-data 上传,字段名固定 file', async () => {
    let received: FormData | null = null
    const adapter: AxiosAdapter = async (config) => {
      received = config.data as FormData
      const response: AxiosResponse = {
        data: { url: '/files/2025/01/01/abc.png', size: 1024, contentType: 'image/png' },
        status: 201,
        statusText: 'Created',
        headers: {},
        config,
      }
      return response
    }
    http.defaults.adapter = adapter

    const result = await uploadImage(makePngFile())

    expect(result.url).toBe('/files/2025/01/01/abc.png')
    const form = received as unknown as FormData
    expect(form).toBeInstanceOf(FormData)
    expect((form.get('file') as File).name).toBe('dish.png')
  })

  it('把后端的 400 归一成 ApiError(类型不符/超限由后端兜底)', async () => {
    const adapter: AxiosAdapter = async (config) => {
      const response: AxiosResponse = {
        data: { code: 'UPLOAD_TYPE_NOT_ALLOWED', message: '只允许 jpg/jpeg/png/webp', traceId: 't1' },
        status: 400,
        statusText: 'Bad Request',
        headers: {},
        config,
      }
      throw new AxiosError('bad request', 'ERR_BAD_REQUEST', config, null, response)
    }
    http.defaults.adapter = adapter

    await expect(uploadImage(makePngFile())).rejects.toMatchObject({
      code: 'UPLOAD_TYPE_NOT_ALLOWED',
      status: 400,
    })
  })
})
