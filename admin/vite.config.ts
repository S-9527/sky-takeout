import { fileURLToPath, URL } from 'node:url'

import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

/**
 * 开发期把 `/api`、`/files`、`/ws` 代理到后端,前端只发同源请求。
 *
 * 这样做的两个理由:
 * 1. 不依赖后端 CORS 配置(虽然后端允许 `http://localhost:*`,同源更省心);
 * 2. 令牌、上传、WebSocket 的地址在开发/生产下写法完全一致,不需要环境变量分支。
 */
const backendTarget = process.env.SKY_BACKEND ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: backendTarget, changeOrigin: true },
      '/files': { target: backendTarget, changeOrigin: true },
      '/ws': { target: backendTarget, changeOrigin: true, ws: true },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/tests/setup.ts'],
    include: ['src/**/*.spec.ts'],
    // Element Plus 必须内联处理:外部化时它对 `async-validator` 的默认导入
    // 在 Node ESM 下会解析成模块命名空间,`new AsyncValidator()` 直接抛错,
    // 表现为"表单校验永远通过"——一个只在测试环境出现、浏览器里不存在的假象。
    server: { deps: { inline: ['element-plus'] } },
    // 内联 Element Plus 后单次转换要好几秒;开启磁盘缓存让重复运行只付一次成本
    fsModuleCache: true,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'json-summary'],
      reportsDirectory: './coverage',
      /**
       * 覆盖率门禁只圈**逻辑层**:api / stores / utils / 可复用组件 / 路由守卫。
       *
       * 为什么不把 `src/views/**` 也算进来:视图是"取数 + 模板",它的正确性靠
       * `pnpm test:integration`(前端代码打真后端)与 `pnpm test:e2e`(真浏览器走一遍)
       * 来证明;用 jsdom 里 mock 出来的假数据刷视图行覆盖率,数字好看但说明不了它对。
       * 视图的覆盖率在 `coverage/index.html` 里仍可查看,只是不参与门禁。
       */
      include: [
        'src/api/**/*.ts',
        'src/stores/**/*.ts',
        'src/utils/**/*.ts',
        'src/components/**/*.vue',
        'src/router/guard.ts',
      ],
      exclude: [
        // ECharts 只是把数据塞进 option,真正的口径在 api 与视图的组装里
        'src/components/charts/**',
      ],
      thresholds: {
        lines: 85,
        statements: 85,
        functions: 80,
        branches: 70,
      },
    },
  },
})
