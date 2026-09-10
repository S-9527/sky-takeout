<template>
  <div class="dashboard-container">
    <div class="container">
      <div class="tableBar">
        <label style="margin-right: 10px">套餐名称：</label>
        <el-input v-model="input"
                  placeholder="请填写套餐名称"
                  style="width: 14%"
                  clearable
                  @clear="init"
                  @keyup.enter="initFun" />

        <label style="margin-right: 10px; margin-left: 20px">套餐分类：</label>
        <el-select v-model="categoryId"
                   style="width: 14%"
                   placeholder="请选择"
                   clearable
                   @clear="init">
          <el-option v-for="item in dishCategoryList"
                     :key="item.value"
                     :label="item.label"
                     :value="item.value" />
        </el-select>

        <label style="margin-right: 10px; margin-left: 20px">售卖状态：</label>
        <el-select v-model="dishStatus"
                   style="width: 14%"
                   placeholder="请选择"
                   clearable
                   @clear="init">
          <el-option v-for="item in saleStatus"
                     :key="item.value"
                     :label="item.label"
                     :value="item.value" />
        </el-select>
        <el-button class="normal-btn continue"
                   @click="init(true)">
          查询
        </el-button>
        <div class="tableLab">
          <span class="delBut non"
                @click="deleteHandle('批量')">批量删除</span>
          <el-button type="primary"
                     style="margin-left: 15px"
                     @click="addSetMeal('add')">
            + 新建套餐
          </el-button>
        </div>
      </div>
      <el-table v-if="tableData.length"
                :data="tableData"
                stripe
                class="tableBox"
                @selection-change="handleSelectionChange">
        <el-table-column type="selection"
                         width="25" />
        <el-table-column prop="name"
                         label="套餐名称" />
        <el-table-column prop="image"
                         label="图片">
          <template #default="{ row }">
            <el-image style="width: 80px; height: 40px; border: none; cursor: pointer"
                      :src="row.image">
              <template #error>
                <div class="image-slot">
                  <img src="./../../assets/noImg.png"
                       style="width: auto; height: 40px; border: none">
                </div>
              </template>
            </el-image>
          </template>
        </el-table-column>
        <el-table-column prop="categoryName"
                         label="套餐分类" />
        <el-table-column prop="price"
                         label="套餐价">
          <template #default="scope">
            <span>￥{{ money(scope.row.price) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="售卖状态">
          <template #default="scope">
            <div class="tableColumn-status"
                 :class="{ 'stop-use': scope.row.status === 0 }">
              {{ scope.row.status === 0 ? '停售' : '启售' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime"
                         label="最后操作时间" />
        <el-table-column label="操作"
                         width="250"
                         align="center">
          <template #default="scope">
            <el-button link
                       size="small"
                       class="blueBug"
                       @click="addSetMeal(scope.row.id)">
              修改
            </el-button>
            <el-button link
                       size="small"
                       class="delBut"
                       @click="deleteHandle('单删', scope.row.id)">
              删除
            </el-button>
            <el-button link
                       size="small"
                       class="blueBug non"
                       :class="{
                         blueBug: scope.row.status === 0,
                         delBut: scope.row.status !== 0
                       }"
                       @click="statusHandle(scope.row)">
              {{ scope.row.status === 0 ? '启售' : '停售' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <Empty v-else
             :is-search="isSearch" />
      <Pagination :total="counts"
                  :page="page"
                  :page-size="pageSize"
                  @size-change="handleSizeChange"
                  @current-change="handleCurrentChange" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getSetmealPage,
  deleteSetmeal,
  setmealStatusByStatus
} from '@/api/setMeal'
import { getCategoryList as getDishCategoryListApi } from '@/api/category'
import type { Category } from '@/api/types/category'
import type { SetmealVO } from '@/api/types/setmeal'
import Empty from '@/components/Empty/index.vue'
import Pagination from '@/components/Pagination/index.vue'
import { useTablePage } from '@/composables/useTablePage'
import { money } from '@/utils/format'

const router = useRouter()

const input = ref('')
const counts = ref<number>(0)
const { page, pageSize, handleSizeChange, handleCurrentChange } = useTablePage(() => init())
const checkList = ref<number[]>([])
const tableData = ref<SetmealVO[]>([])
const dishCategoryList = ref<{ value: number; label: string }[]>([])
const categoryId = ref<number | ''>('')
const dishStatus = ref<number | ''>('')
const isSearch = ref<boolean>(false)
const saleStatus = ref([
  {
    value: 0,
    label: '停售'
  },
  {
    value: 1,
    label: '启售'
  }
])

const initFun = () => {
  page.value = 1
  init()
}

async function init(isSearchVal?: boolean) {
  isSearch.value = isSearchVal ?? false
  const res = await getSetmealPage({
    page: page.value,
    pageSize: pageSize.value,
    name: input.value || undefined,
    categoryId: categoryId.value === '' ? undefined : categoryId.value,
    // status 不能写 || undefined:选"停售"(0)时 0 是 falsy,过滤条件会被整个丢掉
    status: dishStatus.value === '' ? undefined : dishStatus.value
  })
  tableData.value = res.records
  counts.value = res.total
}

// 添加更改
const addSetMeal = (st: string | number) => {
  if (st === 'add') {
    router.push({ path: '/setmeal/add' })
  } else {
    router.push({ path: '/setmeal/add', query: { id: String(st) } })
  }
}

// 删除
const deleteHandle = async (type: string, id?: number) => {
  if (type === '批量' && id === null) {
    if (checkList.value.length === 0) {
      return ElMessage.error('请选择删除对象')
    }
  }
  try {
    await ElMessageBox.confirm('确定删除该套餐?', '确定删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteSetmeal(type === '批量' ? checkList.value.join(',') : id ?? 0)
    ElMessage.success('删除成功！')
    init()
  } catch {
    // 用户取消操作
  }
}

//状态更改
const statusHandle = async (row: SetmealVO) => {
  try {
    await ElMessageBox.confirm('确认更改该套餐状态?', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await setmealStatusByStatus({ status: row.status === 1 ? 0 : 1, ids: row.id ?? 0 })
    ElMessage.success('套餐状态已经更改成功！')
    init()
  } catch {
    // 用户取消操作
  }
}

//获取套餐分类下拉数据
async function getDishCategoryList() {
  const res = await getDishCategoryListApi({
    type: 2
  })
  dishCategoryList.value = res.map((item: Category) => {
    return { value: item.id, label: item.name }
  })
}

// 全部操作
const handleSelectionChange = (val: SetmealVO[]) => {
  const checkArr: number[] = []
  val.forEach((n) => {
    checkArr.push(n.id ?? 0)
  })
  checkList.value = checkArr
}

init()
getDishCategoryList()
</script>
