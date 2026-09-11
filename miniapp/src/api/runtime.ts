/**
 * 跨端 API 适配层 —— 只做一件关键的事:**让 `uni.xxx` 出现在调用点**。
 *
 * uni-app 是按**调用点**重写 `uni.xxx` 的,所以不要"先把 uni 取出来再调":
 * - 小程序端取出来的可能只是模块命名空间,不是完整运行时;
 * - H5 发行(摇树)模式下 `pages.json` 只注入了空桩 `window.uni = {}`,真正的 API 得靠
 *   字面量 `uni.xxx` 触发注入插件从 `@dcloudio/uni-h5` 导入 —— 动态 `runtime.request`
 *   读到的永远是空桩,于是报「当前环境没有 uni.request」。
 *
 * 正确做法:每个调用点直接写裸 `uni.request(...)` / `uni.getStorageSync(...)`,
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

export interface RuntimeStorage {
  get: (key: string) => unknown
  set: (key: string, value: unknown) => void
  remove: (key: string) => void
}

/** 小程序端 `uni.login` 成功时的载荷 */
export interface WechatLoginResult {
  code?: string
}

export type WechatLogin = (options: {
  provider?: string
  success?: (result: WechatLoginResult) => void
  fail?: (error?: unknown) => void
}) => void

export interface RuntimeOverride {
  request?: (options: unknown) => void
  storage?: RuntimeStorage
  /** 伪造平台:H5 是 `web`,微信小程序是 `mp-weixin`;`null` 表示"探测不到" */
  platform?: string | null
  /** 伪造 `uni.login`,避免用例去动全局 */
  wechatLogin?: WechatLogin | null
}

let requestOverride: ((options: unknown) => void) | null = null
let storageOverride: RuntimeStorage | null = null
let platformOverride: string | null | undefined
let wechatLoginOverride: WechatLogin | null = null

/** 仅供单元测试注入替身(生产代码路径永远是直接的 uni.xxx 调用) */
export function setRuntimeForTest(override: RuntimeOverride | null): void {
  requestOverride = override?.request ?? null
  storageOverride = override?.storage ?? null
  // undefined 表示"别伪造,自己去探测";null 表示"就当作探测不到"
  platformOverride = override ? (override.platform === undefined ? undefined : (override.platform ?? null)) : undefined
  wechatLoginOverride = override?.wechatLogin ?? null
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

/**
 * 目标平台,由 `vite.config.ts` 的 `define` 在编译期注入(H5 是 `h5`,
 * 微信小程序是 `mp-weixin`)。
 *
 * 之所以用编译期常量而不是 `uni.getSystemInfoSync()`:后者在微信端会触发
 * 「wx.getSystemInfoSync is deprecated」告警(官方要求改用 getAppBaseInfo /
 * getDeviceInfo / getWindowInfo / getSystemSetting 等)。
 */
declare const __UNI_PLATFORM__: string

/**
 * 当前运行平台。拿不到时返回 `undefined`,调用方按"非小程序"处理。
 */
export function runtimePlatform(): string | undefined {
  if (platformOverride !== undefined) return platformOverride ?? undefined
  if (typeof __UNI_PLATFORM__ === 'string' && __UNI_PLATFORM__) return __UNI_PLATFORM__

  // 兜底:构建期常量缺失(例如换了一套构建配置)时用新 API 探测,不要用已废弃的 getSystemInfoSync
  try {
    const info = uni.getAppBaseInfo?.() as { uniPlatform?: string } | undefined
    return info?.uniPlatform
  } catch {
    return undefined
  }
}

/** H5 / 单测用的固定 code:后端 mock 会把它换成同一个 openid */
export const DEV_LOGIN_CODE = 'dev-h5-customer'

/**
 * 取微信登录 code。
 *
 * - 微信小程序:调 `uni.login()` 拿一次性 code;
 * - 其它平台(H5 / 单测):返回固定开发 code,让整条链路能跑通。
 *
 * **不能凭"有没有 `wx.login`"判断平台**:H5 开发模式下 `window.wx` 就是 `uni` 本身,
 * 而 `uni.login` 是"当前平台不支持"的桩,一调必失败 —— 这正是 H5 点"微信一键登录"
 * 直接提示"登录失败"的原因。这里用编译期平台常量判断,只在小程序里才真正发起登录。
 */
export function runtimeWechatLoginCode(): Promise<string> {
  const login: WechatLogin | null =
    runtimePlatform() === 'mp-weixin' ? (wechatLoginOverride ?? (uni.login as unknown as WechatLogin)) : null

  if (!login) return Promise.resolve(DEV_LOGIN_CODE)

  return new Promise<string>((resolve, reject) => {
    login({
      provider: 'weixin',
      success: (result) => {
        if (result.code) resolve(result.code)
        else reject(new Error('微信未返回 code'))
      },
      fail: () => reject(new Error('微信登录取消或失败')),
    })
  })
}
