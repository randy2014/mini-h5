<template>
  <div class="media-pool">
    <!-- 顶部：Tab 切换 + 新建入口 -->
    <el-card shadow="never" class="pool-card">
      <div class="pool-toolbar">
        <el-radio-group v-model="tab" size="large">
          <el-radio-button value="draft">草稿列表 <b class="cnt">{{ draftTotal }}</b></el-radio-button>
          <el-radio-button value="published">已发布列表 <b class="cnt ok">{{ publishedTotal }}</b></el-radio-button>
        </el-radio-group>
        <div>
          <el-button type="primary" @click="openEditor('IMAGE')">＋ 新建图文</el-button>
          <el-button type="primary" plain @click="openEditor('VIDEO')">＋ 新建视频</el-button>
        </div>
      </div>
    </el-card>

    <!-- 已发布筛选 -->
    <el-card v-if="tab === 'published'" shadow="never" class="pool-card filter-card">
      <el-select v-model="filterChannelId" placeholder="全部频道" clearable style="width: 200px" @change="loadRows()">
        <el-option v-for="c in channels" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="pool-card">
      <el-table :data="rows" v-loading="loading">
        <el-table-column label="内容" min-width="300">
          <template #default="{ row }">
            <div class="post-cell">
              <el-image v-if="coverOf(row)" :src="coverOf(row)" fit="cover" class="cell-cover" preview-teleported
                        :preview-src-list="coverList(row)" hide-on-click-modal />
              <div v-else class="cell-cover placeholder">无图</div>
              <div class="cell-info">
                <b>{{ row.title }}</b>
                <div class="cell-meta">
                  <span class="type-tag" :class="typeClass(row.type)">{{ typeName(row.type) }}</span>
                  <span v-if="row.videoCount" class="video-note">▶ {{ row.videoCount }} 视频</span>
                  <span v-if="row.imageCount" class="video-note">🖼 {{ row.imageCount }} 图</span>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="130">
          <template #default="{ row }">
            <el-tag size="small" :type="row.type === 2 ? 'primary' : row.type === 3 ? 'warning' : 'success'">
              {{ typeName(row.type) }}
            </el-tag>
            <el-tag v-if="tab === 'draft' && row._pending" size="small" type="warning" effect="plain" class="nr-tag">
              转码中
            </el-tag>
            <el-tag v-else-if="tab === 'draft' && row._failed" size="small" type="danger" effect="plain" class="nr-tag">
              转码失败
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="tab === 'draft'" label="挂载频道" width="130">
          <template #default="{ row }">
            <el-tag v-if="row.channelId && channelName(row.channelId)" size="small" type="info" effect="plain">
              {{ channelName(row.channelId) }}
            </el-tag>
            <span v-else class="muted">未选择</span>
          </template>
        </el-table-column>
        <el-table-column v-else label="挂载频道" width="130">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ channelName(row.channelId) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="tab === 'draft' ? '更新时间' : '发布时间'" width="130">
          <template #default="{ row }">{{ fmtTime(tab === 'draft' ? row.updatedAt : row.publishedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <template v-if="tab === 'draft'">
              <el-button link type="primary" @click="openEditor('EDIT', row)">编辑</el-button>
              <el-button link type="success" :disabled="row._notReady > 0"
                         :title="row._failed ? '视频转码失败，请在编辑器里重新转码' : (row._pending ? '视频仍在转码，完成后才能发布' : '')"
                         @click="openPublish(row)">发布</el-button>
              <el-popconfirm title="确认删除该草稿？删除后不可恢复。" @confirm="remove(row)">
                <template #reference><el-button link type="danger">删除</el-button></template>
              </el-popconfirm>
            </template>
            <template v-else>
              <el-button link type="primary" @click="previewDetail(row)">预览</el-button>
              <el-popconfirm title="下架后回到草稿列表，可再编辑/重新发布。确认下架？" @confirm="unpublish(row)">
                <template #reference><el-button link type="warning">下架</el-button></template>
              </el-popconfirm>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination v-model:current-page="page" :page-size="pageSize" :total="tab === 'draft' ? draftTotal : publishedTotal"
                     layout="prev, pager, next, total" class="pager" @current-change="loadRows" />
    </el-card>

    <!-- 新建/编辑抽屉 -->
    <el-drawer v-model="editorVisible" :title="editorTitle" size="560px" destroy-on-close
               :before-close="onEditorClose" @closed="onEditorClosed">
      <div v-loading="saving">
        <el-form label-position="top">
          <el-form-item label="标题（必填）">
            <el-input v-model="form.title" maxlength="120" show-word-limit placeholder="请输入内容标题" />
          </el-form-item>

          <el-form-item label="图片素材（批量 · 自动压缩 ≈1MB/张 · 数量不限）">
            <el-upload :http-request="uploadOne" multiple :show-file-list="false" accept="image/jpeg,image/png,image/gif,image/bmp">
              <el-button>＋ 上传图片</el-button>
            </el-upload>
            <div v-if="imageAssets.length" class="media-grid">
              <div v-for="(a, i) in imageAssets" :key="a.id" class="media-item">
                <img :src="assetUrl(a, 'thumb')" />
                <span class="ord">{{ i + 1 }}{{ i === 0 ? ' · 封面' : '' }}</span>
                <span class="del" @click="removeAsset(a)">✕</span>
                <span v-if="a.status !== 2" class="proc">{{ a.status === 3 ? '失败' : '处理中' }}</span>
              </div>
            </div>
            <div class="hint">第 1 张为封面；可上传 GIF（取首帧静态图）、不支持 WebP</div>
          </el-form-item>

          <el-form-item label="视频素材（每帖最多 1 个 · 自动转码+抽封面）">
            <template v-if="!videoAsset">
              <el-upload :http-request="uploadOne" :show-file-list="false" :disabled="videoUploading"
                         accept="video/mp4,video/quicktime,video/x-matroska,.mkv">
                <el-button type="primary" plain :loading="videoUploading">
                  {{ videoUploading ? `上传中 ${uploadPercent}%` : '＋ 添加视频' }}
                </el-button>
              </el-upload>
            </template>
            <div v-else class="media-item video">
              <video v-if="videoAsset.status === 2" :src="assetUrl(videoAsset, 'main')" controls preload="metadata"
                     style="width:100%;max-height:150px;background:#000" />
              <div v-else class="v-placeholder">
                <span v-if="videoAsset.status === 3">⚠ 转码失败</span>
                <span v-else>⏳ 后台转码中…（大视频需要几分钟，可先去传图片/填标题）</span>
              </div>
              <div class="v-meta">{{ videoAsset.originalName }}
                <span v-if="videoAsset.status === 3 && videoAsset.failReason" class="fail-reason">
                  {{ videoAsset.failReason }}
                </span>
              </div>
              <div class="v-actions">
                <el-button size="small" @click="removeVideo">移除视频</el-button>
                <el-button v-if="videoAsset.status === 3" size="small" type="primary" plain
                           @click="retryVideo(videoAsset)">重新转码</el-button>
              </div>
            </div>
            <div class="hint">mp4/mov/mkv ≤300MB ≤15 分钟；上传完即可继续编辑，转码完成后才能保存/发布</div>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <div class="editor-footer">
          <el-button @click="editorVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="saveDraft">保存草稿</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 发布弹窗：挂载订阅频道管理中的已发布频道 -->
    <el-dialog v-model="publishVisible" title="📌 发布到订阅频道" width="520px">
      <div class="publish-info">
        <b>{{ publishRow?.title }}</b>
        <span class="muted">选择要挂载的频道（数据源：订阅频道管理，仅已发布频道可选）</span>
      </div>
      <el-radio-group v-model="publishChannelId" class="channel-picker">
        <el-radio v-for="c in channels" :key="c.id" :value="c.id" class="channel-opt">
          {{ c.name }} <span class="muted">· {{ c.status === 1 ? '已发布' : '已下架' }}</span>
        </el-radio>
      </el-radio-group>
      <div class="hint">发布后仅在该频道详情页展示，不上首页；可随时下架回草稿</div>
      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button type="primary" :loading="publishing" @click="doPublish">✔ 确认发布</el-button>
      </template>
    </el-dialog>

    <!-- 素材/帖详情预览（含视频播放） -->
    <el-drawer v-model="detailVisible" title="内容预览" size="480px" destroy-on-close>
      <template v-if="detail">
        <h3 class="dt">{{ detail.title }}</h3>
        <div class="detail-media" v-for="a in detail.assets" :key="a.id">
          <video v-if="a.fileType === 2" :src="assetUrl(a, 'main')" controls preload="metadata"
                 style="width:100%;max-height:260px;background:#000;border-radius:8px" />
          <el-image v-else :src="assetUrl(a, 'main')" fit="contain" style="width:100%;max-height:400px" preview-teleported />
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { adminApi, adminUploadApi } from '../services/http';

