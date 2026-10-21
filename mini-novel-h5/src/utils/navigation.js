/**
 * 阅读器返回层级判定。
 *
 * 背景：阅读器左上角「返回」原本用 router.push 打开书籍详情，会在历史栈里再压入一份详情页，
 * 形成 [频道, 详情, 正文, 详情] 的错乱层级——详情页再点返回就回到了正文。
 *
 * 约定：
 * - 上一页就是「同一本书的详情页」→ 直接 router.back() 退栈（正常从详情进正文的路径）；
 * - 其它入口（阅读历史 / 搜索结果 / 直接打开）→ 用 router.replace 把正文页换成详情页，
 *   避免形成「详情 ↔ 正文」循环。
 */
export function isSameBookDetailUrl(previousUrl, bookId) {
  if (!previousUrl || !bookId) {
    return false;
  }
  const prefix = `/h5/book/${bookId}`;
  return previousUrl === prefix || previousUrl.startsWith(`${prefix}?`);
}
