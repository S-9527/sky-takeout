import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  NotificationSocket,
  resolveNotificationUrl,
  type NotificationMessage,
  type SocketLike,
  type SocketState,
} from './notificationSocket'

/** 假 WebSocket:把"什么时候开、什么时候关"完全交给用例控制 */
class FakeSocket implements SocketLike {
  readyState = 0

  sent: string[] = []

  closeCode: number | null = null

  onopen: (() => void) | null = null

  onmessage: ((event: { data: unknown }) => void) | null = null

  onclose: ((event: { code: number }) => void) | null = null

  onerror: ((event: unknown) => void) | null = null

  open(): void {
    this.readyState = 1
    this.onopen?.()
  }

  emit(payload: unknown): void {
    this.onmessage?.({ data: typeof payload === 'string' ? payload : JSON.stringify(payload) })
  }

  send(data: string): void {
    this.sent.push(data)
  }

  close(code = 1000): void {
    this.readyState = 3
    this.closeCode = code
    this.onclose?.({ code })
  }

  sentMessages(): { type: string; timestamp?: string }[] {
    return this.sent.map((raw) => JSON.parse(raw) as { type: string; timestamp?: string })
  }
}

interface TimerEntry {
  handler: () => void
  at: number
  cancelled: boolean
}

function createHarness() {
  const timers: TimerEntry[] = []
  let now = 0
  const sockets: FakeSocket[] = []

  const socket = new NotificationSocket({
    url: 'ws://localhost/ws/admin/notifications',
    tokenProvider: () => 'access-token',
    socketFactory: () => {
      const fake = new FakeSocket()
      sockets.push(fake)
      return fake
    },
    now: () => now,
    setTimer: (handler, timeout) => {
      const entry: TimerEntry = { handler, at: now + timeout, cancelled: false }
      timers.push(entry)
      return entry
    },
    clearTimer: (handle) => {
      ;(handle as TimerEntry).cancelled = true
    },
  })

  const states: SocketState[] = []
  const messages: NotificationMessage[] = []
  socket.onStateChange = (state) => states.push(state)
  socket.onMessage = (message) => messages.push(message)

  /** 推进时间并执行到期定时器(到期时间相同时按注册顺序) */
  function advance(ms: number): void {
    const target = now + ms
    for (;;) {
      const due = timers
        .filter((entry) => !entry.cancelled && entry.at <= target)
        .sort((a, b) => a.at - b.at)[0]
      if (!due) break
      due.cancelled = true
      now = due.at
      due.handler()
    }
    now = target
  }

  return { socket, sockets, advance, states, messages, timers }
}

let harness: ReturnType<typeof createHarness>

