<!--底部数据加载中-->
<template>
  <view class="bottom-box">
    <view class="bottom-item">
      <text class="title">{{loadingText}}</text>
    </view>
  </view>
</template>
<script setup lang="ts">
// 获取父级传的数据
withDefaults(
  defineProps<{
    // 数据加载提示
    loadingText?: string
  }>(),
  {
    loadingText: ''
  }
)
</script>

<style lang="scss">
.bottom-box {
  width: 730rpx;
  .bottom-item {
    width: 730rpx;
    height: 100rpx;
    line-height: 100rpx;
    text-align: center;
    .line {
      display: inline-block;
      width: 200rpx;
      border-bottom: 1px solid #989e98;
      vertical-align: middle;
    }
    .title {
      color: #969799;
      font-size: 28rpx;
    }
  }
}
</style>
