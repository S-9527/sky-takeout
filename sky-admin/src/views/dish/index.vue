<template>
  <div class="dashboard-container">
    <div class="container">
      <div class="tableBar">
        <label style="margin-right: 10px">菜品名称：</label>
        <el-input v-model="input"
                  placeholder="请填写菜品名称"
                  style="width: 14%"
                  clearable
                  @clear="init"
                  @keyup.enter="initFun" />

        <label style="margin-right: 10px; margin-left: 20px">菜品分类：</label>
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
                     @click="addDishtype('add')">
            + 新建菜品
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
                         label="菜品名称" />
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
                         label="菜品分类" />
        <el-table-column label="售价">
          <template #default="scope">
            <span style="margin-right: 10px">￥{{ Number((scope.row.price ?? 0).toFixed(2))*100/100 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="售卖状态">
          <template #default="scope">
            <div class="tableColumn-status"
                 :class="{ 'stop-use': String(scope.row.status) === '0' }">
              {{ String(scope.row.status) === '0' ? '停售' : '启售' }}
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
                       @click="addDishtype(scope.row.id)">
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
                       class="non"
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
  getDishPage,
  deleteDish,
  dishStatusByStatus
} from '@/api/dish'
import { getCategoryList as dishCategoryListApi } from '@/api/category'
import type { DishVO } from '@/api/types/dish'
import Empty from '@/components/Empty/index.vue'
import Pagination from '@/components/Pagination/index.vue'
import { useTablePage } from '@/composables/useTablePage'

const router = useRouter()
const input = ref('')
const counts = ref(0)
const { page, pageSize, handleSizeChange, handleCurrentChange } = useTablePage(() => init())
const checkList = ref<Array<string | number>>([])
const tableData = ref<DishVO[]>([])
const dishCategoryList = ref<{ value: number; label: string }[]>([])
const categoryId = ref<number | ''>('')
const dishStatus = ref<number | ''>('')
const isSearch = ref(false)
const saleStatus = [
  {
    value: 0,
    label: '停售'
  },
  {
    value: 1,
    label: '启售'
  }
]

init()
getDishCategoryList()

const initFun = () => {
  page.value = 1
  init()
}

async function init(searchValue?: boolean) {
  isSearch.value = searchValue ?? false
  await getDishPage({
    page: page.value,
    pageSize: pageSize.value,
    name: input.value || undefined,
    categoryId: categoryId.value === '' ? undefined : categoryId.value,
    // status 不能写 || undefined:选"停售"(0)时 0 是 falsy,过滤条件会被整个丢掉
    status: dishStatus.value === '' ? undefined : dishStatus.value
  })
    .then(res => {
      tableData.value = res.records
      counts.value = res.total
    })
}

// 添加
const addDishtype = (st: string | number) => {
  if (st === 'add') {
    router.push({ path: '/dish/add' })
  } else {
    router.push({ path: '/dish/add', query: { id: String(st) } })
  }
}

// 删除
const deleteHandle = (type: string, id?: number | string) => {
  if (type === '批量' && id === null) {
    if (checkList.value.length === 0) {
      return ElMessage.error('请选择删除对象')
    }
  }
  ElMessageBox.confirm('确认删除该菜品, 是否继续?', '确定删除', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    deleteDish(type === '批量' ? checkList.value.join(',') : id ?? 0)
      .then(() => {
        ElMessage.success('删除成功！')
        init()
      })
  })
}
//获取菜品分类下拉数据
function getDishCategoryList() {
  dishCategoryListApi({
    type: 1
  })
    .then(res => {
      dishCategoryList.value = res.map((item) => {
        return { value: item.id, label: item.name }
      })
    })
}

//状态更改
const statusHandle = (row: DishVO) => {
  ElMessageBox.confirm('确认更改该菜品状态?', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    // 起售停售---批量起售停售接口
    dishStatusByStatus({ status: row.status === 1 ? 0 : 1, id: row.id ?? 0 })
      .then(() => {
        ElMessage.success('菜品状态已经更改成功！')
        init()
      })
  })
}

// 全部操作
const handleSelectionChange = (val: DishVO[]) => {
  const checkArr: Array<string | number> = []
  val.forEach((n) => {
    checkArr.push(n.id ?? 0)
  })
  checkList.value = checkArr
}
</script>
