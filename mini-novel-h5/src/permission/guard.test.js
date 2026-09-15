import test from 'node:test';
import assert from 'node:assert/strict';
import { resolveNavigation } from './guard.js';

const route = (meta, fullPath = '/h5/coin') => ({ meta, fullPath });
const anon = { authenticated: false };
const authed = { authenticated: true };

test('声明了 AUTH 的路由对匿名用户改写为登录页并带回去向', () => {
  const result = resolveNavigation(route({ require: ['AUTH'] }, '/h5/coin'), anon);
  assert.deepEqual(result, {
    path: '/h5/login',
    query: { redirect: '/h5/coin' },
    replace: true
  });
});

test('已登录用户不受影响', () => {
  assert.equal(resolveNavigation(route({ require: ['AUTH'] }), authed), null);
});

test('未声明 require 的路由一律放行（公开阅读页）', () => {
  assert.equal(resolveNavigation(route({ title: '首页' }, '/h5/home'), anon), null);
  assert.equal(resolveNavigation(route(undefined, '/h5/home'), anon), null);
  assert.equal(resolveNavigation(route({ require: [] }, '/h5/home'), anon), null);
});

test('带 query 的深链把完整路径带进 redirect', () => {
  const result = resolveNavigation(route({ require: ['AUTH'] }, '/h5/subscribe/3?from=home'), anon);
  assert.equal(result.query.redirect, '/h5/subscribe/3?from=home');
});

test('非法 fullPath 回退到安全默认页，不产生开放跳转', () => {
  const result = resolveNavigation(route({ require: ['AUTH'] }, 'https://evil.example.com'), anon, '/h5/home');
  assert.equal(result.query.redirect, '/h5/home');
});
