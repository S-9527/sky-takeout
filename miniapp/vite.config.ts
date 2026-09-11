import uni from '@dcloudio/vite-plugin-uni'
import { defineConfig } from 'vite'
import { weappTailwindcss } from 'weapp-tailwindcss/vite'

/**
 * uni-app 的 Vite 配置。
 *
 * ## 为什么用 `async` 配置 + 动态 import 加载 Tailwind
 *
 * 本包不能声明 `"type": "module"`:`@dcloudio/vite-plugin-uni` 是 CJS,
 * 在 ESM 包上下文里默认导入会拿到模块命名空间(`uni is not a function`)。
 * 而 `@tailwindcss/vite` 又是**纯 ESM**(不能被 `require`)。两者只能用
 * "配置本身是 CJS、Tailwind 用动态 import 拉进来"的方式共存。
 *
 * ## 平台差异
 *
 * - **H5**:`@tailwindcss/vite` 直接可用,开发服务器还能配代理(见 `server.proxy`);
 * - **微信小程序**:产物是 WXSS,原子类需要 `weapp-tailwindcss` 翻译成小程序认的写法。
 *   它只处理**独立样式文件**(`src/assets/tailwind.css`)—— 写在 `App.vue` 的 `<style>` 里不会被处理。
 *
 * 注:`weapp-tailwindcss` v5 的导出是 `weappTailwindcss`(v4 时代的 `UnifiedViteWeappTailwindcssPlugin`
 * 已移除),仓库 README 的说明据此更新。
 */
const backendTarget = process.env.SKY_BACKEND ?? 'http://localhost:8080'
const isWeapp = process.env.UNI_PLATFORM === 'mp-weixin'

export default defineConfig(async () => {
  const { default: tailwindcss } = await import('@tailwindcss/vite')
  return {
    plugins: [uni(), tailwindcss(), ...(isWeapp ? weappTailwindcss({ rem2rpx: true }) : [])],
    server: {
      port: 5174,
      proxy: {
        // 只在 H5 下生效:小程序没有"代理"概念,真机/开发者工具里要填完整域名
        '/api': { target: backendTarget, changeOrigin: true },
        '/files': { target: backendTarget, changeOrigin: true },
      },
    },
  }
})
