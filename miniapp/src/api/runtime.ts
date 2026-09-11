/**
 * 跨端 API 适配层 —— 只做一件关键的事:**让 `uni.xxx` 出现在调用点**。
 *
 * 踩过的坑:不要写 `typeof uni !== 'undefined' ? uni : wx` 这种"把运行时对象取出来"的代码。
 * uni-app 的编译器是按**调用点**重写的,把 `uni` 当值传来传去时,编译产物会变成
 * `require("../common/vendor.js").index` —— 那是个模块命名空间,并没有 `.request`,
 * 于是微信开发者工具里报「当前环境没有 uni.request」,而 H5/单测里一切正常。
 *
 * 正确做法:每个调用点直接写 `uni.request(...)` / `uni.getStorageSync(...)`,
 * 编译器会把它们分别重写成目标平台的调用。测试需要替身时用下面的 override。
 */
/** `uni.request` 的最小入参形状(成功/失败回调是必给的) */
export interface UniRequestOptions {
  url: string
  method?: string
  data?: unknown
  header?: Record<string, string>
  timeout?: number
  success?: (response: { statusCode: number; data: unknown; header?: Record<string, string> }) => void
  fail?: (error: { errMsg?: string }) => void
}

let requestOverride: ((options: unknown) => void) | null = null
let storageOverride: {
  get: (key: string) => unknown
  set: (key: string, value: unknown) => void
  remove: (key: string) => void
} | null = null

/** 仅供单元测试注入替身(生产代码路径永远是直接的 uni.xxx 调用) */
export function setRuntimeForTest(override: {
  request?: (options: unknown) => void
  storage?: { get: (key: string) => unknown; set: (key: string, value: unknown) => void; remove: (key: string) => void }
} | null): void {
  requestOverride = override?.request ?? null
  storageOverride = override?.storage ?? null
}

/** 统一的请求入口:测试走替身,生产直接调 uni.request */
export function runtimeRequest(options: unknown): void {
  if (requestOverride) {
    requestOverride(options)
    return
  }
  // 关键:写 unqualified 的 uni.request,交给编译器重写
  // 类型上 uni.request 的 data 更窄(string | AnyObject | ArrayBuffer),这里由调用方保证
  uni.request(options as never)
}

export function runtimeStorageGet(key: string): unknown {
  if (storageOverride) return storageOverride.get(key)
  return uni.getStorageSync(key)
}

export function runtimeStorageSet(key: string, value: unknown): void {
  if (storageOverride) {
    storageOverride.set(key, value)
    return
  }
  uni.setStorageSync(key, value)
}

export function runtimeStorageRemove(key: string): void {
  if (storageOverride) {
    storageOverride.remove(key)
    return
  }
  uni.removeStorageSync(key)
}
