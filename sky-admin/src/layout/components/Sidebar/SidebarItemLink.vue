<template>
  <a v-if="isExternal(to)" :href="to" target="_blank" rel="noopener">
    <slot />
  </a>
  <router-link v-else :to="to">
    <slot />
  </router-link>
</template>

<script setup lang="ts">
import { isExternal } from './validate'

defineProps({
  to: { type: String, required: true }
})
</script>