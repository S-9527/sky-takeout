import type { RouteLocationNormalized, RouteLocationRaw, Router } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import type { Employee } from '@/types'

export interface GuardAuthState {
  authenticated: boolean
  profile: Employee | null
  loadProfile: () => Promise<Employee>
  clearSession: () => void
}

/**
 * 导航决策(纯函数,便于单测;真正的 router 只是它的调用方)。
 *
 * 三条规则:
 * 1. 免登录页面直接放行;已登录的人再点登录页就送回工作台;
 * 2. 未登录 → 去登录页,并带上 `redirect` 便于登录后回到原页面;
 * 3. 已登录但没资料(刷新页面后内存里是空的)→ 先拉资料;
 *    拉资料失败(令牌失效且刷新失败)就清会话回登录页。
 *
 * 角色判定必须用**刚拉回来的**那份资料,而不是进函数时的那份快照:
 * 直接刷新 `/employees` 时资料是空的,如果还拿快照判角色,管理员会被自己的守卫挡在门外
 * (这个 bug 就是 E2E 抓出来的)。
 */
export async function resolveGuard(
  to: RouteLocationNormalized,
  auth: GuardAuthState,
): Promise<true | RouteLocationRaw> {
  if (to.meta.public) {
    if (auth.authenticated && to.name === 'login') return { name: 'workbench' }
    return true
  }

  if (!auth.authenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  let profile = auth.profile
  if (!profile) {
    try {
      profile = await auth.loadProfile()
    } catch {
      auth.clearSession()
      return { name: 'login', query: { redirect: to.fullPath } }
    }
  }

  const roles = to.meta.roles
  if (roles && roles.length > 0) {
    const role = profile?.role
    if (!role || !roles.includes(role)) {
      return { name: 'forbidden' }
    }
  }

  return true
}

/** 把守卫接到真实 router 上 */
export function installGuards(router: Router): void {
  router.beforeEach(async (to) => {
    const auth = useAuthStore()
    return resolveGuard(to, {
      authenticated: auth.authenticated,
      profile: auth.profile,
      loadProfile: () => auth.loadProfile(),
      clearSession: () => auth.clearSession(),
    })
  })

  router.afterEach((to) => {
    const title = to.meta.title
    document.title = title ? `${title} · 苍穹外卖管理端` : '苍穹外卖管理端'
  })
}