const tab = ref('draft');
const rows = ref([]);
const loading = ref(false);
const saving = ref(false);
const publishing = ref(false);
const page = ref(1);
const pageSize = 15;
const draftTotal = ref(0);
const publishedTotal = ref(0);
const channels = ref([]);
const filterChannelId = ref(null);

const operatorId = () => Number(localStorage.getItem('mini_admin_operator_id') || 1);

const editorVisible = ref(false);
const editorTitle = ref('');
const editId = ref(null);
const form = reactive({ title: '' });
const imageAssets = ref([]);
const videoAsset = ref(null);
/** 进行中的上传数量（>0 时禁止保存，避免素材还没入库就提交帖） */
const uploading = ref(0);
/** 视频上传进度（%），仅用于按钮文案 */
const uploadPercent = ref(0);
const videoUploading = ref(false);

const publishVisible = ref(false);
const publishRow = ref(null);
const publishChannelId = ref(null);

const detailVisible = ref(false);
const detail = ref(null);
/** 本次编辑器会话上传的素材 id（未保存即取消时回收，避免磁盘残留） */
const sessionUploaded = ref([]);
const savedThisSession = ref(false);

const allAssets = computed(() => (videoAsset.value ? [...imageAssets.value, videoAsset.value] : imageAssets.value));

