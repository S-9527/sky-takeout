<!--新增收获地址-->
<template>
  <view class="customer-box">
    <uni-nav-bar
      @clickLeft="goBack"
     
      leftIcon="arrowleft"
      :title="delId ? '编辑收货地址' : '新增收货地址'"
      :statusBar="true"
      :fixed="true"
      color="#ffffff"
      backgroundColor="#333333"
    ></uni-nav-bar>
    <view
      class="add_edit"
      :style="{ height: `calc(100% - ${statusBarHeight} - 44px)` }"
    >
      <form class="form_address">
        <view class="uni-form-item uni-column form_item">
          <view class="title">联系人:</view>
          <uni-easyinput
            class="uni-input"
            v-model="form.name"
            placeholder-class="uni-place"
            placeholder="请填写收货人的姓名"
            minlength="2"
            maxlength="12"
          />
          <view class="radio">
            <view
              class="radio-item"
              v-for="(item, index) in items"
              :key="item.value"
              @click="sexChangeHandle(item.value)"
            >
              <image
                v-if="item.value != form.sex"
                class="radio-img"
                src="../../static/icon-radio.png"
              ></image>
              <image
                v-else
                class="radio-img"
                src="../../static/icon-radio-selected.png"
              ></image>
              <text class="radio-label">{{ item.name }}</text>
            </view>
          </view>
        </view>
        <view class="uni-form-item uni-column form_item">
          <view class="title">手机号:</view>
          <uni-easyinput
            class="uni-input"
            v-model="form.phone"
            type="number"
            placeholder-class="uni-place"
            placeholder="请填写收货人手机号码"
            maxlength="11"
          />
        </view>
        <view class="uni-form-item uni-column form_item pad">
          <view class="title">收货地址:</view>
          <!-- 联动省市县 -->
          <view class="update-input">
            <view class="update-adress" @click="openAddres">
              <text
                v-if="showInput"
                class="uni-input"
                :class="address !== '' ? '' : 'uni-place'"
                >{{ address !== "" ? address : "省/市/区" }}</text
              >
              <text class="addressIcon">
                <text class="icon" :class="showClass ? 'iconOn' : ''"></text>
              </text>
            </view>

            <!-- 详细地址 -->
            <textarea
              class="detail"
              :class="{ 'detail-ios': platform == 'ios' }"
              placeholder-class="uni-place"
              v-model="form.detail"
              placeholder="详细地址（精确到门牌号）"
            ></textarea>
          </view>
        </view>
        <view class="uni-form-item uni-column form_item tag-box">
          <view class="title">标签:</view>
          <text
            :class="{ active: form.type === item.type }"
            class="tag_text"
            v-for="item in options"
            :key="item.type"
            @click="getTextOption(item)"
            >{{ item.name }}</text
          >
        </view>
      </form>

      <view class="add_address">
        <button
          class="add_btn"
          type="primary"
          plain="true"
          @click="addAddressFun()"
        >
          保存地址
        </button>
        <button
          v-if="showDel"
          class="del_btn"
          type="default"
          plain="true"
          @click="deleteAddressFun()"
        >
          删除地址
        </button>
      </view>
    </view>
    <simple-address
      ref="simpleAddressRef"
      :pickerValueDefault="cityPickerValueDefault"
      @onConfirm="onConfirm"
      @isClass="isClass"
      themeColor="#F58C21"
    ></simple-address>
  </view>
</template>

<script setup lang="ts">
// @ts-nocheck
import { ref, computed } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import SimpleAddress from '../common/simple-address/simple-address.nvue'
import {
  addAddressBook,
  delAddressBook,
  queryAddressBookById,
  editAddressBook
} from '../api/api'

const platform = ref('ios')
const showDel = ref(false)
const showInput = ref(true)
const valueMan = ref(true)
const valueWoman = ref(true)
const showClass = ref(false)
const items = ref([
  { value: '0', name: '先生' },
  { value: '1', name: '女士' }
])
const current = ref(0)
const options = ref([
  { name: '公司', type: 1 },
  { name: '家', type: 2 },
  { name: '学校', type: 3 }
])
const form = ref({
  name: '',
  phone: '',
  id: 0,
  type: 1 as any,
  sex: '0',
  provinceCode: '11',
  provinceName: '',
  cityCode: '1101',
  cityName: '',
  districtCode: '110102',
  districtName: '',
  detail: ''
})
const cityPickerValueDefault = ref([0, 0, 1])
const pickerText = ref('')
const address = ref('')
const delId = ref('')
const simpleAddressRef = ref<InstanceType<typeof SimpleAddress> | null>(null)

const statusBarHeight = computed(() => {
  return uni.getSystemInfoSync().statusBarHeight + 'px'
})

