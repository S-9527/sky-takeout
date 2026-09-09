import request from '@/utils/request'
import type {
  OrderStatisticsVO,
  OrdersCancelDTO,
  OrdersConfirmDTO,
  OrdersPageQueryDTO,
  OrdersRejectionDTO,
  OrderVO,
  PageResult,
} from './types'

// 订单搜索（分页查询）
export const getOrderDetailPage = (params: OrdersPageQueryDTO) => {
  return request.get<PageResult<OrderVO>>('/order/conditionSearch', { params })
}

// 订单详情
export const queryOrderDetailById = (params: { orderId: number }) => {
  return request.get<OrderVO>(`/order/details/${params.orderId}`)
}

// 派送订单
export const deliveryOrder = (id: number) => {
  return request.put(`/order/delivery/${id}`)
}
// 完成订单
export const completeOrder = (id: number) => {
  return request.put(`/order/complete/${id}`)
}

// 取消订单
export const orderCancel = (params: OrdersCancelDTO) => {
  return request.put('/order/cancel', params)
}

// 接单
export const orderAccept = (params: OrdersConfirmDTO) => {
  return request.put('/order/confirm', params)
}

// 拒单
export const orderReject = (params: OrdersRejectionDTO) => {
  return request.put('/order/rejection', params)
}

// 各状态订单数量统计
export const getOrderListBy = () => {
  return request.get<OrderStatisticsVO>('/order/statistics')
}