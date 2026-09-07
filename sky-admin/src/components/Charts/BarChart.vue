<template>
  <div
    :id="id"
    :class="className"
    :style="{height: height, width: width}"
  />
</template>

<script setup lang="ts">
import * as echarts from 'echarts';
import { nextTick, onBeforeUnmount, ref, watch } from 'vue';
import useChartResize from './mixins/resize';

const props = withDefaults(defineProps<{
  className?: string
  id?: string
  width?: string
  height?: string
  title?: string
  chartData?: any
}>(), {
  className: 'chart',
  id: 'BarChart',
  width: '100%',
  height: '250px',
  title: 'Requests',
  chartData: () => ({})
})

const chart = ref<echarts.ECharts | null>(null)

useChartResize(() => chart.value)

watch(() => props.chartData, () => {
  init()
})

onBeforeUnmount(() => {
  if (!chart.value) {
    return
  }
  chart.value.dispose()
  chart.value = null
})

const init = () => {
  nextTick(() => {
    initChart()
  })
}

const initChart = () => {
  chart.value = echarts.init(document.getElementById(props.id) as HTMLDivElement)

  const data = props.chartData

  chart.value.setOption({
    'title': {
      'text': props.title,
      'left': 'left'
    },
    'tooltip': {
      'trigger': 'item',
      'formatter': '{a} <br/>{b} : {c} ({d}%)'
    },
    'legend': {
      'type': 'scroll',
      'orient': 'vertical',
      'left': 0,
      'top': 50,
      'bottom': 20,
      'data': data.legendData,
      'selected': data.selected
    },
    'series': [
      {
        'name': '占比',
        'type': 'pie',
        'radius': '65%',
        'left':80,
        'center': ['40%', '50%'],
        'data': data.seriesData,
        'emphasis': {
          'itemStyle': {
            'shadowBlur': 10,
            'shadowOffsetX': 0,
            'shadowColor': 'rgba(0, 0, 0, 0.5)'
          }
        },
        'itemStyle': {
          'normal': {
            'color': function (params:any) {
              const colorList = ['#389BFF', '#FFC200', '#52C41A', '#08979C', '#597EF7', '#B37FEB','#FF7875', '#5CDBD3', '#FFC53D'];
              return colorList[params.dataIndex]
            }
          }
        }
      }
    ]
  } as any)
}

init()
</script>