import type { components, operations } from './api'

/**
 * 接口类型的友好出口。
 *
 * 生成的 `api.d.ts` 里全是 `components['schemas']['Xxx']` 这种长路径,
 * 业务代码直接写太难读;这里做一层薄薄的别名,**不新增任何手写类型**——
 * 类型真相仍在 `docs/openapi.yaml`,改契约后重新 `pnpm gen:api` 即可。
 */
export type Schemas = components['schemas']
export type Operations = operations

export type ApiErrorBody = Schemas['ErrorResponse']
export type ErrorDetail = Schemas['ErrorDetail']

export type Employee = Schemas['Employee']
export type EmployeeRole = Schemas['EmployeeRole']
export type EmployeeStatus = Schemas['EmployeeStatus']
export type PagedEmployee = Schemas['PagedEmployee']

/** `1` 起售/启用,`0` 停售/禁用(分类、菜品、套餐共用) */
export type EnabledFlag = Schemas['EnabledFlag']

export type ShopStatus = Schemas['ShopStatus']

export type Category = Schemas['Category']
export type CategoryType = Schemas['CategoryType']
export type PagedCategory = Schemas['PagedCategory']

export type Dish = Schemas['Dish']
export type DishDetail = Schemas['DishDetail']
export type DishFlavor = Schemas['DishFlavor']
export type PagedDish = Schemas['PagedDish']

export type Setmeal = Schemas['Setmeal']
export type SetmealDetail = Schemas['SetmealDetail']
export type SetmealItem = Schemas['SetmealItem']
export type PagedSetmeal = Schemas['PagedSetmeal']

export type Order = Schemas['Order']
export type OrderDetail = Schemas['OrderDetail']
export type OrderItem = Schemas['OrderItem']
export type OrderStatus = Schemas['OrderStatus']
export type OrderStatusCounts = Schemas['OrderStatusCounts']
export type PagedOrder = Schemas['PagedOrder']
export type CancelSide = Schemas['CancelSide']
export type PayStatus = Schemas['PayStatus']

export type Payment = Schemas['Payment']
export type Refund = Schemas['Refund']
export type RefundStatus = Schemas['RefundStatus']
export type PagedRefund = Schemas['PagedRefund']

export type TokenPair = Schemas['TokenPair']

export type TurnoverStats = Schemas['TurnoverStats']
export type TurnoverStatsItem = Schemas['TurnoverStatsItem']
export type UserStats = Schemas['UserStats']
export type UserStatsItem = Schemas['UserStatsItem']
export type OrderStats = Schemas['OrderStats']
export type OrderStatsItem = Schemas['OrderStatsItem']
export type TopDishSales = Schemas['TopDishSales']
export type DishSalesItem = Schemas['DishSalesItem']
export type Workbench = Schemas['Workbench']
export type WorkbenchToday = Schemas['WorkbenchToday']
export type OverviewItem = Schemas['OverviewItem']

export type UploadResult = Schemas['UploadResult']

/** 取某个接口的 query 参数类型(不含分页基类的那几个通用参数以外的任何手写类型) */
export type QueryOf<K extends keyof Operations> = Operations[K] extends {
  parameters: { query?: infer Q }
}
  ? NonNullable<Q>
  : never

/** 取某个接口 200/201 成功响应体类型 */
export type ResponseOf<K extends keyof Operations> = Operations[K]['responses'] extends Record<
  number,
  { content: { 'application/json': infer R } }
>
  ? R
  : never
