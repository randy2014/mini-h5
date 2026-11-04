/**
 * 媒体帖「标题/类型」的前端缓存（sessionStorage）。
 *
 * 用途：从频道详情点进媒体详情时，先把列表里已知的**视频标题**缓存下来，
 * 详情接口还没回来时导航栏就能显示正确标题，不会先闪一下「图文详情」。
 * 只存轻量元信息（id → { title, type }），最多保留最近 200 条。
 */

const STORAGE_KEY = 'mini_h5_media_meta_cache';
const MAX_ENTRIES = 200;

function safeParse(raw) {
  if (!raw) return {};
  try {
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

function readAll() {
  try {
    return safeParse(sessionStorage.getItem(STORAGE_KEY));
  } catch {
    return {};
  }
}

function writeAll(map) {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(map));
  } catch {
    /* 隐私模式 / 配额满：缓存失败不影响页面 */
  }
}

/** 记住某个媒体帖的标题与类型（进入详情前调用）。 */
export function rememberMediaMeta(postId, meta) {
  const id = Number(postId);
  const title = String(meta?.title || '').trim();
  const type = Number(meta?.type);
  if (!id || !title) return;
  const all = readAll();
  // 先删再插，保证最近使用的排在最后（便于淘汰）
  delete all[String(id)];
  all[String(id)] = { title, type: Number.isFinite(type) ? type : null };
  const keys = Object.keys(all);
  if (keys.length > MAX_ENTRIES) {
    keys.slice(0, keys.length - MAX_ENTRIES).forEach((k) => delete all[k]);
  }
  writeAll(all);
}

/** 读取缓存；没有则返回 null。 */
export function readMediaMeta(postId) {
  const id = Number(postId);
  if (!id) return null;
  const entry = readAll()[String(id)];
  if (!entry || !entry.title) return null;
  return { title: String(entry.title), type: Number.isFinite(Number(entry.type)) ? Number(entry.type) : null };
}
