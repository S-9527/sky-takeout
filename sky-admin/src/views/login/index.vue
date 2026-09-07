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
            <!-- <span class="title-label">苍穹外卖</span> -->
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
import type { FormInstance } from 'element-plus'
import { useUserStore } from '@/store/modules/user'

const router = useRouter()
const userStore = useUserStore()
const loginFormRef = ref<FormInstance>()

const validateUsername = (rule: any, value: string, callback: Function) => {
  if (!value) {
    callback(new Error('请输入用户名'))
  } else {
    callback()
  }
}
const validatePassword = (rule: any, value: string, callback: Function) => {
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
  username: String
  password: String
})

const loginRules = {
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
        .Login(loginForm.value as any)
        .then((res: any) => {
          if (String(res.code) === '1') {
            router.push('/')
          } else {
            loading.value = false
          }
        })
        .catch(() => {
          loading.value = false
        })
    } else {
      return false
    }
  })
}
</script>

<style lang="scss">
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

.title {
  margin: 0px auto 10px auto;
  text-align: left;
  color: #707070;
}

.login-form {
  background: #ffffff;
  width: 40%;
  border-radius: 0px 8px 8px 0px;
  display: flex;
  justify-content: center;
  align-items: center;
  .el-form {
    width: 214px;
    height: 307px;
  }
  .el-form-item {
    margin-bottom: 30px;
  }
  .el-input__wrapper {
    box-shadow: none;
    padding: 0 2px;
    border-bottom: 1px solid #e9e9e8;
    border-radius: 0;
  }
  .el-input.is-focus .el-input__wrapper {
    box-shadow: none !important;
  }
  .el-form-item.is-error .el-input__wrapper,
  .el-form-item.is-error .el-input.is-focus .el-input__wrapper {
    box-shadow: none !important;
    border-bottom: 1px solid #fd7065;
  }
  .el-input__inner {
    border: 0;
    border-radius: 0;
    font-size: 12px;
    font-weight: 400;
    color: #333333;
    height: 32px;
    line-height: 32px;
  }
  .el-input__inner::placeholder {
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