onLoad((options: any) => {
  init()
  if (options && options.type === '编辑') {
    delId.value = ''
    showDel.value = true
    uni.setNavigationBarTitle({
      title: '编辑收获地址'
    })
    delId.value = options.id
    queryAddressBookByIdFn(options.id)
  } else {
    showDel.value = false
  }
})

onUnload(() => {
  uni.removeStorage({
    key: 'edit'
  })
})

function init() {
  const res = uni.getSystemInfoSync()
  platform.value = res.platform
}

function goBack() {
  uni.redirectTo({
    url: '/pages/address/address'
  })
}

function queryAddressBookByIdFn(id: number) {
  queryAddressBookById({ id }).then((res: any) => {
    if (res.code === 200) {
      form.value = {
        provinceCode: res.data.provinceCode,
        cityCode: res.data.cityCode,
        districtCode: res.data.districtCode,
        phone: res.data.phone,
        name: res.data.consignee,
        sex: res.data.sex,
        type: Number(res.data.label),
        detail: res.data.detail,
        id: res.data.id
      }
      if (
        res.data.provinceName &&
        res.data.cityName &&
        res.data.districtName
      ) {
        address.value =
          res.data.provinceName +
          '/' +
          res.data.cityName +
          '/' +
          res.data.districtName
      }
    }
  })
}

function isClass(val: boolean) {
  showClass.value = val
}

function openAddres() {
  simpleAddressRef.value?.open()
  uni.hideKeyboard()
}

function onConfirm(e: any) {
  form.value.provinceCode = e.provinceCode
  form.value.cityCode = e.cityCode
  form.value.districtCode = e.areaCode
  address.value = e.label
}

function bindTextAreaBlur(e: any) {
}

function sexChangeHandle(val: string) {
  form.value.sex = val
}

function addAddressFun() {
  if (form.value.name === '') {
    return uni.showToast({
      title: '联系人不能为空',
      duration: 1000,
      icon: 'none'
    })
  } else if (form.value.phone === '') {
    return uni.showToast({
      title: '手机号不能为空',
      duration: 1000,
      icon: 'none'
    })
  } else if (form.value.type === '') {
    return uni.showToast({
      title: '所属标签不能为空',
      duration: 1000,
      icon: 'none'
    })
  } else if (address.value === '') {
    return uni.showToast({
      title: '所在地区不能为空',
      duration: 1000,
      icon: 'none'
    })
  } else if (form.value.detail === '') {
    return uni.showToast({
      title: '详细地址不能为空不能为空',
      duration: 1000,
      icon: 'none'
    })
  }

  if (form.value.phone) {
    const reg =
      /^(13[0-9]|14[01456879]|15[0-35-9]|16[2567]|17[0-8]|18[0-9]|19[0-35-9])\d{8}$/
    if (!reg.test(form.value.phone)) {
      return uni.showToast({
        title: '手机号输入有误',
        duration: 1000,
        icon: 'none'
      })
    }
  }
  if (form.value.name) {
    const reg = /^[\u0391-\uFFE5A-Za-z0-9]{2,12}$/
    if (!reg.test(form.value.name)) {
      return uni.showToast({
        title: '请输入合法的2-12个字符',
        duration: 1000,
        icon: 'none'
      })
    }
  }
  const params: any = {
    ...form.value,
    label: form.value.type,
    consignee: form.value.name,
    provinceName: address.value.split('/')[0],
    cityName: address.value.split('/')[1],
    districtName: address.value.split('/')[2]
  }
  if (showDel.value) {
    editAddressBook(params).then((res: any) => {
      if (res.code === 200) {
        uni.redirectTo({
          url: '/pages/address/address'
        })
      }
    })
  } else {
    delete params.id
    addAddressBook(params).then((res: any) => {
      if (res.code === 200) {
        uni.redirectTo({
          url: '/pages/address/address'
        })
      }
    })
  }
}

function deleteAddressFun() {
  delAddressBook(delId.value as any).then((res: any) => {
    if (res.code === 200) {
      uni.redirectTo({
        url: '/pages/address/address'
      })
      uni.showToast({
        title: '地址删除成功',
        duration: 1000,
        icon: 'none'
      })
      form.value.name = ''
      form.value.phone = ''
      form.value.detail = ''
      form.value.type = 1
      form.value.sex = '0'
      form.value.provinceCode = '11'
      form.value.cityCode = '1101'
      form.value.districtCode = '110102'
    }
  })
}

function getTextOption(item: { name: string; type: number }) {
  form.value.type = item.type
}
</script>

