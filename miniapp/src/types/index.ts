import type { components } from './api'

/**
 * 接口类型的友好出口(顾客端)。
 *
 * 与 `admin/src/types/index.ts` 同源:类型真相都在 `docs/openapi.yaml`,
 * 改契约后跑 `pnpm gen:api` 重新生成,这里只做别名。
 */
export type Schemas = components['schemas']

export type ApiErrorBody = Schemas['ErrorResponse']
export type ErrorDetail = Schemas['ErrorDetail']

export type Customer = Schemas['Customer']
export type TokenPair = Schemas['TokenPair']
export type ShopStatusView = Schemas['ShopStatusView']

export type Category = Schemas['Category']
export type CategoryType = Schemas['CategoryType']
export type Dish = Schemas['Dish']
export type DishDetail = Schemas['DishDetail']
export type Setmeal = Schemas['Setmeal']
export type SetmealDetail = Schemas['SetmealDetail']
export type FlavorChoice = Schemas['FlavorChoice']

export type CartView = Schemas['CartView']
export type CartCategoryGroup = Schemas['CartCategoryGroup']
export type CartItemView = Schemas['CartItemView']

export type Order = Schemas['Order']
export type OrderDetail = Schemas['OrderDetail']
export type OrderStatus = Schemas['OrderStatus']
export type PagedDish = Schemas['PagedDish']
export type PagedOrder = Schemas['PagedOrder']
export type PagedSetmeal = Schemas['PagedSetmeal']
export type PayStatus = Schemas['PayStatus']
export type OrderPreview = Schemas['OrderPreview']
export type OrderSubmitResult = Schemas['OrderSubmitResult']
export type ReorderResult = Schemas['ReorderResult']
export type PreviewOrderItem = Schemas['PreviewOrderItem']

export type Payment = Schemas['Payment']
export type PaymentStartResponse = Schemas['PaymentStartResponse']
export type PaymentStatusView = Schemas['PaymentStatusView']

export type UserAddress = Schemas['UserAddress']
export type ItemType = Schemas['ItemType']
