import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vitest/config'

/**
 * 集成测试:用**小程序自己的 api 层**打真后端(需后端在 :8080,可用 SKY_BACKEND 覆盖)。
 *
 * 与单元测试的区别同管理端:mock 测不出契约 —— 参数名、错误码、令牌刷新、
 * 口味与地址的往返一致性,只有打到真后端才算验过。
 */
const backendBaseUrl = process.env.SKY_BACKEND ?? 'http://localhost:8080'

export default defineConfig({
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
    fileParallelism: false,
    pool: 'forks',
    env: { SKY_BACKEND: backendBaseUrl },
  },
})
