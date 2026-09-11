import { test as base, chromium, type Browser } from '@playwright/test'

import { CDP_ENDPOINT } from './global-setup'

/**
 * 测试夹具:把默认的"Playwright 启动自带浏览器"换成"连上已经在跑的 Edge"。
 *
 * `connectOverCDP` 之后 Playwright 仍然可以 `newContext()`(等价于开一个匿名窗口),
 * 所以 `page`、`context` 这些内建夹具照常可用,用例代码不需要为此改动。
 */
const useBundledBrowser = process.env.SKY_E2E_BUNDLED === '1'

export const test = base.extend({
  browser: [
    async ({}, use: (browser: Browser) => Promise<void>) => {
      const browser = useBundledBrowser
        ? await chromium.launch()
        : await chromium.connectOverCDP(CDP_ENDPOINT)
      await use(browser)
      // bundled 模式是 Playwright 自己拉起的进程,要关;
      // CDP 模式只是断开连接,Edge 是用户的浏览器,不替用户关
      await browser.close()
    },
    { scope: 'worker' as const },
  ],
})

export { expect } from '@playwright/test'
