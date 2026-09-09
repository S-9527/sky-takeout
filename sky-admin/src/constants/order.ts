import { parseQueryNumber } from '@/utils/query'

// 订单状态(取值与后端 com.sky.order.enumeration.OrderStatus 一致)
export const OrderStatus = {
  All: 0,
  PendingPayment: 1,
  ToBeConfirmed: 2,
  Confirmed: 3,
  DeliveryInProgress: 4,
  Completed: 5,
  Cancelled: 6
} as const

export type OrderStatus = (typeof OrderStatus)[keyof typeof OrderStatus]

// 前端展示文案(后端 3 为"已接单",本端页面文案沿用"待派送")
export const ORDER_STATUS_TEXT: Record<OrderStatus, string> = {
  [OrderStatus.All]: '全部订单',
  [OrderStatus.PendingPayment]: '待付款',
  [OrderStatus.ToBeConfirmed]: '待接单',
  [OrderStatus.Confirmed]: '待派送',
  [OrderStatus.DeliveryInProgress]: '派送中',
  [OrderStatus.Completed]: '已完成',
  [OrderStatus.Cancelled]: '已取消'
}

// 状态文案,未知状态返回 fallback
export const statusText = (code: number, fallback = ''): string =>
  ORDER_STATUS_TEXT[code as OrderStatus] ?? fallback

// 判断订单状态是否属于给定状态之一(列表页按状态显示列时用)
export const isOrderStatus = (status: number, ...allowed: number[]) =>
  allowed.includes(status)

// 路由 query 上的状态值解析:缺省、非法或小数一律回到"全部订单"
export const orderStatusFromQuery = (value: unknown): OrderStatus => {
  const n = parseQueryNumber(value)
  if (n === undefined) return OrderStatus.All
  const allowed: number[] = Object.values(OrderStatus)
  return allowed.includes(n) ? (n as OrderStatus) : OrderStatus.All
}

// 支付渠道
export const PayMethod = {
  Wechat: 1,
  Alipay: 2
} as const

export const PAY_METHOD_TEXT: Record<number, string> = {
  [PayMethod.Wechat]: '微信支付',
  [PayMethod.Alipay]: '支付宝支付'
}

export const payMethodText = (method: number | undefined): string =>
  PAY_METHOD_TEXT[method ?? PayMethod.Alipay]

// 固定派送费
export const DELIVERY_FEE = 6
