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
  UserReportVO,
} from './types'
// 订单管理
export const getOrderData = () =>
  request.get<OrderOverViewVO>('/workspace/overviewOrders')
// 菜品总览
export const getOverviewDishes = () =>
  request.get<DishOverViewVO>('/workspace/overviewDishes')
// 套餐总览
export const getSetMealStatistics = () =>
  request.get<SetmealOverViewVO>('/workspace/overviewSetmeals')
// 营业数据
export const getBusinessData = () =>
  request.get<BusinessDataVO>('/workspace/businessData')
/**
 *
 * 报表数据
 *
 **/
// 营业额统计
export const getTurnoverStatistics = (params: ReportQuery) =>
  request.get<TurnoverReportVO>('/report/turnoverStatistics', { params })

// 用户统计
export const getUserStatistics = (params: ReportQuery) =>
  request.get<UserReportVO>('/report/userStatistics', { params })
// 订单统计
export const getOrderStatistics = (params: ReportQuery) =>
  request.get<OrderReportVO>('/report/ordersStatistics', { params })
// 销量排名TOP10
export const getTop = (params: ReportQuery) =>
  request.get<SalesTop10ReportVO>('/report/top10', { params })
// 数据概览
export const getDataOverView = (params: ReportQuery) =>
  request.get<BusinessDataVO>('/report/dataOverView', { params })
// 导出
export function exportInfor() {
  return request.get<Blob>('/report/export', { responseType: 'blob' })
}