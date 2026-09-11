import type { AxiosAdapter, AxiosResponse } from 'axios'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import * as categoryApi from './category'
import * as dishApi from './dish'
import * as employeeApi from './employee'
import { http } from './http'
import * as insightsApi from './insights'
import * as orderApi from './order'
import * as refundApi from './refund'
import * as setmealApi from './setmeal'
import * as shopApi from './shop'
import { clearTokens } from './tokens'

/**
 * 接口路径一览表。
 *
 * 这一组用例看起来"只是复述了一遍 URL",但它挡的是最容易犯、又最难在浏览器里发现的错:
 * 方法写错(把 `PUT` 写成 `PATCH`)、路径少一段、查询参数名写错(如 `beginDate` → `startDate`)。
 * 这类错误单测 mock 掉的 `api` 模块永远发现不了,只有把它对准 `docs/openapi.yaml` 才拦得住。
 */
interface RecordedCall {
  method?: string
  url?: string
  params?: Record<string, unknown> | undefined
  data?: unknown
}

const calls: RecordedCall[] = []

beforeEach(() => {
  calls.length = 0
  clearTokens()
  const adapter: AxiosAdapter = async (config) => {
    calls.push({
      method: config.method?.toUpperCase(),
      url: config.url,
      params: config.params as Record<string, unknown> | undefined,
      data: config.data,
    })
    const response: AxiosResponse = {
      data: {},
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    }
    return response
  }
  http.defaults.adapter = adapter
})

afterEach(() => {
  http.defaults.adapter = undefined
  clearTokens()
})

async function call(action: () => Promise<unknown>): Promise<RecordedCall> {
  calls.length = 0
  await action()
  expect(calls).toHaveLength(1)
  return calls[0]
}

function body(call: RecordedCall): unknown {
  return typeof call.data === 'string' ? JSON.parse(call.data) : call.data
}

describe('身份与员工', () => {
  it('登录 / 登出 / 刷新 / 当前员工 / 改密', async () => {
    expect(await call(() => employeeApi.login({ username: 'admin', password: 'x' }))).toMatchObject({
      method: 'POST',
      url: '/api/v1/admin/auth/login',
    })
    expect(body(calls[0])).toEqual({ username: 'admin', password: 'x' })

    expect(await call(() => employeeApi.logout('rt-1'))).toMatchObject({
      method: 'POST',
      url: '/api/v1/admin/auth/logout',
    })
    expect(await call(() => employeeApi.refresh('rt-1'))).toMatchObject({
      method: 'POST',
      url: '/api/v1/admin/auth/refresh',
    })
    expect(await call(() => employeeApi.fetchCurrentEmployee())).toMatchObject({
      method: 'GET',
      url: '/api/v1/admin/auth/me',
    })
    expect(
      await call(() => employeeApi.changePassword({ oldPassword: 'a', newPassword: 'b' })),
    ).toMatchObject({ method: 'PUT', url: '/api/v1/admin/auth/password' })
  })

  it('员工增删改查', async () => {
    expect(
      await call(() => employeeApi.pageEmployees({ page: 1, pageSize: 20, sort: 'createdAt,desc' })),
    ).toMatchObject({ method: 'GET', url: '/api/v1/admin/employees' })
    expect(calls[0].params).toEqual({ page: 1, pageSize: 20, sort: 'createdAt,desc' })

    expect(await call(() => employeeApi.getEmployee(7))).toMatchObject({
      method: 'GET',
      url: '/api/v1/admin/employees/7',
    })
    expect(
      await call(() =>
        employeeApi.createEmployee({ username: 'lisi', password: 'x', name: '李四', role: 'STAFF' }),
      ),
    ).toMatchObject({ method: 'POST', url: '/api/v1/admin/employees' })
    expect(
      await call(() =>
        employeeApi.updateEmployee(7, { name: '李四', role: 'STAFF', status: 1 }),
      ),
    ).toMatchObject({ method: 'PUT', url: '/api/v1/admin/employees/7' })
    expect(await call(() => employeeApi.changeEmployeeStatus(7, 0))).toMatchObject({
      method: 'PATCH',
      url: '/api/v1/admin/employees/7/status',
    })
    expect(body(calls[0])).toEqual({ status: 0 })
  })
})

