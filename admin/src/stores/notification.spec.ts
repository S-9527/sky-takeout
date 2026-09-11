import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { NotificationMessage } from '@/api/notificationSocket'
import { clearTokens, setTokens } from '@/api/tokens'

import { useNotificationStore } from './notification'

const notify = vi.fn()

vi.mock('element-plus', () => ({
  ElNotification: (options: unknown) => notify(options),
}))

function message(overrides: Partial<NotificationMessage> = {}): NotificationMessage {
  return {
    type: 'ORDER_NEW',
    messageId: `id-${Math.random()}`,
    timestamp: '2025-01-01T12:00:05+08:00',
    payload: { orderId: 4001, orderNo: '202501011200000001', payAmountCents: 10400, itemCount: 3 },
    ...overrides,
  }
}

beforeEach(() => {
  setActivePinia(createPinia())
  notify.mockClear()
})

describe('通知中心', () => {
  it('收到来单提醒:弹窗 + 计数 + 进入通知列表', () => {
    const store = useNotificationStore()
    store.handleMessage(message())

    expect(store.recent).toHaveLength(1)
    expect(store.unreadCount).toBe(1)
    expect(notify).toHaveBeenCalledTimes(1)
    expect(notify.mock.calls[0][0]).toMatchObject({
      title: '来单提醒 · 202501011200000001',
      type: 'success',
      duration: 0,
    })
  })

  it('催单用警告样式', () => {
    const store = useNotificationStore()
    store.handleMessage(
      message({
        type: 'ORDER_URGE',
        payload: { orderId: 4002, orderNo: '202501011200000002', message: '请尽快派送' },
      }),
    )

    expect(notify.mock.calls[0][0]).toMatchObject({
      title: '顾客催单 · 202501011200000002',
      message: '请尽快派送',
      type: 'warning',
    })
  })

  it('点击通知会跳到对应订单(跳转目标由外部注入)', () => {
    const store = useNotificationStore()
    const navigate = vi.fn()
    store.setNavigateHandler(navigate)
    store.handleMessage(message())

    const options = notify.mock.calls[0][0] as { onClick: () => void }
    options.onClick()

    expect(navigate).toHaveBeenCalledWith(4001)
  })

  it('只保留最近 20 条,旧的被挤掉', () => {
    const store = useNotificationStore()
    for (let index = 0; index < 25; index += 1) {
      store.handleMessage(message({ messageId: `m-${index}` }))
    }

    expect(store.recent).toHaveLength(20)
    expect(store.recent[0].messageId).toBe('m-24')
  })

  it('已读/清空/提示音开关', () => {
    const store = useNotificationStore()
    store.handleMessage(message())
    store.handleMessage(message())

    store.markAllRead()
    expect(store.unreadCount).toBe(0)
    expect(store.recent).toHaveLength(2)

    store.clearRecent()
    expect(store.recent).toHaveLength(0)

    const before = store.soundEnabled
    store.toggleSound()
    expect(store.soundEnabled).toBe(!before)
  })

  it('未连接时状态为 idle,断开连接是幂等的', () => {
    const store = useNotificationStore()
    expect(store.connected).toBe(false)
    expect(store.connectionState).toBe('idle')
    expect(() => store.disconnect()).not.toThrow()
    expect(store.connectionState).toBe('idle')
  })
})

/** 假 WebSocket:验证 store 与协议客户端接得上,以及 PONG 不会污染通知中心 */
class FakeWebSocket {
  static instances: FakeWebSocket[] = []

  readyState = 0

  sent: string[] = []

  onopen: (() => void) | null = null

  onmessage: ((event: { data: unknown }) => void) | null = null

  onclose: ((event: { code: number }) => void) | null = null

  onerror: ((event: unknown) => void) | null = null

  url: string

  constructor(url: string) {
    this.url = url
    FakeWebSocket.instances.push(this)
  }

  open(): void {
    this.readyState = 1
    this.onopen?.()
  }

  emit(payload: unknown): void {
    this.onmessage?.({ data: JSON.stringify(payload) })
  }

  send(data: string): void {
    this.sent.push(data)
  }

  close(code = 1000): void {
    this.readyState = 3
    this.onclose?.({ code })
  }
}

describe('连接生命周期', () => {
  beforeEach(() => {
    FakeWebSocket.instances = []
    setTokens({
      accessToken: 'access-1',
      refreshToken: 'refresh-1',
      expiresIn: 7200,
      refreshExpiresIn: 604800,
      tokenType: 'Bearer',
    })
    vi.stubGlobal('WebSocket', FakeWebSocket)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    clearTokens()
  })

  it('connect 会用带令牌的地址建连,并把状态同步到 store', () => {
    const store = useNotificationStore()
    store.connect()

    const socket = FakeWebSocket.instances[0]
    expect(socket.url).toContain('/ws/admin/notifications?token=access-1')
    expect(store.connectionState).toBe('connecting')

    socket.open()
    expect(store.connectionState).toBe('open')
    expect(store.connected).toBe(true)

    store.disconnect()
    expect(store.connectionState).toBe('idle')
  })

  it('PONG 不进通知中心,业务通知才进', () => {
    const store = useNotificationStore()
    store.connect()
    const socket = FakeWebSocket.instances[0]
    socket.open()

    socket.emit({ type: 'PONG', messageId: 'p1', timestamp: '', payload: {} })
    expect(store.recent).toHaveLength(0)

    socket.emit({
      type: 'ORDER_NEW',
      messageId: 'n1',
      timestamp: '2025-01-01T12:00:05+08:00',
      payload: { orderId: 4001, orderNo: 'N1', payAmountCents: 100 },
    })
    expect(store.recent).toHaveLength(1)
    expect(store.unreadCount).toBe(1)

    store.disconnect()
  })
})
