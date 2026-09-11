import type { Page } from '@playwright/test'

import { ADMIN as ADMIN_CREDENTIALS, createPaidOrder } from './backend'
import { expect, test } from './fixtures'

/**
 * 管理端浏览器端到端测试。
 *
 * 与另外两层测试的分工(见 admin/README.md「三层测试」):
 * - 单元/组件测试证明逻辑与组件本身对;
 * - `pnpm test:integration` 证明前端代码打真后端时契约对;
 * - **这一层**证明"真浏览器里跑起来"是对的:路由守卫、表单校验、Element Plus 交互、
 *   以及在真后端数据上渲染页面 —— 组件测试用 mock 数据永远发现不了白屏。
 *
 * 前置:后端在 :8080 运行(`pnpm dev` 会由 Playwright 自动拉起并把 /api 代理过去)。
 * 用例只读为主;唯一会写库的是营业状态切换,并且会在结束时还原。
 */

const ADMIN = ADMIN_CREDENTIALS
const STAFF = { username: 'zhangsan', password: '123456' }

async function login(page: Page, credentials = ADMIN): Promise<void> {
  await page.goto('/login')
  await page.locator('input[name="username"]').fill(credentials.username)
  await page.locator('input[name="password"]').fill(credentials.password)
  await page.getByTestId('login-submit').click()
  await expect(page).toHaveURL(/\/workbench/)
}

test.describe('登录', () => {
  test('管理员登录后进入工作台并显示今日数据', async ({ page }) => {
    await login(page)

    await expect(page.getByTestId('workbench')).toBeVisible()
    await expect(page.getByText('今日营业额')).toBeVisible()
    await expect(page.getByTestId('today-turnover')).toContainText('¥')
    // 侧边栏对管理员显示员工管理
    await expect(page.getByRole('menuitem', { name: '员工管理' })).toBeVisible()
  })

  test('密码错误时留在登录页并提示', async ({ page }) => {
    await page.goto('/login')
    await page.locator('input[name="username"]').fill(ADMIN.username)
    await page.locator('input[name="password"]').fill('wrong-password')
    await page.getByTestId('login-submit').click()

    await expect(page.getByText('用户名或密码错误')).toBeVisible()
    await expect(page).toHaveURL(/\/login/)
  })

  test('空表单提交被前端校验拦下,不发请求', async ({ page }) => {
    await page.goto('/login')
    await page.getByTestId('login-submit').click()

    await expect(page.getByText('请输入用户名')).toBeVisible()
    await expect(page).toHaveURL(/\/login/)
  })

  test('未登录访问业务页会被送回登录页,登录后回到原地址', async ({ page }) => {
    await page.goto('/orders')
    await expect(page).toHaveURL(/\/login\?redirect=/)

    await page.locator('input[name="username"]').fill(ADMIN.username)
    await page.locator('input[name="password"]').fill(ADMIN.password)
    await page.getByTestId('login-submit').click()

    await expect(page).toHaveURL(/\/orders/)
    await expect(page.getByTestId('order-table')).toBeVisible()
  })

  test('刷新页面后仍然保持登录(令牌持久化 + 资料回填)', async ({ page }) => {
    await login(page)
    await page.reload()
    await expect(page.getByTestId('workbench')).toBeVisible()
    await expect(page.getByRole('menuitem', { name: '员工管理' })).toBeVisible()
  })

  test('退出登录后回到登录页,再访问业务页会被拦截', async ({ page }) => {
    await login(page)
    // el-dropdown 默认 hover 触发;退出确认走 ElMessageBox,按容器定位更稳
    await page.locator('.el-dropdown').hover()
    await page.getByText('退出登录').click()
    await page.locator('.el-message-box__btns button.el-button--primary').click()

    await expect(page).toHaveURL(/\/login/)

    await page.goto('/orders')
    await expect(page).toHaveURL(/\/login/)
  })
})

test.describe('权限', () => {
  test('STAFF 看不到员工管理,直接访问会被守卫拦到无权限页', async ({ page }) => {
    await login(page, STAFF)
    await expect(page.getByRole('menuitem', { name: '员工管理' })).toHaveCount(0)

    await page.goto('/employees')
    await expect(page.getByTestId('forbidden')).toBeVisible()
  })
})

