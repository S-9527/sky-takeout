// 与后端 sky-backend 对应的类型定义

// ========== 通用 ==========

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

// ========== 员工 ==========

// 员工登录
export interface EmployeeLoginDTO {
  username: string
  password: string
}

export interface EmployeeLoginVO {
  id: number
  userName: string
  name: string
  token: string
}

// 新增/编辑员工
export interface EmployeeDTO {
  id?: number
  username: string
  name: string
  phone: string
  sex: string
  idNumber: string
}

// 员工分页查询
export interface EmployeePageQueryDTO extends PageQuery {}

// 修改密码
export interface PasswordEditDTO {
  empId: number
  oldPassword: string
  newPassword: string
}

// 员工实体
export interface Employee {
  id: number
  username: string
  name: string
  password?: string
  phone: string
  sex: string
  idNumber: string
  // 0 禁用 1 启用
  status: number
  createTime?: string
  updateTime?: string
  createUser?: number
  updateUser?: number
}

// ========== 分类 ==========

// 新增/编辑分类
export interface CategoryDTO {
  id?: number
  // 1 菜品分类 2 套餐分类
  type: number
  name: string
  sort?: number
}

// 分类分页查询
export interface CategoryPageQueryDTO extends PageQuery {
  // 分类类型 1菜品分类 2套餐分类
  type?: number
}

// 分类实体
export interface Category {
  id: number
  // 1 菜品分类 2 套餐分类
  type: number
  name: string
  sort: number
  // 0 禁用 1 启用
  status: number
  createTime?: string
  updateTime?: string
  createUser?: number
  updateUser?: number
}

// ========== 菜品 ==========

// 菜品口味
export interface DishFlavor {
  id?: number
  dishId?: number
  name: string
  value: string
}

// 新增/编辑菜品
export interface DishDTO {
  id?: number
  name: string
  categoryId: number
  price: number
  image: string
  // 商品码
  code?: string
  description?: string
  // 0 停售 1 起售
  status?: number
  flavors: DishFlavor[]
}

// 菜品分页查询
export interface DishPageQueryDTO extends PageQuery {
  categoryId?: number
  // 0 停售 1 起售
  status?: number
}

// 菜品回显
export interface DishVO extends DishDTO {
  updateTime?: string
  categoryName?: string
}

// 菜品实体（列表数据）
export interface Dish {
  id: number
  name: string
  categoryId: number
  price: number
  image: string
  // 商品码
  code?: string
  description?: string
  // 0 停售 1 起售
  status: number
  createTime?: string
  updateTime?: string
  createUser?: number
  updateUser?: number
}

// ========== 套餐 ==========

// 套餐菜品关系
export interface SetmealDish {
  id?: number
  setmealId?: number
  dishId: number
  name?: string
  price?: number
  // 份数
  copies: number
}

// 新增/编辑套餐
export interface SetmealDTO {
  id?: number
  categoryId: number
  name: string
  price: number
  // 0 停用 1 启用
  status?: number
  description?: string
  image: string
  setmealDishes: SetmealDish[]
}

// 套餐分页查询
export interface SetmealPageQueryDTO extends PageQuery {
  categoryId?: number
  // 0 停用 1 启用
  status?: number
}

// 套餐回显
export interface SetmealVO extends SetmealDTO {
  updateTime?: string
  categoryName?: string
}

// ========== 订单 ==========

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

// ========== 店铺 ==========

// 店铺营业状态
export type ShopStatus = number

// ========== 消息通知 ==========

// 消息通知
export interface Message {
  id: number
  // 1 未读 2 已读
  status: number
  content: string
  details: string
  createTime?: string
  // 消息类型 1 待接单 2 急单 3 待派送 4 催单 5 闭店数据
  type?: number
}

// 消息分页查询
export interface MessagePageQuery {
  pageNum: number
  pageSize: number
  status: number
}

// ========== 报表/工作台 ==========

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