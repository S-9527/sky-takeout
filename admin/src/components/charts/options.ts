import type { EChartsOption } from 'echarts'

import type { DishSalesItem, OrderStatsItem, TurnoverStatsItem, UserStatsItem } from '@/types'
import { centsToYuan } from '@/utils/money'

/**
 * 报表图表配置。
 *
 * 单独成文件的原因:这些映射(数据 → 坐标系)是纯函数,不含生命周期,
 * 放在视图里只会把 `.vue` 撑长;抽出来后视图只负责取数与布局。
 */

const AXIS_LABEL_COLOR = '#64748b'

function categoryAxis(dates: string[]) {
  return {
    type: 'category' as const,
    data: dates,
    axisLabel: { color: AXIS_LABEL_COLOR },
    axisLine: { lineStyle: { color: '#e2e8f0' } },
  }
}

function valueAxis(name?: string) {
  return {
    type: 'value' as const,
    name,
    axisLabel: { color: AXIS_LABEL_COLOR },
    splitLine: { lineStyle: { color: '#f1f5f9' } },
  }
}

/** 营业额:日营业额折线(元) */
export function turnoverOption(daily: TurnoverStatsItem[]): EChartsOption {
  return {
    grid: { left: 64, right: 24, top: 32, bottom: 32 },
    tooltip: { trigger: 'axis', valueFormatter: (value) => `¥${Number(value).toFixed(2)}` },
    xAxis: categoryAxis(daily.map((item) => item.date)),
    yAxis: valueAxis('元'),
    series: [
      {
        name: '营业额',
        type: 'line',
        smooth: true,
        areaStyle: { opacity: 0.15 },
        itemStyle: { color: '#2563eb' },
        data: daily.map((item) => Number(centsToYuan(item.revenueCents).toFixed(2))),
      },
    ],
  }
}

/** 用户统计:新增顾客柱 + 累计顾客折线(双轴) */
export function userOption(daily: UserStatsItem[]): EChartsOption {
  return {
    grid: { left: 56, right: 56, top: 40, bottom: 32 },
    tooltip: { trigger: 'axis' },
    legend: { data: ['新增顾客', '累计顾客'], top: 0 },
    xAxis: categoryAxis(daily.map((item) => item.date)),
    yAxis: [valueAxis('新增'), { ...valueAxis('累计'), splitLine: { show: false } }],
    series: [
      {
        name: '新增顾客',
        type: 'bar',
        itemStyle: { color: '#0ea5e9' },
        data: daily.map((item) => item.newUserCount),
      },
      {
        name: '累计顾客',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        itemStyle: { color: '#f97316' },
        data: daily.map((item) => item.totalUserCount ?? 0),
      },
    ],
  }
}

/** 订单统计:总订单/有效订单柱状 */
export function orderOption(daily: OrderStatsItem[]): EChartsOption {
  return {
    grid: { left: 56, right: 24, top: 40, bottom: 32 },
    tooltip: { trigger: 'axis' },
    legend: { data: ['订单总数', '有效订单'], top: 0 },
    xAxis: categoryAxis(daily.map((item) => item.date)),
    yAxis: valueAxis('单'),
    series: [
      {
        name: '订单总数',
        type: 'bar',
        itemStyle: { color: '#94a3b8' },
        data: daily.map((item) => item.totalOrderCount),
      },
      {
        name: '有效订单',
        type: 'bar',
        itemStyle: { color: '#22c55e' },
        data: daily.map((item) => item.validOrderCount),
      },
    ],
  }
}

/** 销量排行:横向条形图(名次从 1 开始,倒序让第一名在上方) */
export function topDishOption(items: DishSalesItem[]): EChartsOption {
  const ordered = [...items].reverse()
  return {
    grid: { left: 96, right: 48, top: 16, bottom: 24 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: valueAxis(),
    yAxis: {
      type: 'category' as const,
      data: ordered.map((item) => item.name),
      axisLabel: { color: AXIS_LABEL_COLOR },
      axisLine: { lineStyle: { color: '#e2e8f0' } },
    },
    series: [
      {
        name: '销量(份)',
        type: 'bar',
        itemStyle: { color: '#8b5cf6' },
        label: { show: true, position: 'right' },
        data: ordered.map((item) => item.copies),
      },
    ],
  }
}
