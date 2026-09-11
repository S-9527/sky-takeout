import { defineConfig, devices } from '@playwright/test'

/**
 * 浏览器端到端测试:真浏览器(Windows Edge,CDP 连接)+ 真 Vite 开发服务器 + 真后端。
 *
 * 分工(见 admin/README.md「三层测试」):
 * - Vitest 单元/组件:`pnpm test` —— 逻辑与组件的正确性,mock 掉 HTTP;
 * - Vitest 集成:`pnpm test:integration` —— 前端代码打真后端,验契约;
 * - Playwright E2E:`pnpm test:e2e` —— 走真实 UI,验"登录 → 页面 → 写操作"整条链路。
 *
 * 三者都不能替代彼此:E2E 抓的是"模板跑起来会不会白屏、路由守卫有没有生效"这类问题。
 *
 * 浏览器从哪来:见 `e2e/global-setup.ts` —— 本仓库的开发环境是 WSL,
 * 直接复用 Windows 侧已安装的 Edge(调试端口 + CDP),不下载浏览器、不需要 root。
 */
const adminBaseUrl = process.env.SKY_ADMIN_URL ?? 'http://127.0.0.1:5173'

export default defineConfig({
  testDir: './e2e',
  globalSetup: './e2e/global-setup.ts',
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  timeout: 60_000,
  expect: { timeout: 15_000 },
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL: adminBaseUrl,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
  },
  projects: [{ name: 'edge-cdp', use: { ...devices['Desktop Chrome'], channel: undefined } }],
  webServer: {
    command: 'pnpm dev --host 127.0.0.1 --port 5173',
    url: adminBaseUrl,
    reuseExistingServer: true,
    timeout: 120_000,
    stdout: 'ignore',
    stderr: 'pipe',
  },
})
