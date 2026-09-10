import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [uni()],
  server: {
    // 8080 被 sky-backend 占用,小程序 H5 走 8081
    port: 8081,
    proxy: {
      // 接口请求同源代理到本地后端,规避跨域
      '/user': { target: 'http://localhost:8080', changeOrigin: true },
      '/notify': { target: 'http://localhost:8080', changeOrigin: true },
      '/files': { target: 'http://localhost:8080', changeOrigin: true }
    }
  }
})
