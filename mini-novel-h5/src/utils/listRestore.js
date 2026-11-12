/**
 * 列表页「返回定位」公共能力（2026-11 全站横展）。
 *
 * 背景：列表页（订阅频道详情、首页、分类、榜单、搜索、书架、VIP 专区…）下拉分页后点进
 * 小说 / 图文详情，返回时组件重新挂载、列表重新拉第一页 —— 滚动位置和已加载分页全部丢失，
 * 用户被弹回顶部；但列表又必须是「最新数据」（频道可能刚发布内容、已读标记也可能变化）。
 *
 * 做法：
 *  1. 离开列表页时（onBeforeUnmount，DOM 还在文档里）把「渲染快照 + 滚动位置 + 视口锚点」
 *     按 key 存进内存（key 就是页面实例，如 subscribe-channel:12 / rank:hot / search）。
 *  2. 返回同一页时（history.state.forward 存在 = 当前历史条目不是栈顶 = 是「退回来」的，
 *     且快照 path 与当前地址一致）用快照同步渲染：页面高度与离开时一致，滚动能精确落回，
 *     不会先闪一下顶部再跳。
 *  3. 随后后台拉最新数据覆盖列表；覆盖前后用「视口第一条列表项」做锚点校正滚动，
 *     避免顶部新增内容把用户正在看的内容顶走。
 *  4. 快照带 TTL 与账号标记：过期或换账号直接丢弃，不会把上一个人的列表交给新用户。
 *
 * 这里只放纯逻辑（可在 node --test 里跑）；Vue 生命周期见 composables/useListRestore.js。
 */

/** 快照有效期：超过 10 分钟按过期处理，返回时按新页面加载最新数据。 */
export const DEFAULT_TTL = 10 * 60 * 1000;

/** 最多保留的快照数（Map 插入序 = 最近使用序，超出淘汰最旧的）。 */
export const MAX_SNAPSHOTS = 24;

/** 列表项锚点属性名（页面模板里的 data-list-key 与此一致）。 */
export const LIST_ANCHOR_ATTR = 'data-list-key';

/** 判定「已滚出视口」的容差（px）。 */
const VISIBLE_EDGE = 4;

/** key -> { path, account, scrollTop, anchor, state, at } */
const snapshots = new Map();

/** 保存快照；重复 key 覆盖（并刷新为最近使用）。 */
export function saveSnapshot(key, snapshot, now = Date.now()) {
  if (!key) {
    return null;
  }
  const entry = { ...snapshot, at: now };
  snapshots.delete(key);
  snapshots.set(key, entry);
  while (snapshots.size > MAX_SNAPSHOTS) {
    const oldest = snapshots.keys().next();
    if (oldest.done) {
      break;
    }
    snapshots.delete(oldest.value);
  }
  return entry;
}

/** 快照是否还在有效期内。 */
export function isSnapshotFresh(entry, now = Date.now(), ttl = DEFAULT_TTL) {
  if (!entry) {
    return false;
  }
  const at = Number(entry.at);
  if (!Number.isFinite(at)) {
    return false;
  }
  const age = Math.max(0, now - at);
  return age <= Math.max(0, Number(ttl) || 0);
}

/**
 * 读取快照（含 TTL / 账号 / 路径校验，任一不符即丢弃并返回 null）。
 * path 传当前地址；account 传当前登录用户标识。
 */
export function readSnapshot(key, options = {}) {
  const { ttl = DEFAULT_TTL, now = Date.now(), account, path } = options;
  const entry = snapshots.get(key);
  if (!entry) {
    return null;
  }
  if (!isSnapshotFresh(entry, now, ttl)) {
    snapshots.delete(key);
    return null;
  }
  if (account !== undefined && String(entry.account ?? '') !== String(account ?? '')) {
    snapshots.delete(key);
    return null;
  }
  if (path !== undefined && normalizePath(entry.path) !== normalizePath(path)) {
    return null;
  }
  return entry;
}

export function dropSnapshot(key) {
  snapshots.delete(key);
}

export function clearSnapshots() {
  snapshots.clear();
}

export function snapshotCount() {
  return snapshots.size;
}

/** 地址去掉 hash 后比较（列表页 query 变化不影响恢复）。 */
export function normalizePath(path) {
  return String(path || '').split('#')[0];
}

/**
 * 当前历史条目后面是否还有一条（vue-router 的 state.forward）。
 * 有 forward ⇒ 这次是从后面的页面退回来的（而不是 push 进来的新页面）。
 */
export function hasForwardEntry(historyState) {
  const state = historyState !== undefined ? historyState : globalThis.window?.history?.state;
  return Boolean(state && state.forward);
}

/** 快照可用且是「退回来」的，才恢复。 */
export function shouldRestore(snapshot, hasForward) {
  return Boolean(snapshot) && Boolean(hasForward);
}

/**
 * 取视口内第一条列表项作为锚点：返回 { key, top }（top 为相对视口顶部的偏移）。
 * 只读 DOM，不改滚动；没有锚点（列表为空 / 非浏览器环境）返回 null。
 */
export function captureAnchor(attr = LIST_ANCHOR_ATTR, doc = globalThis.document) {
  if (!doc || typeof doc.querySelectorAll !== 'function') {
    return null;
  }
  const nodes = doc.querySelectorAll(`[${attr}]`);
  for (const node of nodes) {
    const rect = typeof node?.getBoundingClientRect === 'function' ? node.getBoundingClientRect() : null;
    if (!rect || rect.bottom <= VISIBLE_EDGE) {
      continue;
    }
    const key = typeof node.getAttribute === 'function' ? node.getAttribute(attr) : '';
    if (!key) {
      continue;
    }
    return { key, top: rect.top };
  }
  return null;
}

/**
 * 数据刷新后按锚点校正滚动：让同一个列表项回到刷新前的视口位置。
 * 锚点元素已不存在（内容被删/被过滤）时保持当前滚动不动，返回 0。
 */
export function applyAnchor(anchor, attr = LIST_ANCHOR_ATTR, options = {}) {
  const { doc = globalThis.document, view = globalThis.window } = options;
  if (!anchor || !anchor.key || !doc || typeof doc.querySelector !== 'function') {
    return 0;
  }
  const selector = `[${attr}="${escapeAttrValue(anchor.key)}"]`;
  const node = doc.querySelector(selector);
  if (!node || typeof node.getBoundingClientRect !== 'function') {
    return 0;
  }
  const delta = Math.round(node.getBoundingClientRect().top - Number(anchor.top || 0));
  if (Math.abs(delta) < 1 || !view || typeof view.scrollBy !== 'function') {
    return 0;
  }
  view.scrollBy(0, delta);
  return delta;
}

/** 回到指定滚动位置（快照渲染完成后再调用）。 */
export function restoreScroll(top, view = globalThis.window) {
  if (!view || typeof view.scrollTo !== 'function') {
    return false;
  }
  const target = Number(top);
  view.scrollTo(0, Number.isFinite(target) ? Math.max(0, target) : 0);
  return true;
}

function escapeAttrValue(value) {
  return String(value).replace(/\\/g, '\\\\').replace(/"/g, '\\"');
}
