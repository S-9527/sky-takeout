<template>
  <div class="HeadLable">
    <span
      v-if="goback"
      class="goBack"
      @click="goBack()"
    ><img
      src="@/assets/icons/btn_back@2x.png"
      alt=""
    > 返回</span>
    <span v-if="!butList">{{ title }}</span>
    <div v-if="butList">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

defineProps({
  goback: { type: Boolean, default: false },
  butList: { type: Boolean, default: false },
  title: { type: String, default: '集团管理' }
})

const router = useRouter()

const goBack = () => {
  router.go(-1)
}
</script>