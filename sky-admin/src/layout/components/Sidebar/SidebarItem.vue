<template>
  <div>
    <div
      v-if="!item.meta || !item.meta.hidden"
      :class="['menu-wrapper', 'full-mode', { 'first-level': isFirstLevel }]"
    >
      <template v-if="theOnlyOneChild && !theOnlyOneChild.children">
        <sidebar-item-link
          v-if="theOnlyOneChild.meta"
          :to="resolvePath(theOnlyOneChild.path)"
        >
          <el-menu-item
            :index="resolvePath(theOnlyOneChild.path)"
            :class="{ 'submenu-title-noDropdown': isFirstLevel }"
          >
            <i
              v-if="theOnlyOneChild.meta.icon"
              class="iconfont"
              :class="theOnlyOneChild.meta.icon"
            />
            <span v-if="theOnlyOneChild.meta.title">{{
              theOnlyOneChild.meta.title
            }}</span>
          </el-menu-item>
        </sidebar-item-link>
      </template>
      <el-sub-menu v-else :index="resolvePath(item.path)" teleported>
        <template #title>
          <i
            v-if="item.meta && item.meta.icon"
            class="iconfont"
            :class="item.meta.icon"
          />
          <span v-if="item.meta && item.meta.title">{{
            item.meta.title
          }}</span>
        </template>
        <template v-if="item.children">
          <sidebar-item
            v-for="child in item.children"
            :key="child.path"
            :item="child"
            :is-collapse="isCollapse"
            :is-first-level="false"
            :base-path="resolvePath(child.path)"
            class="nest-menu"
          />
        </template>
      </el-sub-menu>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { isExternal } from '@/utils/validate'
import SidebarItemLink from './SidebarItemLink.vue'
import type { RouteRecordRaw } from 'vue-router'

const props = defineProps({
  item: { type: Object as () => RouteRecordRaw, required: true },
  isCollapse: { type: Boolean, default: false },
  isFirstLevel: { type: Boolean, default: true },
  basePath: { type: String, default: '' }
})

const showingChildNumber = computed(() => {
  if (props.item.children) {
    const showingChildren = props.item.children.filter((item) => {
      if (item.meta && item.meta.hidden) {
        return false
      }
      return true
    })
    return showingChildren.length
  }
  return 0
})

const theOnlyOneChild = computed(() => {
  if (showingChildNumber.value > 0) {
    return null
  }
  if (props.item.children) {
    for (const child of props.item.children) {
      if (!child.meta || !child.meta.hidden) {
        return child
      }
    }
  }
  return { ...props.item, path: '' }
})

const pathResolve = (basePath: string, routePath: string) => {
  if (isExternal(routePath)) {
    return routePath
  }
  if (isExternal(basePath)) {
    return basePath
  }
  let path = `${basePath}/${routePath}`.replace(/\/{2,}/g, '/')
  if (path.length > 1) {
    path = path.replace(/\/+$/, '')
  }
  if (!path.startsWith('/')) {
    path = `/${path}`
  }
  return path
}

const resolvePath = (routePath: string) => {
  return pathResolve(props.basePath, routePath)
}
</script>