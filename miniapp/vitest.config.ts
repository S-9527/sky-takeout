import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vitest/config'

/**
 * 单元/组件测试配置。
 *
 * 故意**不加载 uni 插件**:`@dcloudio/vite-plugin-uni` 需要 `UNI_PLATFORM` 等编译期上下文,
 * 在 jsdom 里跑单测只需要别名与环境,`uni.*` API 由 `src/tests/setup.ts` 注入桩实现。
 */
export default defineConfig({
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/tests/setup.ts'],
    include: ['src/**/*.spec.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'json-summary'],
      reportsDirectory: './coverage',
      /**
       * 门禁只圈逻辑层(api / stores / utils):页面由小程序真机与集成测试把关,
       * 在 jsdom 里渲染 uni 组件并不能证明小程序端是对的。
       */
      include: ['src/api/**/*.ts', 'src/stores/**/*.ts', 'src/utils/**/*.ts'],
      exclude: ['src/types/**'],
      // 当前口径:api 层(http/tokens)与 cart/dict 已覆盖,页面与部分 store 由集成测试与真机把关
      thresholds: { lines: 60, statements: 60, functions: 70, branches: 80 },
    },
  },
})
