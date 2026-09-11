<script setup lang="ts">
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'

import {
  changeCategoryStatus,
  createCategory,
  deleteCategory,
  pageCategories,
  updateCategory,
} from '@/api/category'
import type { Category, CategoryType, EnabledFlag } from '@/types'
import { CATEGORY_TYPE_DICT, ENABLED_DICT } from '@/utils/dict'
import { formatDateTime } from '@/utils/datetime'
import { confirmDanger, showError, showSuccess } from '@/utils/feedback'

const loading = ref(false)
const saving = ref(false)
const categories = ref<Category[]>([])
const total = ref(0)

const filters = reactive({
  name: '',
  type: undefined as CategoryType | undefined,
  status: undefined as EnabledFlag | undefined,
  page: 1,
  pageSize: 20,
})

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  type: 'DISH' as CategoryType,
  sortOrder: 0,
  status: 1 as EnabledFlag,
})

const rules: FormRules<typeof form> = {
  name: [
    { required: true, message: '请输入分类名称', trigger: 'blur' },
    { max: 32, message: '名称不超过 32 个字符', trigger: 'blur' },
  ],
  type: [{ required: true, message: '请选择分类类型', trigger: 'change' }],
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await pageCategories({
      page: filters.page,
      pageSize: filters.pageSize,
      sort: 'sortOrder,asc',
      name: filters.name.trim() || undefined,
      type: filters.type,
      status: filters.status,
    })
    categories.value = page.records
    total.value = page.total
  } catch (error) {
    showError(error, '分类加载失败')
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
  filters.type = undefined
  filters.status = undefined
  search()
}

function openCreate(): void {
  editingId.value = null
  form.name = ''
  form.type = filters.type ?? 'DISH'
  form.sortOrder = 0
  form.status = 1
  dialogVisible.value = true
}

function openEdit(row: Category): void {
  editingId.value = row.id
  form.name = row.name
  form.type = row.type
  form.sortOrder = row.sortOrder
  form.status = row.status
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingId.value === null) {
      await createCategory({
        name: form.name.trim(),
        type: form.type,
        sortOrder: form.sortOrder,
        status: form.status,
      })
      showSuccess('分类已创建')
    } else {
      await updateCategory(editingId.value, {
        name: form.name.trim(),
        // 类型不可改,原样回传(契约允许省略,这里显式回传以暴露"改类型"的错误用法)
        type: form.type,
        sortOrder: form.sortOrder,
        status: form.status,
      })
      showSuccess('分类已更新')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    showError(error)
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: Category): Promise<void> {
  const next: EnabledFlag = row.status === 1 ? 0 : 1
  try {
    await changeCategoryStatus(row.id, next)
    showSuccess(next === 1 ? '分类已启用' : '分类已禁用')
    await load()
  } catch (error) {
    showError(error)
  }
}

async function remove(row: Category): Promise<void> {
  if (!(await confirmDanger(`确认删除分类「${row.name}」?被商品引用的分类无法删除。`))) return
  try {
    await deleteCategory(row.id)
    showSuccess('分类已删除')
    await load()
  } catch (error) {
    showError(error)
  }
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <el-card shadow="never">
      <el-form :inline="true" @submit.prevent="search">
        <el-form-item label="名称">
          <el-input v-model="filters.name" clearable placeholder="模糊匹配" style="width: 160px" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="filters.type" clearable placeholder="全部" style="width: 140px">
            <el-option label="菜品分类" value="DISH" />
            <el-option label="套餐分类" value="SETMEAL" />
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
          <el-button type="primary" plain :icon="Plus" data-testid="category-create" @click="openCreate">
            新增分类
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="categories" empty-text="暂无分类" data-testid="category-table">
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <el-tag :type="CATEGORY_TYPE_DICT[row.type as CategoryType].tag" size="small" effect="plain">
              {{ CATEGORY_TYPE_DICT[row.type as CategoryType].label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="90" align="right" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="ENABLED_DICT[row.status].tag" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" @click="toggleStatus(row)">
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button size="small" type="danger" text @click="remove(row)">删除</el-button>
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
      :title="editingId === null ? '新增分类' : '编辑分类'"
      width="480px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="32" placeholder="如:川湘菜" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type" :disabled="editingId !== null">
            <el-radio value="DISH">菜品分类</el-radio>
            <el-radio value="SETMEAL">套餐分类</el-radio>
          </el-radio-group>
          <div v-if="editingId !== null" class="text-xs text-slate-400">
            类型不可修改:改了会让已引用的商品落到错误类型的分类下
          </div>
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" data-testid="category-submit" @click="submit">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