function typeName(t) {
  return { 1: '图文', 2: '视频', 3: '图文+视频' }[t] || '未知';
}
function typeClass(t) {
  return t === 2 ? 'v' : t === 3 ? 'm' : 'g';
}
function fmtTime(t) {
  if (!t) return '-';
  return String(t).replace('T', ' ').slice(0, 16);
}
function channelName(id) {
  return channels.value.find((c) => c.id === id)?.name || (id ? `#${id}` : '');
}
function assetUrl(a, kind) {
  if (!a) return '';
  return `/admin-api/media/assets/${a.id}/file?kind=${kind}`;
}
function coverOf(row) {
  const c = row._cover;
  return c ? assetUrl(c, c.fileType === 2 ? 'poster' : 'thumb') : '';
}
function coverList(row) {
  return row._assets ? row._assets.filter((a) => a.fileType === 1).map((a) => assetUrl(a, 'main')) : [];
}

async function loadChannels() {
  try {
    channels.value = await adminApi.get('/media/posts/channels');
  } catch { /* 已发布筛选可空 */ }
}

async function loadRows() {
  loading.value = true;
  try {
    if (tab.value === 'draft') {
      const p = await adminApi.get('/media/posts/drafts', { params: { page: page.value, pageSize } });
      rows.value = await decorate(p.records);
      draftTotal.value = p.total;
    } else {
      const p = await adminApi.get('/media/posts/published', {
        params: { page: page.value, pageSize, channelId: filterChannelId.value || undefined }
      });
      rows.value = await decorate(p.records);
      publishedTotal.value = p.total;
    }
  } finally {
    loading.value = false;
  }
}

