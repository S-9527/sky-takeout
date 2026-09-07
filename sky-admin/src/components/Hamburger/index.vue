<template>
  <div :class="[{'is-active': isActive}]" @click="toggleClick">
    <img class="svg-icon" src="@/assets/icons/hamburger.svg" width="20" height="20" />
  </div>
</template>

<script setup lang="ts">
defineProps({
  isActive: { type: Boolean, default: false }
})

const emit = defineEmits(['toggleClick'])

const toggleClick = () => {
  emit('toggleClick')
}
</script>

<style lang="scss" scoped>
.svg-icon {
  vertical-align: middle;
}

.is-active {
  transform: rotate(180deg);
}
</style>