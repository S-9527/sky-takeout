import { createRouter, createWebHistory } from "vue-router";
import Layout from "@/layout/index.vue";

declare module "vue-router" {
  interface RouteMeta {
    /** 页面标题,同时写入 document.title */
    title?: string
    /** 为 true 时不拦截到 /login */
    notNeedAuth?: boolean
    /** 为 true 时不渲染到侧边栏 */
    hidden?: boolean
    /** 允许访问的角色,缺省表示所有角色可见 */
    roles?: string[]
  }
}


const router = createRouter({
  history: createWebHistory('/'),
  scrollBehavior: (_to, _from, savedPosition) => {
    if (savedPosition) {
      return savedPosition;
    }
    return { left: 0, top: 0 };
  },
  routes: [
    {
      path: "/login",
      component: () => import("@/views/login/index.vue"),
      meta: { title: "苍穹外卖", hidden: true, notNeedAuth: true }
    },
    {
      path: "/404",
      component: () => import("@/views/404.vue"),
      meta: { title: "苍穹外卖", hidden: true, notNeedAuth: true }
    },
    {
      path: "/",
      component: Layout,
      redirect: "/dashboard",
      children: [
        {
          path: "dashboard",
          component: () => import("@/views/dashboard/index.vue"),
          name: "Dashboard",
          meta: {
            title: "工作台",
            icon: "dashboard",
            affix: true
          }
        },
        {
          path: "/statistics",
          component: () => import("@/views/statistics/index.vue"),
          meta: {
            title: "数据统计",
            icon: "icon-statistics"
          }
        },
        {
          path: "order",
          component: () => import("@/views/orderDetails/index.vue"),
          meta: {
            title: "订单管理",
            icon: "icon-order"
          }
        },
        {
          path: "setmeal",
          component: () => import("@/views/setmeal/index.vue"),
          meta: {
            title: "套餐管理",
            icon: "icon-combo"
          }
        },
        {
          path: "dish",
          component: () => import("@/views/dish/index.vue"),
          meta: {
            title: "菜品管理",
            icon: "icon-dish"
          }
        },
        {
          path: "/dish/add",
          component: () => import("@/views/dish/addDishtype.vue"),
          meta: {
            title: "添加菜品",
            hidden: true
          }
        },
        {
          path: "category",
          component: () => import("@/views/category/index.vue"),
          meta: {
            title: "分类管理",
            icon: "icon-category"
          }
        },
        {
          path: "employee",
          component: () => import("@/views/employee/index.vue"),
          meta: {
            title: "员工管理",
            icon: "icon-employee"
          }
        },
        {
          path: "/employee/add",
          component: () => import("@/views/employee/addEmployee.vue"),
          meta: {
            title: "添加员工",
            hidden: true
          }
        },
        {
          path: "/setmeal/add",
          component: () => import("@/views/setmeal/addSetmeal.vue"),
          meta: {
            title: "添加套餐",
            hidden: true
          }
        }
      ]
    },
    {
      path: "/:pathMatch(.*)*",
      redirect: "/404",
      meta: { hidden: true }
    }
  ]
});

export default router;