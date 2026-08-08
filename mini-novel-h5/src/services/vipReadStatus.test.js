import test from 'node:test';
import assert from 'node:assert/strict';
import {
  clearVipReadBooks,
  isVipBookRead,
  markVipBookRead,
  vipReadIds,
  VIP_READ_STORAGE_KEY
} from './vipReadStatus.js';

function memoryStorage(initial = {}) {
  const data = { ...initial };
  return {
    getItem(key) {
      return Object.prototype.hasOwnProperty.call(data, key) ? data[key] : null;
    },
    setItem(key, value) {
      data[key] = String(value);
    },
    removeItem(key) {
      delete data[key];
    }
  };
}

test('marks and reads a VIP book locally', () => {
  const storage = memoryStorage();
  assert.equal(markVipBookRead(12, { chapterId: 3, chapterNo: 1, title: '第一章' }, storage), true);
  assert.equal(isVipBookRead(12, storage), true);
  assert.deepEqual([...vipReadIds(storage)], ['12']);

  const saved = JSON.parse(storage.getItem(VIP_READ_STORAGE_KEY));
  assert.equal(saved['12'].chapterId, 3);
  assert.equal(saved['12'].chapterNo, 1);
});

test('ignores invalid novel ids', () => {
  const storage = memoryStorage();
  assert.equal(markVipBookRead(0, {}, storage), false);
  assert.equal(markVipBookRead('bad', {}, storage), false);
  assert.deepEqual([...vipReadIds(storage)], []);
});

test('clears local VIP read records', () => {
  const storage = memoryStorage();
  markVipBookRead(7, {}, storage);
  clearVipReadBooks(storage);
  assert.equal(isVipBookRead(7, storage), false);
});
