// 通用类型

// 通用分页查询参数
export interface PageQuery {
  page: number
  pageSize: number
  name?: string
}

// 通用分页查询结果
export interface PageResult<T> {
  total: number
  records: T[]
}

// 店铺营业状态
export type ShopStatus = number
