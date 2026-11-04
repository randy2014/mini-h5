import test from 'node:test'
import assert from 'node:assert/strict'
import { importDirectoryItem, importDirectoryItems } from '../src/utils/mediaImport.js'

const makeItem = (title = '第01集') => ({
  title,
  name: `${title}.mp4`,
  status: 'idle',
  percent: 0,
  reason: '',
  assetId: null,
  assetStatus: null,
  postId: null
})

test('单个视频：上传 → 建帖成功', async () => {
  const item = makeItem()
  const posts = []
  await importDirectoryItem({
    item,
    upload: async () => ({ ok: true, assetId: 11, status: 1 }),
    createPost: async (i) => { posts.push({ title: i.title, assetId: i.assetId }); return { id: 99 } }
  })
  assert.equal(item.status, 'done')
  assert.equal(item.percent, 100)
  assert.equal(item.assetId, 11)
  assert.equal(item.assetStatus, 1)
  assert.equal(item.postId, 99)
  assert.deepEqual(posts, [{ title: '第01集', assetId: 11 }])
})

test('单个视频：上传被拒（ok=false）→ 失败且不建帖、不残留素材', async () => {
  const item = makeItem()
  const removed = []
  let created = 0
  await importDirectoryItem({
    item,
    upload: async () => ({ ok: false, reason: '文件超过 300MB 上限' }),
    createPost: async () => { created += 1; return { id: 1 } },
    removeAsset: async (id) => removed.push(id)
  })
  assert.equal(item.status, 'failed')
  assert.match(item.reason, /300MB/)
  assert.equal(created, 0)
  assert.deepEqual(removed, [])
})

test('单个视频：上传成功但建帖失败 → 回收素材（避免磁盘孤儿）', async () => {
  const item = makeItem()
  const removed = []
  await importDirectoryItem({
    item,
    upload: async () => ({ ok: true, assetId: 7, status: 1 }),
    createPost: async () => { throw new Error('网络异常') },
    removeAsset: async (id) => removed.push(id)
  })
  assert.equal(item.status, 'failed')
  assert.equal(item.reason, '网络异常')
  assert.deepEqual(removed, [7])
})

test('单个视频：素材已被别处引用时回收失败也不影响结果', async () => {
  const item = makeItem()
  await importDirectoryItem({
    item,
    upload: async () => ({ ok: true, assetId: 7, status: 2 }),
    createPost: async () => { throw new Error('创建失败') },
    removeAsset: async () => { throw new Error('素材正被内容引用，不能删除') }
  })
  assert.equal(item.status, 'failed')
})

test('上传未返回 assetId 视为失败', async () => {
  const item = makeItem()
  const removed = []
  await importDirectoryItem({
    item,
    upload: async () => ({ ok: true }),
    createPost: async () => ({ id: 1 }),
    removeAsset: async (id) => removed.push(id)
  })
  assert.equal(item.status, 'failed')
  assert.match(item.reason, /素材 id/)
  assert.deepEqual(removed, [])
})

test('进度回调：上传百分比透传并夹在 0-99', async () => {
  const item = makeItem()
  const seen = []
  await importDirectoryItem({
    item,
    upload: async (i, onPercent) => { onPercent(42); onPercent(150); return { ok: true, assetId: 3, status: 1 } },
    createPost: async () => ({ id: 5 }),
    onItemChange: (i) => seen.push(`${i.status}:${i.percent}`)
  })
  assert.ok(seen.includes('uploading:42'))
  assert.ok(seen.includes('uploading:99'))
  assert.ok(seen.includes('creating:100'))
  assert.ok(seen.includes('done:100'))
})

test('批量：单条失败不影响其余，计数正确，跳过非 idle 项', async () => {
  const items = [makeItem('a'), makeItem('b'), makeItem('c'), makeItem('d')]
  items[3].status = 'skip'
  const removed = []
  const counts = await importDirectoryItems({
    items,
    upload: async (i) => {
      if (i.title === 'b') return { ok: false, reason: '格式不支持' }
      if (i.title === 'c') return { ok: true, assetId: 20, status: 1 }
      return { ok: true, assetId: 10, status: 1 }
    },
    createPost: async () => { throw new Error('建帖失败') },
    removeAsset: async (id) => removed.push(id)
  })
  assert.deepEqual(items.map((i) => i.status), ['failed', 'failed', 'failed', 'skip'])
  assert.deepEqual(counts, { created: 0, failed: 3, stopped: false, pending: 0 })
  // a、c 上传成功但建帖失败 → 回收 10、20；b 上传就被拒 → 无素材可回收
  assert.deepEqual(removed, [10, 20])
})

test('批量：shouldStop 在当前项结束后停止，剩余保持 idle', async () => {
  const items = [makeItem('a'), makeItem('b'), makeItem('c')]
  let n = 0
  const counts = await importDirectoryItems({
    items,
    upload: async () => ({ ok: true, assetId: ++n, status: 1 }),
    createPost: async () => ({ id: 1 }),
    shouldStop: () => n >= 2
  })
  assert.equal(counts.created, 2)
  assert.equal(counts.stopped, true)
  assert.equal(counts.pending, 1)
  assert.deepEqual(items.map((i) => i.status), ['done', 'done', 'idle'])
  // 停下后再跑：接着把剩下的导完
  const again = await importDirectoryItems({
    items,
    upload: async () => ({ ok: true, assetId: 30, status: 1 }),
    createPost: async () => ({ id: 2 })
  })
  assert.deepEqual(again, { created: 1, failed: 0, stopped: false, pending: 0 })
})

test('批量：空清单安全返回', async () => {
  assert.deepEqual(await importDirectoryItems({ items: [], upload: async () => ({}) }), {
    created: 0, failed: 0, stopped: false, pending: 0
  })
  assert.deepEqual(await importDirectoryItems({ items: null }), {
    created: 0, failed: 0, stopped: false, pending: 0
  })
})
