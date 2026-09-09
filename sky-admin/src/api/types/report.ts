// 报表与工作台相关类型

// 报表查询区间
export interface ReportQuery {
  begin: string
  end: string
}

// 营业额统计
export interface TurnoverReportVO {
  // 日期，逗号分隔
  dateList: string
  // 营业额，逗号分隔
  turnoverList: string
}

// 用户统计
export interface UserReportVO {
  // 日期，逗号分隔
  dateList: string
  // 用户总量，逗号分隔
  totalUserList: string
  // 新增用户，逗号分隔
  newUserList: string
}

// 订单统计
export interface OrderReportVO {
  // 日期，逗号分隔
  dateList: string
  // 每日订单数，逗号分隔
  orderCountList: string
  // 每日有效订单数，逗号分隔
  validOrderCountList: string
  // 订单总数
  totalOrderCount: number
  // 有效订单数
  validOrderCount: number
  // 订单完成率
  orderCompletionRate: number
}

// 销量排名 Top10
export interface SalesTop10ReportVO {
  // 商品名称列表，逗号分隔
  nameList: string
  // 销量列表，逗号分隔
  numberList: string
}

// 营业额统计（图表数据）
export interface TurnoverStatisticsData {
  // 日期（按天）
  dateList: string[]
  // 营业额
  turnoverList: string[]
}

// 用户统计（图表数据）
export interface UserStatisticsData {
  // 日期（按天）
  dateList: string[]
  // 用户总量
  totalUserList: string[]
  // 新增用户数
  newUserList: string[]
}

// 订单统计（图表数据）
export interface OrderReportChartData {
  data: {
    // 日期（按天）
    dateList: string[]
    // 订单总数
    orderCountList: string[]
    // 有效订单数
    validOrderCountList: string[]
  }
  // 订单总数
  totalOrderCount: number
  // 有效订单数
  validOrderCount: number
  // 订单完成率
  orderCompletionRate: number
}

// 销量排名 Top10（图表数据）
export interface SalesTop10Data {
  // 商品名称
  nameList: string[]
  // 销量
  numberList: string[]
}

// 今日数据（工作台）
export interface BusinessDataVO {
  // 营业额
  turnover: number
  // 有效订单数
  validOrderCount: number
  // 订单完成率
  orderCompletionRate: number
  // 平均客单价
  unitPrice: number
  // 新增用户数
  newUsers: number
}

// 订单概览（工作台）
export interface OrderOverViewVO {
  // 待接单数量
  waitingOrders: number
  // 待派送数量
  deliveredOrders: number
  // 已完成数量
  completedOrders: number
  // 已取消数量
  cancelledOrders: number
  // 全部订单
  allOrders: number
}

// 菜品总览（工作台）
export interface DishOverViewVO {
  // 已启售数量
  sold: number
  // 已停售数量
  discontinued: number
}

// 套餐总览（工作台）
export interface SetmealOverViewVO {
  // 已启售数量
  sold: number
  // 已停售数量
  discontinued: number
}
