<!--标题-->
<template>
  <view :style="{ height: statusBarHeight }" class="uni-status-bar">
    <slot />
  </view>
</template>
<script setup lang="ts">
defineOptions({
  name: "UniStatusBar"
})

const statusBarHeight = uni.getSystemInfoSync().statusBarHeight + "px";
</script>
<style lang="scss" scoped>
.uni-status-bar {
  width: 750rpx;
  height: 40rpx;
}
</style>
