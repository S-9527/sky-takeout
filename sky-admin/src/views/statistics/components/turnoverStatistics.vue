<template>
  <div class="container">
    <h2 class="homeTitle">营业额统计</h2>
    <div class="charBox">
      <Chart :option="chartOption" height="320px" />
      <ul class="orderListLine turnover">
        <li>营业额(元)</li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import Chart from '@/components/Chart/index.vue'
import type { EChartsOption } from '@/utils/echarts'
import type { TurnoverStatisticsData } from '@/api/types'

const props = withDefaults(
  defineProps<{
    turnoverdata?: TurnoverStatisticsData
  }>(),
  {
    turnoverdata: () => ({ dateList: [], turnoverList: [] })
  }
)

const chartOption = computed<EChartsOption>(() => ({
  tooltip: {
    trigger: 'axis'
  },
  grid: {
    top: '5%',
    left: '10',
    right: '50',
    bottom: '12%',
    containLabel: true
  },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    axisLabel: {
      color: '#666',
      fontSize: 12
    },
    axisLine: {
      lineStyle: {
        color: '#E5E4E4',
        width: 1
      }
    },
    data: props.turnoverdata.dateList
  },
  yAxis: [
    {
      type: 'value',
      min: 0,
      axisLabel: {
        color: '#666',
        fontSize: 12
      }
    }
  ],
  series: [
    {
      name: '营业额',
      type: 'line',
      smooth: false,
      showSymbol: false,
      symbolSize: 10,
      itemStyle: {
        color: '#FFD000',
        borderColor: '#FFC100',
        borderWidth: 5
      },
      data: props.turnoverdata.turnoverList
    }
  ]
}))
</script>
