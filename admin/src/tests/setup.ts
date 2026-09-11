import { afterEach } from 'vitest'

/**
 * jsdom 缺的浏览器 API 补齐。
 *
 * Element Plus 的表格/下拉/通知会用到 `ResizeObserver` 与 `matchMedia`,
 * jsdom 不实现它们,不补的话组件一挂载就抛 `not implemented`。
 */
if (typeof window !== 'undefined') {
  if (!window.matchMedia) {
    window.matchMedia = ((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    })) as unknown as typeof window.matchMedia
  }

  class ResizeObserverStub {
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
  }

  const globalWithResizeObserver = globalThis as { ResizeObserver?: unknown }
  globalWithResizeObserver.ResizeObserver ??= ResizeObserverStub
  ;(window as unknown as { ResizeObserver?: unknown }).ResizeObserver ??= ResizeObserverStub
}

// 每个用例都从"未登录"开始,避免令牌在用例之间串味
afterEach(() => {
  // 有的用例会临时把 localStorage 换成 undefined,验证"非浏览器环境"的降级路径
  globalThis.localStorage?.clear()
})
