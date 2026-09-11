import type { OrderStats, TopDishSales, TurnoverStats, UserStats, Workbench } from '@/types'

import { get } from './request'

/**
 * 报表接口的公共查询参数。
 *
 * `beginDate` / `endDate` 都是 `YYYY-MM-DD`,**含首含尾**;都不传时后端默认最近 7 天。
 * 这里仍然显式传区间:让"页面上看到的区间"和"服务端算的区间"永远是同一个,不依赖默认值。
 */
export interface ReportRangeQuery {
  beginDate?: string
  endDate?: string
}

export function getTurnoverStats(query: ReportRangeQuery = {}): Promise<TurnoverStats> {
  return get('/api/v1/admin/insights/turnover-stats', { params: query })
}

export function getUserStats(query: ReportRangeQuery = {}): Promise<UserStats> {
  return get('/api/v1/admin/insights/user-stats', { params: query })
}

export function getOrderStats(query: ReportRangeQuery = {}): Promise<OrderStats> {
  return get('/api/v1/admin/insights/order-stats', { params: query })
}

export function getTopDishes(
  query: ReportRangeQuery & { topNumber?: number } = {},
): Promise<TopDishSales> {
  return get('/api/v1/admin/insights/top-dishes', { params: query })
}

/** 工作台概览:今日数据 + 订单/菜品计数("今日"按门店时区 Asia/Shanghai) */
export function getWorkbench(): Promise<Workbench> {
  return get('/api/v1/admin/insights/workbench')
}
