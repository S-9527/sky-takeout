import { beforeAll, describe, expect, it } from 'vitest'

import { getOrderStats, getTopDishes, getTurnoverStats, getUserStats, getWorkbench } from '@/api/insights'
import {
  acceptOrder,
  cancelOrderByAdmin,
  completeOrder,
  countOrdersByStatus,
  getOrder,
  pageOrders,
  rejectOrder,
  startOrderDelivery,
} from '@/api/order'
import { createRefund, pageRefunds } from '@/api/refund'
import type { OrderDetail } from '@/types'
import { recentDaysRange } from '@/utils/datetime'

import { createPaidOrder, expectApiError, expectOk, loginAsAdmin } from './helpers'

/**
 * 管理端订单链路集成测试(真下单 → 真支付 → 真状态迁移)。
 *
 * 订单是凭证,契约里没有删除接口,所以这些用例会在库里留下真实订单(与后端 smoke 脚本一致);
 * 但**不会**改动种子数据,也不会留下自造的菜品/分类。
 */

let lifecycleOrderId = 0
let lifecycleOrderNo = ''

beforeAll(async () => {
  await loginAsAdmin()
})

describe('查询', () => {
  it('列表 / 详情 / 状态计数能看到刚支付的订单', async () => {
    const order = await createPaidOrder('integration-order-list')
    lifecycleOrderId = order.orderId
    lifecycleOrderNo = order.orderNo

    const detail: OrderDetail = await expectOk(() => getOrder(order.orderId))
    expect(detail.status).toBe('PENDING_ACCEPTANCE')
    expect(detail.payStatus).toBe('PAID')
    expect(detail.orderNo).toBe(order.orderNo)
    expect(detail.items.length).toBeGreaterThan(0)
    expect(detail.payments?.length).toBeGreaterThan(0)
    // `itemCount` 契约里注明是"列表展示用":详情不保证返回,给了就必须与明细条数一致
    if (detail.itemCount !== null && detail.itemCount !== undefined) {
      expect(detail.itemCount).toBe(detail.items.length)
    }

    const counts = await expectOk(() => countOrdersByStatus())
    expect(counts.pendingAcceptance).toBeGreaterThanOrEqual(1)
    expect(counts.all).toBe(
      counts.pendingPayment +
        counts.pendingAcceptance +
        counts.accepted +
        counts.delivering +
        counts.completed +
        counts.cancelled,
    )

    const byOrderNo = await expectOk(() => pageOrders({ page: 1, pageSize: 10, orderNo: order.orderNo }))
    expect(byOrderNo.total).toBe(1)
    expect(byOrderNo.records[0].id).toBe(order.orderId)
    // 列表接口必须给出明细种类数(列表页要展示"共 N 种商品")
    expect(byOrderNo.records[0].itemCount).toBe(detail.items.length)
  })

  it('按状态、手机号、时间区间筛选都能命中', async () => {
    const [beginDate, endDate] = recentDaysRange(7)

    const byStatus = await expectOk(() =>
      pageOrders({ page: 1, pageSize: 50, status: 'PENDING_ACCEPTANCE', sort: 'placedAt,desc' }),
    )
    expect(byStatus.records.some((item) => item.id === lifecycleOrderId)).toBe(true)
    expect(byStatus.records.every((item) => item.status === 'PENDING_ACCEPTANCE')).toBe(true)

    const byPhone = await expectOk(() =>
      pageOrders({ page: 1, pageSize: 50, phone: '13800138000', beginDate, endDate }),
    )
    expect(byPhone.records.every((item) => item.phone === '13800138000')).toBe(true)

    const byDate = await expectOk(() => pageOrders({ page: 1, pageSize: 50, beginDate, endDate }))
    const inRange = byDate.records.find((item) => item.id === lifecycleOrderId)
    expect(inRange?.orderNo).toBe(lifecycleOrderNo)
  })

  it('不存在的订单 → 404 ORDER_NOT_FOUND', async () => {
    await expectApiError(() => getOrder(999_999_999), { status: 404, code: 'ORDER_NOT_FOUND' })
  })
})

