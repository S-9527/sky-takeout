<script setup lang="ts">
import { Delete, Plus, Refresh, Search } from '@element-plus/icons-vue'
import type { FormInstance, FormItemRule, FormRules } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'

import { listCategoryOptions } from '@/api/category'
import { pageDishes } from '@/api/dish'
import {
  changeSetmealsStatus,
  createSetmeal,
  deleteSetmeals,
  getSetmeal,
  pageSetmeals,
  updateSetmeal,
} from '@/api/setmeal'
import ImageUpload from '@/components/ImageUpload.vue'
import type { Category, Dish, EnabledFlag, Setmeal } from '@/types'
import { ENABLED_DICT } from '@/utils/dict'
import { showError, showSuccess } from '@/utils/feedback'
import { centsToYuanText, formatCents, yuanToCents } from '@/utils/money'

const loading = ref(false)
const saving = ref(false)
const setmeals = ref<Setmeal[]>([])
const total = ref(0)
const categoryOptions = ref<Category[]>([])
const dishOptions = ref<Dish[]>([])
const selection = ref<Setmeal[]>([])

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

interface SetmealItemForm {
  dishId: number | undefined
  copies: number
}

interface SetmealForm {
  name: string
  categoryId: number | undefined
  priceYuan: string
  imageUrl: string
  description: string
  status: EnabledFlag
  items: SetmealItemForm[]
}

const form = reactive<SetmealForm>({
  name: '',
  categoryId: undefined,
  priceYuan: '',
  imageUrl: '',
  description: '',
  status: 1,
  items: [],
})

/** 所含菜品合计(分)—— 套餐定价必须 ≤ 合计,否则后端 422 `SETMEAL_PRICE_EXCEEDS_ITEMS` */
const itemsTotalCents = computed(() =>
  form.items.reduce((sum, item) => {
    const dish = dishOptions.value.find((candidate) => candidate.id === item.dishId)
    return sum + (dish?.priceCents ?? 0) * item.copies
  }, 0),
)

const priceCentsValue = computed(() => yuanToCents(form.priceYuan))
const priceExceeds = computed(
  () => priceCentsValue.value !== null && priceCentsValue.value > itemsTotalCents.value,
)

const priceRule: FormItemRule = {
  required: true,
  validator: (_rule, value: string, callback) => {
    const cents = yuanToCents(value)
    if (cents === null) return callback(new Error('请输入正确的金额,如 88.00'))
    if (cents <= 0) return callback(new Error('价格必须大于 0'))
    return callback()
  },
  trigger: 'blur',
}

const rules: FormRules<SetmealForm> = {
  name: [
    { required: true, message: '请输入套餐名称', trigger: 'blur' },
    { max: 64, message: '名称不超过 64 个字符', trigger: 'blur' },
  ],
  categoryId: [{ required: true, message: '请选择所属分类', trigger: 'change' }],
  priceYuan: [priceRule],
}

async function loadCategories(): Promise<void> {
  try {
    categoryOptions.value = await listCategoryOptions('SETMEAL', true)
  } catch (error) {
    showError(error, '套餐分类加载失败')
  }
}

/** 套餐只能包含**起售中**的菜品? 不 —— 可以含停售菜品,但起售套餐时后端会校验 `SETMEAL_DISH_NOT_ON_SALE` */
async function loadDishes(): Promise<void> {
  try {
    const page = await pageDishes({ page: 1, pageSize: 100, sort: 'sortOrder,asc' })
    dishOptions.value = page.records
  } catch (error) {
    showError(error, '菜品列表加载失败')
  }
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await pageSetmeals({
      page: filters.page,
      pageSize: filters.pageSize,
      sort: 'createdAt,desc',
      name: filters.name.trim() || undefined,
      categoryId: filters.categoryId,
      status: filters.status,
    })
    setmeals.value = page.records
    total.value = page.total
  } catch (error) {
    showError(error, '套餐加载失败')
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
  form.items = [{ dishId: undefined, copies: 1 }]
  dialogVisible.value = true
}

async function openEdit(row: Setmeal): Promise<void> {
  editingId.value = row.id
  try {
    const detail = await getSetmeal(row.id)
    form.name = detail.name
    form.categoryId = detail.categoryId
    form.priceYuan = centsToYuanText(detail.priceCents)
    form.imageUrl = detail.imageUrl ?? ''
    form.description = detail.description ?? ''
    form.status = detail.status
    form.items = (detail.items ?? []).map((item) => ({ dishId: item.dishId, copies: item.copies }))
    if (form.items.length === 0) form.items = [{ dishId: undefined, copies: 1 }]
    dialogVisible.value = true
  } catch (error) {
    showError(error, '套餐详情加载失败')
  }
}

