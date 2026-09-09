import router from './router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import type { RouteLocationNormalized } from 'vue-router'
import { useUserStore } from '@/store/modules/user'

NProgress.configure({ 'showSpinner': false })

// pinia 里的 token 是登录态唯一来源,不再各自读 cookie
router.beforeEach((to: RouteLocationNormalized, _: RouteLocationNormalized) => {
  NProgress.start()
  const userStore = useUserStore()
  if (userStore.token) {
    return true
  }
  return to.meta.notNeedAuth ? true : '/login'
})

router.afterEach((to: RouteLocationNormalized) => {
  NProgress.done()
  document.title = to.meta.title ?? ''
})
