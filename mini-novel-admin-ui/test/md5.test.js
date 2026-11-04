import test from 'node:test'
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { createMd5, md5Hex, md5OfFile } from '../src/utils/md5.js'

const utf8 = (s) => new TextEncoder().encode(s)
const nodeMd5 = (bytes) => createHash('md5').update(bytes).digest('hex')

test('MD5 标准测试向量', () => {
  assert.equal(md5Hex(utf8('')), 'd41d8cd98f00b204e9800998ecf8427e')
  assert.equal(md5Hex(utf8('a')), '0cc175b9c0f1b6a831c399e269772661')
  assert.equal(md5Hex(utf8('abc')), '900150983cd24fb0d6963f7d28e17f72')
  assert.equal(md5Hex(utf8('message digest')), 'f96b697d7cb7938d525a2f31aaf161d0')
  assert.equal(md5Hex(utf8('The quick brown fox jumps over the lazy dog')), '9e107d9d372bb6826bd81d3542a419d6')
  assert.equal(md5Hex(new Uint8Array([0])), '93b885adfe0da089cdf634904fd59f71')
})

test('MD5 与 node crypto 一致（含跨块边界长度）', () => {
  // 覆盖 0、1、55、56、57、63、64、65、119、128 等块边界
  for (let size = 0; size <= 200; size += 1) {
    const buf = new Uint8Array(size);
    for (let i = 0; i < size; i += 1) buf[i] = (i * 37 + 11) & 0xff
    assert.equal(md5Hex(buf), nodeMd5(buf), `size=${size}`)
  }
})

test('增量 update 与一次性计算结果一致（随机切块）', () => {
  const buf = new Uint8Array(4096);
  for (let i = 0; i < buf.length; i += 1) buf[i] = (i * 131 + 7) & 0xff
  const expected = nodeMd5(buf)
  for (const chunkSize of [1, 7, 63, 64, 65, 1000, 4096]) {
    const hasher = createMd5()
    for (let off = 0; off < buf.length; off += chunkSize) {
      hasher.update(buf.subarray(off, Math.min(off + chunkSize, buf.length)))
    }
    assert.equal(hasher.digest(), expected, `chunkSize=${chunkSize}`)
  }
})

test('md5OfFile 分块读文件结果与 node crypto 一致', async () => {
  const data = new Uint8Array(1024 * 1024 + 123);
  for (let i = 0; i < data.length; i += 1) data[i] = (i * 17 + 3) & 0xff
  const file = new File([data], 'sample.mp4', { type: 'video/mp4' })
  const expected = nodeMd5(data)
  const seen = []
  const got = await md5OfFile(file, { chunkSize: 128 * 1024, onProgress: (r) => seen.push(r) })
  assert.equal(got, expected)
  assert.ok(seen.length >= 8)
  assert.ok(seen.every((r) => r > 0 && r <= 1))
  assert.equal(seen[seen.length - 1], 1)
})

test('md5OfFile 空文件与默认块大小', async () => {
  const empty = new File([], 'empty.bin')
  assert.equal(await md5OfFile(empty), 'd41d8cd98f00b204e9800998ecf8427e')
  assert.equal(await md5OfFile(null), 'd41d8cd98f00b204e9800998ecf8427e')
})
