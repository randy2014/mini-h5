import test from 'node:test'
import assert from 'node:assert/strict'
import {
  MAX_VIDEO_BYTES,
  buildDirectoryImportItems,
  formatFileSize,
  isVideoFile,
  isVideoFileName,
  resolveDraftTitle,
  resolveGroupTitle,
  titleFromFileName
} from '../src/utils/media.js'

test('视频扩展名判定', () => {
  // 与后端 lastIndexOf('.') 判定口径一致：'.mp4' 这种 dotfile 也算视频扩展名命中
  for (const n of ['a.mp4', 'a.MP4', 'b.mov', 'c.mkv', 'd.webm', 'e.avi', 'f.flv', 'g.m4v', '.mp4']) {
    assert.equal(isVideoFileName(n), true, n)
  }
  for (const n of ['a.jpg', 'a.mp3', 'noext', 'a.', '', null, undefined]) {
    assert.equal(isVideoFileName(n), false, String(n))
  }
})

test('MIME 或扩展名命中即视为视频', () => {
  assert.equal(isVideoFile({ name: 'x.MKV', type: '' }), true)
  assert.equal(isVideoFile({ name: 'x.bin', type: 'video/mp4' }), true)
  assert.equal(isVideoFile({ name: 'x.png', type: 'image/png' }), false)
  assert.equal(isVideoFile(null), false)
})

test('标题取文件名：去扩展名、去目录、压空白、超长截断', () => {
  assert.equal(titleFromFileName('第01集.mp4'), '第01集')
  assert.equal(titleFromFileName('第02集.FINAL.MP4'), '第02集.FINAL')
  assert.equal(titleFromFileName('folder/sub/名 称.mkv'), '名 称')
  assert.equal(titleFromFileName('folder\\sub\\win 路径.mov'), 'win 路径')
  assert.equal(titleFromFileName('  a \t b   c .mp4'), 'a b c')
  assert.equal(titleFromFileName('noext'), 'noext')
  assert.equal(titleFromFileName('超'.repeat(200) + '.mp4').length, 120)
  assert.equal(titleFromFileName(''), '未命名视频')
  assert.equal(titleFromFileName(null), '未命名视频')
  assert.equal(titleFromFileName('...'), '未命名视频')
})

test('目录导入清单：只留视频、超限标 skip、标题取文件名', () => {
  const big = MAX_VIDEO_BYTES + 1
  const { items, skippedNonVideo } = buildDirectoryImportItems([
    { name: '1.mp4', size: 1024, webkitRelativePath: '剧集/1.mp4' },
    { name: '封面.jpg', size: 2048, webkitRelativePath: '剧集/封面.jpg' },
    { name: '2.MKV', size: big, webkitRelativePath: '剧集/子目录/2.MKV' },
    { name: '无扩展名', size: 10, webkitRelativePath: '剧集/无扩展名' }
  ])
  assert.equal(skippedNonVideo, 2)
  assert.equal(items.length, 2)
  assert.deepEqual(items.map((i) => [i.title, i.path, i.status]), [
    ['1', '剧集/1.mp4', 'idle'],
    ['2', '剧集/子目录/2.MKV', 'skip']
  ])
  assert.match(items[1].reason, /300MB/)
})

test('目录导入清单按路径自然序排序（第2集在第10集前，第一条=封面）', () => {
  const { items } = buildDirectoryImportItems([
    { name: '第10集.mp4', size: 1, webkitRelativePath: '剧/第10集.mp4' },
    { name: '第2集.mp4', size: 1, webkitRelativePath: '剧/第2集.mp4' },
    { name: '第1集.mp4', size: 1, webkitRelativePath: '剧/第1集.mp4' }
  ])
  assert.deepEqual(items.map((i) => i.title), ['第1集', '第2集', '第10集'])
})

test('空目录选择', () => {
  const r = buildDirectoryImportItems([])
  assert.deepEqual(r, { items: [], skippedNonVideo: 0 })
  assert.deepEqual(buildDirectoryImportItems(null), { items: [], skippedNonVideo: 0 })
})

test('自动保存草稿的标题：已填标题优先，留空取文件名', () => {
  assert.equal(resolveDraftTitle('  我的标题  ', '第01集.mp4'), '我的标题')
  assert.equal(resolveDraftTitle('', '第01集.mp4'), '第01集')
  assert.equal(resolveDraftTitle('   ', '第01集.mp4'), '第01集')
  assert.equal(resolveDraftTitle(null, 'a.b.mkv'), 'a.b')
  assert.equal(resolveDraftTitle(undefined, ''), '未命名视频')
})

test('目录导入标题：已填优先，留空取目录名（目录名不截扩展名）', () => {
  assert.equal(resolveGroupTitle('  剧场版  ', '剧集'), '剧场版')
  assert.equal(resolveGroupTitle('', '短剧.第一季'), '短剧.第一季')
  assert.equal(resolveGroupTitle(null, '  '), '未命名视频')
})

test('体积展示', () => {
  assert.equal(formatFileSize(0), '0 B')
  assert.equal(formatFileSize(512), '512 B')
  assert.equal(formatFileSize(2048), '2 KB')
  assert.equal(formatFileSize(5 * 1024 * 1024), '5.0 MB')
  assert.equal(formatFileSize(1.5 * 1024 * 1024 * 1024), '1.50 GB')
})