<style lang="scss" scoped>
.add_edit {
  width: 750rpx;
  height: 100%;
  background-color: #fff;
  .form_address {
    .form_item {
      margin: 0 22rpx;
      padding: 36rpx 0;
      border-bottom: 1px solid #efefef;
      display: flex;
      align-items: center;
      &.pad {
        padding-bottom: 10rpx;
        align-items: baseline;
      }
      .title {
        width: 140rpx;
        opacity: 1;
        font-size: 28rpx;
        font-family: PingFangSC, PingFangSC-Medium;
        font-weight: 600;
        text-align: left;
        color: #333333;
        letter-spacing: 0px;
        line-height: 44rpx;
      }
      ::v-deep .is-input-border {
        border: 0 none;
        border-radius: none;
        min-height: auto;
        line-height: 16rpx;
        .uni-easyinput__content-input {
          padding-left: 0 !important;
          font-size: 26rpx;
        }
        .uni-easyinput__placeholder-class {
          font-size: 26rpx;
        }
        .uni-easyinput__content-textarea {
          padding: 18rpx 0 0;
          width: 100%;
          min-height: 60rpx;
          box-sizing: border-box;
          overflow: visible;
          height: auto;
          font-size: 26rpx;
        }
      }
      .uni-input {
        flex: 1;
      }
      ::v-deep .uni-place {
        font-size: 26rpx;
        font-family: PingFangSC, PingFangSC-Regular;
        font-weight: 400;
        color: #999999 !important;
      }
      .radio {
        opacity: 1;
        font-size: 26rpx;
        font-family: PingFangSC, PingFangSC-Regular;
        font-weight: 400;
        text-align: left;
        color: #333333;
        letter-spacing: 0px;
        height: 40rpx;
        display: flex;
        padding-right: 20rpx;
        margin-left: 20rpx;
        .radio-item {
          display: flex;
          align-items: center;
          &:first-child {
            margin-right: 54rpx;
          }
        }
        .radio-img {
          width: 32rpx;
          height: 32rpx;
          margin-right: 10rpx;
        }
      }

      // 标签
      .tag_text {
        width: 68rpx;
        height: 44rpx;
        line-height: 40rpx;
        border: 1px solid #e5e4e4;
        display: inline-block;
        border-radius: 6rpx;
        text-align: center;
        box-sizing: border-box;
        color: #333333;
        font-size: 24rpx;
        &:nth-child(3) {
          margin: 0 20rpx;
        }
        &:nth-child(3) {
          &.active {
            background: #fef8e7;
            border: 1px solid #fef8e7;
          }
        }
        &:nth-child(4) {
          &.active {
            background: #e7fef8;
            border: 1px solid #e7fef8;
          }
        }
      }

      .active {
        background: #e1f1fe;
        border: 1px solid #e1f1fe;
      }
      .addressIcon {
        width: 24px;
        height: 24px;
        display: inline-block;
        position: absolute;
        right: 20rpx;
        top: 0;
        .icon {
          width: 16px;
          height: 16px;
          display: inline-block;
          background: url(../../static/toRight.png) no-repeat 6rpx 50%;
          background-size: contain;
          transform: rotate(90deg);
          margin-left: 10rpx;
        }
        .iconOn {
          transform: rotate(-90deg);
        }
      }

      .update-input {
        flex: 1;
      }
      .update-adress {
        position: relative;
        line-height: 40rpx;
        padding-bottom: 18rpx;
      }
    }
    // 详细地址
    .detail {
      width: 100%;
      height: 50rpx;
      margin: 0;
      ::v-deep .uni-place {
        font-family: PingFangSC, PingFangSC-Regular;
        font-weight: 400;
        color: #999999;
        text-align: left;
        line-height: 50rpx;
      }
    }
    .detail-ios {
      padding: 20rpx 14rpx;
    }
  }
  .add_address {
    margin: 0 auto;
    button {
      margin-top: 40rpx;
      margin: 40rpx 18rpx 0 18rpx;
      height: 86rpx;
      line-height: 86rpx;
      border-radius: 8rpx;
      opacity: 1;
      font-size: 30rpx;
      font-family: PingFangSC, PingFangSC-Medium;
      font-weight: 600;
      text-align: center;
      color: #333333;
      letter-spacing: 0px;
      border: 0 none;
    }
    .add_btn {
      background: #ffc200;

      .img_btn {
        width: 44rpx;
        height: 44rpx;
        vertical-align: middle;
        margin-bottom: 8rpx;
      }
    }

    .del_btn {
      background: #f6f6f6;
    }
  }
}

.customer-box {
  height: 100vh;
}
::v-deep .uni-icons {
  font-size: 24px !important;
}
::v-deep .content-clear-icon {
  display: inline-block;
  width: 36rpx;
  height: 36rpx;
  line-height: 36rpx;
  padding-top: 4rpx;
  padding-bottom: 4rpx;
}
</style>
