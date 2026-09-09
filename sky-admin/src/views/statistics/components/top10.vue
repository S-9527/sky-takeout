<template>
  <div class="container top10">
    <h2 class="homeTitle">销量排名TOP10</h2>
    <div class="charBox">
      <Chart :option="chartOption" height="380px" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import Chart from '@/components/Chart/index.vue'
import { echarts, type EChartsOption } from '@/utils/echarts'
import type { SalesTop10Data } from '@/api/types/report'

const props = withDefaults(
  defineProps<{
    top10data?: SalesTop10Data
  }>(),
  {
    top10data: () => ({ nameList: [], numberList: [] })
  }
)

const chartOption = computed<EChartsOption>(() => ({
  tooltip: {
    trigger: 'axis',
    backgroundColor: '#fff',
    borderRadius: 2,
    textStyle: {
      color: '#333',
      fontSize: 12,
      fontWeight: 300
    }
  },
  grid: {
    top: '-10px',
    left: '0',
    right: '0',
    bottom: '0',
    containLabel: true
  },
  xAxis: {
    show: false
  },
  yAxis: {
    axisLine: {
      show: false
    },
    axisTick: {
      show: false,
      alignWithLabel: true
    },
    type: 'category',
    axisLabel: {
      color: '#666',
      fontSize: 12
    },
    data: props.top10data.nameList
  },
  series: [
    {
      data: props.top10data.numberList,
      type: 'bar',
      showBackground: true,
      backgroundStyle: {
        color: '#F3F4F7'
      },
      barWidth: 20,
      barGap: '80%',
      barCategoryGap: '80%',
      itemStyle: {
        borderRadius: [0, 10, 10, 0],
        color: new echarts.graphic.LinearGradient(1, 0, 0, 0, [
          { offset: 0, color: '#FFBD00' },
          { offset: 1, color: '#FFD000' }
        ])
      },
      label: {
        show: true,
        formatter: '{@score}',
        color: '#333',
        position: ['8', '5']
      }
    }
  ]
}))
</script>
