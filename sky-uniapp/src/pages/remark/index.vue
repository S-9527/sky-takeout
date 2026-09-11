<template>
  <view class="customer-box">
    <uni-nav-bar
      @clickLeft="goBack"
     
      leftIcon="arrowleft"
      title="订单备注"
      statusBar="true"
      fixed="true"
      color="#ffffff"
      backgroundColor="#333333"
    ></uni-nav-bar>
    <view class="wrap">
      <view class="box">
        <view class="contion">
          <view class="order_list">
            <view class="uni-textarea">
              <textarea
                class="beizhu_text"
                :class="{ beizhu_text_ios: platform === 'ios' }"
                placeholder-class="textarea-placeholder"
                v-model="remark"
                placeholder="无接触配送，将商品挂家门口或放前台，地址封闭管理时请电话联系"
                >{{ getVal }}</textarea
              >
              <text class="numText"
                ><text :class="numVal === 0 ? 'tip' : ''">{{ numVal }}</text
                >/50</text
              >
            </view>
          </view>
        </view>
      </view>
      <view class="btnBox">
        <button
          class="add_btn"
          type="primary"
          plain="true"
          @click="handleSaveRemark"
        >
          完成
        </button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
// @ts-nocheck
import { ref, computed, watch } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { storeToRefs } from 'pinia'
import { useAppStore } from '@/stores'

const store = useAppStore()
const { remarkData } = storeToRefs(store)

const remark = ref('')
const numVal = ref(0)

const platform = computed(() => {
  return uni.getSystemInfoSync().platform
})

function validateTextLength(value: string) {
  const cnReg = /([\u4e00-\u9fa5]|[\u3000-\u303F]|[\uFF00-\uFF60])/g
  const mat = value.match(cnReg)
  let length: number
  if (mat) {
    length = mat.length + (value.length - mat.length) * 0.5
    return length
  } else {
    return value.length * 0.5
  }
}

watch(remark, (val) => {
  const leng = validateTextLength(val)
  if (leng <= 50) {
    numVal.value = Math.floor(leng)
  } else {
    remark.value = val.substring(0, 50)
  }
}, { immediate: true })

const getVal = computed(() => {
  return numVal.value
})

onLoad(() => {
  if (remarkData.value === '') {
    remark.value = ''
  } else {
    remark.value = remarkData.value as string
    numVal.value = remark.value.length
  }
})

function goBack() {
  uni.redirectTo({
    url: '/pages/order/index'
  })
}

function handleSaveRemark() {
  uni.redirectTo({
    url: '/pages/order/index'
  })
  store.setRemark(remark.value)
}
</script>

<style src="./../common/Navbar/navbar.scss" lang="scss" scoped></style>
<style src="./../order/style.scss" lang="scss"></style>
