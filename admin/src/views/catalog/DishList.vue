<script setup lang="ts">
import { Delete, Plus, Refresh, Search } from '@element-plus/icons-vue'
import type { FormInstance, FormItemRule, FormRules } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'

import { listCategoryOptions } from '@/api/category'
import {
  changeDishesStatus,
  createDish,
  deleteDishes,
  getDish,
  pageDishes,
  updateDish,
} from '@/api/dish'
import ImageUpload from '@/components/ImageUpload.vue'
import type { Category, Dish, DishFlavor, EnabledFlag } from '@/types'
import { ENABLED_DICT } from '@/utils/dict'
import { showError, showSuccess } from '@/utils/feedback'
import { centsToYuanText, formatCents, yuanToCents } from '@/utils/money'

const loading = ref(false)
const saving = ref(false)
const dishes = ref<Dish[]>([])
const total = ref(0)
const categoryOptions = ref<Category[]>([])
const selection = ref<Dish[]>([])

const filters = reactive({
  name: '',
  categoryId: undefined as number | undefined,
  status: undefined as EnabledFlag | undefined,
  page: 1,
  pageSize: 20,
})

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

interface DishForm {
  name: string
  categoryId: number | undefined
  priceYuan: string
  imageUrl: string
  description: string
  status: EnabledFlag
  sortOrder: number
  flavors: DishFlavor[]
}

const form = reactive<DishForm>({
  name: '',
  categoryId: undefined,
  priceYuan: '',
  imageUrl: '',
  description: '',
  status: 1,
  sortOrder: 0,
  flavors: [],
})

/** 金额校验独立成形:用 `FormItemRule` 标注后参数由上下文推断,不用手写 any */
const priceRule: FormItemRule = {
  required: true,
  validator: (_rule, value: string, callback) => {
    const cents = yuanToCents(value)
    if (cents === null) return callback(new Error('请输入正确的金额,如 48.00'))
    if (cents <= 0) return callback(new Error('价格必须大于 0'))
    return callback()
  },
  trigger: 'blur',
}

const rules: FormRules<DishForm> = {
  name: [
    { required: true, message: '请输入菜品名称', trigger: 'blur' },
    { max: 64, message: '名称不超过 64 个字符', trigger: 'blur' },
  ],
  categoryId: [{ required: true, message: '请选择所属分类', trigger: 'change' }],
  priceYuan: [priceRule],
}

async function loadCategories(): Promise<void> {
  try {
    categoryOptions.value = await listCategoryOptions('DISH', true)
  } catch (error) {
    showError(error, '分类加载失败')
  }
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await pageDishes({
      page: filters.page,
      pageSize: filters.pageSize,
      sort: 'sortOrder,asc',
      name: filters.name.trim() || undefined,
      categoryId: filters.categoryId,
      status: filters.status,
    })
    dishes.value = page.records
    total.value = page.total
  } catch (error) {
    showError(error, '菜品加载失败')
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
  filters.categoryId = undefined
  filters.status = undefined
  search()
}

function openCreate(): void {
  editingId.value = null
  form.name = ''
  form.categoryId = filters.categoryId ?? categoryOptions.value[0]?.id
  form.priceYuan = ''
  form.imageUrl = ''
  form.description = ''
  form.status = 1
  form.sortOrder = 0
  form.flavors = []
  dialogVisible.value = true
}

async function openEdit(row: Dish): Promise<void> {
  editingId.value = row.id
  try {
    const detail = await getDish(row.id)
    form.name = detail.name
    form.categoryId = detail.categoryId
    form.priceYuan = centsToYuanText(detail.priceCents)
    form.imageUrl = detail.imageUrl ?? ''
    form.description = detail.description ?? ''
    form.status = detail.status
    form.sortOrder = detail.sortOrder ?? 0
    form.flavors = (detail.flavors ?? []).map((flavor) => ({
      name: flavor.name,
      options: [...flavor.options],
      sortOrder: flavor.sortOrder,
    }))
    dialogVisible.value = true
  } catch (error) {
    showError(error, '菜品详情加载失败')
  }
}

function addFlavor(): void {
  form.flavors.push({ name: '', options: [], sortOrder: form.flavors.length })
}

