import test from 'node:test'
import assert from 'node:assert/strict'
import { BROWSER_BAR_KEYWORDS, looksLikeBrowserVideoBar } from './browserOverlay.js'

test('命中：悬浮 + 高 z-index + 含「下载/存网盘」', () => {
  assert.equal(looksLikeBrowserVideoBar({ text: '下载', position: 'fixed', zIndex: '2147483647' }), true)
  assert.equal(looksLikeBrowserVideoBar({ text: '存网盘', position: 'absolute', zIndex: '9999' }), true)
  assert.equal(looksLikeBrowserVideoBar({ text: '分享 投屏 倍速', position: 'sticky', zIndex: '1000' }), true)
})

test('不误伤：自家 UI、普通正文、低层级、非悬浮', () => {
  // 我们自家的 Vant 弹层/提示一律不动
  assert.equal(looksLikeBrowserVideoBar({ text: '下载', position: 'fixed', zIndex: '9999', protectedUi: true }), false)
  // 页面正文里出现「下载」二字（不是悬浮条）
  assert.equal(looksLikeBrowserVideoBar({ text: '点击下载客户端', position: 'static', zIndex: 'auto' }), false)
  // 悬浮但层级低（普通内容区）
  assert.equal(looksLikeBrowserVideoBar({ text: '下载', position: 'absolute', zIndex: '10' }), false)
  // 悬浮高但没有关键词
  assert.equal(looksLikeBrowserVideoBar({ text: '视频 1 / 3', position: 'absolute', zIndex: '9999' }), false)
  // 空内容
  assert.equal(looksLikeBrowserVideoBar({ text: '   ', position: 'fixed', zIndex: '9999' }), false)
  assert.equal(looksLikeBrowserVideoBar(), false)
})

test('关键词表覆盖需求里提到的入口', () => {
  assert.ok(BROWSER_BAR_KEYWORDS.includes('下载'))
  assert.ok(BROWSER_BAR_KEYWORDS.includes('存网盘'))
  assert.ok(BROWSER_BAR_KEYWORDS.includes('分享'))
})
