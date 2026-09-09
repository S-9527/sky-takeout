import type { RouteRecordRaw } from 'vue-router'

/** 不需要登录即可访问的路由 */
export const publicRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '苍穹外卖', hidden: true, notNeedAuth: true }
  },
  {
    path: '/404',
    component: () => import('@/views/404.vue'),
    meta: { title: '苍穹外卖', hidden: true, notNeedAuth: true }
  }
]

/** 兜底:未匹配的路径统一落到 404,必须放在所有路由之后 */
export const notFoundRoute: RouteRecordRaw = {
  path: '/:pathMatch(.*)*',
  redirect: '/404',
  meta: { hidden: true }
}
