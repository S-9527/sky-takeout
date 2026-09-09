<template>
  <div class="dashboard-container">
    <div class="container">
      <div class="tableBar"
           style="display: inline-block; width: 100%">
        <label style="margin-right: 10px">分类名称：</label>
        <el-input v-model="name"
                  placeholder="请填写分类名称"
                  style="width: 15%"
                  clearable
                  @clear="init"
                  @keyup.enter="init" />

        <label style="margin-right: 5px; margin-left: 20px">分类类型：</label>
        <el-select v-model="categoryType"
                   placeholder="请选择"
                   clearable
                   style="width: 15%"
                   @clear="init">
          <el-option v-for="item in options"
                     :key="item.value"
                     :label="item.label"
                     :value="item.value" />
        </el-select>

        <div style="float: right">
          <el-button type="primary"
                     class="continue"
                     @click="addClass('class')">
            + 新增菜品分类
          </el-button>
          <el-button type="primary"
                     style="margin-left:20px"
                     @click="addClass('meal')">
            + 新增套餐分类
          </el-button>
        </div>

        <el-button class="normal-btn continue"
                   @click="init(true)">
          查询
        </el-button>
      </div>
      <el-table v-if="tableData.length"
                :data="tableData"
                stripe
                class="tableBox">
        <el-table-column prop="name"
                         label="分类名称" />
        <el-table-column prop="type"
                         label="分类类型">
          <template #default="scope">
            <span>{{ scope.row.type === 1 ? '菜品分类' : '套餐分类' }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="sort"
                         label="排序" />
        <el-table-column label="状态">
          <template #default="scope">
            <div class="tableColumn-status"
                 :class="{ 'stop-use': scope.row.status === 0 }">
              {{ scope.row.status === 0 ? '禁用' : '启用' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime"
                         label="操作时间" />
        <el-table-column label="操作"
                         width="200"
                         align="center">
          <template #default="scope">
            <div class="table-ops">
              <el-button link
                         size="small"
                         class="blueBug"
                         @click="editHandle(scope.row)">
                修改
              </el-button>
              <el-button link
                         size="small"
                         class="delBut"
                         @click="deleteHandle(scope.row.id)">
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
                {{ scope.row.status === 1 ? '禁用' : '启用' }}
              </el-button>
            </div>
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
    <el-dialog v-model="classData.dialogVisible"
               :title="classData.title"
               class="el-dialog--wide"
               width="30%"
               :before-close="handleClose">
      <el-form ref="classDataRef"
               :model="classData"
               class="demo-form-inline"
               :rules="rules"
               label-width="100px">
        <el-form-item label="分类名称："
                      prop="name">
          <el-input v-model="classData.name"
                    placeholder="请输入分类名称"
                    maxlength="20" />
        </el-form-item>
        <el-form-item label="排序："
                      prop="sort">
          <el-input v-model="classData.sort"
                    placeholder="请输入排序" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button size="default"
                     @click="
            ;(classData.dialogVisible = false), classDataRef?.resetFields()
                     ">取 消</el-button>
          <el-button type="primary"
                     :class="{ continue: actionType === 'add' }"
                     size="default"
                     @click="submitForm()">确 定</el-button>
          <el-button v-if="action !== 'edit'"
                     type="primary"
                     size="default"
                     @click="submitForm('go')">
            保存并继续添加
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getCategoryPage,
  deleCategory,
  editCategory,
  addCategory,
  enableOrDisableCategory
} from '@/api/category'
import Empty from '@/components/Empty/index.vue'
import Pagination from '@/components/Pagination/index.vue'
import { useTablePage } from '@/composables/useTablePage'
import type { Category } from '@/api/types/category'
import { nameRule, sortRule } from '@/utils/formRules'

const options: { value: number; label: string }[] = [
  {
    value: 1,
    label: '菜品分类'
  },
  {
    value: 2,
    label: '套餐分类'
  }
]
interface CategoryForm {
  title: string
  dialogVisible: boolean
  id: string
  name: string
  sort: string
}

const actionType = ref('')
const categoryType = ref<number | null>(null)
const name = ref('')
const action = ref('')
const counts = ref(0)
const { page, pageSize, handleSizeChange, handleCurrentChange } = useTablePage(() => init())
const tableData = ref<Category[]>([])
const type = ref(1)
const isSearch = ref(false)
const classData = reactive<CategoryForm>({
  title: '添加菜品分类',
  dialogVisible: false,
  id: '',
  name: '',
  sort: ''
})
const classDataRef = ref<FormInstance>()

const rules: FormRules = {
  name: [nameRule('分类名称')],
  sort: [sortRule('排序')]
}

init()

// 初始化信息
async function init(searchValue?: boolean) {
  isSearch.value = searchValue ?? false
  await getCategoryPage({
    page: page.value,
    pageSize: pageSize.value,
    name: name.value ? name.value : undefined,
    type: categoryType.value ? categoryType.value : undefined
  })
    .then(res => {
      tableData.value = res.records
      counts.value = res.total
    })
}

// 添加
const addClass = (st: string) => {
  if (st === 'class') {
    classData.title = '新增菜品分类'
    type.value = 1
  } else {
    classData.title = '新增套餐分类'
    type.value = 2
  }
  action.value = 'add'
  classData.name = ''
  classData.sort = ''
  classData.dialogVisible = true
  actionType.value = 'add'
}

// 修改
const editHandle = (dat: Category) => {
  classData.title = '修改分类'
  action.value = 'edit'
  classData.name = dat.name
  classData.sort = String(dat.sort)
  classData.id = String(dat.id)
  classData.dialogVisible = true
  actionType.value = 'edit'
}

// 关闭弹窗
const handleClose = (_st: string) => {
  classData.dialogVisible = false
  //对该表单项进行重置，将其值重置为初始值并移除校验结果
  classDataRef.value?.resetFields()
}

//状态修改
const statusHandle = (row: Category) => {
  ElMessageBox.confirm('确认调整该分类的状态?', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
    customClass: 'customClass'
  }).then(() => {
    enableOrDisableCategory({ id: row.id, status: row.status === 1 ? 0 : 1 })
      .then(() => {
        ElMessage.success('分类状态更改成功！')
        init()
      })
  })
}

//删除
const deleteHandle = (id: number) => {
  ElMessageBox.confirm('此操作将永久删除该分类，是否继续？', '确定删除', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    deleCategory(id)
      .then(() => {
        ElMessage.success('删除成功！')
        init()
      })
  })
}

//数据提交
const submitForm = (st?: string) => {
  if (action.value === 'add') {
    classDataRef.value?.validate((value: boolean) => {
      if (value) {
        addCategory({
          name: classData.name,
          type: type.value,
          sort: Number(classData.sort)
        })
          .then(() => {
            ElMessage.success('分类添加成功！')
            classDataRef.value?.resetFields()
            if (!st) {
              classData.dialogVisible = false
            }
            init()
          })
      }
    })
  } else {
    classDataRef.value?.validate((value: boolean) => {
      if (value) {
        editCategory({
          id: Number(classData.id),
          name: classData.name,
          type: type.value,
          sort: Number(classData.sort)
        })
          .then(() => {
            ElMessage.success('分类修改成功！')
            classData.dialogVisible = false
            classDataRef.value?.resetFields()
            init()
          })
      }
    })
  }
}
</script>
<style lang="scss" scoped>
// 页面外壳的通用部分见 styles/component/page-shell.scss
.dashboard {
  &-container {
    .container {
      // 查询条件与操作按钮左右分栏
      .tableBar {
        display: flex;
        justify-content: space-between;
      }
    }
  }
}
</style>
