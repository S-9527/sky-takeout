<!--拨打电话-->
<template>
  <view class="container phoneCon">
    <uni-popup ref="popup" class="popupBox">
      <view class="popup-content">
        <view>{{ phoneData }}</view>
        <view @click="call">呼叫</view>
        <view @click="closePopup" class="closePopup">取消</view>
      </view>
    </uni-popup>
  </view>
</template>
<style src="../../pages/index/style.scss" lang="scss" scoped></style>

<script setup lang="ts">
import { ref } from 'vue'
import { call as makePhoneCall } from '@/utils/index'

const props = defineProps<{
  phoneData?: string
}>()
const emit = defineEmits<{
  (e: 'closePopup'): void
}>()

const popup = ref<any>(null)

// 电话号码拨打
function call() {
  makePhoneCall(props.phoneData || '')
}

// 取消关闭弹层
function closePopup() {
  emit('closePopup')
}

defineExpose({ popup })
</script>