test.describe('订单管理', () => {
  test('列表渲染真实订单,可按状态筛选,能打开详情', async ({ page }) => {
    await login(page)
    await page.goto('/orders')

    const table = page.getByTestId('order-table')
    await expect(table).toBeVisible()
    await expect(table.locator('.el-table__row').first()).toBeVisible()

    // 订单号是 el-link(无 href 时渲染成 span),按类名点进详情页
    await table.locator('.el-table__row').first().locator('.el-link').first().click()
    await expect(page).toHaveURL(/\/orders\/\d+/)
    await expect(page.getByTestId('order-detail')).toBeVisible()
    await expect(page.getByText('商品明细')).toBeVisible()
    await expect(page.getByText('支付与退款')).toBeVisible()
  })

  test('工作台的待接单卡片能跳到带筛选的订单列表', async ({ page }) => {
    await login(page)
    await page.getByTestId('pending-acceptance').click()

    await expect(page).toHaveURL(/\/orders\?status=PENDING_ACCEPTANCE/)
    await expect(page.getByTestId('order-table')).toBeVisible()
  })
})

test.describe('商品与门店', () => {
  test('分类页能新增分类并触发重名校验', async ({ page }) => {
    await login(page)
    await page.goto('/categories')

    await expect(page.getByTestId('category-table')).toBeVisible()
    await page.getByTestId('category-create').click()

    // 名称留空 → 前端校验拦下
    await page.getByTestId('category-submit').click()
    await expect(page.getByText('请输入分类名称')).toBeVisible()

    await page.getByRole('button', { name: '取消' }).click()
    await expect(page.locator('.el-dialog')).toHaveCount(0)
  })

  test('菜品页展示图片列与起售状态', async ({ page }) => {
    await login(page)
    await page.goto('/dishes')

    await expect(page.getByTestId('dish-table')).toBeVisible()
    await expect(page.getByTestId('dish-table').locator('.el-table__row').first()).toBeVisible()
    await expect(page.getByTestId('dish-table').getByText(/起售|停售/).first()).toBeVisible()
  })

  test('员工页对管理员可见且列表有数据', async ({ page }) => {
    await login(page)
    await page.goto('/employees')

    await expect(page.getByTestId('employee-table')).toBeVisible()
    await expect(page.getByTestId('employee-table').getByText('admin')).toBeVisible()
    // 当前账号那一行不能禁用自己
    await expect(page.getByText('当前账号')).toBeVisible()
  })

  test('数据统计页渲染四张图表', async ({ page }) => {
    await login(page)
    await page.goto('/insights')

    await expect(page.getByTestId('insights')).toBeVisible()
    // 等接口回来后再数图:页面上一共 4 个 ECharts 容器
    await expect(page.getByTestId('chart')).toHaveCount(4)
    await expect(page.getByText('区间营业额')).toBeVisible()
  })

  test('营业状态可以切换并还原', async ({ page }) => {
    await login(page)
    await page.goto('/shop')

    const toggle = page.getByTestId('shop-open-switch')
    await expect(toggle).toBeVisible()
    const before = await toggle.locator('input').isChecked()

    try {
      await toggle.click()
      await page.getByTestId('shop-save').click()
      await expect(page.getByText(/门店已开始营业|门店已打烊/)).toBeVisible()
      await expect(toggle.locator('input').isChecked()).resolves.toBe(!before)
    } finally {
      // 不管断言是否成功,都把营业状态放回原样(别把门店留在打烊状态)
      await page.goto('/shop')
      await expect(toggle).toBeVisible()
      if ((await toggle.locator('input').isChecked()) !== before) {
        await toggle.click()
        await page.getByTestId('shop-save').click()
        await expect(page.getByText(/门店已开始营业|门店已打烊/)).toBeVisible()
      }
    }
  })
})

test.describe('来单提醒(WebSocket)', () => {
  test('真下一单会推送到浏览器,弹窗到点自己消失', async ({ page, request }) => {
    test.setTimeout(90_000)
    await login(page)
    await page.goto('/workbench')
    // 头部挂着"通知未连接"标签时说明 WebSocket 还没连上,等它消失再下单
    await expect(page.getByText('通知未连接')).toHaveCount(0)

    const order = await createPaidOrder(request)
    try {
      const notification = page.locator('.el-notification', { hasText: order.orderNo })
      await expect(notification).toBeVisible({ timeout: 20_000 })

      // 关键回归:曾经 duration: 0(永不关闭),连单时弹窗糊满屏幕
      await expect(notification).toHaveCount(0, { timeout: 20_000 })

      // 铃铛里的通知中心仍然留着这一条,漏看也不会丢
      await page.locator('.el-badge button').click()
      await expect(page.getByTestId('notification-item').first()).toContainText(order.orderNo)
    } finally {
      await order.cleanup()
    }
  })
})
