/**
 * H5 权限层 · 组合式 API（页面只问能力，不问状态）
 *
 *   const { can, prompt } = useCapability();
 *   can(CAPABILITY.VIP_READ)
 *   can(CAPABILITY.CHANNEL_MEDIA, { channelId: 3 })
 *   prompt(CAPABILITY.TRADE, { periodType: 'MONTH' })
 */
import { computed } from 'vue';
import { can as evaluate, subscriptionHint } from './rules.js';
import { createPromptOnce, promptFor } from './prompts.js';
import { usePermissionStore } from './store.js';

export function useCapability() {
  const permissionStore = usePermissionStore();
  const snapshot = computed(() => permissionStore.snapshot);
  const once = createPromptOnce();

  /** @returns {{ allowed: boolean, gate: string|null }} */
  function check(capability, ctx = {}) {
    return evaluate(snapshot.value, capability, ctx);
  }

  function can(capability, ctx = {}) {
    return check(capability, ctx).allowed;
  }

  /** 不可用时返回该门禁的提示描述（文案 + 层级 + 落点），可用时返回 null */
  function prompt(capability, ctx = {}) {
    const result = check(capability, ctx);
    return result.allowed ? null : promptFor(result.gate, ctx);
  }

  /**
   * 订阅频道页的门禁原因：
   *  S1 → NEED_LOGIN · S2 → NEED_VIP / VIP_EXPIRED · S3 试用中 → null
   */
  function subscribeGate() {
    return subscriptionHint(snapshot.value);
  }

  return { snapshot, can, check, prompt, subscribeGate, once, permissionStore, promptFor };
}
