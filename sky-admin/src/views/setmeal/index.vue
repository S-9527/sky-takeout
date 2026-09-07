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
          <!-- <span class="blueBug non" @click="statusHandle('1')">批量启售</span>
          <span
            style="border: none"
            class="delBut non"
            @click="statusHandle('0')"
            >批量停售</span
          > -->
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
            <span>￥{{ ((scope.row.price ).toFixed(2) * 100) / 100 }}</span>
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
                         label="最后操作时间">
          <!-- <template slot-scope="scope">
            {{ moment(scope.row.lastUpdateTime).format('YYYY-MM-DD h:m:s') }}
          </template> -->
        </el-table-column>
        <el-table-column label="操作"
                         width="250"
                         align="center">
          <template #default="scope">
            <el-button link
                       size="small"
                       class="blueBug"
                       @click="addSetMeal(scope.row)">
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
                         blueBug: scope.row.status == '0',
                         delBut: scope.row.status != '0'
                       }"
                       @click="statusHandle(scope.row)">
              {{ scope.row.status == '0' ? '启售' : '停售' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <Empty v-else
             :is-search="isSearch" />
      <el-pagination v-if="counts > 10"
                     class="pageList"
                     :page-sizes="[10, 20, 30, 40]"
                     :page-size="pageSize"
                     layout="total, sizes, prev, pager, next, jumper"
                     :total="counts"
                     @size-change="handleSizeChange"
                     @current-change="handleCurrentChange" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import moment from 'moment'
import {
  getSetmealPage,
  deleteSetmeal,
  setmealStatusByStatus,
  dishCategoryList as getDishCategoryListApi
} from '@/api/setMeal'
import Empty from '@/components/Empty/index.vue'

const router = useRouter()

const input = ref<any>('')
const counts = ref<number>(0)
const page = ref<number>(1)
const pageSize = ref<number>(10)
const checkList = ref<any[]>([])
const tableData = ref<any[]>([])
const dishCategoryList = ref<any[]>([])
const categoryId = ref('')
const dishStatus = ref('')
const isSearch = ref<boolean>(false)
const saleStatus = ref<any>([
  {
    value: 0,
    label: '停售'
  },
  {
    value: 1,
    label: '启售'
  }
])

const initProp = (val: any) => {
  input.value = val
  initFun()
}

const initFun = () => {
  page.value = 1
  init()
}

async function init(isSearchVal?: boolean) {
  isSearch.value = isSearchVal
  await getSetmealPage({
    page: page.value,
    pageSize: pageSize.value,
    name: input.value || undefined,
    categoryId: categoryId.value || undefined,
    status: dishStatus.value
  })
    .then(res => {
      if (res && res.data && res.data.code === 1) {
        tableData.value = res.data.data.records
        counts.value = Number(res.data.data.total)
      } else {
        ElMessage.error(res.data.msg)
      }
    })
    .catch(err => {
      ElMessage.error('请求出错了：' + err.message)
    })
}

// 添加更改
const addSetMeal = (st: any) => {
  if (st === 'add') {
    router.push({ path: '/setmeal/add' })
  } else {
    router.push({ path: '/setmeal/add', query: { id: st.id } })
  }
}

// 删除
const deleteHandle = (type: string, id: any) => {
  if (type === '批量' && id === null) {
    if (checkList.value.length === 0) {
      return ElMessage.error('请选择删除对象')
    }
  }
  ElMessageBox.confirm('确定删除该套餐?', '确定删除', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    deleteSetmeal(type === '批量' ? checkList.value.join(',') : id)
      .then(res => {
        if (res.data.code === 1) {
          ElMessage.success('删除成功！')
          init()
        } else {
          ElMessage.error(res.data.msg)
        }
      })
      .catch(err => {
        ElMessage.error('请求出错了：' + err.message)
      })
  })
}

//状态更改
const statusHandle = (row: any) => {
  let params: any = {}
  if (typeof row === 'string') {
    if (checkList.value.length == 0) {
      ElMessage.error('批量操作，请先勾选操作菜品！')
      return false
    }
    params.ids = checkList.value.join(',')
    params.status = row
  } else {
    params.ids = row.id
    params.status = row.status ? '0' : '1'
  }

  ElMessageBox.confirm('确认更改该套餐状态?', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    setmealStatusByStatus(params)
      .then(res => {
        if (res.data.code === 1) {
          ElMessage.success('套餐状态已经更改成功！')
          init()
        } else {
          ElMessage.error(res.data.msg)
        }
      })
      .catch(err => {
        ElMessage.error('请求出错了：' + err.message)
      })
  })
}

//获取套餐分类下拉数据
const getDishCategoryList = () => {
  getDishCategoryListApi({
    type: 2
  })
    .then(res => {
      if (res && res.data && res.data.code === 1) {
        dishCategoryList.value = (
          res.data &&
          res.data.data &&
          res.data.data
        ).map((item: any) => {
          return { value: item.id, label: item.name }
        })
      }
    })
    .catch(() => {})
}

// 全部操作
const handleSelectionChange = (val: any) => {
  let checkArr: string[] = []
  val.forEach((n: any) => {
    checkArr.push(n.id)
  })
  checkList.value = checkArr
}

const handleSizeChange = (val: any) => {
  pageSize.value = val
  init()
}

const handleCurrentChange = (val: any) => {
  page.value = val
  init()
}

init()
getDishCategoryList()
</script>
<style lang="scss">
.el-table-column--selection .cell {
  padding-left: 10px;
}
</style>
<style lang="scss" scoped>
.dashboard {
  &-container {
    margin: 30px;

    .container {
      background: #fff;
      position: relative;
      z-index: 1;
      padding: 30px 28px;
      border-radius: 4px;

      .tableBar {
        margin-bottom: 20px;
        .tableLab {
          float: right;
          span {
            cursor: pointer;
            display: inline-block;
            font-size: 14px;
            padding: 0 20px;
            color: $gray-2;
          }
        }
      }

      .tableBox {
        width: 100%;
        border: 1px solid $gray-5;
        border-bottom: 0;
      }

      .pageList {
        text-align: center;
        margin-top: 30px;
      }
      //查询黑色按钮样式
      .normal-btn {
        background: #333333;
        color: white;
        margin-left: 20px;
      }
    }
  }
}
</style>
