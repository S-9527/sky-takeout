import { defineStore } from 'pinia'

export interface ShopInfo {
  shopName?: string
  shopAddress?: string
  shopId?: number | string
}

export interface BaseUserInfo {
  nickName?: string
  avatarUrl?: string
  gender?: number
}

// 应用全局状态:由原 vuex 平铺迁移而来,字段与 mutation 名称保持不变
export const useAppStore = defineStore('app', {
  state: () => ({
    storeInfo: {} as Record<string, any>, // 店铺请求的id信息
    shopInfo: {} as ShopInfo, // 店铺详细信息
    orderListData: [] as any[], // 购物车列表信息
    baseUserInfo: '' as string | BaseUserInfo, // 存储获取的用户微信的信息(用户名、头像)
    lodding: false,
    sessionId: '',
    addressBackUrl: '',
    dishTypeIndex: 0,
    shopPhone: '', // 店铺电话
    shopStatus: {} as Record<string, any>, // 店铺状态
    orderData: {} as Record<string, any>,
    token: uni.getStorageSync('token') || '', // token 本地持久化,避免冷启动重复登录
    arrivals: '',
    remarkData: '', // 备注
    addressData: {} as Record<string, any>, // 地址选择
    deliveryFee: 0, // 配送费
    gender: 0 // 收货地址对应的性别 0 先生 1 女士
  }),
  actions: {
    setStoreInfo(provider: any) {
      this.storeInfo = provider
    },
    setShopInfo(provider: ShopInfo) {
      this.shopInfo = provider
    },
    initdishListMut(provider: any[]) {
      this.orderListData = provider
    },
    setBaseUserInfo(provider: any) {
      this.baseUserInfo = provider
    },
    setLodding(provider: boolean) {
      this.lodding = provider
    },
    setSessionId(provider: string) {
      this.sessionId = provider
    },
    setAddressBackUrl(provider: string) {
      this.addressBackUrl = provider
    },
    setDishTypeIndex(provider: number) {
      this.dishTypeIndex = provider
    },
    setShopPhone(provider: string) {
      this.shopPhone = provider
    },
    setShopStatus(provider: any) {
      this.shopStatus = provider
    },
    setOrderData(provider: any) {
      this.orderData = provider
    },
    setToken(provider: string) {
      this.token = provider
      uni.setStorageSync('token', provider)
    },
    setArrivalTime(provider: string) {
      this.arrivals = provider
    },
    setRemark(provider: string) {
      this.remarkData = provider
    },
    setAddress(provider: any) {
      this.addressData = provider
    },
    setDeliveryFee(provider: number) {
      this.deliveryFee = provider
    },
    setGender(provider: number) {
      this.gender = provider
    }
  }
})
