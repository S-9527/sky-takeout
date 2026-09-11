import { computed, ref } from 'vue'
import { ElNotification } from 'element-plus'
import { defineStore } from 'pinia'

import {
  NotificationSocket,
  resolveNotificationUrl,
  type NotificationMessage,
  type SocketState,
} from '@/api/notificationSocket'
import { getAccessToken } from '@/api/tokens'
import { describeNotification, notificationOrderId } from '@/utils/notification'

const MAX_RECENT = 20

/**
 * 通知中心。
 *
 * 通知通道**只是提示信号**:契约 4.4 明确"不对通知做业务判断"。
 * 所以这里收到的消息只做三件事:弹提示、记一笔、记个数;
 * 订单到底什么状态,一律回 REST 接口查。
 *
 * 跳转目标通过 `setNavigateHandler` 注入,而不是在这里 `import router` ——
 * 路由守卫依赖 auth store,store 再依赖 router 就成环了。
 */
export const useNotificationStore = defineStore('notification', () => {
  const connectionState = ref<SocketState>('idle')
  const recent = ref<NotificationMessage[]>([])
  const unreadCount = ref(0)
  const soundEnabled = ref(true)

  let socket: NotificationSocket | null = null
  let navigate: ((orderId: number) => void) | null = null

  const connected = computed(() => connectionState.value === 'open')

  function setNavigateHandler(handler: ((orderId: number) => void) | null): void {
    navigate = handler
  }

  function playBeep(): void {
    if (!soundEnabled.value) return
    try {
      const Ctor =
        (globalThis as { AudioContext?: typeof AudioContext }).AudioContext ??
        (globalThis as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
      if (!Ctor) return
      const context = new Ctor()
      const oscillator = context.createOscillator()
      const gain = context.createGain()
      oscillator.connect(gain)
      gain.connect(context.destination)
      oscillator.frequency.value = 880
      gain.gain.value = 0.05
      oscillator.start()
      oscillator.stop(context.currentTime + 0.18)
    } catch {
      // 浏览器不允许自动播放音频时静默降级,通知本身照常显示
    }
  }

  /** 收到一条通知 —— 独立出来是为了能在没有 WebSocket 的情况下单测 */
  function handleMessage(message: NotificationMessage): void {
    recent.value = [message, ...recent.value].slice(0, MAX_RECENT)
    unreadCount.value += 1

    const { title, summary } = describeNotification(message)
    const orderId = notificationOrderId(message)
    ElNotification({
      title,
      message: summary,
      type: message.type === 'ORDER_URGE' ? 'warning' : 'success',
      duration: 0,
      position: 'bottom-right',
      onClick: () => {
        if (orderId !== null) navigate?.(orderId)
      },
    })
    if (message.type === 'ORDER_NEW' || message.type === 'ORDER_URGE') playBeep()
  }

  function connect(): void {
    if (socket) {
      socket.connect()
      return
    }
    socket = new NotificationSocket({
      url: resolveNotificationUrl(),
      tokenProvider: () => getAccessToken(),
    })
    socket.onStateChange = (state) => {
      connectionState.value = state
    }
    socket.onMessage = (message) => {
      // PONG 只是心跳应答,不进通知中心,否则用户会被自己的心跳刷屏
      if (message.type === 'PONG') return
      handleMessage(message)
    }
    socket.connect()
  }

  function disconnect(): void {
    socket?.disconnect()
    socket = null
    connectionState.value = 'idle'
  }

  function markAllRead(): void {
    unreadCount.value = 0
  }

  function clearRecent(): void {
    recent.value = []
    unreadCount.value = 0
  }

  function toggleSound(): void {
    soundEnabled.value = !soundEnabled.value
  }

  return {
    connectionState,
    connected,
    recent,
    unreadCount,
    soundEnabled,
    setNavigateHandler,
    handleMessage,
    connect,
    disconnect,
    markAllRead,
    clearRecent,
    toggleSound,
  }
})
