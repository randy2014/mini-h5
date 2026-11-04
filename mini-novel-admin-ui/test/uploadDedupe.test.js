import test from 'node:test'
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { md5OfFileCached } from '../src/utils/fileFingerprint.js'

function installLocalStorage() {
  const map = new Map()
  globalThis.localStorage = {
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    setItem: (k, v) => { map.set(k, String(v)) },
    removeItem: (k) => { map.delete(k) }
  }
  return map
}

function fakeFile(name, bytes, lastModified = 12345) {
  const file = {
    name,
    size: bytes.length,
    lastModified,
    slices: 0,
    slice(start, end) {
      file.slices += 1
      const part = bytes.subarray(start, end)
      return {
        arrayBuffer: async () => part.buffer.slice(part.byteOffset, part.byteOffset + part.byteLength)
      }
    }
  }
  return file
}

const bytesOf = (size, seed = 3) => {
  const out = new Uint8Array(size)
  for (let i = 0; i < size; i += 1) out[i] = (i * seed + 7) & 0xff
  return out
}

test('md5 缓存：同一文件第二次导入不再重算（不读文件）', async () => {
  installLocalStorage()
  const bytes = bytesOf(5 * 1024 * 1024) // 大于默认 4MB 分块 → 至少读两次
  const file = fakeFile('第01集.mp4', bytes)
  const first = await md5OfFileCached(file)
  const slicesAfterFirst = file.slices
  const second = await md5OfFileCached(file)
  assert.equal(first, second)
  assert.equal(first, createHash('md5').update(bytes).digest('hex'))
  assert.ok(slicesAfterFirst > 1, '首次应分块读取')
  assert.equal(file.slices, slicesAfterFirst, '命中缓存后不应再读文件')
})

test('md5 缓存：修改时间/大小变化不命中缓存', async () => {
  const store = installLocalStorage()
  const bytes = bytesOf(64)
  await md5OfFileCached(fakeFile('a.mp4', bytes, 1000))
  assert.equal(store.size, 1)
  // 不同修改时间 → 新 key，重新计算
  const file2 = fakeFile('a.mp4', bytes, 2000)
  await md5OfFileCached(file2)
  assert.ok(file2.slices > 0)
  assert.equal(store.size, 2)
})

test('md5 缓存：localStorage 不可用时不报错（隐私模式）', async () => {
  globalThis.localStorage = {
    getItem() { throw new Error('SecurityError') },
    setItem() { throw new Error('SecurityError') }
  }
  const bytes = bytesOf(128)
  const md5 = await md5OfFileCached(fakeFile('b.mp4', bytes))
  assert.equal(md5, createHash('md5').update(bytes).digest('hex'))
})
