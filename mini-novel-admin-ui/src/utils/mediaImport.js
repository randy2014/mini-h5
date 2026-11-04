/**
 * 目录批量上传视频的核心流程：逐条「流式上传」，成功的素材由调用方挂到当前内容，
 * 所有视频共用同一条内容标题（封面取第一个视频）。
 *
 * 抽成纯流程（依赖注入 upload）的原因：
 * - 单条失败不能影响整批（视频转码/网络都不可靠，300MB 传一半断了很常见）；
 * - 这些分支要能用 node --test 覆盖，不必起后端和浏览器。
 */

/**
 * 上传单个视频。返回同一个 item（原地更新 status/percent/reason/assetId）。
 *
 * @param {object}   p
 * @param {object}   p.item        清单项（status 由本函数推进：uploading → done/failed）
 * @param {Function} p.upload      (item, onPercent) => Promise<{ok, assetId, status, reason}>
 * @param {Function} [p.onItemChange] (item) => void  进度回调（组件里用于触发渲染）
 */
export async function uploadDirectoryItem({ item, upload, onItemChange }) {
  item.status = 'uploading';
  item.percent = 0;
  item.reason = '';
  onItemChange?.(item);

  try {
    const r = await upload(item, (percent) => {
      item.percent = Math.max(0, Math.min(99, Number(percent) || 0));
      onItemChange?.(item);
    });
    if (!r || r.ok === false) {
      throw new Error(r?.reason || '上传失败');
    }
    if (!r?.assetId) {
      throw new Error('上传未返回素材 id');
    }
    item.assetId = r.assetId;
    item.assetStatus = r.status ?? null;
    item.status = 'done';
    item.percent = 100;
    return item;
  } catch (e) {
    item.status = 'failed';
    item.reason = e?.message || '上传失败';
    return item;
  } finally {
    onItemChange?.(item);
  }
}

/**
 * 批量上传：只处理 status === 'idle' 的项；shouldStop() 返回 true 时在下一项前停止。
 * 每个上传成功的项通过 onUploaded(item) 回调交给调用方（挂到当前内容）。
 * 返回本轮计数 { uploaded, failed, stopped, pending }。
 */
export async function uploadDirectoryItems({
  items,
  upload,
  shouldStop = () => false,
  onItemChange,
  onUploaded
}) {
  const counts = { uploaded: 0, failed: 0, stopped: false, pending: 0 };
  for (const item of items || []) {
    if (item.status !== 'idle') {
      continue;
    }
    if (shouldStop()) {
      counts.stopped = true;
      break;
    }
    await uploadDirectoryItem({ item, upload, onItemChange });
    if (item.status === 'done') {
      counts.uploaded += 1;
      onUploaded?.(item);
    } else if (item.status === 'failed') {
      counts.failed += 1;
    }
  }
  counts.pending = (items || []).filter((i) => i.status === 'idle').length;
  return counts;
}
