/**
 * 管理端通知通道的 WebSocket 客户端(协议见 `docs/03-api.md` 第 4 节)。
 *
 * 契约里写死的几条客户端义务,这个文件逐条落实:
 * - 心跳:每 30s 发 `PING`;60s 没收到任何帧就认为断线并主动重连;
 * - 重连:指数退避 1s、2s、4s……上限 30s;关闭码 `4403`(受众不符/被禁用)**不重连**;
 * - 去重:按 `messageId` 去重,窗口 5 分钟;
 * - 通知不是状态来源:这里只把消息抛给上层,不做任何业务判断。
 *
 * 为了能单测,`WebSocket` 构造、定时器、时钟全部可注入 ——
 * 否则只能靠 `vi.useFakeTimers()` 加全局 mock,测起来又慢又脆。
 */

export const NOTIFICATION_TYPES = ['ORDER_NEW', 'ORDER_URGE', 'PONG'] as const
export type NotificationType = (typeof NOTIFICATION_TYPES)[number]

export interface OrderNewPayload {
  orderId: number
  orderNo: string
  payAmountCents: number
  consignee?: string | null
  phone?: string | null
  detail?: string | null
  itemCount?: number
  remark?: string | null
  placedAt?: string | null
  paidAt?: string | null
}

export interface OrderUrgePayload {
  orderId: number
  orderNo: string
  status?: string
  message?: string | null
  urgedAt?: string | null
}

export interface NotificationMessage {
  type: NotificationType
  messageId: string
  timestamp: string
  payload: Record<string, unknown>
}

/** `lib.dom` 的 `WebSocket` 子集,便于测试注入假实现 */
export interface SocketLike {
  send(data: string): void
  close(code?: number, reason?: string): void
  readyState: number
}

export type SocketState = 'idle' | 'connecting' | 'open' | 'reconnecting' | 'forbidden' | 'closed'

export interface NotificationSocketOptions {
  /** 连接地址(不含 token);token 由 `tokenProvider` 每次(重)连时现取 */
  url: string
  tokenProvider: () => string | null
  socketFactory?: (url: string) => SocketLike
  pingIntervalMs?: number
  pongTimeoutMs?: number
  initialBackoffMs?: number
  maxBackoffMs?: number
  dedupeWindowMs?: number
  now?: () => number
  setTimer?: (handler: () => void, timeout: number) => unknown
  clearTimer?: (handle: unknown) => void
}

export const WS_FORBIDDEN_CODE = 4403

export class NotificationSocket {
  private socket: SocketLike | null = null

  private state: SocketState = 'idle'

  private backoffMs: number

  private pingTimer: unknown = null

  private pongTimer: unknown = null

  private reconnectTimer: unknown = null

  private readonly seen = new Map<string, number>()

  /** `disconnect()` 之后置位:此时再收到 close 事件不能再触发重连 */
  private stopped = false

  private readonly options: Required<Omit<NotificationSocketOptions, 'socketFactory'>> & {
    socketFactory: (url: string) => SocketLike
  }

  onMessage: (message: NotificationMessage) => void = () => {}

  onStateChange: (state: SocketState) => void = () => {}

  constructor(options: NotificationSocketOptions) {
    this.options = {
      pingIntervalMs: 30_000,
      pongTimeoutMs: 60_000,
      initialBackoffMs: 1_000,
      maxBackoffMs: 30_000,
      dedupeWindowMs: 5 * 60 * 1000,
      now: () => Date.now(),
      setTimer: (handler, timeout) => setTimeout(handler, timeout),
      clearTimer: (handle) => clearTimeout(handle as ReturnType<typeof setTimeout>),
      socketFactory: (url: string) => new WebSocket(url) as unknown as SocketLike,
      ...options,
    }
    this.backoffMs = this.options.initialBackoffMs
  }

  getState(): SocketState {
    return this.state
  }

  connect(): void {
    if (this.state === 'forbidden') return
    if (this.socket && (this.socket.readyState === 0 || this.socket.readyState === 1)) return
    this.stopped = false
    const token = this.options.tokenProvider()
    if (!token) {
      this.setState('closed')
      return
    }
    this.setState(this.reconnectTimer ? 'reconnecting' : 'connecting')
    const separator = this.options.url.includes('?') ? '&' : '?'
    const url = `${this.options.url}${separator}token=${encodeURIComponent(token)}`
    const socket = this.options.socketFactory(url)
    this.socket = socket
    // 真 WebSocket 支持 onopen/onmessage/... ;测试注入的假实现也可以用同样的属性
    const raw = socket as unknown as {
      onopen?: () => void
      onmessage?: (event: { data: unknown }) => void
      onclose?: (event: { code: number }) => void
      onerror?: (event: unknown) => void
    }
    raw.onopen = () => this.handleOpen()
    raw.onmessage = (event) => this.handleRawMessage(event.data)
    raw.onclose = (event) => this.handleClose(event?.code ?? 1000)
    raw.onerror = () => {
      // 具体原因由随后的 close 事件给出,这里不重复处理
    }
  }

