import test from 'node:test'
import assert from 'node:assert/strict'
import { detailNavTitle, shouldShowBodyTitle } from './mediaDetail.js'

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
