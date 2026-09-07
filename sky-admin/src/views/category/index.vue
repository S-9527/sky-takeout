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
            <span>{{ scope.row.type == '1' ? '菜品分类' : '套餐分类' }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="sort"
                         label="排序" />
        <el-table-column label="状态">
          <template #default="scope">
            <div class="tableColumn-status"
                 :class="{ 'stop-use': String(scope.row.status) === '0' }">
              {{ String(scope.row.status) === '0' ? '禁用' : '启用' }}
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
                           blueBug: scope.row.status == '0',
                           delBut: scope.row.status != '0'
                         }"
                         @click="statusHandle(scope.row)">
                {{ scope.row.status == '1' ? '禁用' : '启用' }}
              </el-button>
            </div>
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
    <el-dialog :title="classData.title"
               v-model="classData.dialogVisible"
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
          <el-button v-if="action != 'edit'"
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
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import {
  getCategoryPage,
  deleCategory,
  editCategory,
  addCategory,
  enableOrDisableEmployee
} from '@/api/category'
import Empty from '@/components/Empty/index.vue'

const options: any = [
  {
    value: 1,
    label: '菜品分类'
  },
  {
    value: 2,
    label: '套餐分类'
  }
]
const actionType = ref('')
const id = ref('')
const status = ref('')
const categoryType = ref<any>(null)
const name = ref('')
const action = ref('')
const counts = ref(0)
const page = ref(1)
const pageSize = ref(10)
const tableData = ref<any[]>([])
const type = ref('')
const isSearch = ref(false)
const classData = reactive<any>({
  title: '添加菜品分类',
  dialogVisible: false,
  categoryId: '',
  name: '',
  sort: ''
})
const classDataRef = ref<FormInstance>()

const rules = computed(() => {
  return {
    name: [
      {
        required: true,
        trigger: 'blur',
        validator: (_rule: any, value: string, callback: Function) => {
          // const reg = /[\u4e00-\u9fa5]/
          var reg = new RegExp('^[A-Za-z\u4e00-\u9fa5]+$')
          if (!value) {
            callback(new Error(classData.title + '不能为空'))
          } else if (value.length < 2) {
            callback(new Error('分类名称输入不符，请输入2-20个字符'))
          } else if (!reg.test(value)) {
            callback(new Error('分类名称包含特殊字符'))
          } else {
            callback()
          }
        }
      }
    ],
    sort: [
      {
        required: true,
        trigger: 'blur',
        validator: (_rule: any, value: string, callback: Function) => {
          if (value || String(value) === '0') {
            const reg = /^\d+$/
            if (!reg.test(value)) {
              callback(new Error('排序只能输入数字类型'))
            } else if (Number(value) > 99) {
              callback(new Error('排序只能输入0-99数字'))
            } else {
              callback()
            }
          } else {
            callback(new Error('排序不能为空'))
          }
        }
      }
    ]
  }
})

init()

// 初始化信息
async function init(searchValue?: any) {
  isSearch.value = searchValue
  await getCategoryPage({
    page: page.value,
    pageSize: pageSize.value,
    name: name.value ? name.value : undefined,
    type: categoryType.value ? categoryType.value : undefined
  })
    .then(res => {
      if (String(res.data.code) === '1') {
        tableData.value =
          res && res.data && res.data.data && res.data.data.records
        counts.value = Number(res.data.data.total)
      } else {
        ElMessage.error(res.data.desc)
      }
    })
    .catch(err => {
      console.log(err, 'err')
      ElMessage.error('请求出错了：' + err.message)
    })
}

// 添加
const addClass = (st: any) => {
  if (st == 'class') {
    classData.title = '新增菜品分类'
    type.value = '1'
  } else {
    classData.title = '新增套餐分类'
    type.value = '2'
  }
  action.value = 'add'
  classData.name = ''
  classData.sort = ''
  classData.dialogVisible = true
  actionType.value = 'add'
}

// 修改
const editHandle = (dat: any) => {
  classData.title = '修改分类'
  action.value = 'edit'
  classData.name = dat.name
  classData.sort = dat.sort
  classData.id = dat.id
  classData.dialogVisible = true
  actionType.value = 'edit'
}

// 关闭弹窗
const handleClose = (_st: string) => {
  console.log(classDataRef.value, '$refs.classData')
  classData.dialogVisible = false
  //对该表单项进行重置，将其值重置为初始值并移除校验结果
  classDataRef.value?.resetFields()
}

//状态修改
const statusHandle = (row: any) => {
  id.value = row.id
  status.value = row.status
  ElMessageBox.confirm('确认调整该分类的状态?', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
    customClass: 'customClass'
  }).then(() => {
    enableOrDisableEmployee({ id: id.value, status: !status.value ? 1 : 0 })
      .then(res => {
        if (String(res.status) === '200') {
          ElMessage.success('分类状态更改成功！')
          init()
        }
      })
      .catch(err => {
        ElMessage.error('请求出错了：' + err.message)
      })
  })
}

//删除
const deleteHandle = (id: any) => {
  ElMessageBox.confirm('此操作将永久删除该分类，是否继续？', '确定删除', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    deleCategory(id)
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

//数据提交
const submitForm = (st?: any) => {
  if (action.value === 'add') {
    classDataRef.value?.validate((value: boolean) => {
      if (value) {
        addCategory({
          name: classData.name,
          type: type.value,
          sort: classData.sort
        })
          .then(res => {
            if (res.data.code === 1) {
              ElMessage.success('分类添加成功！')
              classDataRef.value?.resetFields()
              if (!st) {
                classData.dialogVisible = false
              }
              init()
            } else {
              ElMessage.error(res.data.desc || res.data.msg)
            }
          })
          .catch(err => {
            ElMessage.error('请求出错了：' + err.message)
          })
      }
    })
  } else {
    classDataRef.value?.validate((value: boolean) => {
      if (value) {
        editCategory({
          id: classData.id,
          name: classData.name,
          sort: classData.sort
        })
          .then(res => {
            if (res.data.code === 1) {
              ElMessage.success('分类修改成功！')
              classData.dialogVisible = false
              classDataRef.value?.resetFields()
              init()
            } else {
              ElMessage.error(res.data.desc || res.data.msg)
            }
          })
          .catch(err => {
            ElMessage.error('请求出错了：' + err.message)
          })
      }
    })
  }
}

//分页
const handleSizeChange = (val: any) => {
  pageSize.value = val
  init()
}

const handleCurrentChange = (val: any) => {
  page.value = val
  init()
}
</script>
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
        display: flex;
        margin-bottom: 20px;
        justify-content: space-between;
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
<style lang='scss'>
// .customClass {
//   .el-button--primary {
//     background-color: #ffc200 !important ;
//   }
// }
</style>
