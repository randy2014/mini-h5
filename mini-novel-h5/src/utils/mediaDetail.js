/**
 * 媒体详情页展示口径（纯函数，便于 node --test 单测）。
 */

/**
 * 导航栏标题：
 * - **视频类**（VIDEO / MIXED）→ 动态显示内容标题（即「视频标题」）；
 * - 纯图文 → 保留「图文详情」；
 * - 异常/缺数据时退回类型文案，避免空白标题。
 */
export function detailNavTitle(detail) {
  const type = detail?.type;
  const title = String(detail?.title || '').trim();
  const isVideoLike = type === 2 || type === 3;
  if (isVideoLike && title) {
    return title;
  }
  if (type === 3) return '图文+视频';
  if (type === 2) return '视频详情';
  return '图文详情';
}

/**
 * 正文里是否还要再显示一遍大标题：
 * 视频类标题已经在导航栏显示，正文不重复；图文仍用正文大标题。
 */
export function shouldShowBodyTitle(detail) {
  return detailNavTitle(detail) !== String(detail?.title || '').trim();
}
