import { reactive } from 'vue'
import { ElMessage } from 'element-plus'
import {
  queryOrderDetailById,
  completeOrder,
  deliveryOrder,
  orderCancel,
  orderReject as orderRejectApi,
  orderAccept as orderAcceptApi
} from '@/api/order'
import { OrderStatus } from '@/constants/order'
import type { OrderVO } from '@/api/types/order'

export type CancelTitle = '取消' | '拒绝'

export interface ReasonOption {
  value: number
  label: string
}

// 拒单原因
export const REJECTION_REASONS: ReasonOption[] = [
  { value: 1, label: '订单量较多，暂时无法接单' },
  { value: 2, label: '菜品已销售完，暂时无法接单' },
  { value: 3, label: '餐厅已打烊，暂时无法接单' },
  { value: 0, label: '自定义原因' }
]

// 取消订单原因
export const CANCEL_REASONS: ReasonOption[] = [
  { value: 1, label: '订单量较多，暂时无法接单' },
  { value: 2, label: '菜品已销售完，暂时无法接单' },
  { value: 3, label: '骑手不足无法配送' },
  { value: 4, label: '客户电话取消' },
  { value: 0, label: '自定义原因' }
]

export const CUSTOM_REASON = '自定义原因'

export interface OrderActionsState {
  // 当前操作的订单行
  row: OrderVO
  // 详情弹窗内容
  detail: Partial<OrderVO>
  detailVisible: boolean
  detailStatus: number
  // 处理完自动跳转下一条
  autoNext: boolean
  // 最近一次接单/拒单是否来自表格(为 true 时不自动弹出下一条详情)
  tableOperated: boolean
  // 取消/拒单弹窗
  cancelVisible: boolean
  cancelTitle: CancelTitle
  cancelReason: string
  remark: string
}

export interface OrderActionsOptions {
  // 接单/拒单/取消/派送/完成 成功后的回调,用于刷新列表
  onSuccess?: () => void
}

export function useOrderActions(options: OrderActionsOptions = {}) {
  const state: OrderActionsState = reactive({
    row: {} as OrderVO,
    detail: {},
    detailVisible: false,
    detailStatus: OrderStatus.All,
    autoNext: true,
    tableOperated: true,
    cancelVisible: false,
    cancelTitle: '取消',
    cancelReason: '',
    remark: ''
  })

  // 查看详情
  async function openDetail(id: number, status: number, rowData?: OrderVO, event?: Event) {
    event?.stopPropagation()
    state.detail = {}
    state.detailVisible = true
    state.detailStatus = status
    state.row = rowData ?? ({ id, status } as OrderVO)
    state.detail = await queryOrderDetailById({ orderId: id })
  }

  function closeDetail() {
    state.detailVisible = false
  }

  function openReject(rowData: OrderVO, event?: Event, fromTable = false) {
    openCancelDialog(rowData, '拒绝', event, fromTable)
  }

  function openCancel(rowData: OrderVO, event?: Event) {
    openCancelDialog(rowData, '取消', event)
  }

  function openCancelDialog(
    rowData: OrderVO,
    title: CancelTitle,
    event?: Event,
    fromTable = false
  ) {
    event?.stopPropagation()
    state.detailVisible = false
    state.detailStatus = rowData.status
    state.row = rowData
    state.tableOperated = fromTable
    state.cancelVisible = true
    state.cancelTitle = title
    state.cancelReason = ''
    state.remark = ''
  }

  function closeCancelDialog() {
    state.cancelVisible = false
    state.cancelReason = ''
  }

  // 接单
  async function accept(rowData: OrderVO, event?: Event, fromTable = false) {
    event?.stopPropagation()
    state.tableOperated = fromTable
    state.detailStatus = rowData.status
    state.row = rowData
    await orderAcceptApi({ id: rowData.id })
    ElMessage.success('操作成功')
    state.detailVisible = false
    options.onSuccess?.()
  }

  // 派送(已接单 -> 派送中) / 完成(派送中 -> 已完成)
  async function deliverOrComplete(rowData: OrderVO, event?: Event) {
    event?.stopPropagation()
    state.detailStatus = rowData.status
    state.row = rowData
    const isDelivery = rowData.status === OrderStatus.Confirmed
    await (isDelivery ? deliveryOrder : completeOrder)(rowData.id)
    ElMessage.success('操作成功')
    state.detailVisible = false
    options.onSuccess?.()
  }

  // 确认取消或拒单
  async function confirmCancel() {
    if (!state.cancelReason) {
      ElMessage.error(`请选择${state.cancelTitle}原因`)
      return
    }
    if (state.cancelReason === CUSTOM_REASON && !state.remark) {
      ElMessage.error(`请输入${state.cancelTitle}原因`)
      return
    }

    const id = state.row.id
    // row 的初始值是空对象,正常流程一定经过 openDetail 填充;这里判一次,避免把 undefined 打进请求
    if (id === undefined) return
    const reason =
      state.cancelReason === CUSTOM_REASON ? state.remark : state.cancelReason

    if (state.cancelTitle === '取消') {
      await orderCancel({ id, cancelReason: reason })
    } else {
      await orderRejectApi({ id, rejectionReason: reason })
    }
    ElMessage.success('操作成功')
    state.cancelVisible = false
    options.onSuccess?.()
  }

  return {
    state,
    openDetail,
    closeDetail,
    openReject,
    openCancel,
    closeCancelDialog,
    accept,
    deliverOrComplete,
    confirmCancel
  }
}

export type OrderActions = ReturnType<typeof useOrderActions>
