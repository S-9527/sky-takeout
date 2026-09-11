<script setup lang="ts">
import { Lock, User } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules<typeof form> = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' },
  ],
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await auth.login(form.username.trim(), form.password)
    ElMessage.success('登录成功')
    const redirect = route.query.redirect
    await router.replace(typeof redirect === 'string' && redirect ? redirect : { name: 'workbench' })
  } catch (error) {
    // 后端对"用户不存在"和"密码错误"统一返回 AUTH_BAD_CREDENTIALS,前端照原样提示
    const message = error instanceof ApiError ? error.message : '登录失败,请稍后重试'
    ElMessage.error(message)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="flex min-h-full items-center justify-center bg-slate-100 px-4">
    <el-card class="w-full max-w-md" shadow="always">
      <div class="mb-6 text-center">
        <h1 class="text-2xl font-semibold text-slate-800">苍穹外卖 · 管理端</h1>
        <p class="mt-2 text-sm text-slate-500">请使用员工账号登录</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        data-testid="login-form"
        @submit.prevent="submit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            :prefix-icon="User"
            placeholder="用户名"
            name="username"
            autocomplete="username"
            data-testid="login-username"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            :prefix-icon="Lock"
            type="password"
            placeholder="密码"
            name="password"
            autocomplete="current-password"
            show-password
            data-testid="login-password"
            @keyup.enter="submit"
          />
        </el-form-item>

        <el-button
          class="w-full"
          type="primary"
          size="large"
          :loading="submitting"
          data-testid="login-submit"
          @click="submit"
        >
          登录
        </el-button>
      </el-form>

      <el-alert class="mt-5" type="info" :closable="false" title="开发环境种子账号">
        <div class="text-xs leading-6">
          管理员 <code>admin / 123456</code><br />
          员工 <code>zhangsan / 123456</code>(无员工管理与退款权限)
        </div>
      </el-alert>
    </el-card>
  </div>
</template>
