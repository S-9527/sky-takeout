<template>
  <el-breadcrumb
    class="app-breadcrumb"
    separator="/"
  >
    <transition-group name="breadcrumb">
      <el-breadcrumb-item
        v-for="(item, index) in breadcrumbs"
        :key="item.path"
      >
        <span
          v-if="item.redirect === 'noredirect' || index === breadcrumbs.length-1"
          class="no-redirect"
        >{{ item.meta.title }}</span>
        <a
          v-else
          @click.prevent="handleLink(item)"
        >{{ item.meta.title }}</a>
      </el-breadcrumb-item>
    </transition-group>
  </el-breadcrumb>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { RouteRecordNormalized } from 'vue-router'

const route = useRoute()
const router = useRouter()
const breadcrumbs = ref<RouteRecordNormalized[]>([])

const getBreadcrumb = () => {
  let matched = route.matched.filter(
    (item) => item.meta && (item.meta as any).title
  )
  breadcrumbs.value = matched.filter((item) => {
    return item.meta && (item.meta as any).title && (item.meta as any).breadcrumb !== false
  })
}

watch(
  () => route.path,
  (path) => {
    if (path.startsWith('/redirect/')) {
      return
    }
    getBreadcrumb()
  }
)

getBreadcrumb()

const handleLink = (item: any) => {
  const { redirect, path } = item
  if (redirect) {
    router.push(redirect)
    return
  }
  router.push(path)
}
</script>

<style lang="scss" scoped>
.el-breadcrumb__inner,
.el-breadcrumb__inner a {
  font-weight: 400 !important;
}

.app-breadcrumb.el-breadcrumb {
  display: inline-block;
  font-size: 14px;
  line-height: 50px;
  margin-left: 8px;

  .no-redirect {
    color: #97a8be;
    cursor: text;
  }
}
</style>