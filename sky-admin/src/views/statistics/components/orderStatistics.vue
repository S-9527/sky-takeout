<template>
  <div class="container">
    <h2 class="homeTitle">订单统计</h2>
    <div class="charBox">
      <div class="orderProportion">
        <div>
          <p>订单完成率</p>
          <p>{{ (orderdata.orderCompletionRate * 100).toFixed(1) }}%</p>
        </div>
        <div class="symbol">=</div>
        <div>
          <p>有效订单</p>
          <p>{{ orderdata.validOrderCount }}</p>
        </div>
        <div class="symbol">/</div>
        <div>
          <p>订单总数</p>
          <p>{{ orderdata.totalOrderCount }}</p>
        </div>
      </div>
      <Chart :option="chartOption" height="300px" />
      <ul class="orderListLine">
        <li class="one"><span></span>订单总数（个）</li>
        <li class="three"><span></span>有效订单（个）</li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import Chart from '@/components/Chart/index.vue'
import type { EChartsOption } from '@/utils/echarts'
import type { OrderReportChartData } from '@/api/types'

const props = withDefaults(
  defineProps<{
    orderdata?: OrderReportChartData
    overviewData?: unknown
  }>(),
  {
    orderdata: () => ({
      data: { dateList: [], orderCountList: [], validOrderCountList: [] },
      totalOrderCount: 0,
      validOrderCount: 0,
      orderCompletionRate: 0
    }),
    overviewData: () => ({})
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
    data: props.orderdata.data.dateList
  },
  yAxis: [
    {
      type: 'value',
      min: 0,
      interval: 50,
      axisLabel: {
        color: '#666',
        fontSize: 12
      }
    }
  ],
  series: [
    {
      name: '订单总数',
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
      data: props.orderdata.data.orderCountList
    },
    {
      name: '有效订单',
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
      data: props.orderdata.data.validOrderCountList
    }
  ]
}))
</script>
