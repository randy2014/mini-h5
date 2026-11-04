import test from 'node:test'
import assert from 'node:assert/strict'

function installSessionStorage() {
  const map = new Map()
  globalThis.sessionStorage = {
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    setItem: (k, v) => { map.set(k, String(v)) },
    removeItem: (k) => { map.delete(k) },
    clear: () => map.clear()
  }
  return map
}

const load = () => import('./mediaMetaCache.js')

test('remember/read：缓存视频标题与类型', async () => {
  installSessionStorage()
  const { rememberMediaMeta, readMediaMeta } = await load()
  rememberMediaMeta(30, { title: '高三爱情故事', type: 2 })
  assert.deepEqual(readMediaMeta(30), { title: '高三爱情故事', type: 2 })
  assert.equal(readMediaMeta(31), null)
})

test('remember：空标题不写入；id 兼容字符串', async () => {
  installSessionStorage()
  const { rememberMediaMeta, readMediaMeta } = await load()
  rememberMediaMeta(5, { title: '   ', type: 2 })
  assert.equal(readMediaMeta(5), null)
  rememberMediaMeta('6', { title: ' 标题 ', type: 3 })
  assert.deepEqual(readMediaMeta(6), { title: '标题', type: 3 })
})

test('remember：只保留最近 200 条', async () => {
  installSessionStorage()
  const { rememberMediaMeta, readMediaMeta } = await load()
  for (let i = 1; i <= 205; i += 1) {
    rememberMediaMeta(i, { title: `T${i}`, type: 2 })
  }
  assert.equal(readMediaMeta(1), null)
  assert.equal(readMediaMeta(5), null)
  assert.equal(readMediaMeta(6).title, 'T6')
  assert.equal(readMediaMeta(205).title, 'T205')
})

test('缓存损坏/不可用时不抛错', async () => {
  const map = installSessionStorage()
  const { rememberMediaMeta, readMediaMeta } = await load()
  map.set('mini_h5_media_meta_cache', '{bad json')
  assert.equal(readMediaMeta(1), null)
  rememberMediaMeta(2, { title: 'ok', type: 2 })
  assert.equal(readMediaMeta(2).title, 'ok')

  globalThis.sessionStorage = {
    getItem() { throw new Error('SecurityError') },
    setItem() { throw new Error('SecurityError') }
  }
  assert.equal(readMediaMeta(2), null)
  assert.doesNotThrow(() => rememberMediaMeta(3, { title: 'x', type: 1 }))
})
