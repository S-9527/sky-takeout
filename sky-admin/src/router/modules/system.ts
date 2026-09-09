import type { RouteRecordRaw } from 'vue-router'

/** 员工管理 */
export const systemRoutes: RouteRecordRaw[] = [
  {
    path: 'employee',
    component: () => import('@/views/employee/index.vue'),
    meta: { title: '员工管理', icon: 'icon-employee' }
  },
  {
    path: 'employee/add',
    component: () => import('@/views/employee/addEmployee.vue'),
    meta: { title: '添加员工', hidden: true }
  }
]
