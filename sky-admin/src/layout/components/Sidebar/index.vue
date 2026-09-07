<template>
  <div>
    <div class="logo">
      <div v-if="!isCollapse"
           class="sidebar-logo">
        <img src="@/assets/login/logo.png"
             style="width: 120px; height: 31px">
      </div>
      <div v-else
           class="sidebar-logo-mini">
        <img src="@/assets/login/mini-logo.png">
      </div>
    </div>
    <el-scrollbar wrap-class="scrollbar-wrapper">
      <el-menu :default-openeds="defOpen"
               :default-active="defAct"
               :collapse="isCollapse"
               :background-color="variables.menuBg"
               :text-color="variables.menuText"
               :active-text-color="variables.menuActiveText"
               :unique-opened="false"
               :collapse-transition="false"
               mode="vertical">
        <sidebar-item v-for="route in routes"
                      :key="route.path"
                      :item="route"
                      :base-path="route.path"
                      :is-collapse="isCollapse" />
      </el-menu>
    </el-scrollbar>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/store/modules/app'
import { useUserStore } from '@/store/modules/user'
import SidebarItem from './SidebarItem.vue'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const variables = {
  menuBg: '#343744',
  menuText: '#bfcbd9',
  menuActiveText: '#FFC200'
}

const defOpen = computed(() => {
  const path = ['/']
  routes.value.forEach((n: any) => {
    if (n.meta.roles && n.meta.roles[0] === roles.value[0]) {
      path.splice(0, 1, n.path)
    }
  })
  return path
})

const defAct = computed(() => {
  return route.path
})

const sidebar = computed(() => appStore.sidebar)

const roles = computed(() => userStore.roles)

const routes = computed(() => {
  const allRoutes = JSON.parse(
    JSON.stringify([...(router.options.routes as any)])
  )
  let menuList = []
  const menu = allRoutes.find((item: any) => item.path === '/')
  if (menu) {
    menuList = menu.children
  }
  return menuList
})

const isCollapse = computed(() => {
  return !sidebar.value.opened
})
</script>

<style lang="scss" scoped>
.logo {
  text-align: center;
  background-color: #ffc100;
  padding: 15px 0 0;
  height: 60px;
  img {
    display: inline-block;
  }
}
.sidebar-logo-mini {
  img {
    width: 30px;
    height: 30px;
  }
}
.el-scrollbar {
  height: 100%;
  background-color: rgb(52, 55, 68);
}

.el-menu {
  border: none;
  height: calc(95vh - 23px);
  width: 100% !important;
  padding: 47px 15px 0;
}
</style>