  /** 主动断开(登出、路由离开),不会再自动重连 */
  disconnect(): void {
    this.stopped = true
    this.clearTimers()
    const socket = this.socket
    this.socket = null
    this.setState('closed')
    socket?.close(1000, 'client closed')
  }

  private setState(state: SocketState): void {
    if (this.state === state) return
    this.state = state
    this.onStateChange(state)
  }

  private handleOpen(): void {
    this.backoffMs = this.options.initialBackoffMs
    this.setState('open')
    this.armPing()
    this.armPongWatchdog()
  }

  private armPing(): void {
    this.clearTimer('pingTimer')
    this.pingTimer = this.options.setTimer(() => {
      this.send({ type: 'PING', timestamp: new Date(this.options.now()).toISOString() })
      this.armPing()
    }, this.options.pingIntervalMs)
  }

  /** 60s 内没有收到**任何**帧(含 PONG)就判为断线 */
  private armPongWatchdog(): void {
    this.clearTimer('pongTimer')
    this.pongTimer = this.options.setTimer(() => {
      this.closeSocket(1000, 'pong timeout')
    }, this.options.pongTimeoutMs)
  }

  /**
   * 关闭当前连接。
   *
   * 只在这里调 `close()`,不顺手再调 `handleClose()`:真实 WebSocket 关闭后会发 `onclose`,
   * 两边都处理就会重连两次;若连接已经不在(readyState=3),`onclose` 不会再来,这时手工兜底。
   */
  private closeSocket(code: number, reason: string): void {
    const socket = this.socket
    if (socket && socket.readyState !== 3) {
      socket.close(code, reason)
      return
    }
    this.handleClose(code)
  }

  private send(payload: unknown): void {
    if (this.socket?.readyState !== 1) return
    this.socket.send(JSON.stringify(payload))
  }

  private handleRawMessage(data: unknown): void {
    this.armPongWatchdog()
    if (typeof data !== 'string') return
    let parsed: unknown
    try {
      parsed = JSON.parse(data)
    } catch {
      return
    }
    const message = parsed as Partial<NotificationMessage>
    if (!message || typeof message.type !== 'string' || typeof message.messageId !== 'string') return
    if (!(NOTIFICATION_TYPES as readonly string[]).includes(message.type)) return
    if (this.isDuplicate(message.messageId)) return
    const envelope: NotificationMessage = {
      type: message.type as NotificationType,
      messageId: message.messageId,
      timestamp: typeof message.timestamp === 'string' ? message.timestamp : '',
      payload: (message.payload as Record<string, unknown>) ?? {},
    }
    this.onMessage(envelope)
  }

  private isDuplicate(messageId: string): boolean {
    const now = this.options.now()
    for (const [id, seenAt] of this.seen) {
      if (now - seenAt > this.options.dedupeWindowMs) this.seen.delete(id)
    }
    if (this.seen.has(messageId)) return true
    this.seen.set(messageId, now)
    return false
  }

  private handleClose(code: number): void {
    this.clearTimers()
    this.socket = null
    if (this.stopped) {
      this.setState('closed')
      return
    }
    if (code === WS_FORBIDDEN_CODE) {
      // 受众不符或被禁用:重连多少次都不会成功,契约要求"不重连"
      this.setState('forbidden')
      return
    }
    this.setState('reconnecting')
    this.reconnectTimer = this.options.setTimer(() => {
      this.reconnectTimer = null
      this.connect()
    }, this.backoffMs)
    this.backoffMs = Math.min(this.backoffMs * 2, this.options.maxBackoffMs)
  }

  private clearTimer(name: 'pingTimer' | 'pongTimer' | 'reconnectTimer'): void {
    const handle = this[name]
    if (handle !== null) {
      this.options.clearTimer(handle)
      this[name] = null
    }
  }

  private clearTimers(): void {
    this.clearTimer('pingTimer')
    this.clearTimer('pongTimer')
    this.clearTimer('reconnectTimer')
  }
}

/** 由 API 根地址推导 WebSocket 地址(同源,开发期走 Vite 的 `/ws` 代理) */
export function resolveNotificationUrl(baseUrl = ''): string {
  if (baseUrl) {
    const httpUrl = baseUrl.replace(/\/$/, '')
    return `${httpUrl.replace(/^http/, 'ws')}/ws/admin/notifications`
  }
  if (typeof location === 'undefined') return '/ws/admin/notifications'
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${location.host}/ws/admin/notifications`
}
