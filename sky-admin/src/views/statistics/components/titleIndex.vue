<template>
  <div class="title-index">
    <div class="month">
      <ul class="tabs">
        <li
          class="li-tab"
          v-for="(item, index) in tabsParam"
          @click="toggleTabs(index)"
          :class="{ active: index === nowIndex }"
          :key="index"
        >
          {{ item }}
          <span></span>
        </li>
      </ul>
    </div>
    <div class="get-time">
      <p>
        已选时间：{{ tateData[0] }} 至
        {{ tateData[tateData.length - 1] }}
      </p>
    </div>
    <el-button
      class="right-el-button"
      @click="handleExport"
    >
      <i class="iconfont icon-download"></i>数据导出
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { exportInfor } from '@/api/index'

const props = withDefaults(
  defineProps<{
    flag?: number
    tateData?: string[]
    turnoverData?: any
  }>(),
  {
    flag: 0,
    tateData: () => [],
    turnoverData: () => ({})
  }
)
const emit = defineEmits(['sendTitleInd'])

const nowIndex = ref(2 - 1)
const value = ref<any[]>([])
const tabsParam = ref(['昨日', '近7日', '近30日', '本周', '本月'])

watch(
  () => props.flag,
  (val) => {
    nowIndex.value = val
  }
)
// tab切换
function toggleTabs(index: number) {
  nowIndex.value = index
  value.value = []
  emit('sendTitleInd', index + 1)
}
//  数据导出
/** 导出按钮操作 */
function handleExport() {
  ElMessageBox.confirm('是否确认导出最近30天运营数据?', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async function () {
      const data = await exportInfor()
      let url = window.URL.createObjectURL(data)
      var a = document.createElement('a')
      document.body.appendChild(a)
      a.href = url
      a.download = '运营数据统计报表.xlsx'
      a.click()
      window.URL.revokeObjectURL(url)
    })
}
</script>
