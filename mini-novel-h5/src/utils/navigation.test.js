import test from 'node:test';
import assert from 'node:assert/strict';
import {
  goBack,
  goBackTo,
  isSameBookDetailUrl,
  isSamePath,
  previousEntry
} from './navigation.js';

function withHistory(back, fn) {
  const original = globalThis.window;
  globalThis.window = { history: { state: { back } } };
  try {
    fn();
  } finally {
    globalThis.window = original;
  }
}

function fakeRouter() {
  const calls = [];
  return {
    calls,
    back() {
      calls.push('back');
    },
    replace(path) {
      calls.push(`replace:${path}`);
    }
  };
}

test('上一页就是本书详情页（无 query）', () => {
  assert.equal(isSameBookDetailUrl('/h5/book/12', 12), true);
  assert.equal(isSameBookDetailUrl('/h5/book/12', '12'), true);
});

test('上一页是本书详情页（带 query）', () => {
  assert.equal(isSameBookDetailUrl('/h5/book/12?chapterId=345&chapterNo=6', 12), true);
});

test('书号前缀相同但不是同一本书 → 不算', () => {
  assert.equal(isSameBookDetailUrl('/h5/book/123', 12), false);
  assert.equal(isSameBookDetailUrl('/h5/book/123?chapterId=1', 12), false);
});

test('上一页是正文 / 其它页面 → 不算', () => {
  assert.equal(isSameBookDetailUrl('/h5/read/345?bookId=12', 12), false);
  assert.equal(isSameBookDetailUrl('/h5/subscribe/history', 12), false);
  assert.equal(isSameBookDetailUrl('/h5/book/12/extra', 12), false);
});

test('空值安全', () => {
  assert.equal(isSameBookDetailUrl('', 12), false);
  assert.equal(isSameBookDetailUrl(null, 12), false);
  assert.equal(isSameBookDetailUrl('/h5/book/12', null), false);
});

test('isSamePath 忽略 query，但不做前缀误匹配', () => {
  assert.equal(isSamePath('/h5/coin', '/h5/coin'), true);
  assert.equal(isSamePath('/h5/coin?tab=1', '/h5/coin'), true);
  assert.equal(isSamePath('/h5/coin', '/h5/coin?tab=1'), true);
  assert.equal(isSamePath('/h5/coins', '/h5/coin'), false);
  assert.equal(isSamePath('/h5/subscribe/1', '/h5/subscribe'), false);
  assert.equal(isSamePath('', '/h5/coin'), false);
});

test('previousEntry 无 window 环境时返回空串', () => {
  withHistory(undefined, () => {
    assert.equal(previousEntry(), '');
  });
  withHistory('/h5/home', () => {
    assert.equal(previousEntry(), '/h5/home');
  });
});

test('goBack：有上一页 → 退栈', () => {
  withHistory('/h5/home', () => {
    const router = fakeRouter();
    goBack(router, '/h5/profile');
    assert.deepEqual(router.calls, ['back']);
  });
});

test('goBack：没有上一页 → 落到 fallback', () => {
  withHistory('', () => {
    const router = fakeRouter();
    goBack(router, '/h5/profile');
    assert.deepEqual(router.calls, ['replace:/h5/profile']);
  });
});

test('goBackTo：上一页就是目标页 → 退栈，不压重复页', () => {
  withHistory('/h5/coin', () => {
    const router = fakeRouter();
    goBackTo(router, '/h5/coin');
    assert.deepEqual(router.calls, ['back']);
  });
});

test('goBackTo：上一页不是目标页 → replace（避免 A↔B 循环）', () => {
  withHistory('/h5/profile', () => {
    const router = fakeRouter();
    goBackTo(router, '/h5/coin');
    assert.deepEqual(router.calls, ['replace:/h5/coin']);
  });
});
