import test from 'node:test'
import assert from 'node:assert/strict'
import { publishDraftsSequentially, splitPublishable } from '../src/utils/mediaPublish.js'

const row = (id, title, notReady) => ({ id, title, _notReady: notReady })

test('批量发布：全部成功', async () => {
  const items = [row(1, 'A', 0), row(2, 'B', 0)]
  const published = []
  const res = await publishDraftsSequentially({
    items,
    publish: async (r) => { published.push(r.id) }
  })
  assert.deepEqual(published, [1, 2])
  assert.deepEqual(res.published.map((r) => r.id), [1, 2])
  assert.deepEqual(res.failures, [])
  assert.equal(res.stopped, false)
  assert.deepEqual(items.map((r) => r._publishState), ['done', 'done'])
})

test('批量发布：单条失败不影响其余，失败原因逐条保留', async () => {
  const items = [row(1, 'A', 0), row(2, 'B', 0), row(3, 'C', 0)]
  const res = await publishDraftsSequentially({
    items,
    publish: async (r) => {
      if (r.id === 2) throw new Error('素材未就绪，暂不能发布（第01集.mp4 转码中或失败）')
    }
  })
  assert.deepEqual(res.published.map((r) => r.id), [1, 3])
  assert.deepEqual(res.failures, [{
    id: 2,
    title: 'B',
    reason: '素材未就绪，暂不能发布（第01集.mp4 转码中或失败）'
  }])
  assert.equal(items[1]._publishState, 'failed')
})

test('批量发布：非 Error 抛出也能记原因；缺标题用 #id 兜底', async () => {
  const items = [{ id: 9, _notReady: 0 }]
  const res = await publishDraftsSequentially({
    items,
    publish: async () => { throw '网络异常' } // eslint-disable-line no-throw-literal
  })
  assert.equal(res.failures.length, 1)
  assert.equal(res.failures[0].title, '#9')
  assert.equal(res.failures[0].reason, '发布失败')
})

test('批量发布：shouldStop 在当前条结束后停止', async () => {
  const items = [row(1, 'A', 0), row(2, 'B', 0), row(3, 'C', 0)]
  let n = 0
  const res = await publishDraftsSequentially({
    items,
    publish: async () => { n += 1 },
    shouldStop: () => n >= 2
  })
  assert.equal(res.published.length, 2)
  assert.equal(res.stopped, true)
  assert.equal(items[2]._publishState, undefined)
})

test('批量发布：空选择安全返回', async () => {
  assert.deepEqual(await publishDraftsSequentially({ items: [] }), {
    published: [], failures: [], stopped: false
  })
  assert.deepEqual(await publishDraftsSequentially({ items: null }), {
    published: [], failures: [], stopped: false
  })
})

test('勾选拆分：素材未就绪的跳过，未知状态交给后端校验，空行忽略', () => {
  const { publishable, skipped } = splitPublishable([
    row(1, '可发布', 0),
    row(2, '转码中', 1),
    row(3, '转码失败', 2),
    { id: 4, title: '状态未知' },
    null
  ])
  assert.deepEqual(publishable.map((r) => r.id), [1, 4])
  assert.deepEqual(skipped.map((r) => r.id), [2, 3])
  assert.deepEqual(splitPublishable([]), { publishable: [], skipped: [] })
})