/** 抽屉已关闭时的静默刷新（转码完成后更新草稿列表的可发布状态） */
async function refreshListQuietly() {
  try {
    await loadRows();
  } catch { /* 忽略：下次操作会再刷 */ }
}

async function decorate(posts) {
  const out = [];
  for (const post of posts) {
    try {
      const d = await adminApi.get(`/media/posts/${post.id}`);
      post._cover = d.cover;
      post._assets = d.assets;
      post.imageCount = d.assets.filter((a) => a.fileType === 1).length;
      post.videoCount = d.assets.filter((a) => a.fileType === 2).length;
      // 未就绪素材（转码中/失败）：>0 时草稿可保存但不允许发布
      post._pending = d.assets.filter((a) => a.status === 1).length;
      post._failed = d.assets.filter((a) => a.status === 3).length;
      post._notReady = post._pending + post._failed;
    } catch { /* 忽略装饰失败 */ }
    out.push(post);
  }
  return out;
}

function openEditor(mode, row) {
  stopAllPolls(); // 换内容时丢弃上一轮轮询定时器
  editId.value = row?.id || null;
  form.title = row?.title || '';
  imageAssets.value = [];
  videoAsset.value = null;
  sessionUploaded.value = []; // 本次编辑器会话上传的素材（取消/未采用时回收，避免磁盘残留）
  if (row) {
    // 编辑：复用 detail 数据装载素材
    adminApi.get(`/media/posts/${row.id}`).then((d) => {
      form.title = d.post?.title || row.title;
      d.assets.forEach((a) => {
        if (a.fileType === 1) imageAssets.value.push(a);
        else videoAsset.value = a;
      });
    });
  }
  editorTitle.value = mode === 'EDIT' ? '✏️ 编辑内容' : (mode === 'VIDEO' ? '🎬 新建视频内容' : '🖼 新建图文内容');
  editorVisible.value = true;
}

/** 删除未采用的素材（后端对仍被引用的素材会拒绝，安全） */
async function discardAssets(ids) {
  for (const id of ids) {
    try {
      await adminApi.delete(`/media/assets/${id}`);
    } catch { /* 仍被其他内容引用则保留 */ }
  }
}

/** 关闭前拦截：视频还在转码时关闭会丢弃素材（白跑一次转码），先确认 */
function onEditorClose(done) {
  if (videoAsset.value && videoAsset.value.status === 1) {
    ElMessageBox.confirm('视频还在后台转码，现在关闭会丢弃该视频，需要重新上传。确认关闭？', '视频转码中', {
      confirmButtonText: '仍要关闭',
      cancelButtonText: '继续等待',
      type: 'warning'
    })
      .then(() => done())
      .catch(() => {});
    return;
  }
  done();
}

/** 抽屉关闭且未保存 → 回收本次上传的素材（记录 + 磁盘文件） */
async function onEditorClosed() {
  if (savedThisSession.value) {
    savedThisSession.value = false;
    sessionUploaded.value = [];
    // 已保存但视频还在转码：保留轮询，转码完成后自动刷新草稿列表（「转码中」→ 可发布）
    if (!(videoAsset.value && videoAsset.value.status === 1)) {
      stopAllPolls();
    }
    return;
  }
  stopAllPolls();
  const pending = [...sessionUploaded.value];
  sessionUploaded.value = [];
  if (pending.length) {
    await discardAssets(pending);
  }
}

