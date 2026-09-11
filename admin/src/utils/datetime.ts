/**
 * 时间工具。
 *
 * 关键约定:**一律按门店时区 `Asia/Shanghai` 展示**,而不是浏览器本地时区。
 * 后端的"今日营业额""最近 7 天"都是按门店自然日算的(领域文档 D8);
 * 如果管理员在 UTC 的机器上打开页面,按本地时区渲染会让人以为数据错了。
 * 因此这里用 `Intl.DateTimeFormat` 固定时区,而不是 `new Date().getHours()`。
 */

export const STORE_TIME_ZONE = 'Asia/Shanghai'

const dateTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: STORE_TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
})

const dateFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: STORE_TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

const timeFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: STORE_TIME_ZONE,
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

function partsToRecord(parts: Intl.DateTimeFormatPart[]): Record<string, string> {
  const record: Record<string, string> = {}
  for (const part of parts) record[part.type] = part.value
  return record
}

function parse(value: string | number | Date | null | undefined): Date | null {
  if (value === null || value === undefined || value === '') return null
  const date = value instanceof Date ? value : new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

/** `2025-01-01 12:00:00`(门店时区);无效/空值返回 `-` */
export function formatDateTime(value: string | number | Date | null | undefined): string {
  const date = parse(value)
  if (!date) return '-'
  const p = partsToRecord(dateTimeFormatter.formatToParts(date))
  // zh-CN 在部分 Node 版本里会把小时格式成 `24` 表示午夜,统一收敛为 `00`
  const hour = p.hour === '24' ? '00' : p.hour
  return `${p.year}-${p.month}-${p.day} ${hour}:${p.minute}:${p.second}`
}

/** `2025-01-01`(门店时区);无效/空值返回 `-` */
export function formatDate(value: string | number | Date | null | undefined): string {
  const date = parse(value)
  if (!date) return '-'
  const p = partsToRecord(dateFormatter.formatToParts(date))
  return `${p.year}-${p.month}-${p.day}`
}

/** `12:00`(门店时区);无效/空值返回 `-` */
export function formatTime(value: string | number | Date | null | undefined): string {
  const date = parse(value)
  if (!date) return '-'
  const p = partsToRecord(timeFormatter.formatToParts(date))
  return `${p.hour === '24' ? '00' : p.hour}:${p.minute}`
}

/** 空值返回空串(表格里比 `-` 更适合"从未登录"这类字段) */
export function formatDateTimeOrEmpty(value: string | number | Date | null | undefined): string {
  const text = formatDateTime(value)
  return text === '-' ? '' : text
}

/** `Date` → `YYYY-MM-DD`(按门店时区拆年月日,不做 UTC 偏移) */
export function toDateParam(value: Date): string {
  const p = partsToRecord(dateFormatter.formatToParts(value))
  return `${p.year}-${p.month}-${p.day}`
}

/** 今天(门店时区)的 `YYYY-MM-DD` */
export function today(now: Date = new Date()): string {
  return toDateParam(now)
}

/**
 * 报表默认区间:含首含尾的最近 `days` 天。
 * `days=7` 且今天是 01-07 → `2025-01-01 ~ 2025-01-07`(与后端"默认最近 7 天"一致)。
 */
export function recentDaysRange(days: number, now: Date = new Date()): [string, string] {
  const span = Math.max(1, Math.floor(days))
  const end = new Date(now.getTime())
  const begin = new Date(now.getTime() - (span - 1) * 24 * 60 * 60 * 1000)
  return [toDateParam(begin), toDateParam(end)]
}

/**
 * 订单倒计时:剩余毫秒(15 分钟支付窗口,领域文档 R2)。
 * 返回 0 表示已超时。`placedAt` 为空时返回 `null`(无从判断)。
 */
export function remainingPayMillis(
  placedAt: string | null | undefined,
  timeoutMinutes = 15,
  now: Date = new Date(),
): number | null {
  const placed = parse(placedAt)
  if (!placed) return null
  const deadline = placed.getTime() + timeoutMinutes * 60 * 1000
  return Math.max(0, deadline - now.getTime())
}

/** 毫秒 → `mm:ss`(工作台/详情页的支付倒计时展示) */
export function formatCountdown(millis: number): string {
  const total = Math.max(0, Math.floor(millis / 1000))
  const minutes = String(Math.floor(total / 60)).padStart(2, '0')
  const seconds = String(total % 60).padStart(2, '0')
  return `${minutes}:${seconds}`
}
