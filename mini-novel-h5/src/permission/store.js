/**
 * H5 权限层 · 状态仓库
 *
 * 职责：把「这个用户当前能做什么」聚合到一个可复用的快照里，供 can() / prompt 使用。
 *  - 身份与 VIP 原始字段来自 useUserStore（登录态、profile.vipExpireTime / vipActivatedAt）
 *  - 订阅集合与币余额由本 store 拉取
 * 页面不得再各自拼条件或直接读 localStorage。
 */
import { defineStore } from 'pinia';
import { useUserStore } from '../stores/user.js';
import { fetchBalance, fetchMySubscribes } from '../services/subscribe.js';
import { ADULT_CONFIRMED_KEY, FALLBACK_PRICES } from './constants.js';
import { buildSnapshot } from './rules.js';

/** 快照缓存时长：避免每个页面都打一遍 3 个接口 */
const LOAD_TTL_MS = 60 * 1000;

let inflight = null;

export const usePermissionStore = defineStore('permission', {
  state: () => ({
    subscribes: [],
    coinBalance: 0,
    prices: { ...FALLBACK_PRICES },
    adultConfirmed: readAdultConfirmed(),
    loaded: false,
    loading: false,
    loadedAt: 0,
    lastError: null
  }),

  getters: {
    /** 判定快照：所有 can() 的唯一输入 */
    snapshot(state) {
      const userStore = useUserStore();
      return buildSnapshot({
        authenticated: userStore.isAuthenticated,
        profile: userStore.profile,
        subscribes: state.subscribes,
        coinBalance: state.coinBalance,
        prices: state.prices,
        adultConfirmed: state.adultConfirmed
      });
    }
  },

  actions: {
    /**
     * 拉取（或复用）权限快照。
     * @param {{ force?: boolean }} [options]
     */
    async load({ force = false } = {}) {
      const userStore = useUserStore();

      if (!userStore.isAuthenticated) {
        this.reset();
        return this.snapshot;
      }
      if (inflight) {
        return inflight;
      }
      if (!force && this.loaded && Date.now() - this.loadedAt < LOAD_TTL_MS) {
        return this.snapshot;
      }

      this.loading = true;
      inflight = (async () => {
        const [profile, subscribes, balance] = await Promise.allSettled([
          userStore.loadProfile(),
          fetchMySubscribes(),
          fetchBalance()
        ]);
        if (subscribes.status === 'fulfilled' && Array.isArray(subscribes.value)) {
          this.subscribes = subscribes.value;
        }
        if (balance.status === 'fulfilled' && balance.value) {
          this.coinBalance = Number(balance.value.balance) || 0;
        }
        this.lastError = profile.status === 'rejected' ? profile.reason : null;
        this.loaded = true;
        this.loadedAt = Date.now();
        return this.snapshot;
      })();

      try {
        return await inflight;
      } finally {
        inflight = null;
        this.loading = false;
      }
    },

    /** 只刷身份与 VIP 字段（比 load 轻，用于 VIP 变更后） */
    async refreshProfile() {
      const userStore = useUserStore();
      if (!userStore.isAuthenticated) {
        this.reset();
        return this.snapshot;
      }
      await userStore.loadProfile();
      this.loadedAt = Date.now();
      return this.snapshot;
    },

    /** 订阅成功后本地登记，避免整页重拉 */
    applySubscribe({ channelId, endTime } = {}) {
      const id = Number(channelId);
      if (!Number.isFinite(id) || id <= 0) return;
      const rest = this.subscribes.filter((item) => Number(item?.channelId) !== id);
      this.subscribes = [
        { channelId: id, status: 'ACTIVE', endTime: endTime || null },
        ...rest
      ];
      this.loaded = true;
      this.loadedAt = Date.now();
    },

    setCoinBalance(value) {
      this.coinBalance = Number(value) || 0;
    },

    setPrices(prices) {
      this.prices = { ...FALLBACK_PRICES, ...(prices || {}) };
    },

    confirmAdult() {
      localStorage.setItem(ADULT_CONFIRMED_KEY, 'yes');
      this.adultConfirmed = true;
    },

    /** 退出登录 / 会话失效时调用（成人确认是本机意愿，不重置） */
    reset() {
      this.subscribes = [];
      this.coinBalance = 0;
      this.loaded = false;
      this.loading = false;
      this.loadedAt = 0;
      this.lastError = null;
    }
  }
});

function readAdultConfirmed() {
  try {
    return localStorage.getItem(ADULT_CONFIRMED_KEY) === 'yes';
  } catch {
    return false;
  }
}
