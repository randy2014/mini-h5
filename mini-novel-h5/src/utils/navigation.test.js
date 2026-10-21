import test from 'node:test';
import assert from 'node:assert/strict';
import { isSameBookDetailUrl } from './navigation.js';

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
