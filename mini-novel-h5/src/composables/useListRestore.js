import { nextTick, onBeforeUnmount, onMounted } from 'vue';
import { onBeforeRouteLeave, useRoute } from 'vue-router';
import { USER_ID_KEY } from '../services/authStorage.js';
import {
  LIST_ANCHOR_ATTR,
  applyAnchor,
  captureAnchor,
  hasForwardEntry,
  readSnapshot,
  restoreScroll,
  saveSnapshot
} from '../utils/listRestore.js';

/**
 * 列表页「返回定位」：进入详情前记住位置与已加载分页，返回时先用快照渲染、再静默拉最新数据。
 *
 * 用法（页面 setup 内调用一次，替代原来的 onMounted(load)）：
 *   useListRestore({
 *     key: `subscribe-channel:${channelId}`,
 *     capture: () => ({ feed: [...feed.value], page: page.value, ... }),
 *     restore: (state) => { feed.value = state.feed; ...; return true; },
 *     load
 *   });
 *
 * - key      同一列表实例的固定标识（含频道 id / 榜单类型这类维度），也可传函数在离开时求值；
 * - capture  离开时把「渲染所需状态」抓成可还原的普通对象（数组记得浅拷贝）；
 * - restore  用快照恢复响应式状态；返回 false 表示快照不可用，按新页面加载；
 * - load     拉最新数据并覆盖列表，签名 load({ silent })，silent=true 表示不要展示整页 loading；
 * - anchorAttr 列表项锚点属性名，默认 data-list-key。
 *
 * 返回 { restored, refreshInPlace }，页面一般不用处理。
 */
export function useListRestore(options = {}) {
  const { key, capture, restore, load, anchorAttr = LIST_ANCHOR_ATTR, ttl } = options;
  const route = useRoute();
  const keyOf = () => (typeof key === 'function' ? key() : key);

  // 只有「退回来」（当前条目不是历史栈顶）且快照是同一地址、同一账号、未过期时才恢复
  const snapshot = hasForwardEntry()
    ? readSnapshot(keyOf(), { ttl, account: currentAccount(), path: route.fullPath })
    : null;
  const restored = Boolean(snapshot) && restoreSnapshot(snapshot) !== false;
  // 静默刷新是异步的：期间用户可能已经点进详情页，卸载后不要再动新页面的滚动
  let disposed = false;
  function restoreSnapshot(entry) {
    if (!restore) {
      return undefined;
    }
    try {
      return restore(entry.state);
    } catch (error) {
      // 快照结构变化（升级/字段缺失）时不要让页面挂掉，退化成普通加载
      console.warn('[listRestore] 快照恢复失败，按新页面加载', error);
      return false;
    }
  }

  onMounted(async () => {
    if (!restored) {
      await safeLoad();
      return;
    }
    // 快照已同步渲染出列表高度，先落回离开时的滚动位置，再后台换成最新数据
    await nextTick();
    restoreScroll(snapshot.scrollTop || 0);
    if (snapshot.anchor) {
      // 离开时点过的那条可能已经不在列表里（如小说被标记已读隐藏）：锚点找不到就保留像素位置
      applyAnchor(snapshot.anchor, anchorAttr);
    }
    if (load) {
      await refreshInPlace();
    }
  });

  /** 拉最新数据；刷新前后用锚点校正滚动，避免顶部新增内容把当前视图顶走。 */
  async function refreshInPlace() {
    const anchor = captureAnchor(anchorAttr);
    await safeLoad({ silent: true });
    if (disposed) {
      return;
    }
    await nextTick();
    if (disposed) {
      return;
    }
    applyAnchor(anchor, anchorAttr);
  }

  async function safeLoad(options) {
    try {
      await load(options);
    } catch (error) {
      // load 内部一般已处理错误（toast/错误块）；这里兜住未捕获异常，避免 onMounted 报未处理拒绝
      console.warn('[listRestore] 列表加载失败', error);
    }
  }

  /**
   * 保存快照：只能在这里（onBeforeRouteLeave）存 —— 离开守卫里 route 仍指向本页、DOM 与
   * 滚动位置都还在。不要放到 onBeforeUnmount 兜底：目标页是懒加载时，卸载可能比路由守卫
   * 晚很多，那时 currentRoute 已指向详情页，会把 path 存错（快照返不回来）。
   */
  function persistSnapshot() {
    const snapshotKey = keyOf();
    if (!snapshotKey || !capture) {
      return;
    }
    saveSnapshot(snapshotKey, {
      path: route.fullPath,
      account: currentAccount(),
      scrollTop: typeof window === 'undefined' ? 0 : window.scrollY || 0,
      anchor: captureAnchor(anchorAttr),
      state: capture()
    });
  }

  onBeforeRouteLeave(() => {
    persistSnapshot();
  });

  // 卸载后不再动新页面的滚动（静默刷新可能还在飞）
  onBeforeUnmount(() => {
    disposed = true;
  });

  return { restored, refreshInPlace };
}

/** 账号标识：换登录用户后不复用上一个人的列表快照。 */
function currentAccount() {
  try {
    return String(localStorage.getItem(USER_ID_KEY) || '');
  } catch {
    return '';
  }
}
