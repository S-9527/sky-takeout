<script setup lang="ts">
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'

import {
  changeEmployeeStatus,
  createEmployee,
  pageEmployees,
  updateEmployee,
} from '@/api/employee'
import { useAuthStore } from '@/stores/auth'
import type { Employee, EmployeeRole, EmployeeStatus } from '@/types'
import { confirmDanger, showError, showSuccess } from '@/utils/feedback'
import { EMPLOYEE_ROLE_DICT, EMPLOYEE_STATUS_DICT } from '@/utils/dict'
import { formatDateTime } from '@/utils/datetime'

/**
 * 员工管理(仅 ADMIN)。
 *
 * 两条后端规则在界面上也要体现,否则用户只会看到 422:
 * - ADMIN 不能把自己的角色降为 STAFF(`EMPLOYEE_SELF_ROLE_CHANGE`);
 * - ADMIN 不能禁用自己(`EMPLOYEE_SELF_DISABLE`)。
 * 因此当前登录账号那一行的这两个控件直接禁用。
 */
const auth = useAuthStore()

const loading = ref(false)
const saving = ref(false)
const employees = ref<Employee[]>([])
const total = ref(0)

const filters = reactive({
  name: '',
  status: undefined as EmployeeStatus | undefined,
  role: undefined as EmployeeRole | undefined,
  page: 1,
  pageSize: 20,
})

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  username: '',
  password: '',
  name: '',
  phone: '',
  role: 'STAFF' as EmployeeRole,
  status: 1 as EmployeeStatus,
})

const rules: FormRules<typeof form> = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 32, message: '用户名 3~32 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码 6~32 个字符', trigger: 'blur' },
  ],
  name: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { max: 32, message: '姓名不超过 32 个字符', trigger: 'blur' },
  ],
  phone: [
    {
      pattern: /^1[3-9]\d{9}$/,
      message: '请输入正确的手机号',
      trigger: 'blur',
    },
  ],
}

function isSelf(row: Employee): boolean {
  return auth.profile?.id === row.id
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await pageEmployees({
      page: filters.page,
      pageSize: filters.pageSize,
      sort: 'createdAt,desc',
      name: filters.name.trim() || undefined,
      status: filters.status,
      role: filters.role,
    })
    employees.value = page.records
    total.value = page.total
  } catch (error) {
    showError(error, '员工列表加载失败')
  } finally {
    loading.value = false
  }
}

function search(): void {
  filters.page = 1
  void load()
}

function reset(): void {
  filters.name = ''
  filters.status = undefined
  filters.role = undefined
  search()
}

function openCreate(): void {
  editingId.value = null
  form.username = ''
  form.password = ''
  form.name = ''
  form.phone = ''
  form.role = 'STAFF'
  form.status = 1
  dialogVisible.value = true
}

function openEdit(row: Employee): void {
  editingId.value = row.id
  form.username = row.username
  form.password = ''
  form.name = row.name
  form.phone = row.phone ?? ''
  form.role = row.role
  form.status = row.status
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingId.value === null) {
      await createEmployee({
        username: form.username.trim(),
        password: form.password,
        name: form.name.trim(),
        phone: form.phone.trim() || undefined,
        role: form.role,
      })
      showSuccess('员工已创建')
    } else {
      await updateEmployee(editingId.value, {
        name: form.name.trim(),
        phone: form.phone.trim() || undefined,
        role: form.role,
        status: form.status,
      })
      showSuccess('员工已更新')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    showError(error)
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: Employee): Promise<void> {
  const next: EmployeeStatus = row.status === 1 ? 0 : 1
  const actionText = next === 1 ? '启用' : '禁用'
  if (next === 0 && !(await confirmDanger(`禁用后 ${row.name} 的登录状态立即失效,确认禁用?`))) {
    return
  }
  try {
    await changeEmployeeStatus(row.id, next)
    showSuccess(`已${actionText}${row.name}`)
    await load()
  } catch (error) {
    showError(error)
  }
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <el-alert
      type="info"
      :closable="false"
      title="员工管理仅管理员可见;禁用员工会立即让其访问令牌失效。"
    />

    <el-card shadow="never">
      <el-form :inline="true" @submit.prevent="search">
        <el-form-item label="用户名/姓名">
          <el-input v-model="filters.name" clearable placeholder="模糊匹配" style="width: 180px" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="filters.role" clearable placeholder="全部" style="width: 130px">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="员工" value="STAFF" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部" style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
          <el-button :icon="Refresh" @click="load">刷新</el-button>
          <el-button type="primary" plain :icon="Plus" data-testid="employee-create" @click="openCreate">
            新增员工
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="employees" empty-text="暂无员工" data-testid="employee-table">
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="name" label="姓名" min-width="120" />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column label="角色" width="110">
          <template #default="{ row }">
            <el-tag :type="EMPLOYEE_ROLE_DICT[row.role as EmployeeRole].tag" size="small">
              {{ EMPLOYEE_ROLE_DICT[row.role as EmployeeRole].label }}
            </el-tag>
            <el-tag v-if="isSelf(row)" class="ml-1" size="small" type="info" effect="plain">当前账号</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="EMPLOYEE_STATUS_DICT[row.status as EmployeeStatus].tag" size="small" effect="plain">
              {{ EMPLOYEE_STATUS_DICT[row.status as EmployeeStatus].label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近登录" width="170">
          <template #default="{ row }">
            {{ row.lastLoginAt ? formatDateTime(row.lastLoginAt) : '从未登录' }}
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button
              size="small"
              :disabled="row.status === 1 && isSelf(row)"
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="mt-4 justify-end"
        layout="total, sizes, prev, pager, next"
        :total="total"
        :current-page="filters.page"
        :page-size="filters.pageSize"
        :page-sizes="[10, 20, 50]"
        @current-change="
          (page: number) => {
            filters.page = page
            load()
          }
        "
        @size-change="
          (size: number) => {
            filters.pageSize = size
            filters.page = 1
            load()
          }
        "
      />
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId === null ? '新增员工' : '编辑员工'"
      width="520px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            :disabled="editingId !== null"
            maxlength="32"
            placeholder="登录名,全局唯一"
          />
        </el-form-item>
        <el-form-item v-if="editingId === null" label="初始密码" prop="password">
          <el-input v-model="form.password" type="password" show-password maxlength="32" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" maxlength="32" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" maxlength="20" placeholder="选填" />
        </el-form-item>
        <el-form-item label="角色">
          <el-radio-group
            v-model="form.role"
            :disabled="editingId !== null && editingId === auth.profile?.id"
          >
            <el-radio value="STAFF">员工</el-radio>
            <el-radio value="ADMIN">管理员</el-radio>
          </el-radio-group>
          <div v-if="editingId === auth.profile?.id" class="text-xs text-slate-400">
            不能修改自己的角色
          </div>
        </el-form-item>
        <el-form-item v-if="editingId !== null" label="状态">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            :disabled="editingId === auth.profile?.id"
          />
          <span v-if="editingId === auth.profile?.id" class="ml-2 text-xs text-slate-400">
            不能禁用自己
          </span>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" data-testid="employee-submit" @click="submit">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
