import test from 'node:test';
import assert from 'node:assert/strict';

// 简易 sessionStorage 垫片：模块在调用时才访问 sessionStorage
const store = new Map();
globalThis.sessionStorage = {
  getItem: (key) => (store.has(key) ? store.get(key) : null),
  setItem: (key, value) => store.set(key, String(value)),
  removeItem: (key) => store.delete(key),
  clear: () => store.clear()
};

const { readChannelName, rememberChannelName } = await import('./channelMetaCache.js');

test('记住后能读回频道名', () => {
  store.clear();
  rememberChannelName(12, '精品AI漫剧');
  assert.equal(readChannelName(12), '精品AI漫剧');
  assert.equal(readChannelName('12'), '精品AI漫剧');
});

test('未缓存 / 非法 id → 空串', () => {
  store.clear();
  assert.equal(readChannelName(99), '');
  assert.equal(readChannelName(0), '');
  assert.equal(readChannelName(null), '');
});

test('空名称不写入，空白名 trim 后写入', () => {
  store.clear();
  rememberChannelName(5, '');
  rememberChannelName(6, '   ');
  assert.equal(readChannelName(5), '');
  assert.equal(readChannelName(6), '');
  rememberChannelName(7, '  精品漫画  ');
  assert.equal(readChannelName(7), '精品漫画');
});

test('同 id 覆盖为新名称', () => {
  store.clear();
  rememberChannelName(3, '旧名');
  rememberChannelName(3, '新名');
  assert.equal(readChannelName(3), '新名');
});

test('缓存损坏时安全返回空串', () => {
  store.clear();
  store.set('mini_h5_channel_meta_cache', 'not-json');
  assert.equal(readChannelName(1), '');
});
