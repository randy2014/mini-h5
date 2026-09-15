/**
 * H5 权限层 · 路由守卫
 *
 * 约定：路由 meta.require 声明该页面的「整页级」最低要求。
 *   meta: { require: ['AUTH'] }  → 未登录直接去登录页（带 redirect），页面不再自己判
 *
 * 说明：VIP 与频道订阅属于「页内 L0/L1」，由 useCapability 在页面里表达
 * （VIP 专区有自己的三层门禁屏、频道页有自己的订阅引导条），
 * 因此阶段 1 的守卫只强制 AUTH；REQUIREMENT 保留扩展位。
 */
import { safeRedirect } from '../services/loginPreferences.js';

export const REQUIREMENT = {
  AUTH: 'AUTH'
};

const LOGIN_PATH = '/h5/login';
const DEFAULT_FALLBACK = '/h5/home';

/**
 * 纯函数：算出该次导航是否需要改写。
 * @param {{ meta?: object, fullPath: string }} to
 * @param {{ authenticated: boolean }} snapshot
 * @returns {object|null} 需要重定向时返回目标 location，否则 null
 */
export function resolveNavigation(to, snapshot, fallback = DEFAULT_FALLBACK) {
  const requires = Array.isArray(to?.meta?.require) ? to.meta.require : [];
  if (!requires.length) return null;

  if (requires.includes(REQUIREMENT.AUTH) && !snapshot?.authenticated) {
    return {
      path: LOGIN_PATH,
      query: { redirect: safeRedirect(to?.fullPath, fallback) },
      replace: true
    };
  }
  return null;
}

/**
 * 注册守卫。getSnapshot 在每次导航时求值（登录态是响应式的，不能在注册时快照）。
 */
export function installPermissionGuard(router, getSnapshot) {
  router.beforeEach((to) => resolveNavigation(to, getSnapshot()));
}
