<!--菜品信息-->
<template>
  <view class="box order_list">
    <view class="word_text">
      <text class="word_style">{{ shopInfo.shopName }}</text>
    </view>
    <view class="order-type">
      <view class="type_item" v-for="(obj, index) in orderDataes" :key="index">
        <view class="dish_img">
          <image mode="aspectFill" :src="obj.image" class="dish_img_url"></image>
        </view>
        <view class="dish_info">
          <view class="dish_name"> {{ obj.name }} </view>
          <view class="dish_dishFlavor" v-if="obj.dishFlavor">
            {{ obj.dishFlavor }}
          </view>
          <view class="dish_price">×
            <text v-if="obj.number && obj.number > 0" class="dish_number">{{
              obj.number
            }}</text>
          </view>
          <view class="dish_active">
            <text>￥</text> {{ obj.amount.toFixed(2) }}
          </view>
        </view>
      </view>
      <view class="iconUp">
        <view @click="showDisplay = !showDisplay" v-if="orderListDataes.length > 3">
          <text>{{ !showDisplay ? "展开更多" : "点击收起" }}</text>
          <image class="icon_img" :class="showDisplay ? 'icon_imgDown' : ''" src="../../../static/toRight.png" mode="">
          </image>
        </view>
      </view>
      <view class="orderList">
        <view class="orderInfo">
          <text class="text">打包费</text><text class="may">￥</text>{{ orderDishNumber }}
        </view>
        <view class="orderInfo">
          <text class="text">配送费</text><text class="may">￥</text>{{ deliveryFee }}
        </view>
        <view class="totalMoney">
          合计<text class="text"><text>￥</text>{{ orderDishPrice.toFixed(2) }}</text>
        </view>
      </view>
    </view>
  </view>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useAppStore } from '@/stores'

const showDisplay = ref<boolean>(false)

// 获取父级传的数据
withDefaults(
  defineProps<{
    // 菜品数据
    orderDataes?: any[]
    // 菜品数据
    orderListDataes?: any[]
    // 打包费
    orderDishNumber?: number
    // 合计总钱数
    orderDishPrice?: number
  }>(),
  {
    orderDataes: () => [],
    orderListDataes: () => [],
    orderDishNumber: 0,
    orderDishPrice: 0
  }
)

const store = useAppStore()
const { deliveryFee, shopInfo } = storeToRefs(store)
</script>
<style src="./../style.scss" lang="scss" scoped></style>