async function uploadOne(opt) {
  const file = opt.file;
  const isVideo = isVideoFile(file);
  uploading.value += 1;
  if (isVideo) {
    videoUploading.value = true;
    uploadPercent.value = 0;
  }
  try {
    // 走「流式 + 关闭超时」的上传专用客户端：
    // 1) 关超时：47MB 视频光传输就可能超过全局 15s；
    // 2) 不走 multipart：请求体直写媒体卷，省掉容器 /tmp 的那一份重复落盘（300MB 视频省一半写盘）。
    const r = await adminUploadApi.post('/media/assets/upload-stream', file, {
      params: { fileName: file.name, operatorId: operatorId() },
      headers: { 'Content-Type': 'application/octet-stream' },
      maxBodyLength: Infinity,
      maxContentLength: Infinity,
      onUploadProgress: (e) => {
        if (isVideo && e.total) {
          uploadPercent.value = Math.min(99, Math.round((e.loaded / e.total) * 100));
        }
      }
    });
    if (!r || !r.ok) {
      ElMessage.error(r?.reason || '上传失败');
      return;
    }
    // 图片后端同步压缩完返回 READY；视频返回 PROCESSING，后台转码，按 id 轮询
    const asset = { id: r.assetId, fileType: r.fileType, status: r.status, originalName: file.name };
    sessionUploaded.value.push(r.assetId);
    if (r.fileType === 2) {
      videoAsset.value = asset;
      ElMessage.success('视频已上传，正在后台转码，完成后即可保存/发布');
      pollAsset(asset);
    } else {
      imageAssets.value.push(asset);
      ElMessage.success('图片已上传（自动压缩）');
    }
  } catch (e) {
    ElMessage.error(e.message || '上传失败');
  } finally {
    uploading.value -= 1;
    if (isVideo) {
      videoUploading.value = false;
      uploadPercent.value = 0;
    }
  }
}

function isVideoFile(file) {
  if (!file) return false;
  if ((file.type || '').startsWith('video/')) return true;
  return /\.(mp4|mov|mkv|webm|avi|flv|m4v)$/i.test(file.name || '');
}

/** 转码状态轮询：assetId → intervalId */
const pollTimers = new Map();
const POLL_INTERVAL_MS = 3000;
const POLL_MAX_TICKS = 600; // 30 分钟兜底，防止页面长期挂着定时器

function stopPoll(assetId) {
  const t = pollTimers.get(assetId);
  if (t) {
    clearInterval(t);
    pollTimers.delete(assetId);
  }
}

function stopAllPolls() {
  pollTimers.forEach((t) => clearInterval(t));
  pollTimers.clear();
}

/**
 * 按 id 查询单个素材的转码状态。
 * 旧实现查「素材列表最新一条」再 find，同帖还传了图片时视频状态永远刷不到，
 * 界面会一直停在「转码中…」并挡住保存。
 */
function pollAsset(asset) {
  stopPoll(asset.id);
  let ticks = 0;
  let failures = 0;
  const timer = setInterval(async () => {
    ticks += 1;
    try {
      const fresh = await adminApi.get(`/media/assets/${asset.id}`);
      failures = 0;
      if (fresh) {
        asset.status = fresh.status;
        asset.failReason = fresh.failReason;
        if (fresh.status === 2) {
          stopPoll(asset.id);
          ElMessage.success('视频转码完成，可以保存/发布了');
          if (!editorVisible.value) await refreshListQuietly();
        } else if (fresh.status === 3) {
          stopPoll(asset.id);
          ElMessage.error(`视频转码失败：${fresh.failReason || '原因未知'}`);
          if (!editorVisible.value) await refreshListQuietly();
        }
      }
    } catch {
      failures += 1;
      if (failures >= 5) stopPoll(asset.id); // 素材可能已被删除，停止无效轮询
    } finally {
      if (ticks >= POLL_MAX_TICKS) stopPoll(asset.id);
    }
  }, POLL_INTERVAL_MS);
  pollTimers.set(asset.id, timer);
}

/** 转码失败后手动重试（后端 reprocess 会重新投递到异步线程池） */
async function retryVideo(asset) {
  try {
    const fresh = await adminApi.post(`/media/assets/${asset.id}/reprocess`);
    asset.status = fresh?.status ?? 1;
    asset.failReason = null;
    ElMessage.success('已重新提交转码');
    pollAsset(asset);
  } catch (e) {
    ElMessage.error(e.message || '重新转码失败');
  }
}

