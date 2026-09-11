<script setup lang="ts">
import { Plus } from '@element-plus/icons-vue'
import { computed, ref } from 'vue'

import { uploadImage, validateImageFile } from '@/api/upload'
import { showError } from '@/utils/feedback'

/**
 * 图片上传。
 *
 * 用 `el-upload` 的 `http-request` 自定义上传,而不是让它自己发请求 ——
 * 这样上传走的是同一个 axios 实例:带得上 Bearer 令牌、401 能自动刷新、
 * 错误体也会被归一成 `ApiError`。
 */
const props = withDefaults(
  defineProps<{
    modelValue?: string | null
    /** 校验/上传失败时把原因抛给父组件(通常用来提示) */
    disabled?: boolean
  }>(),
  { modelValue: '', disabled: false },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const uploading = ref(false)
const progress = ref(0)

const previewUrl = computed(() => props.modelValue ?? '')

const beforeUpload = (file: File): boolean => {
  const problem = validateImageFile(file)
  if (problem) {
    showError(problem)
    return false
  }
  return true
}

interface UploadRequestOptions {
  file: File
}

async function customUpload(options: UploadRequestOptions): Promise<void> {
  uploading.value = true
  progress.value = 0
  try {
    const result = await uploadImage(options.file, {
      onProgress: (percent) => {
        progress.value = percent
      },
    })
    emit('update:modelValue', result.url)
  } catch (error) {
    showError(error, '图片上传失败')
  } finally {
    uploading.value = false
  }
}

function clear(): void {
  emit('update:modelValue', '')
}
</script>

<template>
  <div class="flex items-start gap-3">
    <el-upload
      class="sky-image-upload"
      :show-file-list="false"
      :disabled="disabled || uploading"
      :before-upload="beforeUpload"
      :http-request="customUpload"
      accept="image/jpeg,image/png,image/webp"
      data-testid="image-upload"
    >
      <div
        class="flex h-24 w-24 items-center justify-center overflow-hidden rounded border border-dashed border-slate-300 bg-slate-50"
      >
        <img v-if="previewUrl" :src="previewUrl" alt="商品图片" class="h-full w-full object-cover" />
        <el-icon v-else :size="22" class="text-slate-400"><Plus /></el-icon>
      </div>
    </el-upload>

    <div class="text-xs text-slate-500">
      <div>支持 jpg / jpeg / png / webp,单张不超过 5MB</div>
      <el-progress v-if="uploading" class="mt-2 w-40" :percentage="progress" :stroke-width="6" />
      <el-button v-if="previewUrl && !disabled" class="mt-2" text size="small" @click="clear">
        清除图片
      </el-button>
    </div>
  </div>
</template>
