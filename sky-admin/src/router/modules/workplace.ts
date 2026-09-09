import type { RouteRecordRaw } from 'vue-router'

/** 工作台与数据统计 */
export const workplaceRoutes: RouteRecordRaw[] = [
  {
    path: 'dashboard',
    component: () => import('@/views/dashboard/index.vue'),
    name: 'Dashboard',
    meta: { title: '工作台', icon: 'dashboard' }
  },
  {
    path: 'statistics',
    component: () => import('@/views/statistics/index.vue'),
    meta: { title: '数据统计', icon: 'icon-statistics' }
  }
]
