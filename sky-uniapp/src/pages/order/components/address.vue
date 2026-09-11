<!--地址-->
<template>
  <view class="container new_address">
    <!-- 地址 -->
    <view class="top" @click="goAddress">
      <!-- 无地址 -->
      <view v-if="!address" class="address_name_disabled">
        请选择收货地址
      </view>
      <!-- end -->
      <!-- 有地址 -->
      <view v-else class="address_name">
        <view class="address">
          <text class="tag" :class="'tag' + tagLabel">{{ addressLabel }}</text>
          <text class="word">{{ address }}</text>
        </view>
        <view class="name">
          <text class="name_1">{{ cryptoName }}</text>
          <text class="name_2">{{ phoneNumber }}</text>
        </view>
        <view v-if="address" class="infoTip"
          >为减少接触，降低风险，推荐使用无接触配送</view
        >
      </view>
      <view class="address_image">
        <view class="to_right"></view>
      </view>
      <!-- end -->
    </view>
    <!-- 送达时间 -->
    <view class="bottom">
      <div class="bottomTime" @click="openTimePopuo('bottom')">
        <text class="time_name_disabled">立即送出</text>
        <view class="address_image">
          <text class="">{{ arrivalTime }}送达</text>
          <view class="to_right"></view>
        </view>
      </div>

      <view v-if="address" class="infoTip"
        >因配送订单较多，送达时间可能波动</view
      >
    </view>
    <!-- end -->
    <!-- 时间弹层 -->
    <uni-popup ref="timePopup" @change="change" class="popupBox">
      <view class="popup-content">
        <view class="pickerCon">
          <view class="dayBox">
            <scroll-view
              scroll-x="true"
              :scroll-into-view="scrollinto"
              :scroll-with-animation="true"
            >
              <view
                v-for="(item, index) in popleft"
                :key="index"
                :id="'tab' + index"
                class="scroll-row-item"
                @click="dateChange(index)"
              >
                <view v-for="(val, i) in weeks" :key="i">
                  <view
                    :class="tabIndex == index ? 'scroll-row-day' : ''"
                    v-if="index === i"
                    ><text class="line"></text>{{ item
                    }}<text class="week">({{ val }})</text></view
                  >
                </view>
              </view>
            </scroll-view>
          </view>
          <view class="timeBox">
            <scroll-view
              class="card_order_list"
              scroll-y="true"
              scroll-top="40rpx"
            >
              <view
                v-for="(val, i) in newDateData"
                :key="i"
                class="item"
                :class="selectValue === i ? 'city-column_select' : ''"
                @click="timeClick(val, i)"
                >{{ val }}</view
              >
            </scroll-view>
          </view>
        </view>
        <view class="btns" @click="onsuer">取消</view>
      </view>
    </uni-popup>
    <!-- end -->
  </view>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useAppStore } from '@/stores'

// 获取父级传的数据
const props = withDefaults(
  defineProps<{
    // 是公司还是家里样式
    tagLabel?: string
    // 是公司还是家里
    addressLabel?: string
    // 地址
    address?: string
    // 名称
    nickName?: string
    gender?: number
    // 电话
    phoneNumber?: string
    // 送达时间
    arrivalTime?: string
    // 当前选中
    tabIndex?: number
    // 当前选中的时间样式
    selectValue?: number
    // 时间选中的左侧数据（今天、明天）
    popleft?: string[]
    // 周几
    weeks?: string[]
    // 时间段
    newDateData?: string[]
  }>(),
  {
    tagLabel: '',
    addressLabel: '',
    address: '',
    nickName: '',
    gender: -1,
    phoneNumber: '',
    arrivalTime: '',
    tabIndex: 0,
    selectValue: 0,
    popleft: () => [],
    weeks: () => [],
    newDateData: () => []
  }
)
const emit = defineEmits<{
  (e: 'goAddress'): void
  (e: 'change'): void
  (e: 'dateChange', index: number): void
  (e: 'timeClick', val: { val: any; i: number }): void
}>()

const store = useAppStore()
const { gender: storeGender } = storeToRefs(store)

const timePopup = ref<any>(null)

// 地址选择
function goAddress() {
  emit('goAddress')
}

// 送达时间弹层
function openTimePopuo(type: any) {
  timePopup.value?.open(type)
}

function change() {
  emit('change')
}

// 星期几选择
function dateChange(index: number) {
  emit('dateChange', index)
}

// 选中时间段
function timeClick(val: any, i: number) {
  emit('timeClick', { val: val, i: i })
  onsuer()
}

// 取消时间选择
function onsuer(type: any) {
  timePopup.value?.close(type)
}

// 万先生
const cryptoName = computed(() => {
  if (storeGender.value === 0) {
    // 男
    return props.nickName.charAt(0) + '先生'
  } else {
    // 女
    return props.nickName.charAt(0) + '女士'
  }
})
</script>
<style src="./../style.scss" lang="scss" scoped></style>