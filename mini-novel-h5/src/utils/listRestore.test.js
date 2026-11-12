import test from 'node:test';
import assert from 'node:assert/strict';
import {
  DEFAULT_TTL,
  MAX_SNAPSHOTS,
  applyAnchor,
  captureAnchor,
  clearSnapshots,
  dropSnapshot,
  hasForwardEntry,
  isSnapshotFresh,
  normalizePath,
  readSnapshot,
  restoreScroll,
  saveSnapshot,
  shouldRestore,
  snapshotCount
} from './listRestore.js';

function node(key, top, bottom) {
  return {
    key,
    getAttribute: () => key,
    getBoundingClientRect: () => ({ top, bottom })
  };
}

function fakeDoc(nodes) {
  return {
    querySelectorAll: () => nodes,
    querySelector: (selector) => nodes.find((item) => selector.includes(`"${item.key}"`)) || null
  };
}

function fakeView() {
  const calls = [];
  return {
    calls,
    scrollBy(x, y) {
      calls.push(['scrollBy', x, y]);
    },
    scrollTo(x, y) {
      calls.push(['scrollTo', x, y]);
    }
  };
}

test('快照存取：保存后可读回，并带上时间戳', () => {
  clearSnapshots();
  const saved = saveSnapshot('channel:12', { path: '/h5/subscribe/12', scrollTop: 480, state: { feed: [] } }, 1000);
  assert.equal(saved.at, 1000);
  const entry = readSnapshot('channel:12', { now: 1000 });
  assert.equal(entry.scrollTop, 480);
  assert.equal(entry.path, '/h5/subscribe/12');
  assert.equal(snapshotCount(), 1);
});

test('空 key 不保存', () => {
  clearSnapshots();
  assert.equal(saveSnapshot('', { scrollTop: 10 }), null);
  assert.equal(snapshotCount(), 0);
});

test('快照过期：超过 TTL 读取返回 null，并清掉旧条目', () => {
  clearSnapshots();
  saveSnapshot('k', { scrollTop: 10 }, 0);
  assert.equal(readSnapshot('k', { now: DEFAULT_TTL + 1 }), null);
  assert.equal(snapshotCount(), 0);
});

test('快照有效期边界：刚好等于 TTL 仍可用', () => {
  assert.equal(isSnapshotFresh({ at: 0 }, DEFAULT_TTL, DEFAULT_TTL), true);
  assert.equal(isSnapshotFresh({ at: 0 }, DEFAULT_TTL + 1, DEFAULT_TTL), false);
  assert.equal(isSnapshotFresh(null, 0), false);
  assert.equal(isSnapshotFresh({ at: 'bad' }, 0), false);
});

test('换账号不复用上一个人的列表', () => {
  clearSnapshots();
  saveSnapshot('k', { scrollTop: 10, account: 7 }, 0);
  assert.equal(readSnapshot('k', { now: 0, account: 7 })?.scrollTop, 10);
  assert.equal(readSnapshot('k', { now: 0, account: 8 }), null);
  assert.equal(snapshotCount(), 0);
});

test('路径不一致不恢复（query / hash 不参与比较）', () => {
  clearSnapshots();
  saveSnapshot('search', { path: '/h5/search?q=A#top', scrollTop: 10 }, 0);
  assert.ok(readSnapshot('search', { now: 0, path: '/h5/search?q=A' }));
  assert.equal(readSnapshot('search', { now: 0, path: '/h5/search?q=B' }), null);
  assert.equal(normalizePath('/h5/rank/hot#list'), '/h5/rank/hot');
});

test('快照数量有上限，淘汰最旧的', () => {
  clearSnapshots();
  for (let index = 0; index < MAX_SNAPSHOTS + 5; index += 1) {
    saveSnapshot(`k${index}`, { scrollTop: index }, 0);
  }
  assert.equal(snapshotCount(), MAX_SNAPSHOTS);
  assert.equal(readSnapshot('k0', { now: 0 }), null);
  assert.ok(readSnapshot(`k${MAX_SNAPSHOTS + 4}`, { now: 0 }));
});

test('重复保存同一 key 覆盖，并保留最后一次', () => {
  clearSnapshots();
  saveSnapshot('k', { scrollTop: 10 }, 0);
  saveSnapshot('k', { scrollTop: 99 }, 1);
  assert.equal(readSnapshot('k', { now: 1 }).scrollTop, 99);
  assert.equal(snapshotCount(), 1);
});

test('dropSnapshot 只删指定 key', () => {
  clearSnapshots();
  saveSnapshot('a', {}, 0);
  saveSnapshot('b', {}, 0);
  dropSnapshot('a');
  assert.equal(readSnapshot('a', { now: 0 }), null);
  assert.ok(readSnapshot('b', { now: 0 }));
});

test('hasForwardEntry：有 forward 才是「退回来」的', () => {
  assert.equal(hasForwardEntry({ forward: '/h5/book/12' }), true);
  assert.equal(hasForwardEntry({ forward: null }), false);
  assert.equal(hasForwardEntry({}), false);
  assert.equal(hasForwardEntry(null), false);
  assert.equal(hasForwardEntry(undefined), false);
});

test('shouldRestore：快照 + 退回，缺一不可', () => {
  assert.equal(shouldRestore({}, true), true);
  assert.equal(shouldRestore(null, true), false);
  assert.equal(shouldRestore({}, false), false);
});

test('captureAnchor：取视口内第一条列表项', () => {
  const doc = fakeDoc([
    node('feed-novel-1', -60, -10),
    node('feed-novel-2', -20, 40),
    node('feed-novel-3', 60, 120)
  ]);
  assert.deepEqual(captureAnchor('data-list-key', doc), { key: 'feed-novel-2', top: -20 });
});

test('captureAnchor：全部滚出视口 / 空列表 / 非浏览器环境 → null', () => {
  assert.equal(captureAnchor('data-list-key', fakeDoc([node('a', -100, -10)])), null);
  assert.equal(captureAnchor('data-list-key', fakeDoc([])), null);
  assert.equal(captureAnchor('data-list-key', undefined), null);
});

test('applyAnchor：同一锚点上移则把滚动位置加回去', () => {
  const doc = fakeDoc([node('feed-novel-2', 120, 180)]);
  const view = fakeView();
  const delta = applyAnchor({ key: 'feed-novel-2', top: -20 }, 'data-list-key', { doc, view });
  assert.equal(delta, 140);
  assert.deepEqual(view.calls, [['scrollBy', 0, 140]]);
});

test('applyAnchor：锚点消失 / 位置未变 → 不动滚动', () => {
  const doc = fakeDoc([node('other', 10, 50)]);
  const view = fakeView();
  assert.equal(applyAnchor({ key: 'missing', top: 0 }, 'data-list-key', { doc, view }), 0);
  assert.equal(applyAnchor({ key: 'other', top: 10 }, 'data-list-key', { doc, view }), 0);
  assert.equal(view.calls.length, 0);
});

test('applyAnchor：null 锚点安全', () => {
  const view = fakeView();
  assert.equal(applyAnchor(null, 'data-list-key', { doc: fakeDoc([]), view }), 0);
  assert.equal(view.calls.length, 0);
});

test('restoreScroll：落回快照位置，非法值按 0', () => {
  const view = fakeView();
  assert.equal(restoreScroll(320, view), true);
  assert.equal(restoreScroll('bad', view), true);
  assert.deepEqual(view.calls, [['scrollTo', 0, 320], ['scrollTo', 0, 0]]);
  assert.equal(restoreScroll(320, undefined), false);
});
