/**
 * 增量 MD5（纯 JS，浏览器没有 WebCrypto MD5）。
 *
 * 为什么需要它：上传前要判断「这个文件是否已经上传过」。后端 `media_asset.md5` 是既有查重口径，
 * 所以前端必须算出同一个 md5 才能命中，不能用 SHA-256（DB 没有这一列）。
 *
 * 大文件（视频 ≤300MB）按块喂给 `update()`，不会一次性把整个文件读进内存。
 * 实现按「块循环 + |0 运算 + 预计算表」写成热路径，避免每轮函数调用/装箱。
 */

/** 每轮左移位数。 */
const SHIFTS = new Int8Array([
  7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
  5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
  4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
  6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21
]);

/** K[i] = floor(abs(sin(i+1)) * 2^32)。 */
const SINES = new Int32Array(64);
for (let i = 0; i < 64; i += 1) {
  SINES[i] = (Math.floor(Math.abs(Math.sin(i + 1)) * 4294967296)) | 0;
}

/** 增量 MD5：update() 可多次调用，最后 digest() 出 32 位小写 hex。 */
export function createMd5() {
  let a = 0x67452301;
  let b = 0xefcdab89 | 0;
  let c = 0x98badcfe | 0;
  let d = 0x10325476;

  const buffer = new Uint8Array(64);
  const words = new Int32Array(16);
  let bufferLen = 0;
  let totalBytes = 0;

  function block(src, offset) {
    for (let i = 0; i < 16; i += 1) {
      const p = offset + i * 4;
      words[i] = src[p] | (src[p + 1] << 8) | (src[p + 2] << 16) | (src[p + 3] << 24);
    }
    let aa = a;
    let bb = b;
    let cc = c;
    let dd = d;
    for (let i = 0; i < 64; i += 1) {
      let f;
      let g;
      if (i < 16) {
        f = (bb & cc) | (~bb & dd);
        g = i;
      } else if (i < 32) {
        f = (dd & bb) | (~dd & cc);
        g = (5 * i + 1) & 15;
      } else if (i < 48) {
        f = bb ^ cc ^ dd;
        g = (3 * i + 5) & 15;
      } else {
        f = cc ^ (bb | ~dd);
        g = (7 * i) & 15;
      }
      const t = (aa + f + SINES[i] + words[g]) | 0;
      const s = SHIFTS[i];
      const rotated = (t << s) | (t >>> (32 - s));
      // 轮换：a←d, d←c, c←b, b←b+rotate(a+f+k+m)
      aa = dd;
      dd = cc;
      cc = bb;
      bb = (bb + rotated) | 0;
    }
    a = (a + aa) | 0;
    b = (b + bb) | 0;
    c = (c + cc) | 0;
    d = (d + dd) | 0;
  }

  function update(bytes) {
    totalBytes += bytes.length;
    let offset = 0;
    if (bufferLen > 0) {
      const need = 64 - bufferLen;
      const take = Math.min(need, bytes.length);
      buffer.set(bytes.subarray(0, take), bufferLen);
      bufferLen += take;
      offset = take;
      if (bufferLen === 64) {
        block(buffer, 0);
        bufferLen = 0;
      }
    }
    while (offset + 64 <= bytes.length) {
      block(bytes, offset);
      offset += 64;
    }
    if (offset < bytes.length) {
      buffer.set(bytes.subarray(offset), 0);
      bufferLen = bytes.length - offset;
    }
    return hasher;
  }

  function digest() {
    const bitLenLow = (totalBytes << 3) | 0;
    const bitLenHigh = Math.floor(totalBytes / 536870912) | 0;
    const padLen = bufferLen < 56 ? 56 - bufferLen : 120 - bufferLen;
    const tail = new Uint8Array(padLen + 8);
    tail[0] = 0x80;
    tail[padLen] = bitLenLow & 0xff;
    tail[padLen + 1] = (bitLenLow >>> 8) & 0xff;
    tail[padLen + 2] = (bitLenLow >>> 16) & 0xff;
    tail[padLen + 3] = (bitLenLow >>> 24) & 0xff;
    tail[padLen + 4] = bitLenHigh & 0xff;
    tail[padLen + 5] = (bitLenHigh >>> 8) & 0xff;
    tail[padLen + 6] = (bitLenHigh >>> 16) & 0xff;
    tail[padLen + 7] = (bitLenHigh >>> 24) & 0xff;
    update(tail);
    const out = [a, b, c, d];
    let hex = '';
    for (let i = 0; i < 4; i += 1) {
      for (let j = 0; j < 4; j += 1) {
        hex += ((out[i] >>> (8 * j)) & 0xff).toString(16).padStart(2, '0');
      }
    }
    return hex;
  }

  const hasher = { update, digest };
  return hasher;
}

/** 一次性算 md5（Uint8Array → hex）。 */
export function md5Hex(bytes) {
  const hasher = createMd5();
  hasher.update(bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes));
  return hasher.digest();
}

/**
 * 浏览器里对流式算文件 md5：按块读取（默认 4MB），大视频不会整包进内存。
 * @param {File|Blob} file
 * @param {{chunkSize?: number, onProgress?: (ratio: number) => void}} [options]
 */
export async function md5OfFile(file, options = {}) {
  const chunkSize = options.chunkSize || 4 * 1024 * 1024;
  const onProgress = options.onProgress;
  const hasher = createMd5();
  const total = Number(file?.size) || 0;
  let offset = 0;
  while (offset < total) {
    const end = Math.min(offset + chunkSize, total);
    const chunk = file.slice(offset, end);
    const buf = await chunk.arrayBuffer();
    hasher.update(new Uint8Array(buf));
    offset = end;
    onProgress?.(total ? offset / total : 1);
  }
  return hasher.digest();
}
