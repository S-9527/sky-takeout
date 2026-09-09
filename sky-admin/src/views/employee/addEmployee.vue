<template>
  <div class="addBrand-container">
    <HeadLable :title="title"
               :goback="true" />
    <div class="container">
      <el-form ref="ruleFormRef"
               :model="ruleForm"
               :rules="rules"
               :inline="false"
               label-width="180px"
               class="demo-ruleForm">
        <!--          <el-form-item label="员工职级" prop="region">-->
        <!--            <el-select v-model="ruleForm.region" placeholder="请选择品牌名称">-->
        <!--              <el-option label="区域一" value="shanghai"></el-option>-->
        <!--              <el-option label="区域二" value="beijing"></el-option>-->
        <!--            </el-select>-->
        <!--            <el-button @click="submitForm('ruleForm')" type="primary" class="continue" style="margin-left: 10px;" >+新增职级</el-button>-->
        <!--          </el-form-item>-->
        <el-form-item label="账号:"
                      prop="username">
          <el-input v-model="ruleForm.username"
                    placeholder="请输入账号"
                    maxlength="20" />
        </el-form-item>
        <el-form-item label="员工姓名:"
                      prop="name">
          <el-input v-model="ruleForm.name"
                    placeholder="请输入员工姓名"
                    maxlength="12" />
        </el-form-item>
        <!-- <el-form-item
          label="密码:"
          prop="password"
        >
          <el-input
            v-model="ruleForm.password"
            type="password"
            autocomplete="off"
            placeholder="请输入密码"
          />
        </el-form-item> -->
        <!-- <el-form-item
          label="确认密码:"
          prop="rePassword"
        >
          <el-input
            v-model="ruleForm.rePassword"
            type="password"
            autocomplete="off"
            placeholder="请输入确认密码"
          />
        </el-form-item> -->
        <el-form-item label="手机号:"
                      prop="phone">
          <el-input v-model="ruleForm.phone"
                    placeholder="请输入手机号"
                    maxlength="11" />
        </el-form-item>
        <el-form-item label="性别:"
                      prop="sex">
          <el-radio-group v-model="ruleForm.sex">
            <el-radio :value="'男'">男</el-radio>
            <el-radio :value="'女'">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="身份证号:"
                      prop="idNumber"
                      class="idNumber">
          <el-input v-model="ruleForm.idNumber"
                    placeholder="请输入身份证号"
                    maxlength="20" />
        </el-form-item>
        <div class="subBox address">
          <!-- <el-form-item> -->
          <el-button @click="() => $router.push('/employee')">
            取消
          </el-button>
          <el-button type="primary"
                     :class="{ continue: actionType === 'add' }"
                     @click="submitForm('ruleForm', false)">
            保存
          </el-button>
          <el-button v-if="actionType == 'add'"
                     type="primary"
                     @click="submitForm('ruleForm', true)">
            保存并继续添加
          </el-button>
          <!-- </el-form-item> -->
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormItemRule } from 'element-plus'
import HeadLable from '@/components/HeadLable/index.vue'
import { queryEmployeeById, addEmployee, editEmployee } from '@/api/employee'

const route = useRoute()
const router = useRouter()

const title = ref('添加员工')
const actionType = ref('')
const ruleForm = ref({
  name: '',
  phone: '',
  sex: '男',
  idNumber: '',
  username: ''
})
const ruleFormRef = ref<FormInstance>()

const isCellPhone = (val: string) => {
  if (!/^1(3|4|5|6|7|8)\d{9}$/.test(val)) {
    return false
  } else {
    return true
  }
}

type Validator = NonNullable<FormItemRule['validator']>

const checkphone: Validator = (_rule, value, callback) => {
  if (value == '') {
    callback(new Error('请输入手机号'))
  } else if (!isCellPhone(value)) {
    callback(new Error('请输入正确的手机号!'))
  } else {
    callback()
  }
}

const validID: Validator = (_rule, value, callback) => {
  let reg = /(^\d{15}$)|(^\d{18}$)|(^\d{17}(\d|X|x)$)/
  if (value == '') {
    callback(new Error('请输入身份证号码'))
  } else if (reg.test(value)) {
    callback()
  } else {
    callback(new Error('身份证号码不正确'))
  }
}

const rules = computed(() => {
  return {
    name: [
      {
        required: true,
        validator: ((_rule, value, callback) => {
          if (!value) {
            callback(new Error('请输入员工姓名'))
          } else {
            callback()
          }
        }) as Validator,
        trigger: 'blur'
      }
    ],
    username: [
      {
        required: true,
        validator: ((_rule, value, callback) => {
          if (!value) {
            callback(new Error('请输入账号'))
          } else {
            const reg = /^([a-z]|[0-9]){3,20}$/
            if (!reg.test(value)) {
              callback(new Error('账号输入不符，请输入3-20个字符'))
            } else {
              callback()
            }
          }
        }) as Validator,
        trigger: 'blur'
      }
    ],
    phone: [{ required: true, validator: checkphone, trigger: 'blur' }],
    idNumber: [{ required: true, validator: validID, trigger: 'blur' }]
  }
})

const init = async () => {
  const id = route.query.id
  queryEmployeeById(Number(id)).then((res) => {
    ruleForm.value = res
    ruleForm.value.sex = res.sex === '0' ? '女' : '男'
  })
}

const submitForm = (_formName: string, st: boolean) => {
  ruleFormRef.value?.validate((valid) => {
    if (valid) {
      if (actionType.value === 'add') {
        const params = {
          ...ruleForm.value,
          sex: ruleForm.value.sex === '女' ? '0' : '1'
        }
        addEmployee(params)
          .then(() => {
            ElMessage.success('员工添加成功！')
            if (!st) {
              router.push({ path: '/employee' })
            } else {
              ruleForm.value = {
                username: '',
                name: '',
                phone: '',
                sex: '男',
                idNumber: ''
              }
            }
          })
          .catch(() => {})
      } else {
        const params = {
          ...ruleForm.value,
          sex: ruleForm.value.sex === '女' ? '0' : '1'
        }
        editEmployee(params)
          .then(() => {
            ElMessage.success('员工信息修改成功！')
            router.push({ path: '/employee' })
          })
          .catch(() => {})
      }
    }
  })
}

actionType.value = route.query.id ? 'edit' : 'add'
if (route.query.id) {
  title.value = '修改员工信息'
  init()
}
</script>

<style lang="scss" scoped>
.addBrand {
  &-container {
    margin: 30px;
    margin-top: 0px;
    .HeadLable {
      background-color: transparent;
      margin-bottom: 0px;
      padding-left: 0px;
    }
    .container {
      position: relative;
      z-index: 1;
      background: #fff;
      padding: 30px;
      border-radius: 4px;
      // min-height: 500px;
      .subBox {
        padding-top: 30px;
        text-align: center;
        border-top: solid 1px $gray-5;
      }
    }
    .idNumber {
      margin-bottom: 39px;
    }

    .el-form-item {
      margin-bottom: 29px;
    }
    .el-input {
      width: 293px;
    }
  }
}
</style>
