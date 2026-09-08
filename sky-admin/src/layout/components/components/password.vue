<template>
  <el-dialog
    title="修改密码"
    :model-value="visible"
    width="568px"
    class="pwdCon"
    append-to-body
    :close-on-click-modal="false"
    @update:model-value="onVisibleChange"
    @closed="resetFields"
  >
    <el-form ref="formRef" :model="form" label-width="85px" :rules="rules">
      <el-form-item label="原始密码：" prop="oldPassword">
        <el-input
          v-model="form.oldPassword"
          type="password"
          placeholder="请输入"
        ></el-input>
      </el-form-item>
      <el-form-item label="新密码：" prop="newPassword">
        <el-input
          v-model="form.newPassword"
          type="password"
          placeholder="6 - 20位密码，数字或字母，区分大小写"
        ></el-input>
      </el-form-item>
      <el-form-item label="确认密码：" prop="affirmPassword">
        <el-input
          v-model="form.affirmPassword"
          type="password"
          placeholder="请输入"
        ></el-input>
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="onVisibleChange(false)">取 消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保 存</el-button>
      </div>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
// 接口
import { editPassword } from '@/api/users'
import { useUserStore } from '@/store/modules/user'
import router from '@/router'

const props = defineProps({
  dialogFormVisible: { type: Boolean, default: false }
})

const emit = defineEmits(['handleclose'])

const userStore = useUserStore()

const visible = ref(props.dialogFormVisible)
watch(
  () => props.dialogFormVisible,
  (v) => {
    visible.value = v
  }
)

const formRef = ref<FormInstance>()
const saving = ref(false)
const form = reactive({
  oldPassword: '',
  newPassword: '',
  affirmPassword: ''
})

const validatePwd = (_rule: any, value: any, callback: Function) => {
  const reg = /^[0-9A-Za-z]{6,20}$/
  if (!value) {
    callback(new Error('请输入'))
  } else if (!reg.test(value)) {
    callback(new Error('6 - 20位密码，数字或字母，区分大小写'))
  } else {
    callback()
  }
}
const validatePass2 = (_rule: any, value: any, callback: Function) => {
  if (!value) {
    callback(new Error('请再次输入密码'))
  } else if (value !== form.newPassword) {
    callback(new Error('密码不一致，请重新输入密码'))
  } else {
    callback()
  }
}
const rules: FormRules = {
  oldPassword: [{ validator: validatePwd, trigger: 'blur' }],
  newPassword: [{ validator: validatePwd, trigger: 'blur' }],
  affirmPassword: [{ validator: validatePass2, trigger: 'blur' }]
}

const resetFields = () => {
  formRef.value?.clearValidate()
  formRef.value?.resetFields()
  form.oldPassword = ''
  form.newPassword = ''
  form.affirmPassword = ''
}

const onVisibleChange = (v: boolean) => {
  visible.value = v
  emit('handleclose')
}

const handleSave = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const { data } = await editPassword({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword
    })
    if (data.code === 200) {
      ElMessage.success('密码修改成功，请重新登录')
      resetFields()
      onVisibleChange(false)
      userStore.LogOut().then(() => {
        router.replace({ path: '/login' })
      })
    } else {
      ElMessage.error(data.msg || '密码修改失败')
    }
  } catch {
    ElMessage.error('密码修改失败，请稍后重试')
  } finally {
    saving.value = false
  }
}
</script>
<style lang="scss">
.pwdCon {
  .el-dialog__body {
    padding-top: 60px;
    padding: 60px 100px 0;
  }
  .el-input__inner {
    padding: 0 12px;
  }
  .el-form-item {
    margin-bottom: 26px;
  }
  .el-form-item__label {
    text-align: left;
  }
  .el-dialog__footer {
    padding-top: 14px;
  }
}
</style>