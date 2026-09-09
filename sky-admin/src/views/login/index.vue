<template>
  <div class="login">
    <div class="login-box">
      <img src="@/assets/login/login-l.png" alt="" />
      <div class="login-form">
        <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules">
          <div class="login-form-title">
            <img
              src="@/assets/login/icon_logo.png"
              style="width: 149px; height: 38px"
              alt=""
            />
          </div>
          <el-form-item prop="username">
            <el-input
              v-model="loginForm.username"
              type="text"
              auto-complete="off"
              placeholder="账号"
            >
              <template #prefix>
                <i class="iconfont icon-user" />
              </template>
            </el-input>
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="密码"
              @keyup.enter="handleLogin"
            >
              <template #prefix>
                <i class="iconfont icon-lock" />
              </template>
            </el-input>
          </el-form-item>
          <el-form-item style="width: 100%">
            <el-button
              :loading="loading"
              class="login-btn"
              size="default"
              type="primary"
              style="width: 100%"
              @click.prevent="handleLogin"
            >
              <span v-if="!loading">登录</span>
              <span v-else>登录中...</span>
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormItemRule, FormRules } from 'element-plus'
import { useUserStore } from '@/store/modules/user'

const router = useRouter()
const userStore = useUserStore()
const loginFormRef = ref<FormInstance>()

type Validator = NonNullable<FormItemRule['validator']>

const validateUsername: Validator = (_rule, value, callback) => {
  if (!value) {
    callback(new Error('请输入用户名'))
  } else {
    callback()
  }
}
const validatePassword: Validator = (_rule, value, callback) => {
  if (value.length < 6) {
    callback(new Error('密码必须在6位以上'))
  } else {
    callback()
  }
}
const loginForm = ref({
  username: 'admin',
  password: '123456',
} as {
  username: string
  password: string
})

const loginRules: FormRules = {
  username: [{ validator: validateUsername, trigger: 'blur' }],
  password: [{ validator: validatePassword, trigger: 'blur' }],
}
const loading = ref(false)

// 登录
const handleLogin = () => {
  loginFormRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      loading.value = true
      await userStore
        .Login(loginForm.value)
        .then(() => {
          router.push('/')
        })
        .catch(() => {
          loading.value = false
        })
    }
  })
}
</script>

<style lang="scss" scoped>
.login {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  background-color: #333;
}

.login-box {
  width: 1000px;
  height: 474.38px;
  border-radius: 8px;
  display: flex;
  img {
    width: 60%;
    height: auto;
  }
}

.login-form {
  background: #ffffff;
  width: 40%;
  border-radius: 0px 8px 8px 0px;
  display: flex;
  justify-content: center;
  align-items: center;
  :deep(.el-form) {
    width: 214px;
    height: 307px;
  }
  :deep(.el-form-item) {
    margin-bottom: 30px;
  }
  :deep(.el-input__wrapper) {
    box-shadow: none;
    padding: 0 2px;
    border-bottom: 1px solid #e9e9e8;
    border-radius: 0;
  }
  :deep(.el-input.is-focus .el-input__wrapper) {
    box-shadow: none !important;
  }
  :deep(.el-form-item.is-error .el-input__wrapper),
  :deep(.el-form-item.is-error .el-input.is-focus .el-input__wrapper) {
    box-shadow: none !important;
    border-bottom: 1px solid #fd7065;
  }
  :deep(.el-input__inner) {
    border: 0;
    border-radius: 0;
    font-size: 12px;
    font-weight: 400;
    color: #333333;
    height: 32px;
    line-height: 32px;
  }
  :deep(.el-input__inner::placeholder) {
    color: #aeb5c4;
  }
}

.login-btn {
  border-radius: 17px;
  padding: 11px 20px !important;
  margin-top: 10px;
  font-weight: 500;
  font-size: 12px;
  border: 0;
  color: #333333;
  background-color: #ffc200;
  &:hover,
  &:focus {
    background-color: #ffc200;
    color: #ffffff;
  }
}
.login-form-title {
  height: 36px;
  display: flex;
  justify-content: center;
  align-items: center;
  margin-bottom: 40px;
  .title-label {
    font-weight: 500;
    font-size: 20px;
    color: #333333;
    margin-left: 10px;
  }
}
</style>