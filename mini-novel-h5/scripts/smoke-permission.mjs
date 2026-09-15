/**
 * 权限层集成冒烟测试（Node 内直接跑 pinia，不依赖浏览器/构建）
 * 覆盖最容易出错的接线：跨 store getter、can() 判定、订阅本地登记、会话重置。
 * 只读内存快照，不发任何网络请求。
 */
import assert from 'node:assert/strict';
import { createPinia, setActivePinia } from 'pinia';
import { CAPABILITY, GATE } from '../src/permission/constants.js';
import { usePermissionStore } from '../src/permission/store.js';
import { useUserStore } from '../src/stores/user.js';

const FUTURE = '2099-12-31T23:59:59';
const PAST = '2020-01-01T00:00:00';

function memoryStorage() {
  const map = new Map();
  return {
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    setItem: (k, v) => map.set(k, String(v)),
    removeItem: (k) => map.delete(k),
    clear: () => map.clear()
  };
}

globalThis.localStorage = memoryStorage();
setActivePinia(createPinia());

const userStore = useUserStore();
const permissionStore = usePermissionStore();

// 1) 匿名
assert.equal(permissionStore.snapshot.authenticated, false);
assert.equal(permissionStore.snapshot.vipActive, false);
assert.equal(permissionStore.snapshot.trialActive, false);

// 2) 登录但未加载 profile（快照不得谎报 VIP）
userStore.userId = 7;
userStore.token = 'token';
assert.equal(permissionStore.snapshot.authenticated, true);
assert.equal(permissionStore.snapshot.vipActive, false);
assert.equal(permissionStore.snapshot.vipState, 'NONE');

// 3) VIP 生效 + 试用中（profile 来自 POST /auth/login 的同一个 VO）
userStore.profile = { id: 7, vipExpireTime: FUTURE, vipActivatedAt: new Date().toISOString() };
assert.equal(permissionStore.snapshot.vipActive, true);
assert.equal(permissionStore.snapshot.trialActive, true, 'VIP 激活 7 天内为试用');
assert.equal(permissionStore.snapshot.vipState, 'ACTIVE');

// 4) 试用结束 + 未订阅频道
userStore.profile = { id: 7, vipExpireTime: FUTURE, vipActivatedAt: PAST };
assert.equal(permissionStore.snapshot.trialActive, false);
permissionStore.subscribes = [];
const denied = permissionStore.snapshot;
assert.equal(denied.authenticated, true);

// 5) 订阅后本地登记即时生效（不必等下一次 load）
permissionStore.applySubscribe({ channelId: 3, endTime: FUTURE });
assert.equal(permissionStore.subscribes.length, 1);
assert.equal(permissionStore.subscribes[0].channelId, 3);
assert.equal(permissionStore.subscribes[0].status, 'ACTIVE');

// 6) 重复登记不产生重复记录
permissionStore.applySubscribe({ channelId: 3, endTime: FUTURE });
assert.equal(permissionStore.subscribes.length, 1);

// 7) 会话失效：退出登录后快照回到匿名，且成人确认保留
permissionStore.confirmAdult();
assert.equal(permissionStore.adultConfirmed, true);
userStore.clearSession();
permissionStore.reset();
assert.equal(permissionStore.snapshot.authenticated, false);
assert.equal(permissionStore.snapshot.vipActive, false);
assert.equal(permissionStore.subscribes.length, 0);
assert.equal(permissionStore.loaded, false);
assert.equal(permissionStore.adultConfirmed, true, '成人确认是本机意愿，不随会话清空');

// 8) 常量连通性
assert.equal(typeof GATE.NEED_LOGIN, 'string');
assert.equal(typeof CAPABILITY.VIP_READ, 'string');

console.log('✓ 权限层集成冒烟通过：跨 store getter、快照判定、订阅登记、会话重置');
