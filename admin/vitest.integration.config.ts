import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

/**
 * 集成测试配置:用**前端自己的代码**(`src/api/**`、`src/stores/**`)打**真实后端**。
 *
 * 与 `vitest.config`(单元/组件测试,jsdom + mock)的分工:
 * - 单元测试证明"逻辑对不对";
 * - 集成测试证明"契约对不对" —— 请求参数名、响应字段、错误码、令牌刷新,
 *   这些只有打到真后端(:8080 + 真 MySQL/Redis)才能验证,也是 mock 测不出来的那部分。
 *
 * 运行前需先启动后端:`pnpm test:integration`(见 admin/README.md)。
 */
const backendBaseUrl = process.env.SKY_BACKEND ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'node',
    globals: true,
    include: ['tests/integration/**/*.spec.ts'],
    setupFiles: ['./tests/integration/setup.ts'],
    testTimeout: 30_000,
    hookTimeout: 30_000,
    // 集成用例共享真实库里的订单/退款数据,并发跑会互相踩,串行执行
    fileParallelism: false,
    pool: 'forks',
    env: {
      SKY_BACKEND: backendBaseUrl,
    },
  },
})
