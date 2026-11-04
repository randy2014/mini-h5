/**
 * 多媒体池子公共逻辑（纯函数，便于 node --test 单测）。
 *
 * 口径与后端保持一致：
 * - 视频扩展名白名单 = AdminMediaAssetController.VIDEO_EXT；
 * - 单视频上限 300MB = upload-stream / nginx / multipart 三方同口径；
 * - 标题上限 120 字 = MediaPostServiceImpl.requireTitle。
 */

/** 与后端 VIDEO_EXT 对齐（小写）。 */
export const VIDEO_EXTENSIONS = ['mp4', 'mov', 'mkv', 'webm', 'avi', 'flv', 'm4v'];

/** 单文件 300MB 上限。 */
export const MAX_VIDEO_BYTES = 300 * 1024 * 1024;

/** 标题最长 120 字（后端硬校验）。 */
export const MAX_TITLE_CHARS = 120;

/** 文件名是否为视频（按扩展名；不看 MIME，mkv 等 MIME 常为空）。 */
export function isVideoFileName(name) {
  const n = String(name || '');
  const dot = n.lastIndexOf('.');
  if (dot < 0 || dot === n.length - 1) return false;
  return VIDEO_EXTENSIONS.includes(n.slice(dot + 1).toLowerCase());
}

/** 浏览器 File 是否按视频处理：MIME 命中或扩展名命中，二者其一即可。 */
export function isVideoFile(file) {
  if (!file) return false;
  if ((file.type || '').startsWith('video/')) return true;
  return isVideoFileName(file.name);
}

/**
 * 标题取文件名：
 * - 去掉目录部分与最后一个扩展名（`第01集.mp4` → `第01集`，`a.b.mkv` → `a.b`）；
 * - 连续空白压缩成单个空格并 trim（文件名里的制表符/换行不会带进标题）；
 * - 超 120 字截断；取不到有效内容时兜底「未命名视频」。
 */
export function titleFromFileName(name, maxLen = MAX_TITLE_CHARS) {
  let base = String(name == null ? '' : name);
  const slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
  if (slash >= 0) {
    base = base.slice(slash + 1);
  }
  const dot = base.lastIndexOf('.');
  if (dot > 0) {
    base = base.slice(0, dot);
  }
  base = base.replace(/\s+/g, ' ').trim();
  if (!base || base === '.' || base === '..') {
    base = '未命名视频';
  }
  return base.length > maxLen ? base.slice(0, maxLen) : base;
}

/**
 * 自动保存草稿时的标题口径：运营已填标题优先，留空则取视频文件名（去扩展名）。
 * 目的：视频上传后立刻能存成草稿，不因为标题未填而把素材悬在编辑器里。
 */
export function resolveDraftTitle(userTitle, videoFileName) {
  const typed = String(userTitle == null ? '' : userTitle).trim();
  return typed || titleFromFileName(videoFileName);
}

/** 人类可读体积（列表/汇总展示用）。 */export function formatFileSize(bytes) {
  const n = Number(bytes) || 0;
  if (n <= 0) return '0 B';
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)} KB`;
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} MB`;
  return `${(n / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

/**
 * 把「选择目录」拿到的文件列表整理成可导入清单。
 *
 * 规则：
 * - 非视频文件直接忽略（计入 skippedNonVideo），不报错、不中断整批；
 * - 单个视频 >300MB 标记为 skip 并给出原因（该条不导入，其余照常）；
 * - 其余标记 idle，等待逐条「上传 → 建草稿」。
 *
 * 纯函数：只读 name / size / webkitRelativePath，单测可直接传普通对象。
 */
export function buildDirectoryImportItems(files) {
  const items = [];
  let skippedNonVideo = 0;
  for (const f of files || []) {
    if (!isVideoFile(f)) {
      skippedNonVideo += 1;
      continue;
    }
    const name = String(f.name || '');
    const size = Number(f.size) || 0;
    const oversize = size > MAX_VIDEO_BYTES;
    items.push({
      file: f,
      path: String(f.webkitRelativePath || name),
      name,
      title: titleFromFileName(name),
      size,
      status: oversize ? 'skip' : 'idle',
      percent: 0,
      reason: oversize ? '超过 300MB 上限，未导入' : '',
      assetId: null,
      assetStatus: null,
      postId: null
    });
  }
  return { items, skippedNonVideo };
}
