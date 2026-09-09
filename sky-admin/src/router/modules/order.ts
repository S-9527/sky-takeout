import type { RouteRecordRaw } from 'vue-router'

/** 订单管理 */
export const orderRoutes: RouteRecordRaw[] = [
  {
    path: 'order',
    component: () => import('@/views/orderDetails/index.vue'),
    meta: { title: '订单管理', icon: 'icon-order' }
  }
]