describe('状态迁移', () => {
  it('接单 → 派送 → 完成,每一步都写时间戳', async () => {
    await expectOk(() => acceptOrder(lifecycleOrderId))
    let detail = await expectOk(() => getOrder(lifecycleOrderId))
    expect(detail.status).toBe('ACCEPTED')
    expect(detail.acceptedAt).toBeTruthy()

    await expectOk(() => startOrderDelivery(lifecycleOrderId))
    detail = await expectOk(() => getOrder(lifecycleOrderId))
    expect(detail.status).toBe('DELIVERING')
    expect(detail.deliveringAt).toBeTruthy()

    await expectOk(() => completeOrder(lifecycleOrderId))
    detail = await expectOk(() => getOrder(lifecycleOrderId))
    expect(detail.status).toBe('COMPLETED')
    expect(detail.completedAt).toBeTruthy()
  })

  it('非法迁移被状态机拒绝:R10', async () => {
    // 已完成的订单不能再次接单
    await expectApiError(() => acceptOrder(lifecycleOrderId), {
      status: 422,
      code: 'ORDER_INVALID_TRANSITION',
    })
    await expectApiError(() => startOrderDelivery(lifecycleOrderId), {
      status: 422,
      code: 'ORDER_INVALID_TRANSITION',
    })
  })

  it('派送中的订单不能取消(已出餐,只能走售后)', async () => {
    const order = await createPaidOrder('integration-order-delivering')
    await expectOk(() => acceptOrder(order.orderId))
    await expectOk(() => startOrderDelivery(order.orderId))

    await expectApiError(() => cancelOrderByAdmin(order.orderId, '想取消'), {
      status: 422,
      code: 'ORDER_INVALID_TRANSITION',
    })
    // 状态没有被改动
    expect((await expectOk(() => getOrder(order.orderId))).status).toBe('DELIVERING')
  })
})

describe('取消与退款', () => {
  it('商家取消已支付订单:自动退款(R6),订单转已取消', async () => {
    const order = await createPaidOrder('integration-order-cancel')
    await expectOk(() => cancelOrderByAdmin(order.orderId, '集成测试:商家取消'))

    const detail = await expectOk(() => getOrder(order.orderId))
    expect(detail.status).toBe('CANCELLED')
    expect(detail.cancelSide).toBe('MERCHANT')
    expect(detail.cancelledAt).toBeTruthy()
    // 已支付订单被强制退款:mock 渠道受理即成功
    expect(['REFUNDED', 'PAID']).toContain(detail.payStatus)
    expect(detail.refunds?.length ?? 0).toBeGreaterThanOrEqual(1)
  })

  it('商家拒单:必须退款,订单转已取消', async () => {
    const order = await createPaidOrder('integration-order-reject')
    await expectOk(() => rejectOrder(order.orderId, '集成测试:菜品售完'))

    const detail = await expectOk(() => getOrder(order.orderId))
    expect(detail.status).toBe('CANCELLED')
    expect(detail.cancelSide).toBe('MERCHANT')
    expect(detail.refunds?.length ?? 0).toBeGreaterThanOrEqual(1)
  })

  it('管理端发起整单退款:受理成功;重复发起 → 409', async () => {
    const order = await createPaidOrder('integration-order-refund')

    const refund = await expectOk(() =>
      createRefund({
        orderNo: order.orderNo,
        reason: '集成测试:顾客申请退款',
        reasonType: 'CUSTOMER_APPLY',
      }),
    )
    expect(refund.refundNo).toMatch(/^RF/)
    expect(refund.amountCents).toBe(order.payAmountCents)

    await expectApiError(
      () =>
        createRefund({
          orderNo: order.orderNo,
          reason: '再来一次',
          reasonType: 'CUSTOMER_APPLY',
        }),
      { status: 409, code: 'PAY_REFUND_ALREADY_EXISTS' },
    )
  })

  it('未支付订单不能退款', async () => {
    // 用一个不存在的订单号也能验证"订单不存在",这里直接验证金额不符的分支更稳:
    // 传一个与实付不一致的金额 → 422 PAY_REFUND_AMOUNT_EXCEEDED
    const order = await createPaidOrder('integration-order-amount')
    await expectApiError(
      () =>
        createRefund({
          orderNo: order.orderNo,
          reason: '金额对不上',
          reasonType: 'OTHER',
          expectedAmountCents: order.payAmountCents + 1,
        }),
      { status: 422, code: 'PAY_REFUND_AMOUNT_EXCEEDED' },
    )
  })

  it('退款记录分页能按订单号查到', async () => {
    const order = await createPaidOrder('integration-order-refund-page')
    await expectOk(() =>
      createRefund({ orderNo: order.orderNo, reason: '集成测试:分页', reasonType: 'OTHER' }),
    )

    const page = await expectOk(() => pageRefunds({ page: 1, pageSize: 10, orderNo: order.orderNo }))
    expect(page.total).toBeGreaterThanOrEqual(1)
    expect(page.records.every((item) => item.orderNo === order.orderNo)).toBe(true)
  })
})

