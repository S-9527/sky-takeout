<!--餐品/套餐详情弹层-->
<template>
  <!-- 餐品详情 -->
  <view class="dish_detail_pop" v-if="dishDetailes.type == 1">
    <image
      mode="aspectFill"
      class="div_big_image"
      :src="dishDetailes.image"
    ></image>
    <view class="title">{{ dishDetailes.name }}</view>
    <view class="desc">{{ dishDetailes.description }}</view>
    <view class="but_item">
      <view class="price">
        <text class="ico">￥</text>
        {{ Number(dishDetailes.price).toFixed(2) }}
      </view>
      <view
        class="active"
        v-if="dishDetailes.flavors.length === 0 && dishDetailes.dishNumber > 0"
      >
        <image
          src="../../../static/btn_red.png"
          @click="redDishAction(dishDetailes, '普通')"
          class="dish_red"
          mode=""
        ></image>
        <text class="dish_number">{{ dishDetailes.dishNumber }}</text>
        <image
          src="../../../static/btn_add.png"
          class="dish_add"
          @click="addDishAction(dishDetailes, '普通')"
          mode=""
        ></image>
      </view>

      <view class="active" v-if="dishDetailes.flavors.length > 0"
        ><view class="dish_card_add" @click="moreNormDataesHandle(dishDetailes)"
          >选择规格</view
        ></view
      >
      <view
        class="active"
        v-if="
          dishDetailes.dishNumber === 0 && dishDetailes.flavors.length === 0
        "
      >
        <view class="dish_card_add" @click="addDishAction(dishDetailes, '普通')"
          >加入购物车</view
        >
      </view>
    </view>
    <view class="close" @click="dishClose"
      ><image
        class="close_img"
        src="../../../static/but_close.png"
        mode=""
      ></image
    ></view>
  </view>
  <!-- end -->
  <!-- 套餐详情 -->
  <view class="dish_detail_pop" v-else>
    <scroll-view class="dish_items" scroll-y="true" scroll-top="0rpx">
      <view
        class="dish_item"
        v-for="(item, index) in dishMealData"
        :key="index"
      >
        <image class="div_big_image" :src="item.image" mode=""></image>
        <view class="title">
          {{ item.name }}
          <text style="">X{{ item.copies }}</text>
        </view>
        <view class="desc">{{ item.description }}</view>
      </view>
    </scroll-view>
    <view class="but_item">
      <view class="price">
        <text class="ico">￥</text>
        {{ dishDetailes.price }}
      </view>
      <view
        class="active"
        v-if="dishDetailes.dishNumber && dishDetailes.dishNumber > 0"
      >
        <image
          src="../../../static/btn_red.png"
          @click="redDishAction(dishDetailes, '普通')"
          class="dish_red"
          mode=""
        ></image>
        <text class="dish_number">{{ dishDetailes.dishNumber }}</text>
        <image
          src="../../../static/btn_add.png"
          class="dish_add"
          @click="addDishAction(dishDetailes, '普通')"
          mode=""
        ></image>
      </view>
      <view class="active" v-else-if="dishDetailes.dishNumber == 0"
        ><view
          class="dish_card_add"
          @click="addDishAction(dishDetailes, '普通')"
          >加入购物车</view
        ></view
      >
    </view>
    <view class="close" @click="dishClose"
      ><image
        class="close_img"
        src="../../../static/but_close.png"
        mode=""
      ></image
    ></view>
  </view>
  <!-- end -->
</template>

<script setup lang="ts">
// 获取父级传的数据
defineProps<{
  dishDetailes?: any
  openDetailPop?: boolean
  dishMealData?: any[]
}>()
const emit = defineEmits<{
  (e: 'addDishAction', val: any): void
  (e: 'redDishAction', val: any): void
  (e: 'moreNormDataesHandle', obj: any): void
  (e: 'dishClose'): void
}>()

// 加入购物车
function addDishAction(obj: any, item: string) {
  emit('addDishAction', { obj, item })
}
function redDishAction(obj: any, item: string) {
  emit('redDishAction', { obj, item })
}
// 选择规格
function moreNormDataesHandle(obj: any) {
  emit('moreNormDataesHandle', obj)
}
// 关闭菜单详情
function dishClose() {
  emit('dishClose')
}
</script>
<style lang="scss" scoped>
.dish_detail_pop {
  width: calc(100vw - 160rpx);
  box-sizing: border-box;
  position: relative;
  top: 50%;
  left: 50%;
  padding: 40rpx;
  transform: translateX(-50%) translateY(-50%);
  background: #fff;
  border-radius: 20rpx;
  .div_big_image {
    width: 100%;
    height: 320rpx;
    border-radius: 10rpx;
  }
  .title {
    font-size: 40rpx;
    line-height: 80rpx;
    text-align: center;
    font-weight: bold;
  }
  .dish_items {
    height: 60vh;
  }
  .but_item {
    display: flex;
    position: relative;
    flex: 1;
    .price {
      text-align: left;
      color: #e94e3c;
      line-height: 88rpx;
      box-sizing: border-box;
      font-size: 48rpx;
      font-weight: bold;
      .ico {
        font-size: 28rpx;
      }
    }
    .active {
      position: absolute;
      right: 0rpx;
      bottom: 20rpx;
      display: flex;
      .dish_add,
      .dish_red {
        display: block;
        width: 72rpx;
        height: 72rpx;
      }
      .dish_number {
        padding: 0 10rpx;
        line-height: 72rpx;
        font-size: 30rpx;
        font-family: PingFangSC, PingFangSC-Medium;
        font-weight: 500;
      }
      .dish_card_add {
        width: 200rpx;
        line-height: 60rpx;
        text-align: center;
        font-weight: 500;
        font-size: 28rpx;
        opacity: 1;
        background: #ffc200;
        border-radius: 30rpx;
      }
    }
  }
}
.close {
  position: absolute;
  bottom: -180rpx;
  left: 50%;
  transform: translateX(-50%);
  .close_img {
    width: 88rpx;
    height: 88rpx;
  }
}
</style>
