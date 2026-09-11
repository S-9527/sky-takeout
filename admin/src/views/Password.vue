<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import { showError, showSuccess } from '@/utils/feedback'

const router = useRouter()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const saving = ref(false)

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const rules: FormRules<typeof form> = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码 6~32 位', trigger: 'blur' },
  ],
  confirmPassword: [
    {
      required: true,
      validator: (_rule, value: string, callback) => {
        if (value !== form.newPassword) return callback(new Error('两次输入的密码不一致'))
        return callback()
      },
      trigger: 'blur',
    },
  ],
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    // 后端改密成功后会撤销所有 refresh token,必须重新登录
    await auth.changePassword(form.oldPassword, form.newPassword)
    showSuccess('密码已修改,请重新登录')
    await router.replace({ name: 'login' })
  } catch (error) {
    showError(error, '密码修改失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="mx-auto max-w-lg">
    <el-card shadow="never">
      <template #header><span class="font-medium">修改密码</span></template>

      <el-alert
        class="mb-4"
        type="warning"
        :closable="false"
        title="修改成功后所有登录状态会被撤销,需要用新密码重新登录。"
      />

      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" @submit.prevent="submit">
        <el-form-item label="当前密码" prop="oldPassword">
          <el-input v-model="form.oldPassword" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="form.newPassword"
            type="password"
            show-password
            autocomplete="new-password"
            data-testid="new-password"
          />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" data-testid="password-submit" @click="submit">
            确认修改
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>
