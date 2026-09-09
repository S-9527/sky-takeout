import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import Layout from '@/layout/index.vue'
import { notFoundRoute, publicRoutes } from './modules/public'
import { workplaceRoutes } from './modules/workplace'
import { menuRoutes } from './modules/menu'
import { orderRoutes } from './modules/order'
import { systemRoutes } from './modules/system'

declare module 'vue-router' {
  interface RouteMeta {
    /** 页面标题,同时写入 document.title */
    title?: string
    /** 侧边栏图标类名(iconfont) */
    icon?: string
    /** 为 true 时不渲染到侧边栏 */
    hidden?: boolean
    /** 为 true 时不拦截到 /login */
    notNeedAuth?: boolean
    /** 允许访问的角色,缺省表示所有角色可见 */
    roles?: string[]
  }
}

const layoutRoute: RouteRecordRaw = {
  path: '/',
  component: Layout,
  redirect: '/dashboard',
  children: [
    ...workplaceRoutes,
    ...menuRoutes,
    ...orderRoutes,
    ...systemRoutes
  ]
}

const router = createRouter({
  history: createWebHistory('/'),
  scrollBehavior: (_to, _from, savedPosition) => {
    if (savedPosition) {
      return savedPosition
    }
    return { left: 0, top: 0 }
  },
  routes: [...publicRoutes, layoutRoute, notFoundRoute]
})

export default router
