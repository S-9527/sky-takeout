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
import type { LineChartData } from '@/api/charts';

const props = withDefaults(defineProps<{
  className?: string
  id?: string
  width?: string
  height?: string
  title?: string
  chartData?: LineChartData
}>(), {
  className: 'chart',
  id: 'mixedChart',
  width: '100%',
  height: '250px',
  title: 'Requests',
  chartData: () => ({ xData: [], yData: [] })
})

const chart = ref<ReturnType<typeof echarts.init> | null>(null)

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
  chart.value.setOption({
    'backgroundColor': '#fff',
    'title': {
      'text': props.title,
      'top': '0',
      'textStyle': {
        'color': '#000',
        'fontSize': 18
      },
      'subtextStyle': {
        'color': '#90979c',
        'fontSize': 16
      }
    },
    'tooltip': {
      'trigger': 'axis'
    },
    'grid': {
      'left': '50',
      'right': '5%',
      'borderWidth': 0,
      'top': 60,
      'bottom': 35,
      'textStyle': {
        'color': '#fff'
      }
    },
    'xAxis': [{
      'type': 'category',
      'axisLine': {
        'lineStyle': {
          'color': '#90979c'
        }
      },
      'splitLine': {
        'show': false
      },
      'axisTick': {
        'show': true
      },
      'splitArea': {
        'show': false
      },
      'axisLabel': {
        'interval': 0,
        'rotate':-20
      },
      'data': props.chartData.xData
    }],
    'yAxis': [{
      'type': 'value',
      'splitLine': {
        'show': false
      },
      'axisLine': {
        'lineStyle': {
          'color': '#90979c'
        }
      },
      'axisTick': {
        'show': false
      },
      'axisLabel': {
        'interval': 0
      },
      'splitArea': {
        'show': false
      }
    }],
    'series': [{
      'name': '店内',
      'type': 'bar',
      'stack': 'total',
      'barMaxWidth': 15,
      'barGap': '10%',
      'itemStyle': {
        'borderRadius': [10, 10, 0, 0],
        'color': new echarts.graphic.LinearGradient(
          0, 0, 0, 1,
          [
            {'offset': 0, 'color': '#55A9FF'},
            {'offset': 1, 'color': '#379AFF'}
          ]
        )
      },
      'label': {
        'show': true,
        'textStyle': {
          'color': '#fff'
        },
        'position': 'insideTop',
        formatter(p: { value: number }) {
          return p.value > 0 ? String(p.value) : '';
        }
      },
      'data': props.chartData.yData
    }]
  } as echarts.EChartsOption)
}

init()
</script>