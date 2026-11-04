/**
 * 目录批量导入视频的核心流程：逐条「流式上传 → 建一条草稿」。
 *
 * 抽成纯流程（依赖注入 upload / createPost / removeAsset）的原因：
 * - 单条失败不能影响整批（视频转码/网络都不可靠，300MB 传一半断了很常见）；
 * - 上传成功但建帖失败时，必须把素材回收掉，否则磁盘留孤儿；
 * - 这些分支要能用 node --test 覆盖，不必起后端和浏览器。
 */

/**
 * 导入单个视频。返回同一个 item（原地更新 status/percent/reason/postId）。
 *
 * @param {object}   p
 * @param {object}   p.item        清单项（status 由本函数推进：uploading → creating → done/failed）
 * @param {Function} p.upload      (item, onPercent) => Promise<{ok, assetId, status, reason}>
 * @param {Function} p.createPost  (item) => Promise<{id}>
 * @param {Function} [p.removeAsset] (assetId) => Promise<any>  建帖失败时回收素材
 * @param {Function} [p.onItemChange] (item) => void  进度回调（组件里用于触发渲染）
 */
export async function importDirectoryItem({ item, upload, createPost, removeAsset, onItemChange }) {
  item.status = 'uploading';
  item.percent = 0;
  item.reason = '';
  onItemChange?.(item);

  let assetId = null;
  try {
    const r = await upload(item, (percent) => {
      item.percent = Math.max(0, Math.min(99, Number(percent) || 0));
      onItemChange?.(item);
    });
    if (!r || r.ok === false) {
      throw new Error(r?.reason || '上传失败');
    }
    assetId = r.assetId ?? null;
    if (!assetId) {
      throw new Error('上传未返回素材 id');
    }
    item.assetId = assetId;
    item.assetStatus = r.status ?? null;
    item.status = 'creating';
    item.percent = 100;
    onItemChange?.(item);

    const post = await createPost(item);
    item.postId = post?.id ?? null;
    item.status = 'done';
    return item;
  } catch (e) {
    item.status = 'failed';
    item.reason = e?.message || '导入失败';
    // 内容没建成 → 回收刚上传的素材，别在磁盘上留孤儿
    // （素材若已被别的内容引用，后端会拒绝删除，这里静默忽略，清理任务兜底）
    if (assetId && !item.postId && removeAsset) {
      try {
        await removeAsset(assetId);
      } catch { /* 忽略 */ }
    }
    return item;
  } finally {
    onItemChange?.(item);
  }
}

/**
 * 批量导入：只处理 status === 'idle' 的项；shouldStop() 返回 true 时在下一项前停止。
 * 返回本轮计数 { created, failed, stopped, pending }。
 */
export async function importDirectoryItems({
  items,
  upload,
  createPost,
  removeAsset,
  shouldStop = () => false,
  onItemChange
}) {
  const counts = { created: 0, failed: 0, stopped: false, pending: 0 };
  for (const item of items || []) {
    if (item.status !== 'idle') {
      continue;
    }
    if (shouldStop()) {
      counts.stopped = true;
      break;
    }
    await importDirectoryItem({ item, upload, createPost, removeAsset, onItemChange });
    if (item.status === 'done') {
      counts.created += 1;
    } else if (item.status === 'failed') {
      counts.failed += 1;
    }
  }
  counts.pending = (items || []).filter((i) => i.status === 'idle').length;
  return counts;
}
