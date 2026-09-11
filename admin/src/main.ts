import * as ElementPlusIcons from '@element-plus/icons-vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { createPinia } from 'pinia'
import { createApp } from 'vue'

import { onAuthExpired, setApiBaseUrl } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

import App from './App.vue'
import router from './router'

// Tailwind 在前,Element Plus 在后 —— 顺序见 assets/main.css 的注释
import '@/assets/main.css'
import 'element-plus/dist/index.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 图标全量注册:`<el-icon><Search /></el-icon>` 这样直接写组件名,
// 模板里少一层 import。代价是打包体积,管理端不敏感(见 README「已知取舍」)。
for (const [name, component] of Object.entries(ElementPlusIcons)) {
  app.component(name, component)
}

const auth = useAuthStore()

// 令牌彻底失效(刷新也失败)时统一在这里落地:清会话 + 跳登录,保留原地址
onAuthExpired(() => {
  auth.clearSession()
  const current = router.currentRoute.value
  if (current.name === 'login') return
  void router.replace({
    name: 'login',
    query: current.fullPath && current.fullPath !== '/' ? { redirect: current.fullPath } : {},
  })
})

// 生产部署在同源时留空,开发期由 Vite 代理转发到后端
setApiBaseUrl(import.meta.env.VITE_API_BASE_URL ?? '')

app.mount('#app')
