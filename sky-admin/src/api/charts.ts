import request from '@/utils/request'
/**
 *
 * 报表图表数据（日报/周报/月报）
 *
 **/

// 按单日查询
export interface ChartDateQuery {
  date: string
}

// 按类型+单日查询
export interface ChartTypeDateQuery extends ChartDateQuery {
  // 1 金额 2 数量
  type: number
}

// 按日期范围查询
export interface ChartRangeQuery {
  start: string
  end: string
}

// 按类型+日期范围查询
export interface ChartTypeRangeQuery extends ChartRangeQuery {
  // 1 金额 2 数量
  type: number
}

// 销售趋势/排行（折线、柱状图数据）
export interface TrendData {
  xaxis: string[]
  series: number[]
}

// 分类占比/收款构成条目
export interface SalesRankItem {
  name: string
  percent: number
  value: number
}

// 顶部汇总数据
export interface SalesSummary {
  payTotal: number
  noPayTotal: number
  totalPerson: number
}

// 优惠指标条目
export interface DiscountItem {
  name: string
  value: number
  percent: number
}

// 优惠指标汇总
export interface DiscountData {
  dataList: DiscountItem[]
}

// ========== 图表组件数据 ==========

// 折线图/柱状图数据
export interface LineChartData {
  xData: string[]
  yData: number[]
}

// 饼图数据
export interface PieChartData {
  legendData: string[]
  seriesData: SalesRankItem[]
  selected: Record<string, boolean>
}

// 获取当日销售趋势
export const getDayDataes = (params: ChartTypeDateQuery) =>
  request.get<TrendData>('/charts/dayDataes', { params })

// 获取当日菜品分类销售占比
export const getDayPayType = (params: ChartDateQuery) =>
  request.get<SalesRankItem[]>('/charts/dayPayType', { params })

// 获取优惠类型汇总
export const getprivilege = (params: ChartDateQuery) =>
  request.get<DiscountData>('/charts/privilege', { params })

// 获取当日菜品分类销售排行
export const getSalesRanking = (params: ChartTypeDateQuery) =>
  request.get<SalesRankItem[]>('/charts/salesRanking', { params })

// 获取当日菜品销售排行
export const getDayRanking = (params: ChartTypeDateQuery) =>
  request.get<TrendData>('/charts/dayRanking', { params })

// 获取当日销售汇总
export const getChartsDataes = (params: ChartRangeQuery) =>
  request.get<SalesSummary>('/charts/chartsDataes', { params })

// 时间范围之内的销售趋势
export const getTimeQuantumDataes = (params: ChartTypeRangeQuery) =>
  request.get<TrendData>('/charts/timeQuantumDataes', { params })

// 时间范围之内各种支付类型汇总
export const getTimeQuantumReceivables = (params: ChartRangeQuery) =>
  request.get<SalesRankItem[]>('/charts/timeQuantumReceivables', { params })

// 时间范围之内菜品类别销售汇总
export const getTimeQuantumType = (params: ChartTypeRangeQuery) =>
  request.get<SalesRankItem[]>('/charts/timeQuantumType', { params })

// 时间范围之内菜品销售排行
export const getTimeQuantumDishes = (params: ChartRangeQuery) =>
  request.get<TrendData>('/charts/timeQuantumDishes', { params })

// 时间范围之内优惠指标汇总
export const getTimeQuantumDiscount = (params: ChartRangeQuery) =>
  request.get<DiscountData>('/charts/timeQuantumDiscount', { params })