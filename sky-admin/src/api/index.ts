import request from '@/utils/request'
import type {
  BusinessDataVO,
  DishOverViewVO,
  OrderOverViewVO,
  OrderReportVO,
  ReportQuery,
  SalesTop10ReportVO,
  SetmealOverViewVO,
  TurnoverReportVO,
  UserReportVO
} from './types'

// ========== 工作台 ==========

// 订单概览
export const getWorkspaceOrderOverview = () =>
  request.get<OrderOverViewVO>('/workspace/overviewOrders')

// 菜品总览
export const getWorkspaceDishOverview = () =>
  request.get<DishOverViewVO>('/workspace/overviewDishes')

// 套餐总览
export const getWorkspaceSetmealOverview = () =>
  request.get<SetmealOverViewVO>('/workspace/overviewSetmeals')

// 今日经营数据
export const getWorkspaceBusinessData = () =>
  request.get<BusinessDataVO>('/workspace/businessData')

// ========== 报表 ==========

// 营业额统计
export const getTurnoverStatistics = (params: ReportQuery) =>
  request.get<TurnoverReportVO>('/report/turnoverStatistics', { params })

// 用户统计
export const getUserStatistics = (params: ReportQuery) =>
  request.get<UserReportVO>('/report/userStatistics', { params })

// 订单统计
export const getOrderStatistics = (params: ReportQuery) =>
  request.get<OrderReportVO>('/report/ordersStatistics', { params })

// 销量排名 Top10
export const getSalesTop10 = (params: ReportQuery) =>
  request.get<SalesTop10ReportVO>('/report/top10', { params })

// 导出报表
export function exportReport() {
  return request.get<Blob>('/report/export', { responseType: 'blob' })
}
