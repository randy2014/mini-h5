/**
 * H5 权限层 · 会话失效处理
 *
 * 后端返回 401 时（token 过期 / 被顶号 / 账号被删），http.js 会清掉 localStorage 的凭证，
 * 但 Pinia 里的 store 不会自动同步 —— 只清 localStorage 会留下「store 认为已登录、页面拿不到数据」
 * 的错乱态。这里把 store 清理与跳登录接上。
 *
 * 只在「原本处于登录态」时才跳登录：匿名用户在公开页误触登录接口（不该发生，但防御）不应被弹走。
 */
import { setUnauthorizedHandler } from '../services/http.js';
import { safeRedirect } from '../services/loginPreferences.js';
import { useUserStore } from '../stores/user.js';
import { usePermissionStore } from './store.js';

export function installSessionGuard(router) {
  setUnauthorizedHandler(() => {
    const userStore = useUserStore();
    const permissionStore = usePermissionStore();
    const wasAuthenticated = userStore.isAuthenticated;

    userStore.clearSession();
    permissionStore.reset();

    if (!wasAuthenticated) return;

    const current = router.currentRoute.value;
    if (current.name === 'login') return;
    router.replace({
      path: '/h5/login',
      query: { redirect: safeRedirect(current.fullPath, '/h5/home') }
    });
  });
}
