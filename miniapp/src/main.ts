import { createSSRApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import './assets/tailwind.css'

/**
 * uni-app 的入口约定:必须导出 `createApp` 并返回 `{ app }`,
 * 由各端运行时决定怎么挂载(H5 挂 DOM,小程序挂页面栈)。
 */
export function createApp() {
  const app = createSSRApp(App)
  app.use(createPinia())
  return { app }
}
