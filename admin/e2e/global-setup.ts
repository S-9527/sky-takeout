import { spawn } from 'node:child_process'
import { existsSync } from 'node:fs'

import type { FullConfig } from '@playwright/test'

/**
 * E2E 前置:准备好浏览器。
 *
 * 本仓库的开发环境是 **WSL**。WSL 里没有 Chrome/Edge,而 Playwright 自带浏览器又需要
 * 一堆系统库(`libnspr4`、`libnss3`、`libasound2`,装它们要 root)—— 但 Windows 侧本来就装着 Edge,
 * 而当前 WSL 用的是镜像网络,Windows 的 `127.0.0.1` 与 WSL 是通的。
 * 所以这里改成:**启动 Windows Edge 并开调试端口,测试通过 CDP 连上去**。
 * 好处是零下载、零 root,而且测的是真实浏览器内核。
 *
 * 想要用 Playwright 自带浏览器,设 `SKY_E2E_BUNDLED=1` 并自行 `playwright install` 即可。
 */

export const CDP_ENDPOINT = process.env.SKY_EDGE_CDP ?? 'http://127.0.0.1:9222'

const EDGE_CANDIDATES = [
  process.env.SKY_EDGE_PATH,
  '/mnt/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  '/mnt/c/Program Files/Microsoft/Edge/Application/msedge.exe',
].filter((path): path is string => !!path)

const EDGE_ARGS = [
  '--remote-debugging-port=9222',
  '--user-data-dir=C:\\temp\\sky-admin-e2e',
  '--no-first-run',
  '--no-default-browser-check',
  '--disable-extensions',
  '--disable-gpu',
  '--headless=new',
  'about:blank',
]

async function isCdpReady(): Promise<boolean> {
  try {
    const response = await fetch(`${CDP_ENDPOINT}/json/version`, { signal: AbortSignal.timeout(1500) })
    return response.ok
  } catch {
    return false
  }
}

async function waitForCdp(timeoutMs: number): Promise<void> {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    if (await isCdpReady()) return
    await new Promise((resolve) => setTimeout(resolve, 500))
  }
  throw new Error(`等待浏览器调试端口超时:${CDP_ENDPOINT}/json/version`)
}

export default async function globalSetup(_config: FullConfig): Promise<void> {
  if (process.env.SKY_E2E_BUNDLED === '1') return
  if (await isCdpReady()) return

  const edge = EDGE_CANDIDATES.find((path) => existsSync(path))
  if (!edge) {
    throw new Error(
      `没找到 Windows Edge(${EDGE_CANDIDATES.join(' / ')})。\n` +
        '两个选择:① 设 SKY_EDGE_PATH 指向本机 Edge/Chrome;' +
        '② 用 Playwright 自带浏览器:pnpm exec playwright install chromium 后设 SKY_E2E_BUNDLED=1。',
    )
  }

  // detached:测试结束后 Edge 继续留着,下次直接复用;它是用户的浏览器进程,不该被测试随手杀掉
  spawn(edge, EDGE_ARGS, { detached: true, stdio: 'ignore' }).unref()
  await waitForCdp(20_000)
}
