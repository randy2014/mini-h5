/**
 * H5 权限层 · 入口
 *
 * 结构：
 *   constants.js      状态 / 能力域 / 门禁原因 / 提示层级
 *   rules.js          纯判定（buildSnapshot · can · activeSubscription）—— 有单测
 *   errors.js         后端 code → 门禁原因归一化 —— 有单测
 *   prompts.js        门禁原因 → 层级 + 文案 + 落点（唯一文案出口）
 *   store.js          usePermissionStore：唯一状态来源
 *   useCapability.js  页面侧组合式 API
 *   guard.js          路由 meta.require 整页级拦截
 *   session.js        401 时同步清 store 并跳登录
 */
import { useUserStore } from '../stores/user.js';
import { TOKEN_KEY } from '../services/authStorage.js';
import { installPermissionGuard } from './guard.js';
import { installSessionGuard } from './session.js';

export {
  ADULT_CONFIRMED_KEY,
  CAPABILITY,
  FALLBACK_PRICES,
  GATE,
  PERIOD_DAYS,
  PROMPT_LEVEL,
  TRIAL_DAYS,
  VIP_STATE
} from './constants.js';

export { activeSubscription, buildSnapshot, can, daysLeft, priceFor, subscriptionHint, toMillis } from './rules.js';

export { API_CODE, ApiError, classifyApiError, isApiError, isGateError } from './errors.js';

export { createPromptOnce, promptFor } from './prompts.js';

export { usePermissionStore } from './store.js';

export { useCapability } from './useCapability.js';

export { REQUIREMENT, installPermissionGuard, resolveNavigation } from './guard.js';

export { installSessionGuard } from './session.js';

/**
 * 在 main.js 里安装权限层（顺序：createPinia → router → installPermission）。
 */
export function installPermission(router) {
  installSessionGuard(router);
  installPermissionGuard(router, () => {
    try {
      const userStore = useUserStore();
      return { authenticated: userStore.isAuthenticated };
    } catch {
      // pinia 尚未就绪时的兜底：不要因为守卫异常打断全部导航
      return { authenticated: Boolean(localStorage.getItem(TOKEN_KEY)) };
    }
  });
}
