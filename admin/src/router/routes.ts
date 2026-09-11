import type { RouteRecordRaw } from 'vue-router'

import type { EmployeeRole } from '@/types'

declare module 'vue-router' {
  interface RouteMeta {
    /** 浏览器标题与页面标题 */
    title?: string
    /** 免登录页面(只有登录页与 404) */
    public?: boolean
    /** 允许访问的角色;不写表示所有已登录员工 */
    roles?: EmployeeRole[]
  }
}

/**
 * 路由表。
 *
 * 约定:
 * - 除登录页外全部挂在 `AdminLayout` 下,由布局统一渲染侧边栏/头部;
 * - 需要 ADMIN 的页面在 `meta.roles` 上声明,守卫统一拦截 ——
 *   前端拦截只是"不让人点进去看空白页",真正的权限边界在后端
 *   (STAFF 调员工接口会拿到 403 `AUTH_PERMISSION_DENIED`)。
 */
export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: { name: 'workbench' },
    children: [
      {
        path: 'workbench',
        name: 'workbench',
        component: () => import('@/views/Workbench.vue'),
        meta: { title: '工作台' },
      },
      {
        path: 'orders',
        name: 'orders',
        component: () => import('@/views/order/OrderList.vue'),
        meta: { title: '订单管理' },
      },
      {
        path: 'orders/:id(\\d+)',
        name: 'order-detail',
        component: () => import('@/views/order/OrderDetail.vue'),
        meta: { title: '订单详情' },
      },
      {
        path: 'refunds',
        name: 'refunds',
        component: () => import('@/views/order/RefundList.vue'),
        meta: { title: '退款记录' },
      },
      {
        path: 'shop',
        name: 'shop',
        component: () => import('@/views/shop/ShopStatus.vue'),
        meta: { title: '营业状态' },
      },
      {
        path: 'categories',
        name: 'categories',
        component: () => import('@/views/catalog/CategoryList.vue'),
        meta: { title: '分类管理' },
      },
      {
        path: 'dishes',
        name: 'dishes',
        component: () => import('@/views/catalog/DishList.vue'),
        meta: { title: '菜品管理' },
      },
      {
        path: 'setmeals',
        name: 'setmeals',
        component: () => import('@/views/catalog/SetmealList.vue'),
        meta: { title: '套餐管理' },
      },
      {
        path: 'insights',
        name: 'insights',
        component: () => import('@/views/Insights.vue'),
        meta: { title: '数据统计' },
      },
      {
        path: 'employees',
        name: 'employees',
        component: () => import('@/views/employee/EmployeeList.vue'),
        meta: { title: '员工管理', roles: ['ADMIN'] },
      },
      {
        path: 'password',
        name: 'password',
        component: () => import('@/views/Password.vue'),
        meta: { title: '修改密码' },
      },
      {
        path: 'forbidden',
        name: 'forbidden',
        component: () => import('@/views/Forbidden.vue'),
        meta: { title: '无访问权限' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFound.vue'),
    meta: { title: '页面不存在', public: true },
  },
]
