import test from 'node:test';
import assert from 'node:assert/strict';
import { CAPABILITY, GATE, VIP_STATE } from './constants.js';
import { activeSubscription, buildSnapshot, can, daysLeft, priceFor, subscriptionHint } from './rules.js';

const NOW = Date.parse('2026-09-13T10:00:00');
const FUTURE = '2027-01-01T00:00:00';
const PAST = '2026-09-01T00:00:00';

/** S1 匿名 */
const s1 = () => buildSnapshot({ now: NOW, authenticated: false });
/** S2 已登录非VIP（从未开通） */
const s2 = () => buildSnapshot({ now: NOW, authenticated: true, profile: { nickname: 'u' } });
/** S2b 已登录 · VIP 已过期 */
const s2b = () => buildSnapshot({ now: NOW, authenticated: true, profile: { vipExpireTime: PAST } });
/** S3 VIP + 试用期内（激活于 3 天前） */
const s3 = () =>
  buildSnapshot({
    now: NOW,
    authenticated: true,
    profile: { vipExpireTime: FUTURE, vipActivatedAt: '2026-09-10T10:00:00' }
  });
/** S4 VIP + 试用结束 + 未订阅 */
const s4 = () =>
  buildSnapshot({
    now: NOW,
    authenticated: true,
    profile: { vipExpireTime: FUTURE, vipActivatedAt: '2026-08-01T00:00:00' }
  });
/** S5 VIP + 试用结束 + 已订阅频道 3 */
const s5 = () =>
  buildSnapshot({
    now: NOW,
    authenticated: true,
    profile: { vipExpireTime: FUTURE, vipActivatedAt: '2026-08-01T00:00:00' },
    subscribes: [{ channelId: 3, status: 'ACTIVE', endTime: FUTURE }]
  });

test('P0 公共阅读对所有状态放行', () => {
  for (const snapshot of [s1(), s2(), s2b(), s3(), s4(), s5()]) {
    assert.equal(can(snapshot, CAPABILITY.PUBLIC_READ).allowed, true);
  }
});

test('P1 VIP 阅读：S1 需登录、S2 需 VIP、S2b 已过期、S3/S4/S5 放行', () => {
  assert.deepEqual(can(s1(), CAPABILITY.VIP_READ), { allowed: false, gate: GATE.NEED_LOGIN });
  assert.deepEqual(can(s2(), CAPABILITY.VIP_READ), { allowed: false, gate: GATE.NEED_VIP });
  assert.deepEqual(can(s2b(), CAPABILITY.VIP_READ), { allowed: false, gate: GATE.VIP_EXPIRED });
  assert.equal(can(s3(), CAPABILITY.VIP_READ).allowed, true);
  assert.equal(can(s4(), CAPABILITY.VIP_READ).allowed, true);
  assert.equal(can(s5(), CAPABILITY.VIP_READ).allowed, true);
});

test('P2 频道媒体只看订阅/试用，与 VIP 无关', () => {
  const ctx = { channelId: 3 };
  assert.equal(can(s1(), CAPABILITY.CHANNEL_MEDIA, ctx).gate, GATE.NEED_LOGIN);
  assert.equal(can(s2(), CAPABILITY.CHANNEL_MEDIA, ctx).gate, GATE.NEED_CHANNEL_SUBSCRIBE);
  assert.equal(can(s3(), CAPABILITY.CHANNEL_MEDIA, ctx).allowed, true, '试用期内全频道开放');
  assert.equal(can(s4(), CAPABILITY.CHANNEL_MEDIA, ctx).gate, GATE.NEED_CHANNEL_SUBSCRIBE);
  assert.equal(can(s5(), CAPABILITY.CHANNEL_MEDIA, ctx).allowed, true);
  assert.equal(
    can(s5(), CAPABILITY.CHANNEL_MEDIA, { channelId: 9 }).gate,
    GATE.NEED_CHANNEL_SUBSCRIBE,
    '订阅了 3 不等于能看 9'
  );
});

test('P2 关键真相：VIP 过期但订阅仍在 → 媒体可看', () => {
  const snapshot = buildSnapshot({
    now: NOW,
    authenticated: true,
    profile: { vipExpireTime: PAST, vipActivatedAt: '2026-08-01T00:00:00' },
    subscribes: [{ channelId: 3, status: 'ACTIVE', endTime: FUTURE }]
  });
  assert.equal(can(snapshot, CAPABILITY.CHANNEL_MEDIA, { channelId: 3 }).allowed, true);
  assert.equal(can(snapshot, CAPABILITY.VIP_READ).gate, GATE.VIP_EXPIRED);
});

