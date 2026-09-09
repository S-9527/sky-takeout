// 订单相关类型

// 订单状态与文案见 src/constants/order.ts

import type { PageQuery } from './common'

// 订单明细
export interface OrderDetail {
  id?: number
  name: string
  orderId?: number
  dishId?: number
  setmealId?: number
  dishFlavor?: string
  // 数量
  number: number
  // 金额
  amount: number
  image?: string
}

// 订单实体
export interface Orders {
  id: number
  // 订单号
  number: string
  // 订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消 7退款
  status: number
  userId: number
  addressBookId?: number
  orderTime?: string
  checkoutTime?: string
  // 支付方式 1微信 2支付宝
  payMethod?: number
  // 支付状态 0未支付 1已支付 2退款
  payStatus?: number
  // 实收金额
  amount?: number
  remark?: string
  userName?: string
  phone?: string
  address?: string
  consignee?: string
  cancelReason?: string
  rejectionReason?: string
  cancelTime?: string
  estimatedDeliveryTime?: string
  // 配送状态 1立即送出 0选择具体时间
  deliveryStatus?: number
  deliveryTime?: string
  packAmount?: number
  tablewareNumber?: number
  // 餐具数量状态 1按餐量提供 0选择具体数量
  tablewareStatus?: number
}

// 订单详情（搜索/详情返回）
export interface OrderVO extends Orders {
  // 订单菜品信息
  orderDishes?: string
  orderDetailList?: OrderDetail[]
}

// 订单分页查询
export interface OrdersPageQueryDTO extends PageQuery {
  number?: string
  phone?: string
  status?: number
  beginTime?: string
  endTime?: string
}

// 各状态订单数量统计
export interface OrderStatisticsVO {
  // 待接单数量
  toBeConfirmed: number
  // 待派送数量
  confirmed: number
  // 派送中数量
  deliveryInProgress: number
}

// 接单
export interface OrdersConfirmDTO {
  id: number
  // 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消 7退款
  status?: number
}

// 拒单
export interface OrdersRejectionDTO {
  id: number
  rejectionReason: string
}

// 取消订单
export interface OrdersCancelDTO {
  id: number
  cancelReason: string
}