function removeFlavor(index: number): void {
  form.flavors.splice(index, 1)
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const priceCents = yuanToCents(form.priceYuan)
  if (priceCents === null || form.categoryId === undefined) return

  const flavors = form.flavors
    .filter((flavor) => flavor.name.trim() !== '' && flavor.options.length > 0)
    .map((flavor, index) => ({
      name: flavor.name.trim(),
      options: flavor.options,
      sortOrder: index,
    }))

  saving.value = true
  try {
    const payload = {
      categoryId: form.categoryId,
      name: form.name.trim(),
      priceCents,
      imageUrl: form.imageUrl || undefined,
      description: form.description.trim() || undefined,
      sortOrder: form.sortOrder,
      flavors,
    }
    if (editingId.value === null) {
      await createDish({ ...payload, status: form.status })
      showSuccess('菜品已创建')
    } else {
      await updateDish(editingId.value, { ...payload, status: form.status })
      showSuccess('菜品已更新')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    showError(error)
  } finally {
    saving.value = false
  }
}

async function removeSelected(): Promise<void> {
  if (selection.value.length === 0) return
  try {
    await deleteDishes(selection.value.map((dish) => dish.id))
    showSuccess('已删除所选菜品')
    selection.value = []
    await load()
  } catch (error) {
    // 被套餐引用的菜品会返回 422 SETMEAL_CONTAINS_DISH
    showError(error)
  }
}

async function batchStatus(status: EnabledFlag): Promise<void> {
  if (selection.value.length === 0) return
  try {
    await changeDishesStatus(
      selection.value.map((dish) => dish.id),
      status,
    )
    showSuccess(status === 1 ? '已起售所选菜品' : '已停售所选菜品(含引用它的套餐)')
    await load()
  } catch (error) {
    showError(error)
  }
}

/** 单行起售/停售走同一个批量接口,只是 ids 只有一个 */
async function toggleStatus(row: Dish): Promise<void> {
  const next: EnabledFlag = row.status === 1 ? 0 : 1
  try {
    await changeDishesStatus([row.id], next)
    showSuccess(next === 1 ? '菜品已起售' : '菜品已停售')
    await load()
  } catch (error) {
    showError(error)
  }
}

onMounted(async () => {
  await loadCategories()
  await load()
})
</script>

<template>
  <div class="space-y-4">
    <el-card shadow="never">
      <el-form :inline="true" @submit.prevent="search">
        <el-form-item label="名称">
          <el-input v-model="filters.name" clearable placeholder="模糊匹配" style="width: 160px" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="filters.categoryId" clearable placeholder="全部" style="width: 160px">
            <el-option
              v-for="category in categoryOptions"
              :key="category.id"
              :label="category.name"
              :value="category.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部" style="width: 120px">
            <el-option label="起售" :value="1" />
            <el-option label="停售" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
          <el-button :icon="Refresh" @click="load">刷新</el-button>
          <el-button type="primary" plain :icon="Plus" data-testid="dish-create" @click="openCreate">
            新增菜品
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <div class="mb-3 flex gap-2">
        <el-button :disabled="selection.length === 0" @click="batchStatus(1)">批量起售</el-button>
        <el-button :disabled="selection.length === 0" @click="batchStatus(0)">批量停售</el-button>
        <el-button
          type="danger"
          plain
          :icon="Delete"
          :disabled="selection.length === 0"
          @click="removeSelected"
        >
          批量删除
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="dishes"
        empty-text="暂无菜品"
        data-testid="dish-table"
        @selection-change="(rows: Dish[]) => (selection = rows)"
      >
        <el-table-column type="selection" width="46" />
        <el-table-column label="图片" width="90">
          <template #default="{ row }">
            <el-image
              v-if="row.imageUrl"
              :src="row.imageUrl"
              fit="cover"
              class="h-12 w-12 rounded"
              :preview-src-list="[row.imageUrl]"
              preview-teleported
            >
              <template #error>
                <div class="flex h-12 w-12 items-center justify-center rounded bg-slate-100 text-xs text-slate-400">
                  无图
                </div>
              </template>
            </el-image>
            <div v-else class="flex h-12 w-12 items-center justify-center rounded bg-slate-100 text-xs text-slate-400">
              无图
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="categoryName" label="分类" width="130" />
        <el-table-column label="价格" width="120" align="right">
          <template #default="{ row }">
            <span class="sky-amount">{{ formatCents(row.priceCents) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" align="right" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="ENABLED_DICT[row.status].tag" size="small">
              {{ row.status === 1 ? '起售' : '停售' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" @click="toggleStatus(row)">
              {{ row.status === 1 ? '停售' : '起售' }}
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
      :title="editingId === null ? '新增菜品' : '编辑菜品'"
      width="720px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="64" placeholder="如:水煮牛肉" />
        </el-form-item>
        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="选择菜品分类" style="width: 240px">
            <el-option
              v-for="category in categoryOptions"
              :key="category.id"
              :label="category.name"
              :value="category.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="价格" prop="priceYuan">
          <el-input v-model="form.priceYuan" placeholder="单位:元,如 48.00" style="width: 200px">
            <template #prepend>¥</template>
          </el-input>
          <span class="ml-2 text-xs text-slate-400">提交时换算为整数分</span>
        </el-form-item>
        <el-form-item label="图片">
          <ImageUpload v-model="form.imageUrl" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>

        <el-form-item label="口味">
          <div class="w-full">
            <div v-for="(flavor, index) in form.flavors" :key="index" class="mb-2 flex items-center gap-2">
              <el-input v-model="flavor.name" placeholder="维度名,如 辣度" style="width: 160px" />
              <el-select
                v-model="flavor.options"
                multiple
                filterable
                allow-create
                default-first-option
                placeholder="选项,回车添加"
                style="width: 320px"
              >
                <el-option v-for="option in flavor.options" :key="option" :label="option" :value="option" />
              </el-select>
              <el-button text type="danger" @click="removeFlavor(index)">删除</el-button>
            </div>
            <el-button text type="primary" :icon="Plus" @click="addFlavor">添加口味维度</el-button>
            <div class="text-xs text-slate-400">
              口味选项以字符串数组存储(不是逗号拼接),选项里含逗号也不会坏
            </div>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" data-testid="dish-submit" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
