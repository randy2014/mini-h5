import test from 'node:test'
import assert from 'node:assert/strict'
import { uploadDirectoryItem, uploadDirectoryItems } from '../src/utils/mediaImport.js'

const makeItem = (name = '第01集.mp4') => ({
  name,
  status: 'idle',
  percent: 0,
  reason: '',
  assetId: null,
  assetStatus: null
})

test('单个视频：上传成功', async () => {
  const item = makeItem()
  await uploadDirectoryItem({
    item,
    upload: async () => ({ ok: true, assetId: 11, status: 1 })
  })
  assert.equal(item.status, 'done')
  assert.equal(item.percent, 100)
  assert.equal(item.assetId, 11)
  assert.equal(item.assetStatus, 1)
})

test('单个视频：上传被拒（ok=false）→ 失败并带原因', async () => {
  const item = makeItem()
  await uploadDirectoryItem({
    item,
    upload: async () => ({ ok: false, reason: '文件超过 300MB 上限' })
  })
  assert.equal(item.status, 'failed')
  assert.match(item.reason, /300MB/)
  assert.equal(item.assetId, null)
})

test('单个视频：异常与缺 assetId 都算失败', async () => {
  const a = makeItem()
  await uploadDirectoryItem({ item: a, upload: async () => { throw new Error('网络异常') } })
  assert.equal(a.status, 'failed')
  assert.equal(a.reason, '网络异常')

  const b = makeItem()
  await uploadDirectoryItem({ item: b, upload: async () => ({ ok: true }) })
  assert.equal(b.status, 'failed')
  assert.match(b.reason, /素材 id/)
})

test('进度回调：百分比透传并夹在 0-99', async () => {
  const item = makeItem()
  const seen = []
  await uploadDirectoryItem({
    item,
    upload: async (i, onPercent) => { onPercent(42); onPercent(150); return { ok: true, assetId: 3, status: 1 } },
    onItemChange: (i) => seen.push(`${i.status}:${i.percent}`)
  })
  assert.ok(seen.includes('uploading:42'))
  assert.ok(seen.includes('uploading:99'))
  assert.ok(seen.includes('done:100'))
})

test('批量：单条失败不影响其余，成功项逐个回调 onUploaded', async () => {
  const items = [makeItem('a.mp4'), makeItem('b.mp4'), makeItem('c.mp4'), makeItem('d.mp4')]
  items[3].status = 'skip'
  const uploaded = []
  const counts = await uploadDirectoryItems({
    items,
    upload: async (i) => {
      if (i.name === 'b.mp4') return { ok: false, reason: '格式不支持' }
      return { ok: true, assetId: i.name.charCodeAt(0), status: 1 }
    },
    onUploaded: (i) => uploaded.push(i.name)
  })
  assert.deepEqual(items.map((i) => i.status), ['done', 'failed', 'done', 'skip'])
  assert.deepEqual(counts, { uploaded: 2, failed: 1, stopped: false, pending: 0 })
  assert.deepEqual(uploaded, ['a.mp4', 'c.mp4'])
})

test('批量：shouldStop 在当前项结束后停止，剩余保持 idle', async () => {
  const items = [makeItem('a.mp4'), makeItem('b.mp4'), makeItem('c.mp4')]
  let n = 0
  const counts = await uploadDirectoryItems({
    items,
    upload: async () => ({ ok: true, assetId: ++n, status: 1 }),
    shouldStop: () => n >= 2
  })
  assert.equal(counts.uploaded, 2)
  assert.equal(counts.stopped, true)
  assert.equal(counts.pending, 1)
  assert.deepEqual(items.map((i) => i.status), ['done', 'done', 'idle'])

  // 停下后再跑：接着把剩下的传完（实际使用中用于「继续」）
  const again = await uploadDirectoryItems({
    items,
    upload: async () => ({ ok: true, assetId: 30, status: 1 })
  })
  assert.deepEqual(again, { uploaded: 1, failed: 0, stopped: false, pending: 0 })
})

test('批量：空清单安全返回', async () => {
  assert.deepEqual(await uploadDirectoryItems({ items: [] }), {
    uploaded: 0, failed: 0, stopped: false, pending: 0
  })
  assert.deepEqual(await uploadDirectoryItems({ items: null }), {
    uploaded: 0, failed: 0, stopped: false, pending: 0
  })
})