function removeAsset(a) {
  const i = imageAssets.value.indexOf(a);
  if (i >= 0) imageAssets.value.splice(i, 1);
  // 新建模式（素材尚未被任何内容引用）→ 立即回收磁盘文件；编辑模式留待保存时由后端释放
  if (!editId.value) {
    discardAssets([a.id]);
    sessionUploaded.value = sessionUploaded.value.filter((id) => id !== a.id);
  }
}

/** 移除已选视频（同上：新建模式立即回收） */
function removeVideo() {
  const v = videoAsset.value;
  videoAsset.value = null;
  if (v?.id) {
    stopPoll(v.id); // 转码中移除也要停轮询；后端会在转码完成时发现记录已删并回收产物文件
  }
  if (!editId.value && v?.id) {
    discardAssets([v.id]);
    sessionUploaded.value = sessionUploaded.value.filter((id) => id !== v.id);
  }
}

async function saveDraft() {
  if (form.title.trim() === '') {
    ElMessage.warning('请填写标题');
    return;
  }
  if (uploading.value > 0) {
    ElMessage.warning('文件仍在上传中，请等上传完成再保存');
    return;
  }
  if (allAssets.value.length === 0) {
    ElMessage.warning('请至少上传一张图片或一个视频');
    return;
  }
  saving.value = true;
  try {
    const body = { title: form.title.trim(), assetIds: allAssets.value.map((a) => a.id), operatorId: operatorId() };
    if (editId.value) {
      await adminApi.put(`/media/posts/${editId.value}`, body);
    } else {
      await adminApi.post('/media/posts', body);
    }
    const pending = allAssets.value.filter((a) => a.status !== 2).length;
    ElMessage.success(pending > 0
      ? '已保存到草稿（视频仍在后台转码，完成后草稿列表会变可发布）'
      : '已保存到草稿');
    savedThisSession.value = true;
    // 回收本次上传但未被采用（被移除）的素材，避免磁盘残留
    const usedIds = new Set(allAssets.value.map((a) => a.id));
    const unused = sessionUploaded.value.filter((id) => !usedIds.has(id));
    if (unused.length) {
      await discardAssets(unused);
    }
    sessionUploaded.value = [];
    editorVisible.value = false;
    tab.value = 'draft';
    page.value = 1;
    await loadRows();
  } catch (e) {
    ElMessage.error(e.message || '保存失败');
  } finally {
    saving.value = false;
  }
}

function openPublish(row) {
  if (row._notReady > 0) {
    ElMessage.warning(row._failed ? '视频转码失败，请先在编辑器里重新转码' : '视频仍在后台转码，完成后才能发布');
    return;
  }
  publishRow.value = row;
  publishChannelId.value = row.channelId || null;
  publishVisible.value = true;
}

async function doPublish() {
  if (!publishChannelId.value) {
    ElMessage.warning('请选择挂载频道');
    return;
  }
  publishing.value = true;
  try {
    await adminApi.post(`/media/posts/${publishRow.value.id}/publish`, {
      channelId: publishChannelId.value, operatorId: operatorId()
    });
    ElMessage.success('已发布 → 已发布列表');
    publishVisible.value = false;
    tab.value = 'published';
    page.value = 1;
    await loadRows();
  } catch (e) {
    ElMessage.error(e.message || '发布失败');
  } finally {
    publishing.value = false;
  }
}

async function remove(row) {
  try {
    await adminApi.delete(`/media/posts/${row.id}`);
    ElMessage.success('已删除');
    await loadRows();
  } catch (e) {
    ElMessage.error(e.message || '删除失败');
  }
}

async function unpublish(row) {
  try {
    await adminApi.post(`/media/posts/${row.id}/unpublish`);
    ElMessage.success('已下架 → 回到草稿列表');
    await loadRows();
  } catch (e) {
    ElMessage.error(e.message || '下架失败');
  }
}

