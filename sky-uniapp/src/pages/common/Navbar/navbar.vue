<template>
	<view class="navBar">
		<!-- 个人中心 -->
		<view @click="myCenterFun" class="person-box" :style="{ top: ht + 'px' }">
			<image class="test_image" src="../../../static/center.png"></image>
			<span class="person-title">个人中心</span>
		</view>
		<image class="index_bg" src="../../../static/bg.png"></image>
	</view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const ht = computed(() => {
	// #ifdef MP-WEIXIN
	const res = uni.getMenuButtonBoundingClientRect()
	return res.top + 5
	// #endif
	// #ifndef MP-WEIXIN
	return uni.upx2px(100)
	// #endif
})

function myCenterFun() {
	uni.navigateTo({
		url: '/pages/my/my'
	})
}
</script>

<style src="./navbar.scss" lang="scss"></style>
