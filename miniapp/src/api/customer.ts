import type {
  CartItemView,
  CartView,
  Category,
  CategoryType,
  Dish,
  DishDetail,
  FlavorChoice,
  ItemType,
  OrderDetail,
  OrderPreview,
  OrderSubmitResult,
  PagedDish,
  PagedOrder,
  PagedSetmeal,
  PaymentStartResponse,
  PaymentStatusView,
  ReorderResult,
  Setmeal,
  SetmealDetail,
  ShopStatusView,
  UserAddress,
} from '@/types'

import { http } from './http'

/* ------------------------------------------------------------------ 门店与目录 */

/**
 * 门店状态。
 *
 * 注意:契约的全局 `security` 是 bearerAuth,顾客端**所有**接口(含目录与门店状态)
 * 都要带令牌 —— 只有登录、刷新与支付回调例外。所以这里不能走匿名请求,
 * 小程序必须先登录再进点餐页。
 */
export function getShopStatus(): Promise<ShopStatusView> {
  return http.get<ShopStatusView>('/api/v1/customer/shop/status')
}

/**
 * 顾客端分类。
 *
 * 不传 `type` 返回全部可见分类;传 `DISH` / `SETMEAL` 只返回该类型的
 * —— 小程序首页左侧的分类栏只展示菜品分类,套餐单列一个入口。
 */
export function listCategories(type?: CategoryType): Promise<Category[]> {
  return http.get<Category[]>('/api/v1/customer/catalog/categories', {
    data: type ? { type } : undefined,
  })
}

/**
 * 某个分类下的菜品。
 *
 * 两个只有打真后端才会暴露的点(集成测试正是在这里踩的):
 * 1. `categoryId` 是**必填**的(契约里标了 required),不传会被判 400;
 * 2. 返回的是**分页对象**(`PagedDish`),不是裸数组 —— 顾客端同样带 Page/PageSize。
 *    这里默认一次取 100 条,单页点餐足够。
 */
export function listDishes(categoryId: number, page = 1, pageSize = 100): Promise<PagedDish> {
  return http.get<PagedDish>('/api/v1/customer/catalog/dishes', {
    data: { categoryId, page, pageSize },
  })
}

export function getDish(id: number): Promise<DishDetail> {
  return http.get<DishDetail>(`/api/v1/customer/catalog/dishes/${id}`)
}

export function listSetmeals(
  categoryId?: number,
  page = 1,
  pageSize = 100,
): Promise<PagedSetmeal> {
  return http.get<PagedSetmeal>('/api/v1/customer/catalog/setmeals', {
    data: { ...(categoryId === undefined ? {} : { categoryId }), page, pageSize },
  })
}

export function getSetmeal(id: number): Promise<SetmealDetail> {
  return http.get<SetmealDetail>(`/api/v1/customer/catalog/setmeals/${id}`)
}

export type { Category, Dish, Setmeal }

/* ------------------------------------------------------------------ 购物车 */

export function getCart(): Promise<CartView> {
  return http.get<CartView>('/api/v1/customer/cart/items')
}

export interface AddCartItemBody {
  itemType: ItemType
  dishId?: number
  setmealId?: number
  quantity: number
  flavorChoice?: FlavorChoice[]
}

export function addCartItem(body: AddCartItemBody): Promise<CartItemView> {
  return http.post<CartItemView>('/api/v1/customer/cart/items', body)
}

/** 覆盖式改数量(不是增减);传 0 由后端判定是否非法 */
export function updateCartItemQuantity(id: number, quantity: number): Promise<void> {
  return http.put<void>(`/api/v1/customer/cart/items/${id}/quantity`, { quantity })
}

export function clearCart(): Promise<void> {
  return http.delete<void>('/api/v1/customer/cart/items')
}

/* ------------------------------------------------------------------ 订单 */

export function previewOrder(addressId?: number): Promise<OrderPreview> {
  return http.post<OrderPreview>(
    '/api/v1/customer/orders/preview',
    addressId === undefined ? {} : { addressId },
  )
}

export interface SubmitOrderBody {
  addressId?: number
  remark?: string
  tablewareCount?: number
  /** 客户端看到的合计(分),用于服务端重算不符时报 422 `ORDER_PRICE_CHANGED` */
  expectedTotalAmountCents?: number
}

export function submitOrder(body: SubmitOrderBody): Promise<OrderSubmitResult> {
  return http.post('/api/v1/customer/orders', body)
}

export interface MyOrderQuery {
  page?: number
  pageSize?: number
  sort?: string
  status?: string
  placedAtFrom?: string
  placedAtTo?: string
}

export function pageMyOrders(query: MyOrderQuery = {}): Promise<PagedOrder> {
  return http.get<PagedOrder>('/api/v1/customer/orders', { data: query })
}

export function getMyOrder(id: number): Promise<OrderDetail> {
  return http.get<OrderDetail>(`/api/v1/customer/orders/${id}`)
}

export function cancelMyOrder(id: number, reason?: string): Promise<void> {
  return http.post<void>(`/api/v1/customer/orders/${id}/cancellation`, { reason })
}

export function reorder(id: number): Promise<ReorderResult> {
  return http.post<ReorderResult>(`/api/v1/customer/orders/${id}/reorder`)
}

export function remindOrder(id: number, message?: string): Promise<void> {
  return http.post<void>(`/api/v1/customer/orders/${id}/reminders`, { message })
}

/* ------------------------------------------------------------------ 支付 */

export function createPayment(
  orderId: number,
  channel: 'WECHAT' | 'MOCK' = 'MOCK',
): Promise<PaymentStartResponse> {
  return http.post<PaymentStartResponse>(`/api/v1/customer/orders/${orderId}/payments`, { channel })
}

export function getPaymentStatus(orderId: number): Promise<PaymentStatusView> {
  return http.get<PaymentStatusView>(`/api/v1/customer/orders/${orderId}/payments/status`)
}

/* ------------------------------------------------------------------ 地址簿 */

export function listAddresses(): Promise<UserAddress[]> {
  return http.get<UserAddress[]>('/api/v1/customer/addresses')
}

export interface AddressBody {
  consignee: string
  phone: string
  province: string
  city: string
  district: string
  detail: string
  label?: string
  isDefault: 0 | 1
}

export function createAddress(body: AddressBody): Promise<UserAddress> {
  return http.post<UserAddress>('/api/v1/customer/addresses', body)
}

export function getAddress(id: number): Promise<UserAddress> {
  return http.get<UserAddress>(`/api/v1/customer/addresses/${id}`)
}

export function updateAddress(id: number, body: AddressBody): Promise<UserAddress> {
  return http.put<UserAddress>(`/api/v1/customer/addresses/${id}`, body)
}

export function deleteAddress(id: number): Promise<void> {
  return http.delete<void>(`/api/v1/customer/addresses/${id}`)
}

export function setDefaultAddress(id: number): Promise<void> {
  return http.patch<void>(`/api/v1/customer/addresses/${id}/default`)
}