test('P3 交易：需登录且币余额足够', () => {
  const ctx = { periodType: 'MONTH' };
  assert.equal(can(s1(), CAPABILITY.TRADE, ctx).gate, GATE.NEED_LOGIN);
  assert.equal(can(s2(), CAPABILITY.TRADE, ctx).gate, GATE.NEED_COIN);
  const poor = buildSnapshot({ now: NOW, authenticated: true, profile: { vipExpireTime: FUTURE }, coinBalance: 299 });
  assert.equal(can(poor, CAPABILITY.TRADE, ctx).gate, GATE.NEED_COIN);
  const ok = buildSnapshot({ now: NOW, authenticated: true, profile: { vipExpireTime: FUTURE }, coinBalance: 300 });
  assert.equal(can(ok, CAPABILITY.TRADE, ctx).allowed, true);
});

test('P4 账户：只需登录', () => {
  assert.equal(can(s1(), CAPABILITY.ACCOUNT).gate, GATE.NEED_LOGIN);
  assert.equal(can(s2(), CAPABILITY.ACCOUNT).allowed, true);
});

test('未知能力不放行（避免新增能力静默提权）', () => {
  assert.deepEqual(can(s5(), 'SOMETHING_NEW'), { allowed: false, gate: GATE.FORBIDDEN });
  assert.equal(can(null, CAPABILITY.PUBLIC_READ).allowed, false);
});

test('VIP 态细分与试用期判定与后端一致', () => {
  assert.equal(s2().vipState, VIP_STATE.NONE);
  assert.equal(s2b().vipState, VIP_STATE.EXPIRED);
  assert.equal(s3().vipState, VIP_STATE.ACTIVE);

  // 试用只看 vip_activated_at，不要求 VIP 仍有效（与 SubscribeServiceImpl.isTrialActive 一致）
  const trialNoVip = buildSnapshot({
    now: NOW,
    authenticated: true,
    profile: { vipExpireTime: PAST, vipActivatedAt: '2026-09-10T10:00:00' }
  });
  assert.equal(trialNoVip.trialActive, true);
  assert.equal(can(trialNoVip, CAPABILITY.CHANNEL_MEDIA, { channelId: 7 }).allowed, true);
});

test('试用期在第 7 天整点结束', () => {
  const justInside = buildSnapshot({
    now: Date.parse('2026-09-17T09:59:59'),
    authenticated: true,
    profile: { vipActivatedAt: '2026-09-10T10:00:00' }
  });
  const justOutside = buildSnapshot({
    now: Date.parse('2026-09-17T10:00:00'),
    authenticated: true,
    profile: { vipActivatedAt: '2026-09-10T10:00:00' }
  });
  assert.equal(justInside.trialActive, true);
  assert.equal(justOutside.trialActive, false);
});

test('订阅必须同时满足状态有效与未到期', () => {
  const snapshot = buildSnapshot({
    now: NOW,
    authenticated: true,
    subscribes: [
      { channelId: 1, status: 'ACTIVE', endTime: FUTURE },
      { channelId: 2, status: 'ACTIVE', endTime: PAST },
      { channelId: 3, status: 'EXPIRED', endTime: FUTURE },
      { channelId: 4, status: 'CANCELLED', endTime: FUTURE }
    ]
  });
  assert.ok(activeSubscription(snapshot, 1));
  assert.equal(activeSubscription(snapshot, 2), null, '已到期不算');
  assert.equal(activeSubscription(snapshot, 3), null, 'EXPIRED 不算');
  assert.equal(activeSubscription(snapshot, 4), null, '已取消不算');
  assert.equal(activeSubscription(snapshot, 0), null);
  assert.equal(activeSubscription(snapshot, null), null);
});

test('价格表与剩余天数', () => {
  assert.equal(priceFor({ MONTH: 300 }, 'MONTH'), 300);
  assert.equal(priceFor({ MONTH: 300 }, 'WEEK'), 300, '未知周期回退 MONTH');
  assert.equal(priceFor(null, 'YEAR'), 3000, '缺省用兜底表');
  assert.equal(daysLeft(FUTURE, NOW) > 0, true);
  assert.equal(daysLeft(PAST, NOW), 0);
});

test('subscribeGate 区分 S1 / S2 / S2b / S3', () => {
  assert.equal(subscriptionHint(s1()), GATE.NEED_LOGIN);
  assert.equal(subscriptionHint(s2()), GATE.NEED_VIP);
  assert.equal(subscriptionHint(s2b()), GATE.VIP_EXPIRED);
  assert.equal(subscriptionHint(s3()), null);
  assert.equal(subscriptionHint(s4()), null);
});
