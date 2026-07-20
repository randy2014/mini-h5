import test from 'node:test';
import assert from 'node:assert/strict';
import { canRequestVipContent, shouldCheckVipStatus } from './vipAccess.js';

test('未登录用户可进入资格页但不校验状态或请求 VIP 内容', () => {
  assert.equal(shouldCheckVipStatus(false), false);
  assert.equal(canRequestVipContent(false, { active: true }), false);
});

test('普通用户可进入资格页但不得请求 VIP 内容', () => {
  assert.equal(shouldCheckVipStatus(true), true);
  assert.equal(canRequestVipContent(true, { active: false }), false);
});

test('只有已登录且有效 VIP 才可请求书单和分类', () => {
  assert.equal(canRequestVipContent(true, { active: true }), true);
});
