import { md5OfFile } from './md5.js';

/**
 * 文件指纹（md5）与其 localStorage 缓存。
 *
 * 目的：上传前判断「这个文件是否已经上传过」时，重复导入同一个目录不必重算大文件哈希。
 * 纯本地逻辑、不依赖 axios，便于 node --test 单测（http 调用放在 uploadDedupe.js）。
 */

const CACHE_PREFIX = 'mini_admin_file_md5:';

function cacheKey(file) {
  return `${CACHE_PREFIX}${file.name}|${file.size}|${file.lastModified || 0}`;
}

function readCachedMd5(file) {
  try {
    return localStorage.getItem(cacheKey(file)) || '';
  } catch {
    return '';
  }
}

function writeCachedMd5(file, md5) {
  try {
    localStorage.setItem(cacheKey(file), md5);
  } catch {
    /* 隐私模式 / 配额满：忽略缓存即可 */
  }
}

/**
 * 算文件 md5：优先用 localStorage 缓存（同名 + 同大小 + 同修改时间视为同一文件）。
 * 目录重复导入时直接命中缓存，不再读文件/重算。
 */
export async function md5OfFileCached(file, { onProgress } = {}) {
  const cached = readCachedMd5(file);
  if (cached) {
    onProgress?.(1);
    return cached;
  }
  const md5 = await md5OfFile(file, { onProgress });
  writeCachedMd5(file, md5);
  return md5;
}
