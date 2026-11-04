import { adminApi } from '../services/http.js';
import { md5OfFileCached } from './fileFingerprint.js';

/**
 * 上传前查重：命中返回后端已有的 READY 素材（可直接复用、跳过整包上传），未命中返回 null。
 *
 * 查重只是「省流量 / 省时间」的优化：算哈希失败、请求失败、对象不存在，一律回退到正常上传，
 * 不影响主流程（服务端本身还有 md5 去重兜底，只是那要在传完之后才知道）。
 */
export async function findExistingAssetByFile(file, { onProgress } = {}) {
  if (!file || !file.size) {
    return null;
  }
  try {
    const md5 = await md5OfFileCached(file, { onProgress });
    const asset = await adminApi.get('/media/assets/by-md5', { params: { md5 } });
    return asset && asset.id ? asset : null;
  } catch {
    return null;
  }
}