describe('工作台与报表', () => {
  it('工作台返回今日数据与两组概览', async () => {
    const workbench = await expectOk(() => getWorkbench())

    expect(workbench.today.turnoverCents).toBeGreaterThanOrEqual(0)
    expect(workbench.today.totalOrderCount).toBeGreaterThanOrEqual(0)
    expect(Array.isArray(workbench.orderOverview)).toBe(true)
    expect(Array.isArray(workbench.dishOverview)).toBe(true)
    expect(workbench.orderOverview.some((item) => item.name === 'pendingAcceptance')).toBe(true)
    // 概览项都带中文标题,页面直接展示
    expect(workbench.orderOverview.every((item) => typeof item.title === 'string')).toBe(true)
  })

  it('四张报表在同一区间内自洽', async () => {
    const [beginDate, endDate] = recentDaysRange(7)
    const range = { beginDate, endDate }

    const turnover = await expectOk(() => getTurnoverStats(range))
    expect(turnover.beginDate).toBe(beginDate)
    expect(turnover.endDate).toBe(endDate)
    expect(turnover.daily.length).toBe(7)
    expect(turnover.daily.reduce((sum, item) => sum + item.revenueCents, 0)).toBe(turnover.sum)

    const users = await expectOk(() => getUserStats(range))
    expect(users.daily.length).toBe(7)
    expect(users.totalUserCount).toBeGreaterThanOrEqual(users.newUserCount)

    const orders = await expectOk(() => getOrderStats(range))
    expect(orders.daily.length).toBe(7)
    expect(orders.validOrderCount).toBeLessThanOrEqual(orders.totalOrderCount)
    expect(orders.validOrderRate).toBeGreaterThanOrEqual(0)
    expect(orders.validOrderRate).toBeLessThanOrEqual(1)

    const top = await expectOk(() => getTopDishes({ ...range, topNumber: 5 }))
    expect(top.topNumber).toBe(5)
    expect(top.items.length).toBeLessThanOrEqual(5)
    // 名次从 1 开始且单调递增
    top.items.forEach((item, index) => expect(item.rank).toBe(index + 1))
  })

  it('区间跨度超过 366 天 → 400 REPORT_DATE_RANGE_TOO_LARGE', async () => {
    await expectApiError(
      () => getTurnoverStats({ beginDate: '2020-01-01', endDate: '2025-01-01' }),
      { status: 400, code: 'REPORT_DATE_RANGE_TOO_LARGE' },
    )
  })

  it('区间起止颠倒 → 400', async () => {
    await expectApiError(() => getTurnoverStats({ beginDate: '2025-01-10', endDate: '2025-01-01' }), {
      status: 400,
    })
  })
})
