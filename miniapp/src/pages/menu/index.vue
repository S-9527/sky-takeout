<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'

import * as api from '@/api/customer'
import { ApiError } from '@/api/http'
import { useCartStore } from '@/stores/cart'
import { useSessionStore } from '@/stores/session'
import type { Category, Dish, DishDetail, FlavorChoice } from '@/types'
import { formatCents } from '@/utils/money'

/**
 * 点餐页(首页)。
 *
 * 数据流:分类(左)→ 当前分类的菜品(右)→ 加购 → 购物车(服务端算账)。
 * 所有"数量合并、上限、下架判断"都在后端,这里只负责展示与转发。
 */
const session = useSessionStore()
const cart = useCartStore()

const categories = ref<Category[]>([])
const activeCategoryId = ref<number | null>(null)
const dishes = ref<Dish[]>([])
const shopOpen = ref(true)
const notice = ref<string | null>(null)
const loading = ref(false)

const cartVisible = ref(false)
const flavorVisible = ref(false)
const flavorDish = ref<DishDetail | null>(null)
const chosenFlavors = ref<Record<string, string>>({})

const activeCategoryName = computed(
  () => categories.value.find((item) => item.id === activeCategoryId.value)?.name ?? '',
)

async function bootstrap(): Promise<void> {
  if (!session.authenticated) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  loading.value = true
  try {
    const [shop, categoryList] = await Promise.all([api.getShopStatus(), api.listCategories('DISH')])
    shopOpen.value = shop.isOpen
    notice.value = shop.notice ?? null
    categories.value = categoryList
    // 购物车按服务端为准,每次回到页面都重拉(令牌失效会由 http 层统一处理)
    await cart.refresh()
    if (categoryList.length > 0 && activeCategoryId.value === null) {
      await selectCategory(categoryList[0].id)
    }
  } catch (error) {
    if (error instanceof ApiError && error.isAuthError) return
    uni.showToast({ title: '加载失败,请下拉重试', icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function selectCategory(categoryId: number): Promise<void> {
  activeCategoryId.value = categoryId
  try {
    dishes.value = (await api.listDishes(categoryId)).records
  } catch (error) {
    if (error instanceof ApiError) uni.showToast({ title: error.message, icon: 'none' })
  }
}

/** 有口味配置的菜要先选口味:契约要求每个维度都要给值(`CART_FLAVOR_REQUIRED`) */
async function onAddDish(dish: Dish): Promise<void> {
  if (!shopOpen.value) {
    uni.showToast({ title: '门店已打烊,暂时无法下单', icon: 'none' })
    return
  }
  try {
    const detail = await api.getDish(dish.id)
    if (detail.flavors && detail.flavors.length > 0) {
      flavorDish.value = detail
      chosenFlavors.value = {}
      flavorVisible.value = true
      return
    }
    await addToCart({ itemType: 'DISH', dishId: dish.id })
  } catch (error) {
    showError(error)
  }
}

async function addToCart(body: {
  itemType: 'DISH' | 'SETMEAL'
  dishId?: number
  setmealId?: number
  flavorChoice?: FlavorChoice[]
}): Promise<void> {
  try {
    await cart.add(body)
    uni.showToast({ title: '已加入购物车', icon: 'none' })
  } catch (error) {
    showError(error)
  }
}

async function confirmFlavors(): Promise<void> {
  const detail = flavorDish.value
  if (!detail) return
  const missing = (detail.flavors ?? []).find((flavor) => !chosenFlavors.value[flavor.name])
  if (missing) {
    uni.showToast({ title: `请选择${missing.name}`, icon: 'none' })
    return
  }
  flavorVisible.value = false
  await addToCart({
    itemType: 'DISH',
    dishId: detail.id,
    flavorChoice: Object.entries(chosenFlavors.value).map(([name, option]) => ({ name, option })),
  })
}

async function changeQuantity(id: number, quantity: number): Promise<void> {
  try {
    if (quantity <= 0) await cart.remove(id)
    else await cart.setQuantity(id, quantity)
    if (cart.isEmpty) cartVisible.value = false
  } catch (error) {
    showError(error)
  }
}

async function clearCart(): Promise<void> {
  try {
    await cart.clear()
    cartVisible.value = false
  } catch (error) {
    showError(error)
  }
}

function goCheckout(): void {
  if (cart.isEmpty) return
  if (!shopOpen.value) {
    uni.showToast({ title: '门店已打烊,暂时无法下单', icon: 'none' })
    return
  }
  cartVisible.value = false
  uni.navigateTo({ url: '/pages/order/confirm' })
}

function showError(error: unknown): void {
  const message = error instanceof ApiError ? error.message : '操作失败,请稍后重试'
  uni.showToast({ title: message, icon: 'none' })
}

/** 订单角标之类的运行时状态每次回到页面都刷一次 */
onShow(() => {
  void bootstrap()
})
</script>

<template>
  <view class="flex h-screen flex-col bg-slate-50">
    <view v-if="!shopOpen" class="bg-amber-100 px-4 py-2 text-xs text-amber-700">
      门店已打烊,可以浏览但暂时无法下单
    </view>
    <view v-else-if="notice" class="bg-blue-50 px-4 py-2 text-xs text-blue-700">{{ notice }}</view>

    <view class="flex flex-1 overflow-hidden">
      <!-- 左:分类 -->
      <scroll-view scroll-y class="w-24 bg-white">
        <view
          v-for="category in categories"
          :key="category.id"
          class="border-l-4 px-2 py-4 text-center text-sm"
          :class="
            category.id === activeCategoryId
              ? 'border-blue-600 bg-slate-50 font-medium text-blue-600'
              : 'border-transparent text-slate-600'
          "
          @click="selectCategory(category.id)"
        >
          {{ category.name }}
        </view>
      </scroll-view>

      <!-- 右:商品 -->
      <scroll-view scroll-y class="flex-1 px-3">
        <view class="py-3 text-xs text-slate-400">{{ activeCategoryName }}</view>
        <view v-if="loading" class="py-10 text-center text-sm text-slate-400">加载中…</view>
        <view v-for="dish in dishes" :key="dish.id" class="mb-3 flex rounded-lg bg-white p-3">
          <image
            v-if="dish.imageUrl"
            :src="dish.imageUrl"
            class="mr-3 h-16 w-16 rounded"
            mode="aspectFill"
          />
          <view v-else class="mr-3 flex h-16 w-16 items-center justify-center rounded bg-slate-100 text-xs text-slate-400">
            无图
          </view>
          <view class="flex flex-1 flex-col justify-between">
            <view>
              <view class="text-sm font-medium text-slate-800">{{ dish.name }}</view>
              <view class="mt-1 line-clamp-1 text-xs text-slate-400">{{ dish.description }}</view>
            </view>
            <view class="flex items-center justify-between">
              <text class="text-sm font-semibold text-red-500">{{ formatCents(dish.priceCents) }}</text>
              <button
                class="rounded-full bg-blue-600 px-3 py-1 text-xs text-white"
                :data-testid="`add-dish-${dish.id}`"
                @click="onAddDish(dish)"
              >
                加入
              </button>
            </view>
          </view>
        </view>
        <view v-if="!loading && dishes.length === 0" class="py-10 text-center text-sm text-slate-400">
          这个分类下暂时没有在售商品
        </view>
        <view class="h-24" />
      </scroll-view>
    </view>

    <!-- 底部购物车栏 -->
    <view class="flex items-center border-t border-slate-200 bg-white px-3 py-2">
      <view class="relative" @click="cartVisible = !cartVisible">
        <view class="flex h-11 w-11 items-center justify-center rounded-full bg-blue-600 text-white">
          车
        </view>
        <view
          v-if="cart.totalQuantity > 0"
          class="absolute -right-1 -top-1 rounded-full bg-red-500 px-1.5 text-xs text-white"
          data-testid="cart-badge"
        >
          {{ cart.totalQuantity }}
        </view>
      </view>
      <view class="ml-3 flex-1">
        <view class="text-sm font-semibold text-red-500" data-testid="cart-total">
          {{ formatCents(cart.totalAmountCents) }}
        </view>
        <view class="text-xs text-slate-400">共 {{ cart.totalQuantity }} 件</view>
      </view>
      <button
        class="rounded-full px-5 py-2 text-sm text-white"
        :class="cart.isEmpty ? 'bg-slate-300' : 'bg-blue-600'"
        :disabled="cart.isEmpty"
        data-testid="go-checkout"
        @click="goCheckout"
      >
        去结算
      </button>
    </view>

    <!-- 购物车明细 -->
    <view v-if="cartVisible" class="fixed inset-0 z-20 flex flex-col justify-end bg-black/40">
      <view class="max-h-2/3 rounded-t-xl bg-white p-4">
        <view class="mb-3 flex items-center justify-between">
          <text class="text-sm font-medium">购物车</text>
          <text class="text-xs text-slate-400" @click="clearCart">清空</text>
        </view>
        <scroll-view scroll-y class="max-h-72">
          <view v-for="item in cart.items" :key="item.id" class="mb-3 flex items-center">
            <view class="flex-1">
              <view class="text-sm text-slate-800">{{ item.name ?? '商品' }}</view>
              <view v-if="item.flavorChoice?.length" class="text-xs text-slate-400">
                {{ item.flavorChoice.map((f) => `${f.name}:${f.option}`).join(' / ') }}
              </view>
              <view v-if="item.available === false" class="text-xs text-red-500">
                {{ item.unavailableReason ?? '已下架' }}
              </view>
            </view>
            <text class="mr-3 text-sm text-red-500">{{ formatCents(item.amountCents) }}</text>
            <view class="flex items-center">
              <button class="h-6 w-6 rounded-full bg-slate-100 text-sm" @click="changeQuantity(item.id, item.quantity - 1)">
                −
              </button>
              <text class="mx-3 text-sm">{{ item.quantity }}</text>
              <button class="h-6 w-6 rounded-full bg-blue-600 text-sm text-white" @click="changeQuantity(item.id, item.quantity + 1)">
                +
              </button>
            </view>
          </view>
          <view v-if="cart.isEmpty" class="py-8 text-center text-sm text-slate-400">购物车是空的</view>
        </scroll-view>
        <button class="mt-3 w-full rounded-full bg-slate-100 py-2 text-sm" @click="cartVisible = false">
          收起
        </button>
      </view>
    </view>

    <!-- 口味选择 -->
    <view v-if="flavorVisible && flavorDish" class="fixed inset-0 z-30 flex flex-col justify-end bg-black/40">
      <view class="rounded-t-xl bg-white p-4">
        <view class="mb-3 text-sm font-medium">{{ flavorDish.name }}</view>
        <view v-for="flavor in flavorDish.flavors" :key="flavor.name" class="mb-4">
          <view class="mb-2 text-xs text-slate-500">{{ flavor.name }}</view>
          <view class="flex flex-wrap gap-2">
            <view
              v-for="option in flavor.options"
              :key="option"
              class="rounded-full border px-3 py-1 text-xs"
              :class="
                chosenFlavors[flavor.name] === option
                  ? 'border-blue-600 bg-blue-50 text-blue-600'
                  : 'border-slate-200 text-slate-600'
              "
              @click="chosenFlavors[flavor.name] = option"
            >
              {{ option }}
            </view>
          </view>
        </view>
        <view class="flex gap-3">
          <button class="flex-1 rounded-full bg-slate-100 py-2 text-sm" @click="flavorVisible = false">
            取消
          </button>
          <button class="flex-1 rounded-full bg-blue-600 py-2 text-sm text-white" @click="confirmFlavors">
            加入购物车
          </button>
        </view>
      </view>
    </view>
  </view>
</template>