describe('门店', () => {
  it('查询与切换营业状态都是 /admin/shop/status', async () => {
    expect(await call(() => shopApi.getShopStatus())).toMatchObject({
      method: 'GET',
      url: '/api/v1/admin/shop/status',
    })
    expect(await call(() => shopApi.updateShopStatus({ isOpen: false }))).toMatchObject({
      method: 'PUT',
      url: '/api/v1/admin/shop/status',
    })
    expect(body(calls[0])).toEqual({ isOpen: false })
  })
})

describe('分类', () => {
  it('分页 / 选项 / 详情 / 增删改 / 启停用', async () => {
    expect(await call(() => categoryApi.pageCategories({ page: 1, type: 'DISH' }))).toMatchObject({
      method: 'GET',
      url: '/api/v1/admin/categories',
    })
    expect(await call(() => categoryApi.listCategoryOptions('SETMEAL', true))).toMatchObject({
      method: 'GET',
      url: '/api/v1/admin/categories/options',
    })
    expect(calls[0].params).toEqual({ type: 'SETMEAL', includeDisabled: true })

    expect(await call(() => categoryApi.getCategory(10))).toMatchObject({
      method: 'GET',
      url: '/api/v1/admin/categories/10',
    })
    expect(
      await call(() => categoryApi.createCategory({ name: '川湘菜', type: 'DISH', sortOrder: 1 })),
    ).toMatchObject({ method: 'POST', url: '/api/v1/admin/categories' })
    expect(
      await call(() => categoryApi.updateCategory(10, { name: '川湘菜', sortOrder: 2, status: 1 })),
    ).toMatchObject({ method: 'PUT', url: '/api/v1/admin/categories/10' })
    expect(await call(() => categoryApi.deleteCategory(10))).toMatchObject({
      method: 'DELETE',
      url: '/api/v1/admin/categories/10',
    })
    expect(await call(() => categoryApi.changeCategoryStatus(10, 0))).toMatchObject({
      method: 'PATCH',
      url: '/api/v1/admin/categories/10/status',
    })
  })
})

describe('菜品', () => {
  it('分页 / 详情 / 增改 / 批量删除 / 批量启停用', async () => {
    expect(await call(() => dishApi.pageDishes({ page: 1, categoryId: 10 }))).toMatchObject({
      method: 'GET',
      url: '/api/v1/admin/dishes',
    })
    expect(await call(() => dishApi.getDish(2001))).toMatchObject({
      method: 'GET',
      url: '/api/v1/admin/dishes/2001',
    })
    expect(
      await call(() =>
        dishApi.createDish({ categoryId: 10, name: '水煮牛肉', priceCents: 4800, sortOrder: 0 }),
      ),
    ).toMatchObject({ method: 'POST', url: '/api/v1/admin/dishes' })
    expect(
      await call(() =>
        dishApi.updateDish(2001, {
          categoryId: 10,
          name: '水煮牛肉',
          priceCents: 5200,
          status: 1,
        }),
      ),
    ).toMatchObject({ method: 'PUT', url: '/api/v1/admin/dishes/2001' })

    expect(await call(() => dishApi.deleteDishes([2001, 2002]))).toMatchObject({
      method: 'DELETE',
      url: '/api/v1/admin/dishes',
    })
    expect(calls[0].params).toEqual({ ids: '2001,2002' })

    expect(await call(() => dishApi.changeDishesStatus([2001], 0))).toMatchObject({
      method: 'PATCH',
      url: '/api/v1/admin/dishes/status',
    })
    expect(body(calls[0])).toEqual({ ids: [2001], status: 0 })
  })
})

describe('套餐', () => {
  it('分页 / 详情 / 增改 / 批量删除 / 批量启停用', async () => {
    expect(await call(() => setmealApi.pageSetmeals({ page: 1 }))).toMatchObject({
      method: 'GET',
      url: '/api/v1/admin/setmeals',
    })
    expect(await call(() => setmealApi.getSetmeal(3001))).toMatchObject({
      method: 'GET',
      url: '/api/v1/admin/setmeals/3001',
    })
    expect(
      await call(() =>
        setmealApi.createSetmeal({
          categoryId: 20,
          name: '双人套餐',
          priceCents: 8800,
          items: [{ dishId: 2001, copies: 2 }],
        }),
      ),
    ).toMatchObject({ method: 'POST', url: '/api/v1/admin/setmeals' })
    expect(
      await call(() =>
        setmealApi.updateSetmeal(3001, {
          categoryId: 20,
          name: '双人套餐',
          priceCents: 8800,
          status: 1,
        }),
      ),
    ).toMatchObject({ method: 'PUT', url: '/api/v1/admin/setmeals/3001' })
    expect(await call(() => setmealApi.deleteSetmeals([3001]))).toMatchObject({
      method: 'DELETE',
      url: '/api/v1/admin/setmeals',
    })
    expect(await call(() => setmealApi.changeSetmealsStatus([3001], 1))).toMatchObject({
      method: 'PATCH',
      url: '/api/v1/admin/setmeals/status',
    })
  })
})

