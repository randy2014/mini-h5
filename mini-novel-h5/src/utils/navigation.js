/**
 * H5 统一导航辅助：保证「返回」始终回到上一层，且直接打开/刷新进入时不会退无可退。
 *
 * 背景：阅读器左上角返回曾用 router.push 打开书籍详情，在历史栈里压出重复的详情页，
 * 详情页再返回就弹回正文；同类「用 push/replace 打开上一层页面」的写法在其它页面也会
 * 造成层级错乱或重复页。这里统一收口，所有页面返回都走 goBack / goBackTo。
 */

/** 当前历史栈里的上一页完整路径；直接打开 / 刷新 / 外部链接进入时为 ''。 */
export function previousEntry() {
  if (typeof window === 'undefined' || !window.history || !window.history.state) {
    return '';
  }
  return window.history.state.back || '';
}

/** 上一页是否就是这个路径（忽略 query）。 */
export function isSamePath(previousUrl, path) {
  if (!previousUrl || !path) {
    return false;
  }
  const base = String(path).split('?')[0];
  return previousUrl === base || previousUrl.startsWith(`${base}?`);
}

/** 上一页是否就是这本书的详情页（/h5/book/{id}，带不带 query 都算）。 */
export function isSameBookDetailUrl(previousUrl, bookId) {
  if (!bookId) {
    return false;
  }
  return isSamePath(previousUrl, `/h5/book/${bookId}`);
}

/**
 * 统一「返回上一层」：
 * - 有站内上一页 → router.back()（正常返回上一层）；
 * - 没有（直接打开 / 刷新 / 外链）→ router.replace(fallbackPath)，避免退出应用或停在本页。
 */
export function goBack(router, fallbackPath) {
  if (!router) {
    return;
  }
  if (previousEntry()) {
    router.back();
    return;
  }
  if (fallbackPath) {
    router.replace(fallbackPath);
    return;
  }
  router.back();
}

/**
 * 返回到指定页面：
 * - 上一页就是它 → router.back() 退栈（不再压一份重复页面）；
 * - 否则 router.replace(path)，把当前页替换掉，避免形成「A ↔ B」循环。
 */
export function goBackTo(router, path) {
  if (!router || !path) {
    return;
  }
  if (isSamePath(previousEntry(), path)) {
    router.back();
    return;
  }
  router.replace(path);
}
