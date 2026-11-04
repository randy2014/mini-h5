/**
 * 多媒体池子「批量发布」的纯逻辑（便于 node --test 单测）：
 * 一条内容仍然只挂**一个**频道，批量发布只是把勾选的多条内容逐条发到**同一个**频道。
 *
 * 单独拆出来的原因：
 * - 单条失败不能影响整批（有的素材还在转码/已发布/被频道校验拒绝，都只影响它自己）；
 * - 失败原因要逐条回传给运营，而不是笼统报一个「批量发布失败」。
 */

/**
 * 逐条发布。返回 { published: 成功的内容[], failures: [{ id, title, reason }], stopped }。
 *
 * @param {object}   p
 * @param {Array}    p.items        勾选的内容（当前页草稿行）
 * @param {Function} p.publish      (item) => Promise<any>  调后端发布接口
 * @param {Function} [p.onItemChange] (item) => void  每处理完一条回调（进度展示用）
 * @param {Function} [p.shouldStop]  () => boolean  返回 true 时在下一项前停止
 */
export async function publishDraftsSequentially({ items, publish, onItemChange, shouldStop = () => false }) {
  const results = { published: [], failures: [], stopped: false };
  for (const item of items || []) {
    if (shouldStop()) {
      results.stopped = true;
      break;
    }
    try {
      await publish(item);
      item._publishState = 'done';
      item._publishError = '';
      results.published.push(item);
    } catch (e) {
      item._publishState = 'failed';
      item._publishError = e?.message || '发布失败';
      results.failures.push({
        id: item?.id,
        title: item?.title || `#${item?.id}`,
        reason: item._publishError
      });
    }
    onItemChange?.(item);
  }
  return results;
}

/**
 * 拆出「可直接发布」与「需跳过」：
 * - 素材有转码中/失败的（`_notReady > 0`）跳过，发布接口也不会让过，提前拦掉少一次失败请求；
 * - `_notReady` 缺失（列表装饰请求失败等未知状态）时按「可发布」处理，交给后端校验。
 */
export function splitPublishable(rows) {
  const publishable = [];
  const skipped = [];
  for (const row of rows || []) {
    if (!row) {
      continue;
    }
    if (Number(row._notReady) > 0) {
      skipped.push(row);
    } else {
      publishable.push(row);
    }
  }
  return { publishable, skipped };
}
