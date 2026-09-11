<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'

import { onAuthExpired } from '@/api/http'
import { useSessionStore } from '@/stores/session'

/**
 * 应用入口。
 *
 * uni-app 的 `App.vue` **不能有模板**:它不对应任何页面,只是应用级生命周期与全局样式的挂载点。
 * 全局样式写在 `assets/tailwind.css`(Tailwind v4 + 小程序的要求,见该文件注释)。
 */
const session = useSessionStore()

onAuthExpired(() => {
  session.clearSession()
  const pages = getCurrentPages?.() ?? []
  const current = pages[pages.length - 1]
  // 已经在登录页就不用再跳,否则会把用户刚输入的内容顶掉
  if (current?.route && current.route.includes('login')) return
  void uni.reLaunch({ url: '/pages/login/index' })
})

onLaunch(() => {
  // 冷启动时令牌在本地、资料不在内存:补拉一次,失败就当作未登录
  if (session.authenticated && !session.profile) {
    void session.loadProfile().catch(() => session.clearSession())
  }
})
</script>

<style>
/* 全局样式统一放在 assets/tailwind.css;这里保持为空,避免小程序端丢失原子类 */
</style>
