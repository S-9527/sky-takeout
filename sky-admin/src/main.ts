import { createApp } from 'vue'
import 'normalize.css'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import '@/styles/element-variables.scss'
import '@/styles/index.scss'
import '@/styles/home.scss'
import '@/styles/element-overrides.scss'
import '@/styles/icon/iconfont.css'
import App from '@/App.vue'
import pinia from '@/store'
import router from '@/router'
import '@/permission'

const app = createApp(App)

app.use(ElementPlus, { locale: zhCn })
app.use(pinia)
app.use(router)

app.mount('#app')