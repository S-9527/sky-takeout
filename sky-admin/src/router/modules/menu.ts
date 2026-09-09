import type { RouteRecordRaw } from 'vue-router'

/** 菜品、套餐与分类 */
export const menuRoutes: RouteRecordRaw[] = [
  {
    path: 'dish',
    component: () => import('@/views/dish/index.vue'),
    meta: { title: '菜品管理', icon: 'icon-dish' }
  },
  {
    path: 'dish/add',
    component: () => import('@/views/dish/addDishtype.vue'),
    meta: { title: '添加菜品', hidden: true }
  },
  {
    path: 'setmeal',
    component: () => import('@/views/setmeal/index.vue'),
    meta: { title: '套餐管理', icon: 'icon-combo' }
  },
  {
    path: 'setmeal/add',
    component: () => import('@/views/setmeal/addSetmeal.vue'),
    meta: { title: '添加套餐', hidden: true }
  },
  {
    path: 'category',
    component: () => import('@/views/category/index.vue'),
    meta: { title: '分类管理', icon: 'icon-category' }
  }
]
