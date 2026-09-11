import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as uploadApi from '@/api/upload'

import ImageUpload from './ImageUpload.vue'

vi.mock('@/api/upload', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/upload')>()
  return { ...actual, uploadImage: vi.fn() }
})

const mockedUpload = vi.mocked(uploadApi.uploadImage)

/** `el-upload` 在 jsdom 里点不动文件选择框,直接把它的 props 拿出来手工触发 */
const ElUploadStub = defineComponent({
  name: 'ElUpload',
  props: ['httpRequest', 'beforeUpload', 'disabled', 'showFileList', 'accept'],
  template: '<div class="upload-stub"><slot /></div>',
})

function mountUpload(modelValue = '') {
  return mount(ImageUpload, {
    props: { modelValue },
    global: { stubs: { 'el-upload': ElUploadStub, 'el-icon': true, 'el-progress': true, 'el-button': true } },
  })
}

beforeEach(() => {
  mockedUpload.mockReset()
})

describe('ImageUpload', () => {
  it('有图片时显示预览', () => {
    const wrapper = mountUpload('/files/a.png')
    expect(wrapper.find('img').attributes('src')).toBe('/files/a.png')
  })

  it('上传成功把 URL 抛给父组件', async () => {
    mockedUpload.mockResolvedValue({ url: '/files/new.png' })
    const wrapper = mountUpload()
    const upload = wrapper.findComponent(ElUploadStub)
    const request = upload.props('httpRequest') as (options: { file: File }) => Promise<void>

    await request({ file: new File([new Uint8Array(10)], 'a.png', { type: 'image/png' }) })

    expect(mockedUpload).toHaveBeenCalledTimes(1)
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['/files/new.png'])
  })

  it('上传失败不抛异常,也不改模型值', async () => {
    mockedUpload.mockRejectedValue(new Error('boom'))
    const wrapper = mountUpload()
    const upload = wrapper.findComponent(ElUploadStub)
    const request = upload.props('httpRequest') as (options: { file: File }) => Promise<void>

    await expect(
      request({ file: new File([new Uint8Array(10)], 'a.png', { type: 'image/png' }) }),
    ).resolves.toBeUndefined()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('本地预校验拦下非图片,不发起上传', () => {
    const wrapper = mountUpload()
    const upload = wrapper.findComponent(ElUploadStub)
    const beforeUpload = upload.props('beforeUpload') as (file: File) => boolean

    expect(beforeUpload(new File([new Uint8Array(10)], 'a.gif', { type: 'image/gif' }))).toBe(false)
    expect(beforeUpload(new File([new Uint8Array(10)], 'a.png', { type: 'image/png' }))).toBe(true)
  })

  it('清除图片时把模型值置空', async () => {
    const wrapper = mountUpload('/files/a.png')
    const clearButton = wrapper.findAll('el-button-stub').at(-1)
    await clearButton?.trigger('click')
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([''])
  })
})
