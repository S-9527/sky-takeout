<template>
  <div class="container">
    <h2 class="homeTitle">用户统计</h2>
    <div class="charBox">
      <Chart :option="chartOption" height="320px" />
      <ul class="orderListLine user">
        <li class="one"><span></span>用户总量（个）</li>
        <li class="three"><span></span>新增用户（个）</li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import Chart from '@/components/Chart/index.vue'
import type { EChartsOption } from '@/utils/echarts'
import type { UserStatisticsData } from '@/api/types'

const props = withDefaults(
  defineProps<{
    userdata?: UserStatisticsData
  }>(),
  {
    userdata: () => ({ dateList: [], totalUserList: [], newUserList: [] })
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
    top: '5%',
    left: '20',
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
    data: props.userdata.dateList
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
      name: '用户总量',
      type: 'line',
      smooth: false,
      showSymbol: false,
      symbolSize: 10,
      itemStyle: {
        color: '#FFD000'
      },
      lineStyle: {
        color: '#FFD000'
      },
      emphasis: {
        itemStyle: {
          color: '#fff',
          borderWidth: 5,
          borderColor: '#FFC100'
        }
      },
      data: props.userdata.totalUserList
    },
    {
      name: '新增用户',
      type: 'line',
      smooth: false,
      showSymbol: false,
      symbolSize: 10,
      itemStyle: {
        color: '#FD7F7F'
      },
      lineStyle: {
        color: '#FD7F7F'
      },
      emphasis: {
        itemStyle: {
          color: '#fff',
          borderWidth: 5,
          borderColor: '#FD7F7F'
        }
      },
      data: props.userdata.newUserList
    }
  ]
}))
</script>
