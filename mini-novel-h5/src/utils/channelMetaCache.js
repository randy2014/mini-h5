/**
 * 订阅频道「名称」的前端缓存（sessionStorage）。
 *
 * 背景：频道详情页的标题此前恒为「频道详情」——后端 feed 接口不下发频道名，页面里
 * channelName 也从未被赋值。现在从频道列表进入时先把已知名称存下来，详情页首帧就能
 * 显示正确标题；直接打开/刷新时再按需拉一次频道列表兜底。
 * 只存轻量映射（channelId → name），最多保留最近 100 条。
 */

const STORAGE_KEY = 'mini_h5_channel_meta_cache';
const MAX_ENTRIES = 100;

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

/** 记住频道名称（频道列表拿到数据时、或详情页兜底查询命中时调用）。 */
export function rememberChannelName(channelId, name) {
  const id = Number(channelId);
  const text = String(name || '').trim();
  if (!id || !text) return;
  const all = readAll();
  // 先删再插，保证最近使用的排在最后（便于淘汰）
  delete all[String(id)];
  all[String(id)] = text;
  const keys = Object.keys(all);
  if (keys.length > MAX_ENTRIES) {
    keys.slice(0, keys.length - MAX_ENTRIES).forEach((k) => delete all[k]);
  }
  writeAll(all);
}

/** 读取频道名称；没有则返回空串。 */
export function readChannelName(channelId) {
  const id = Number(channelId);
  if (!id) return '';
  const name = readAll()[String(id)];
  return typeof name === 'string' ? name.trim() : '';
}