function addItem(): void {
  form.items.push({ dishId: undefined, copies: 1 })
}

function removeItem(index: number): void {
  form.items.splice(index, 1)
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const priceCents = yuanToCents(form.priceYuan)
  if (priceCents === null || form.categoryId === undefined) return

  const items = form.items
    .filter((item) => item.dishId !== undefined)
    .map((item) => ({ dishId: item.dishId as number, copies: item.copies }))

  if (items.length === 0) {
    showError(new Error('套餐必须至少包含一个菜品'))
    return
  }
  const duplicated = new Set(items.map((item) => item.dishId))
  if (duplicated.size !== items.length) {
    showError(new Error('同一菜品不能重复添加'))
    return
  }

  saving.value = true
  try {
    const payload = {
      categoryId: form.categoryId,
      name: form.name.trim(),
      priceCents,
      imageUrl: form.imageUrl || undefined,
      description: form.description.trim() || undefined,
      items,
    }
    if (editingId.value === null) {
      await createSetmeal({ ...payload, status: form.status })
      showSuccess('套餐已创建')
    } else {
      await updateSetmeal(editingId.value, { ...payload, status: form.status })
      showSuccess('套餐已更新')
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
    await deleteSetmeals(selection.value.map((setmeal) => setmeal.id))
    showSuccess('已删除所选套餐')
    selection.value = []
    await load()
  } catch (error) {
    showError(error)
  }
}

async function batchStatus(status: EnabledFlag): Promise<void> {
  if (selection.value.length === 0) return
  try {
    await changeSetmealsStatus(
      selection.value.map((setmeal) => setmeal.id),
      status,
    )
    showSuccess(status === 1 ? '已起售所选套餐' : '已停售所选套餐')
    await load()
  } catch (error) {
    // 所含菜品停售时起售会返回 422 SETMEAL_DISH_NOT_ON_SALE
    showError(error)
  }
}

async function toggleStatus(row: Setmeal): Promise<void> {
  const next: EnabledFlag = row.status === 1 ? 0 : 1
  try {
    await changeSetmealsStatus([row.id], next)
    showSuccess(next === 1 ? '套餐已起售' : '套餐已停售')
    await load()
  } catch (error) {
    showError(error)
  }
}

onMounted(async () => {
  await Promise.all([loadCategories(), loadDishes()])
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
          <el-button type="primary" plain :icon="Plus" data-testid="setmeal-create" @click="openCreate">
            新增套餐
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
        :data="setmeals"
        empty-text="暂无套餐"
        data-testid="setmeal-table"
        @selection-change="(rows: Setmeal[]) => (selection = rows)"
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
      :title="editingId === null ? '新增套餐' : '编辑套餐'"
      width="760px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="64" placeholder="如:双人牛肉套餐" />
        </el-form-item>
        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="选择套餐分类" style="width: 240px">
            <el-option
              v-for="category in categoryOptions"
              :key="category.id"
              :label="category.name"
              :value="category.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="价格" prop="priceYuan">
          <el-input v-model="form.priceYuan" placeholder="单位:元" style="width: 200px">
            <template #prepend>¥</template>
          </el-input>
          <span class="ml-2 text-xs" :class="priceExceeds ? 'text-red-500' : 'text-slate-400'">
            所含菜品合计 {{ formatCents(itemsTotalCents) }}
            <template v-if="priceExceeds">—— 套餐价不能高于合计</template>
          </span>
        </el-form-item>
        <el-form-item label="图片">
          <ImageUpload v-model="form.imageUrl" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="255" show-word-limit />
        </el-form-item>

        <el-form-item label="组成">
          <div class="w-full">
            <div v-for="(item, index) in form.items" :key="index" class="mb-2 flex items-center gap-2">
              <el-select
                v-model="item.dishId"
                filterable
                placeholder="选择菜品"
                style="width: 320px"
              >
                <el-option
                  v-for="dish in dishOptions"
                  :key="dish.id"
                  :label="`${dish.name}(${formatCents(dish.priceCents)})`"
                  :value="dish.id"
                  :disabled="form.items.some((row, i) => i !== index && row.dishId === dish.id)"
                />
              </el-select>
              <el-input-number v-model="item.copies" :min="1" :max="99" />
              <el-button text type="danger" @click="removeItem(index)">删除</el-button>
            </div>
            <el-button text type="primary" :icon="Plus" @click="addItem">添加菜品</el-button>
            <div class="text-xs text-slate-400">
              套餐是独立商品,有独立图片与定价;同一菜品不可重复出现
            </div>
          </div>
        </el-form-item>

        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
          <span class="ml-2 text-xs text-slate-400">起售要求所含菜品全部起售</span>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="saving"
          :disabled="priceExceeds"
          data-testid="setmeal-submit"
          @click="submit"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
