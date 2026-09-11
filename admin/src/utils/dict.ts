import type {
  CancelSide,
  CategoryType,
  EmployeeRole,
  EmployeeStatus,
  Order,
  OrderStatus,
  PayStatus,
  RefundStatus,
} from '@/types'

/** Element Plus `el-tag` 的 type 取值 */
export type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

interface DictItem {
  label: string
  tag: TagType
}

/**
 * 订单状态字典(领域文档 §4 状态机)。
 *
 * 中文名与后端 `docs/01-domain.md` 的用词保持一致:待付款 / 待接单 / 已接单 / 派送中 / 已完成 / 已取消。
 */
export const ORDER_STATUS_DICT: Record<OrderStatus, DictItem> = {
  PENDING_PAYMENT: { label: '待付款', tag: 'warning' },
  PENDING_ACCEPTANCE: { label: '待接单', tag: 'danger' },
  ACCEPTED: { label: '已接单', tag: 'primary' },
  DELIVERING: { label: '派送中', tag: 'primary' },
  COMPLETED: { label: '已完成', tag: 'success' },
  CANCELLED: { label: '已取消', tag: 'info' },
}

export const ORDER_STATUS_OPTIONS: { value: OrderStatus; label: string }[] = (
  Object.keys(ORDER_STATUS_DICT) as OrderStatus[]
).map((value) => ({ value, label: ORDER_STATUS_DICT[value].label }))

export const PAY_STATUS_DICT: Record<PayStatus, DictItem> = {
  UNPAID: { label: '未支付', tag: 'warning' },
  PAID: { label: '已支付', tag: 'success' },
  REFUNDED: { label: '已退款', tag: 'info' },
  PARTIAL_REFUNDED: { label: '部分退款', tag: 'warning' },
}

export const REFUND_STATUS_DICT: Record<RefundStatus, DictItem> = {
  PENDING: { label: '处理中', tag: 'warning' },
  SUCCESS: { label: '退款成功', tag: 'success' },
  FAILED: { label: '退款失败', tag: 'danger' },
}

export const PAYMENT_STATUS_DICT: Record<string, DictItem> = {
  PENDING: { label: '待支付', tag: 'warning' },
  SUCCESS: { label: '支付成功', tag: 'success' },
  FAILED: { label: '支付失败', tag: 'danger' },
  CLOSED: { label: '已关闭', tag: 'info' },
}

export const CANCEL_SIDE_DICT: Record<CancelSide, DictItem> = {
  CUSTOMER: { label: '顾客取消', tag: 'info' },
  MERCHANT: { label: '商家取消', tag: 'warning' },
  SYSTEM: { label: '系统关闭', tag: 'info' },
}

export const EMPLOYEE_ROLE_DICT: Record<EmployeeRole, DictItem> = {
  ADMIN: { label: '管理员', tag: 'danger' },
  STAFF: { label: '员工', tag: 'primary' },
}

export const EMPLOYEE_STATUS_DICT: Record<EmployeeStatus, DictItem> = {
  1: { label: '启用', tag: 'success' },
  0: { label: '禁用', tag: 'info' },
}

export const ENABLED_DICT: Record<number, DictItem> = {
  1: { label: '起售', tag: 'success' },
  0: { label: '停售', tag: 'info' },
}

export const CATEGORY_TYPE_DICT: Record<CategoryType, DictItem> = {
  DISH: { label: '菜品分类', tag: 'primary' },
  SETMEAL: { label: '套餐分类', tag: 'warning' },
}

/** 退款原因分类(`RefundCreateRequest.reasonType`,取值与库里的 CHECK 约束一致) */
export const REFUND_REASON_TYPE_DICT: Record<string, DictItem> = {
  MERCHANT_REJECT: { label: '商家拒单', tag: 'warning' },
  MERCHANT_CANCEL: { label: '商家取消', tag: 'warning' },
  CUSTOMER_APPLY: { label: '顾客申请', tag: 'info' },
  OTHER: { label: '其他', tag: 'info' },
}

export const REFUND_REASON_TYPE_OPTIONS = Object.entries(REFUND_REASON_TYPE_DICT).map(
  ([value, item]) => ({ value, label: item.label }),
)

function pick(dict: Record<string, DictItem>, key: string | null | undefined): DictItem | undefined {
  if (!key) return undefined
  return dict[key]
}

export function orderStatusLabel(status: string | null | undefined): string {
  return pick(ORDER_STATUS_DICT, status)?.label ?? '未知状态'
}

export function orderStatusTag(status: string | null | undefined): TagType {
  return pick(ORDER_STATUS_DICT, status)?.tag ?? 'info'
}

export function payStatusLabel(status: string | null | undefined): string {
  return pick(PAY_STATUS_DICT, status)?.label ?? '-'
}

export function payStatusTag(status: string | null | undefined): TagType {
  return pick(PAY_STATUS_DICT, status)?.tag ?? 'info'
}

export function refundStatusLabel(status: string | null | undefined): string {
  return pick(REFUND_STATUS_DICT, status)?.label ?? '-'
}

export function refundStatusTag(status: string | null | undefined): TagType {
  return pick(REFUND_STATUS_DICT, status)?.tag ?? 'info'
}

export function paymentStatusLabel(status: string | null | undefined): string {
  return pick(PAYMENT_STATUS_DICT, status)?.label ?? '-'
}

export function cancelSideLabel(side: string | null | undefined): string {
  return pick(CANCEL_SIDE_DICT, side)?.label ?? '-'
}

export function employeeRoleLabel(role: string | null | undefined): string {
  return pick(EMPLOYEE_ROLE_DICT, role)?.label ?? '-'
}

export function refundReasonTypeLabel(type: string | null | undefined): string {
  return pick(REFUND_REASON_TYPE_DICT, type)?.label ?? '-'
}

/**
 * 商家可执行的动作 —— 与后端状态机一一对应,前端只负责"把不允许的按钮藏起来",
 * 真正的判定仍在后端(点错了会收到 422 `ORDER_INVALID_TRANSITION`,前端照样要提示)。
 */
export function canAccept(order: Pick<Order, 'status'>): boolean {
  return order.status === 'PENDING_ACCEPTANCE'
}

export function canReject(order: Pick<Order, 'status'>): boolean {
  return order.status === 'PENDING_ACCEPTANCE'
}

export function canDeliver(order: Pick<Order, 'status'>): boolean {
  return order.status === 'ACCEPTED'
}

export function canComplete(order: Pick<Order, 'status'>): boolean {
  return order.status === 'DELIVERING'
}

/** `DELIVERING` 不可取消(已出餐,只能走售后,本期不做) */
export function canCancel(order: Pick<Order, 'status'>): boolean {
  return order.status === 'PENDING_ACCEPTANCE' || order.status === 'ACCEPTED'
}

/**
 * 是否允许发起整单退款:已支付 + 非 `COMPLETED`(R8)。
 *
 * "是否已有退款记录"只有详情接口才知道(`OrderDetail.refunds`),
 * 列表页判断不了,所以这里不假装能判断 —— 重复退款由后端 409 `PAY_REFUND_ALREADY_EXISTS` 兜底。
 */
export function canRefund(order: Pick<Order, 'status' | 'payStatus'>): boolean {
  const paid = order.payStatus === 'PAID' || order.payStatus === 'PARTIAL_REFUNDED'
  return paid && order.status !== 'COMPLETED'
}
