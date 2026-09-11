<script setup lang="ts">
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import * as echarts from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

import type { EChartsOption } from 'echarts'

/**
 * ECharts 容器。
 *
 * 只做三件事:挂载时实例化、`option` 变化时更新、卸载时销毁(不销毁会漏 canvas 与 resize 监听)。
 * 图表配置由调用方构造,这里不认识任何业务字段 —— 报表口径都在 API 与视图里。
 *
 * 按需注册而不是 `import * as echarts from 'echarts'`:
 * 全量引入会把整包(含地图、关系图、3D 等)打进产物,报表页首屏要多下 ~700KB,
 * 而这里实际只用到折线/柱状 + 网格/图例/提示框。
 */
echarts.use([LineChart, BarChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const props = withDefaults(
  defineProps<{
    option: EChartsOption
    height?: string
    loading?: boolean
  }>(),
  { height: '280px', loading: false },
)

const container = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

function render(): void {
  if (!chart) return
  // notMerge:切换统计区间时系列会变少,合并会把上一次的残留画出来
  chart.setOption(props.option, true)
}

function resize(): void {
  chart?.resize()
}

onMounted(() => {
  if (!container.value) return
  chart = echarts.init(container.value)
  render()
  window.addEventListener('resize', resize)
})

watch(() => props.option, render, { deep: true })

watch(
  () => props.loading,
  (value) => {
    if (!chart) return
    if (value) chart.showLoading()
    else chart.hideLoading()
  },
)

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div ref="container" :style="{ height: props.height, width: '100%' }" data-testid="chart" />
</template>
