<script setup lang="ts">
import {
  Bell,
  BellFilled,
  DataLine,
  Dish,
  Food,
  Lock,
  Money,
  Odometer,
  Shop,
  SwitchButton,
  User,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { describeNotification, notificationOrderId } from '@/utils/notification'
import { formatDateTime } from '@/utils/datetime'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const notifications = useNotificationStore()

interface MenuItem {
  name: string
  title: string
  icon: unknown
  adminOnly?: boolean
}

interface MenuGroup {
  title?: string
  items: MenuItem[]
}

const menuGroups: MenuGroup[] = [
  {
    items: [{ name: 'workbench', title: '工作台', icon: Odometer }],
  },
  {
    title: '订单',
    items: [
      { name: 'orders', title: '订单管理', icon: Food },
      { name: 'refunds', title: '退款记录', icon: Money },
    ],
  },
  {
    title: '商品',
    items: [
      { name: 'categories', title: '分类管理', icon: Dish },
      { name: 'dishes', title: '菜品管理', icon: Food },
      { name: 'setmeals', title: '套餐管理', icon: Dish },
    ],
  },
  {
    title: '门店',
    items: [
      { name: 'shop', title: '营业状态', icon: Shop },
      { name: 'insights', title: '数据统计', icon: DataLine },
      { name: 'employees', title: '员工管理', icon: User, adminOnly: true },
    ],
  },
]

const visibleGroups = computed(() =>
  menuGroups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => !item.adminOnly || auth.isAdmin),
    }))
    .filter((group) => group.items.length > 0),
)

/** 详情页高亮它所属的列表菜单项 */
const activeMenu = computed(() => {
  if (route.name === 'order-detail') return 'orders'
  return typeof route.name === 'string' ? route.name : ''
})

const pageTitle = computed(() => route.meta.title ?? '')
const soundOn = ref(notifications.soundEnabled)

watch(
  () => notifications.soundEnabled,
  (value) => {
    soundOn.value = value
  },
)

function goOrder(orderId: number): void {
  void router.push({ name: 'order-detail', params: { id: orderId } })
}

function openNotification(message: Parameters<typeof describeNotification>[0]): void {
  const orderId = notificationOrderId(message)
  if (orderId !== null) goOrder(orderId)
}

async function logout(): Promise<void> {
  await ElMessageBox.confirm('确认退出登录?', '提示', { type: 'warning' }).catch(() => 'cancel')
    .then(async (action) => {
      if (action === 'cancel') return
      notifications.disconnect()
      await auth.logout()
      ElMessage.success('已退出登录')
      await router.replace({ name: 'login' })
    })
}

onMounted(() => {
  notifications.setNavigateHandler(goOrder)
  notifications.connect()
})

onBeforeUnmount(() => {
  notifications.setNavigateHandler(null)
  notifications.disconnect()
})
</script>

<template>
  <el-container class="h-full">
    <el-aside width="220px" class="flex flex-col bg-slate-900">
      <div class="flex h-16 items-center gap-2 px-4 text-white">
        <el-icon :size="22"><Shop /></el-icon>
        <span class="text-base font-semibold">苍穹外卖管理端</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        background-color="#0f172a"
        text-color="#cbd5e1"
        active-text-color="#ffffff"
        class="flex-1 border-r-0"
      >
        <template v-for="group in visibleGroups" :key="group.title ?? 'default'">
          <div v-if="group.title" class="px-4 pt-4 pb-1 text-xs text-slate-500">
            {{ group.title }}
          </div>
          <el-menu-item v-for="item in group.items" :key="item.name" :index="item.name" :route="{ name: item.name }">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="flex items-center justify-between border-b border-slate-200 bg-white">
        <div class="flex items-center gap-3">
          <h2 class="text-base font-semibold text-slate-800">{{ pageTitle }}</h2>
          <el-tag v-if="!notifications.connected" type="info" size="small" effect="plain">
            通知未连接
          </el-tag>
        </div>

        <div class="flex items-center gap-3">
          <el-button
            text
            :icon="soundOn ? BellFilled : Bell"
            :title="soundOn ? '关闭提示音' : '开启提示音'"
            @click="notifications.toggleSound()"
          >
            {{ soundOn ? '提示音开' : '提示音关' }}
          </el-button>

          <el-popover placement="bottom-end" :width="360" trigger="click" @show="notifications.markAllRead()">
            <template #reference>
              <el-badge :value="notifications.unreadCount" :hidden="notifications.unreadCount === 0">
                <el-button :icon="Bell" circle />
              </el-badge>
            </template>
            <div class="max-h-80 overflow-auto">
              <div class="mb-2 flex items-center justify-between">
                <span class="text-sm font-semibold">通知</span>
                <el-button text size="small" @click="notifications.clearRecent()">清空</el-button>
              </div>
              <el-empty v-if="notifications.recent.length === 0" description="暂无通知" :image-size="60" />
              <div
                v-for="message in notifications.recent"
                :key="message.messageId"
                class="cursor-pointer rounded px-2 py-2 hover:bg-slate-100"
                data-testid="notification-item"
                @click="openNotification(message)"
              >
                <div class="flex items-center justify-between text-sm">
                  <span class="font-medium">{{ describeNotification(message).title }}</span>
                  <span class="text-xs text-slate-400">{{ formatDateTime(message.timestamp) }}</span>
                </div>
                <div class="text-xs text-slate-500">{{ describeNotification(message).summary }}</div>
              </div>
            </div>
          </el-popover>

          <el-dropdown>
            <span class="flex cursor-pointer items-center gap-2 text-sm text-slate-700">
              <el-avatar :size="28">{{ auth.displayName.slice(0, 1) || '员' }}</el-avatar>
              {{ auth.displayName }}
              <el-tag size="small" :type="auth.isAdmin ? 'danger' : 'info'" effect="plain">
                {{ auth.isAdmin ? '管理员' : '员工' }}
              </el-tag>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item :icon="Lock" @click="router.push({ name: 'password' })">
                  修改密码
                </el-dropdown-item>
                <el-dropdown-item :icon="SwitchButton" divided @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="bg-slate-50">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>
