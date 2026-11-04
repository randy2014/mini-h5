import test from 'node:test'
import assert from 'node:assert/strict'
import { detailNavTitle, normalizeMediaDetail, shouldShowBodyTitle } from './mediaDetail.js'

test('normalizeMediaDetail：后端嵌套结构 {post, assets, cover} 拍平', () => {
  const data = {
    post: { id: 30, type: 2, title: '高三爱情故事', channelId: 5 },
    assets: [{ id: 1, fileType: 2 }],
    cover: { id: 1, fileType: 2 }
  }
  const flat = normalizeMediaDetail(data)
  assert.equal(flat.title, '高三爱情故事')
  assert.equal(flat.type, 2)
  assert.equal(flat.assets.length, 1)
  assert.equal(flat.assets[0].id, 1)
  assert.equal(flat.cover.id, 1)
  assert.equal(detailNavTitle(flat), '高三爱情故事')
})

test('normalizeMediaDetail：已经是扁平结构时保持不变', () => {
  const flat = normalizeMediaDetail({ id: 1, type: 1, title: '图集', assets: [{ id: 9, fileType: 1 }] })
  assert.equal(flat.title, '图集')
  assert.equal(flat.assets.length, 1)
  assert.equal(detailNavTitle(flat), '图文详情')
})

test('normalizeMediaDetail：缺数据/空结构兜底', () => {
  assert.equal(normalizeMediaDetail(null), null)
  const empty = normalizeMediaDetail({ post: { id: 1 } })
  assert.deepEqual(empty.assets, [])
  assert.equal(empty.cover, null)
})

test('视频类详情页导航标题 = 内容标题', () => {
  assert.equal(detailNavTitle({ type: 2, title: '高三爱情故事第8集' }), '高三爱情故事第8集')
  assert.equal(detailNavTitle({ type: 3, title: '花絮合集' }), '花絮合集')
  assert.equal(detailNavTitle({ type: 2, title: '  第一集  ' }), '第一集')
})

test('纯图文 / 缺数据时退回类型文案', () => {
  assert.equal(detailNavTitle({ type: 1, title: '某图集' }), '图文详情')
  assert.equal(detailNavTitle({ type: 2, title: '' }), '视频详情')
  assert.equal(detailNavTitle({ type: 3, title: '   ' }), '图文+视频')
  assert.equal(detailNavTitle(null), '图文详情')
})

test('视频类不在正文重复大标题，图文仍在正文显示', () => {
  assert.equal(shouldShowBodyTitle({ type: 2, title: '第一集' }), false)
  assert.equal(shouldShowBodyTitle({ type: 3, title: '合集' }), false)
  assert.equal(shouldShowBodyTitle({ type: 1, title: '某图集' }), true)
  assert.equal(shouldShowBodyTitle({ type: 2, title: '' }), true)
})
