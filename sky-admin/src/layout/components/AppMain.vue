<template>
  <section class="app-main">
    <router-view v-slot="{ Component }">
      <transition
        name="fade-transform"
        mode="out-in"
      >
        <component :is="Component" />
      </transition>
    </router-view>
  </section>
</template>

<style lang="scss" scoped>
.app-main {
  height: calc(100% - 64px);
  overflow-y: auto;
}
</style>