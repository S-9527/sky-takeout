// 后端 /user 接口的 DTO / VO 类型,与 sky-backend 各实体保持一致

// 通用分页结果
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

// 用户登录返回
export interface UserLoginVO {
  id: number
  openid: string
  accessToken: string
  refreshToken: string
  shopName?: string
  shopAddress?: string
  shopId?: number
}

// 分类
export interface Category {
  id: number
  type: number
  name: string
  sort: number
  status: number
  createTime?: string
  updateTime?: string
}

// 菜品(含口味)
export interface DishFlavor {
  id?: number
  dishId?: number
  name: string
  value: string
}

export interface Dish {
  id: number
  name: string
  categoryId: number
  price: string | number
  image: string
  description: string
  status?: number
  updateTime?: string
  flavors?: DishFlavor[]
  copies?: string
}

// 套餐
export interface Setmeal {
  id: number
  categoryId: number
  name: string
  price: string | number
  description: string
  image: string
  status?: number
  copies?: string
}

export interface SetmealDish {
  id: number
  name: string
  price: string | number
  copies?: number
  image?: string
}

// 购物车
export interface ShoppingCart {
  id: number
  name: string
  image: string
  dishId?: number
  setmealId?: number
  dishFlavor?: string
  number: number
  amount: string | number
  createTime?: string
}

// 地址簿
export interface AddressBook {
  id?: number
  userId?: number
  consignee: string
  sex?: string
  phone: string
  provinceCode?: string
  provinceName?: string
  cityCode?: string
  cityName?: string
  districtCode?: string
  districtName?: string
  detail?: string
  label?: string
  isDefault?: number
}

// 订单菜品详情
export interface OrderDetail {
  id: number
  name: string
  orderId: number
  dishId?: number
  setmealId?: number
  dishFlavor?: string
  number: number
  amount: string | number
  image: string
}

// 订单
export interface Order {
  id: number
  number: string
  status: number
  userId: number
  addressBookId: number
  orderTime: string
  checkoutTime?: string
  payMethod: number
  payStatus: number
  amount: number
  remark?: string
  userName?: string
  phone?: string
  address?: string
  consignee?: string
  cancelReason?: string
  rejectionReason?: string
  cancelTime?: string
  estimatedDeliveryTime: string
  deliveryStatus?: number
  deliveryTime?: string
  packAmount: number
  tablewareNumber: number
  tablewareStatus: number
  orderDishes?: string
  orderDetailList: OrderDetail[]
}

// 下单返回
export interface OrderSubmitVO {
  id: number
  orderNumber: string
  orderAmount: number
  orderTime: string
}

// 支付返回
export interface OrderPaymentVO {
  nonceStr: string
  paySign: string
  timeStamp: string
  signType: string
  packageStr: string
}
