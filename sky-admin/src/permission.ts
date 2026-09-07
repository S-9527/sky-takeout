import router from './router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import type { RouteLocationNormalized } from 'vue-router'
import Cookies from 'js-cookie'

NProgress.configure({ 'showSpinner': false })

router.beforeEach((to: RouteLocationNormalized, _: RouteLocationNormalized) => {
  NProgress.start()
  if (Cookies.get('token')) {
    return true
  } else {
    if (!to.meta.notNeedAuth) {
      return '/login'
    } else {
      return true
    }
  }
})

router.afterEach((to: RouteLocationNormalized) => {
  NProgress.done()
  document.title = to.meta.title as string
})