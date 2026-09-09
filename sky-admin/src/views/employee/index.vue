<template>
  <div class="dashboard-container">
    <div class="container">
      <div class="tableBar">
        <label style="margin-right: 5px">员工姓名：</label>
        <el-input
          v-model="input"
          placeholder="请输入员工姓名"
          style="width: 15%"
          clearable
          @clear="init"
          @keyup.enter="initFun"
        />
        <el-button class="normal-btn continue" @click="init(true)"
          >查询</el-button
        >
        <el-button
          type="primary"
          style="float: right"
          @click="addEmployeeHandle('add')"
        >
          + 添加员工
        </el-button>
      </div>
      <el-table
        v-if="tableData.length"
        :data="tableData"
        stripe
        class="tableBox"
      >
        <el-table-column prop="name" label="员工姓名" />
        <el-table-column prop="username" label="账号" />
        <el-table-column prop="phone" label="手机号" />
        <el-table-column label="账号状态">
          <template #default="scope">
            <div
              class="tableColumn-status"
              :class="{ 'stop-use': String(scope.row.status) === '0' }"
            >
              {{ String(scope.row.status) === '0' ? '禁用' : '启用' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="最后操作时间" />
        <el-table-column label="操作" width="160" align="center">
          <template #default="scope">
            <el-button
              link
              size="small"
              class="blueBug"
              :class="{ 'disabled-text': scope.row.username === 'admin' }"
              :disabled="scope.row.username === 'admin'"
              @click="addEmployeeHandle(scope.row.id, scope.row.username)"
            >
              修改
            </el-button>
            <el-button
              :disabled="scope.row.username === 'admin'"
              link
              size="small"
              class="non"
              :class="{
                'disabled-text': scope.row.username === 'admin',
                blueBug: scope.row.status == '0',
                delBut: scope.row.status != '0',
              }"
              @click="statusHandle(scope.row)"
            >
              {{ scope.row.status == '1' ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <Empty v-else :is-search="isSearch" />
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
import { getEmployeeList, enableOrDisableEmployee } from '@/api/employee'
import type { Employee } from '@/api/types/employee'
import Empty from '@/components/Empty/index.vue'
import Pagination from '@/components/Pagination/index.vue'
import { useTablePage } from '@/composables/useTablePage'

const router = useRouter()

const input = ref('')
const counts = ref<number>(0)
const { page, pageSize, handleSizeChange, handleCurrentChange } = useTablePage(() => init())
const tableData = ref<Employee[]>([])
const id = ref('')
const status = ref('')
const isSearch = ref<boolean>(false)

const initFun = () => {
  page.value = 1
  init()
}

async function init(isSearchVal?: boolean) {
  isSearch.value = isSearchVal ?? false
  const params = {
    page: page.value,
    pageSize: pageSize.value,
    name: input.value ? input.value : undefined,
  }
  await getEmployeeList(params)
    .then((res) => {
      tableData.value = res.records
      counts.value = res.total
    })
}

// 添加
const addEmployeeHandle = (st: string | number, username?: string) => {
  if (st === 'add') {
    router.push({ path: '/employee/add' })
  } else {
    if (username === 'admin') {
      return
    }
    router.push({ path: '/employee/add', query: { id: String(st) } })
  }
}

//状态修改
const statusHandle = (row: Employee) => {
  if (row.username === 'admin') {
    return
  }
  id.value = String(row.id)
  status.value = String(row.status)
  ElMessageBox.confirm('确认调整该账号的状态?', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    enableOrDisableEmployee({ id: Number(id.value), status: !status.value ? 1 : 0 })
      .then(() => {
        ElMessage.success('账号状态更改成功！')
        init()
      })
  })
}

init()
</script>

<style lang="scss" scoped>
.disabled-text {
  color: #bac0cd !important;
}
</style>