async function previewDetail(row) {
  detail.value = await adminApi.get(`/media/posts/${row.id}`);
  detailVisible.value = true;
}

onMounted(async () => {
  await loadChannels();
  await loadRows();
});

onUnmounted(() => stopAllPolls());
</script>

<style scoped>
.media-pool { display: flex; flex-direction: column; gap: 14px; }
.pool-card { border-radius: 10px; }
.pool-toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px; }
.pool-toolbar .cnt { color: #e07b39; }
.pool-toolbar .cnt.ok { color: #1e8e50; }
.filter-card { margin-top: 0; }
.post-cell { display: flex; gap: 10px; align-items: center; }
.cell-cover { width: 58px; height: 78px; border-radius: 6px; flex-shrink: 0; }
.cell-cover.placeholder { background: #eef1f5; color: #a8b3c0; display: flex; align-items: center; justify-content: center; font-size: 11px; }
.cell-info b { font-size: 13px; color: #26384c; }
.cell-meta { margin-top: 4px; display: flex; gap: 8px; align-items: center; }
.type-tag { font-size: 11px; padding: 1px 7px; border-radius: 4px; font-weight: 600; }
.type-tag.g { background: #e7f6f0; color: #1f7a5c; }
.type-tag.v { background: #eaf2ff; color: #2f6fd8; }
.type-tag.m { background: #f6eefe; color: #7a3fe0; }
.video-note { font-size: 11px; color: #98a5b5; }
.nr-tag { margin-left: 6px; }
.muted { color: #98a5b5; font-size: 12px; }
.pager { justify-content: flex-end; padding: 14px 4px 0; }
.media-grid { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 10px; }
.media-item { width: 108px; position: relative; }
.media-item img { width: 108px; height: 76px; border-radius: 8px; object-fit: cover; border: 1px solid #e5eaf0; display: block; }
.media-item .ord { position: absolute; left: 4px; bottom: 4px; background: rgba(31,111,100,.92); color: #fff; font-size: 10px; padding: 1px 6px; border-radius: 4px; }
.media-item .del { position: absolute; top: 4px; right: 4px; background: rgba(0,0,0,.55); color: #fff; width: 18px; height: 18px; line-height: 17px; text-align: center; border-radius: 50%; font-size: 10px; cursor: pointer; }
.media-item .proc { position: absolute; top: 4px; left: 4px; background: rgba(230,180,34,.95); color: #5c4300; font-size: 9px; padding: 1px 5px; border-radius: 4px; }
.media-item.video { width: 100%; }
.v-placeholder { display: flex; align-items: center; justify-content: center; height: 84px; border-radius: 8px;
  background: #f3f5f1; border: 1px dashed #cfd9d3; color: #6f7978; font-size: 12px; text-align: center; padding: 0 10px; }
.v-actions { display: flex; gap: 8px; margin-top: 6px; }
.v-meta { font-size: 11px; color: #55657a; margin: 6px 0; }
.v-meta .fail-reason { display: block; color: #c0392b; margin-top: 3px; word-break: break-all; }
.hint { font-size: 11px; color: #98a5b5; margin-top: 6px; line-height: 1.7; }
.editor-footer { display: flex; justify-content: flex-end; gap: 8px; padding: 4px 0; }
.publish-info { background: #f7f9fb; border-radius: 10px; padding: 12px 14px; display: flex; flex-direction: column; gap: 6px; margin-bottom: 14px; }
.publish-info b { font-size: 14px; }
.channel-picker { display: flex; flex-direction: column; gap: 8px; width: 100%; }
.channel-opt { height: auto; padding: 9px 10px; border: 1px solid #e3e9f1; border-radius: 8px; margin-right: 0 !important; width: 100%; }
.detail-media { margin: 10px 0; }
.dt { font-size: 16px; color: #1f2d3d; }
</style>
