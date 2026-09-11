<script setup lang="ts">
/**
 * 根组件只做一件事:给路由一个出口。
 * 全局布局在 `layouts/AdminLayout.vue`,登录页等免登录页面不走那个布局。
 */
</script>

<template>
  <router-view />
</template>
