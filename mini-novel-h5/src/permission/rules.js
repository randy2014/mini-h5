/**
 * H5 权限层 · 判定规则（纯函数，无框架依赖，可被 node:test 直接覆盖）
 *
 * 判定式与后端对齐（见 docs/h5-permission-matrix.md 附表）：
 *   VIP 有效     = vip_expire_time > now
 *   试用中       = vip_activated_at + 7 天 > now        （与后端一致：不要求 VIP 仍有效）
 *   频道可访问   = 试用中 ∨ 该频道订阅有效
 *   交易         = 已登录 ∧ 币余额足够（是否还要 VIP 见 §D1，尚未定论，故此处不强加）
 */
import {
  CAPABILITY,
  FALLBACK_PRICES,
  GATE,
  TRIAL_DAYS,
  VIP_STATE
} from './constants.js';

const DAY_MS = 24 * 60 * 60 * 1000;

/** 把后端 LocalDateTime / 时间戳 / Date 归一为毫秒；无效返回 null */
export function toMillis(value) {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  if (value instanceof Date) {
    const ms = value.getTime();
    return Number.isNaN(ms) ? null : ms;
  }
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null;
  }
  const ms = new Date(value).getTime();
  return Number.isNaN(ms) ? null : ms;
}

/**
 * 组装判定快照。所有 can() 判定只读这个对象，页面不得各自拼条件。
 */
export function buildSnapshot({
  authenticated = false,
  profile = null,
  subscribes = [],
  coinBalance = 0,
  prices = null,
  adultConfirmed = false,
  now = Date.now()
} = {}) {
  const vipExpireMs = toMillis(profile?.vipExpireTime);
  const vipActivatedMs = toMillis(profile?.vipActivatedAt);
  const vipActive = Boolean(authenticated && vipExpireMs !== null && vipExpireMs > now);
  const trialActive = Boolean(
    authenticated && vipActivatedMs !== null && now < vipActivatedMs + TRIAL_DAYS * DAY_MS
  );

  return {
    authenticated: Boolean(authenticated),
    profile: profile || null,
    vipExpireTime: profile?.vipExpireTime ?? null,
    vipActivatedAt: profile?.vipActivatedAt ?? null,
    vipExpireMs,
    vipActivatedMs,
    vipActive,
    trialActive,
    vipState: !vipActive ? (vipExpireMs !== null ? VIP_STATE.EXPIRED : VIP_STATE.NONE) : VIP_STATE.ACTIVE,
    subscribes: Array.isArray(subscribes) ? subscribes : [],
    coinBalance: Number(coinBalance) || 0,
    prices: { ...FALLBACK_PRICES, ...(prices || {}) },
    adultConfirmed: Boolean(adultConfirmed),
    now
  };
}

/**
 * 能力判定。
 * @returns {{ allowed: boolean, gate: string|null }}
 */
export function can(snapshot, capability, ctx = {}) {
  if (!snapshot) {
    return deny(GATE.NEED_LOGIN);
  }
  switch (capability) {
    case CAPABILITY.PUBLIC_READ:
      return allow();

    case CAPABILITY.VIP_READ:
      if (!snapshot.authenticated) return deny(GATE.NEED_LOGIN);
      if (snapshot.vipActive) return allow();
      return deny(snapshot.vipState === VIP_STATE.EXPIRED ? GATE.VIP_EXPIRED : GATE.NEED_VIP);

    case CAPABILITY.CHANNEL_MEDIA:
      if (!snapshot.authenticated) return deny(GATE.NEED_LOGIN);
      if (snapshot.trialActive) return allow();
      return activeSubscription(snapshot, ctx.channelId) ? allow() : deny(GATE.NEED_CHANNEL_SUBSCRIBE);

    case CAPABILITY.TRADE: {
      if (!snapshot.authenticated) return deny(GATE.NEED_LOGIN);
      const price = priceFor(snapshot.prices, ctx.periodType);
      if (ctx.assertBalance !== false && snapshot.coinBalance < price) return deny(GATE.NEED_COIN);
      return allow();
    }

    case CAPABILITY.ACCOUNT:
      if (!snapshot.authenticated) return deny(GATE.NEED_LOGIN);
      return allow();

    default:
      // 未知能力一律不放行，避免新增能力时静默提权
      return deny(GATE.FORBIDDEN);
  }
}

/** 该频道是否存在有效订阅（不含试用） */
export function activeSubscription(snapshot, channelId) {
  const target = Number(channelId);
  if (!snapshot || !Number.isFinite(target) || target <= 0) {
    return null;
  }
  return (
    snapshot.subscribes.find((item) => {
      if (Number(item?.channelId) !== target) return false;
      const status = String(item?.status || 'ACTIVE').toUpperCase();
      if (status === 'CANCELLED' || status === 'EXPIRED') return false;
      const endMs = toMillis(item?.endTime);
      return endMs !== null && endMs > snapshot.now;
    }) || null
  );
}

/** 周期价格（未知周期回退 MONTH，与后端 priceFor 一致） */
export function priceFor(prices, periodType) {
  const table = prices || FALLBACK_PRICES;
  const key = String(periodType || 'MONTH').toUpperCase();
  return Number(table[key] ?? table.MONTH ?? FALLBACK_PRICES.MONTH);
}

/** 订阅剩余天数（后端 daysLeft 的本地兜底计算） */
export function daysLeft(endTime, now = Date.now()) {
  const endMs = toMillis(endTime);
  if (endMs === null) return 0;
  return Math.max(0, Math.ceil((endMs - now) / DAY_MS));
}

/** 该状态下是否需要为「订阅频道」提供 VIP 引导（供页面区分 S3/S4/S5 文案） */
export function subscriptionHint(snapshot) {
  if (!snapshot?.authenticated) return GATE.NEED_LOGIN;
  if (snapshot.trialActive) return null;
  if (!snapshot.vipActive) {
    return snapshot.vipState === VIP_STATE.EXPIRED ? GATE.VIP_EXPIRED : GATE.NEED_VIP;
  }
  return null;
}

function allow() {
  return { allowed: true, gate: null };
}

function deny(gate) {
  return { allowed: false, gate };
}