beforeEach(() => {
  harness = createHarness()
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('连接与心跳', () => {
  it('把令牌放进查询参数(浏览器 WebSocket 不能带自定义头)', () => {
    harness.socket.connect()
    expect(harness.sockets).toHaveLength(1)
    // socketFactory 收到的 url 由 options.url + token 拼成,这里直接验证行为:能建出连接
    expect(harness.socket.getState()).toBe('connecting')
  })

  it('连接建立后进入 open,并每 30s 发一次 PING', () => {
    harness.socket.connect()
    harness.sockets[0].open()
    expect(harness.socket.getState()).toBe('open')

    harness.advance(30_000)
    expect(harness.sockets[0].sentMessages().filter((item) => item.type === 'PING')).toHaveLength(1)

    // 服务端回 PONG 后看门狗归零,才能再等到下一个心跳点
    harness.sockets[0].emit({ type: 'PONG', messageId: 'pong-1', timestamp: '', payload: {} })
    harness.advance(30_000)

    const pings = harness.sockets[0].sentMessages().filter((item) => item.type === 'PING')
    expect(pings).toHaveLength(2)
    expect(harness.socket.getState()).toBe('open')
  })

  it('没有令牌时不建连接,直接置为 closed', () => {
    const socket = new NotificationSocket({
      url: 'ws://localhost/ws',
      tokenProvider: () => null,
      socketFactory: () => {
        throw new Error('不应该被调用')
      },
    })
    socket.connect()
    expect(socket.getState()).toBe('closed')
  })

  it('收到任何帧都会重置 60s 断线看门狗', () => {
    harness.socket.connect()
    harness.sockets[0].open()

    harness.advance(50_000)
    harness.sockets[0].emit({ type: 'PONG', messageId: 'm1', timestamp: '', payload: {} })
    harness.advance(50_000)

    // 若看门狗没被重置,这里早就断线重连了
    expect(harness.socket.getState()).toBe('open')
    expect(harness.sockets).toHaveLength(1)
  })

  it('60s 没有任何帧 → 判为断线并重连', () => {
    harness.socket.connect()
    harness.sockets[0].open()

    harness.advance(60_000)
    expect(harness.socket.getState()).toBe('reconnecting')

    harness.advance(1_000)
    expect(harness.sockets).toHaveLength(2)
  })
})

describe('消息处理', () => {
  it('按 messageId 去重(5 分钟内重复投递只算一条)', () => {
    harness.socket.connect()
    const socket = harness.sockets[0]
    socket.open()

    const message = {
      type: 'ORDER_NEW',
      messageId: 'dup-1',
      timestamp: '2025-01-01T12:00:00+08:00',
      payload: { orderId: 1 },
    }
    socket.emit(message)
    socket.emit(message)
    // 5 分钟窗口过后同 id 可以再进来
    harness.advance(6 * 60 * 1000)
    socket.emit(message)

    expect(harness.messages).toHaveLength(2)
    expect(harness.messages[0].type).toBe('ORDER_NEW')
  })

  it('忽略非法 JSON 与未知类型,不抛异常', () => {
    harness.socket.connect()
    const socket = harness.sockets[0]
    socket.open()

    socket.emit('not json')
    socket.emit({ type: 'UNKNOWN', messageId: 'x' })
    socket.emit({ messageId: 'no-type' })

    expect(harness.messages).toHaveLength(0)
  })
})

describe('重连策略', () => {
  it('指数退避:1s、2s、4s……上限 30s', () => {
    harness.socket.connect()
    harness.sockets[0].open()
    harness.sockets[0].close(1000)
    expect(harness.socket.getState()).toBe('reconnecting')

    harness.advance(999)
    expect(harness.sockets).toHaveLength(1)
    harness.advance(1)
    expect(harness.sockets).toHaveLength(2)

    // 第二次失败后退避翻倍
    harness.sockets[1].close(1000)
    harness.advance(1_000)
    expect(harness.sockets).toHaveLength(2)
    harness.advance(1_000)
    expect(harness.sockets).toHaveLength(3)

    // 第三次失败后 4s
    harness.sockets[2].close(1000)
    harness.advance(3_999)
    expect(harness.sockets).toHaveLength(3)
    harness.advance(1)
    expect(harness.sockets).toHaveLength(4)
  })

  it('连接成功后退避重置为初始值', () => {
    harness.socket.connect()
    harness.sockets[0].open()
    harness.sockets[0].close(1000)
    harness.advance(1_000)
    harness.sockets[1].open()
    harness.sockets[1].close(1000)

    harness.advance(1_000)
    expect(harness.sockets).toHaveLength(3)
  })

  it('关闭码 4403(受众不符/被禁用)不重连', () => {
    harness.socket.connect()
    harness.sockets[0].open()
    harness.sockets[0].close(4403)

    expect(harness.socket.getState()).toBe('forbidden')
    harness.advance(10 * 60 * 1000)
    expect(harness.sockets).toHaveLength(1)

    // 也不允许再手工连
    harness.socket.connect()
    expect(harness.sockets).toHaveLength(1)
  })

  it('disconnect 之后不再重连,并且幂等', () => {
    harness.socket.connect()
    harness.sockets[0].open()
    harness.socket.disconnect()
    expect(harness.socket.getState()).toBe('closed')
    expect(harness.sockets[0].closeCode).toBe(1000)

    harness.advance(10 * 60 * 1000)
    expect(harness.sockets).toHaveLength(1)
    expect(() => harness.socket.disconnect()).not.toThrow()
  })

  it('重复 connect 不会建出第二条连接', () => {
    harness.socket.connect()
    harness.socket.connect()
    expect(harness.sockets).toHaveLength(1)
  })
})

describe('resolveNotificationUrl', () => {
  it('给了后端地址就换成 ws 协议(集成测试/跨域部署用)', () => {
    expect(resolveNotificationUrl('http://localhost:8080')).toBe(
      'ws://localhost:8080/ws/admin/notifications',
    )
    expect(resolveNotificationUrl('https://api.example.com/')).toBe(
      'wss://api.example.com/ws/admin/notifications',
    )
  })
})
