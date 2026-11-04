import test from 'node:test'
import assert from 'node:assert/strict'
import {
  MAX_IMAGES_PER_POST,
  MAX_VIDEO_BYTES,
  buildDirectoryImportItems,
  coerceMaxCount,
  formatFileSize,
  isImageFile,
  isImageFileName,
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

test('图片扩展名判定：支持 jpg/png/gif/bmp，不支持 webp', () => {
  for (const n of ['a.jpg', 'b.JPEG', 'c.png', 'd.gif', 'e.bmp']) {
    assert.equal(isImageFileName(n), true, n)
  }
  for (const n of ['a.webp', 'b.mp4', 'noext', 'a.', '', null]) {
    assert.equal(isImageFileName(n), false, String(n))
  }
  assert.equal(isImageFile({ name: 'x.webp', type: 'image/webp' }), false)
  assert.equal(isImageFile({ name: 'x.png', type: 'image/png' }), true)
  assert.equal(isImageFile({ name: 'x.bin', type: 'image/gif' }), true)
  assert.equal(isImageFile(null), false)
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
  const { items, skippedOther } = buildDirectoryImportItems([
    { name: '1.mp4', size: 1024, webkitRelativePath: '剧集/1.mp4' },
    { name: '封面.jpg', size: 2048, webkitRelativePath: '剧集/封面.jpg' },
    { name: '2.MKV', size: big, webkitRelativePath: '剧集/子目录/2.MKV' },
    { name: '无扩展名', size: 10, webkitRelativePath: '剧集/无扩展名' }
  ])
  assert.equal(skippedOther, 2)
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

test('图片目录导入：只留图片、忽略视频/webp、按文件名自然排序', () => {
  const { items, skippedOther } = buildDirectoryImportItems([
    { name: '10.jpg', size: 100, webkitRelativePath: '图集/10.jpg' },
    { name: '2.png', size: 100, webkitRelativePath: '图集/2.png' },
    { name: '1.gif', size: 100, webkitRelativePath: '图集/1.gif' },
    { name: 'a.webp', size: 100, webkitRelativePath: '图集/a.webp' },
    { name: 'v.mp4', size: 100, webkitRelativePath: '图集/v.mp4' }
  ], { kind: 'image' })
  assert.equal(skippedOther, 2)
  assert.deepEqual(items.map((i) => i.name), ['1.gif', '2.png', '10.jpg'])
  assert.ok(items.every((i) => i.status === 'idle'))
})

test('图片目录导入：超出单帖上限的部分自动跳过并给原因', () => {
  const files = Array.from({ length: MAX_IMAGES_PER_POST + 5 }, (_, i) => ({
    name: `${String(i + 1).padStart(3, '0')}.jpg`,
    size: 10,
    webkitRelativePath: `图集/${String(i + 1).padStart(3, '0')}.jpg`
  }))
  const { items, overCap } = buildDirectoryImportItems(files, { kind: 'image', maxCount: MAX_IMAGES_PER_POST })
  assert.equal(overCap, 5)
  assert.equal(items.filter((i) => i.status === 'idle').length, MAX_IMAGES_PER_POST)
  const skipped = items.filter((i) => i.status === 'skip')
  assert.equal(skipped.length, 5)
  assert.match(skipped[0].reason, new RegExp(`最多 ${MAX_IMAGES_PER_POST} 张`))
  // 截断发生在排序之后：留下的是文件名靠前的前 100 张
  assert.equal(items[0].name, '001.jpg')
  assert.equal(items[MAX_IMAGES_PER_POST - 1].name, '100.jpg')
})

test('图片目录导入：剩余名额为 0 时全部跳过', () => {
  const { items, overCap } = buildDirectoryImportItems(
    [{ name: '1.jpg', size: 1, webkitRelativePath: 'a/1.jpg' }],
    { kind: 'image', maxCount: 0 }
  )
  assert.equal(overCap, 1)
  assert.equal(items[0].status, 'skip')
})

test('视频目录导入不受图片上限影响（默认不限条数）', () => {
  const files = Array.from({ length: MAX_IMAGES_PER_POST + 3 }, (_, i) => ({
    name: `e${i}.mp4`,
    size: 10,
    webkitRelativePath: `剧/e${i}.mp4`
  }))
  const { items } = buildDirectoryImportItems(files)
  assert.equal(items.filter((i) => i.status === 'idle').length, MAX_IMAGES_PER_POST + 3)
})

test('回归：maxCount 为 null/undefined/空串时视为不限（视频目录不能被当成 0 张上限）', () => {
  const videos = [
    { name: '1.mp4', size: 10, webkitRelativePath: '剧/1.mp4' },
    { name: '2.mp4', size: 10, webkitRelativePath: '剧/2.mp4' }
  ]
  for (const maxCount of [null, undefined, '']) {
    const { items, overCap } = buildDirectoryImportItems(videos, { maxCount })
    assert.equal(overCap, 0, `maxCount=${String(maxCount)}`)
    assert.ok(items.every((i) => i.status === 'idle'), `maxCount=${String(maxCount)} 不应被跳过`)
    assert.ok(items.every((i) => !/上限/.test(i.reason || '')), `maxCount=${String(maxCount)} 不应出现上限原因`)
  }
  // 显式 0 才是「没有名额」
  const zero = buildDirectoryImportItems(videos, { maxCount: 0 })
  assert.equal(zero.overCap, 2)
})

test('coerceMaxCount：只认有效数字，其余视为不限', () => {
  assert.equal(coerceMaxCount(null), null)
  assert.equal(coerceMaxCount(undefined), null)
  assert.equal(coerceMaxCount(''), null)
  assert.equal(coerceMaxCount('abc'), null)
  assert.equal(coerceMaxCount(NaN), null)
  assert.equal(coerceMaxCount(0), 0)
  assert.equal(coerceMaxCount('5'), 5)
  assert.equal(coerceMaxCount(3.7), 3)
  assert.equal(coerceMaxCount(-3), 0)
})

test('空目录选择', () => {
  const r = buildDirectoryImportItems([])
  assert.deepEqual(r, { items: [], skippedOther: 0, overCap: 0 })
  assert.deepEqual(buildDirectoryImportItems(null), { items: [], skippedOther: 0, overCap: 0 })
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