describe('订单', () => {
  it('分页查询把时间区间参数名传成 beginDate/endDate', async () => {
    await orderApi.pageOrders({
      page: 1,
      pageSize: 20,
      status: 'PENDING_ACCEPTANCE',
      beginDate: '2025-01-01',
      endDate: '2025-01-31',
      orderNo: '202501011200000001',
      phone: '13800138000',
    })

    expect(calls[0]).toMatchObject({ method: 'GET', url: '/api/v1/admin/orders' })
    expect(calls[0].params).toEqual({
      page: 1,
      pageSize: 20,
      status: 'PENDING_ACCEPTANCE',
      beginDate: '2025-01-01',
      endDate: '2025-01-31',
      orderNo: '202501011200000001',
      phone: '13800138000',
    })
  })

  it('状态计数与详情', async () => {
    expect(await call(() => orderApi.countOrdersByStatus())).toMatchObject({
      method: 'GET',
      url: '/api/v1/admin/orders/status-counts',
    })
    expect(await call(() => orderApi.getOrder(4001))).toMatchObject({
      method: 'GET',
      url: '/api/v1/admin/orders/4001',
    })
  })

  it('五个商家动作都是 POST 且无请求体(接单/拒单/派送/完成/取消)', async () => {
    expect(await call(() => orderApi.acceptOrder(4001))).toMatchObject({
      method: 'POST',
      url: '/api/v1/admin/orders/4001/acceptance',
    })
    expect(await call(() => orderApi.rejectOrder(4001, '菜品已售完'))).toMatchObject({
      method: 'POST',
      url: '/api/v1/admin/orders/4001/rejection',
    })
    expect(body(calls[0])).toEqual({ reason: '菜品已售完' })
    expect(await call(() => orderApi.startOrderDelivery(4001))).toMatchObject({
      method: 'POST',
      url: '/api/v1/admin/orders/4001/delivery',
    })
    expect(await call(() => orderApi.completeOrder(4001))).toMatchObject({
      method: 'POST',
      url: '/api/v1/admin/orders/4001/completion',
    })
    expect(await call(() => orderApi.cancelOrderByAdmin(4001, '顾客电话要求取消'))).toMatchObject({
      method: 'POST',
      url: '/api/v1/admin/orders/4001/cancellation',
    })
  })
})

describe('退款', () => {
  it('查询与发起退款', async () => {
    expect(await call(() => refundApi.pageRefunds({ page: 1, status: 'SUCCESS' }))).toMatchObject({
      method: 'GET',
      url: '/api/v1/admin/refunds',
    })
    expect(
      await call(() =>
        refundApi.createRefund({ orderNo: 'N1', reason: '顾客申请', reasonType: 'CUSTOMER_APPLY' }),
      ),
    ).toMatchObject({ method: 'POST', url: '/api/v1/admin/refunds' })
    expect(body(calls[0])).toEqual({
      orderNo: 'N1',
      reason: '顾客申请',
      reasonType: 'CUSTOMER_APPLY',
    })
  })
})

describe('报表', () => {
  it('四个统计接口的区间参数一致', async () => {
    const range = { beginDate: '2025-01-01', endDate: '2025-01-07' }

    expect(await call(() => insightsApi.getTurnoverStats(range))).toMatchObject({
      method: 'GET',
      url: '/api/v1/admin/insights/turnover-stats',
    })
    expect(calls[0].params).toEqual(range)

    expect(await call(() => insightsApi.getUserStats(range))).toMatchObject({
      url: '/api/v1/admin/insights/user-stats',
    })
    expect(await call(() => insightsApi.getOrderStats(range))).toMatchObject({
      url: '/api/v1/admin/insights/order-stats',
    })
    expect(await call(() => insightsApi.getTopDishes({ ...range, topNumber: 10 }))).toMatchObject({
      url: '/api/v1/admin/insights/top-dishes',
    })
    expect(calls[0].params).toEqual({ ...range, topNumber: 10 })

    expect(await call(() => insightsApi.getWorkbench())).toMatchObject({
      method: 'GET',
      url: '/api/v1/admin/insights/workbench',
    })
  })
})
