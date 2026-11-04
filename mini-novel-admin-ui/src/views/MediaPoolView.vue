<template>
  <div class="media-pool">
    <!-- 顶部：Tab 切换 + 新建入口 -->
    <el-card shadow="never" class="pool-card">
      <div class="pool-toolbar">
        <el-radio-group v-model="tab" size="large" @change="onTabChange">
          <el-radio-button value="draft">草稿列表 <b class="cnt">{{ draftTotal }}</b></el-radio-button>
          <el-radio-button value="published">已发布列表 <b class="cnt ok">{{ publishedTotal }}</b></el-radio-button>
        </el-radio-group>
        <div class="toolbar-actions">
          <el-button v-if="tab === 'draft'" type="success" plain :disabled="!selectedRows.length"
                     @click="openBatchPublish">
            📌 批量发布{{ selectedRows.length ? `（${selectedRows.length}）` : '' }}
          </el-button>
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
      <el-table ref="draftTable" :data="rows" v-loading="loading" :row-key="(row) => row.id"
                @selection-change="onSelectionChange">
        <!-- 草稿列表支持多选：勾选多条 → 批量发布到同一个频道 -->
        <el-table-column v-if="tab === 'draft'" type="selection" width="44" />
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
            <div class="hint">第 1 张为封面（若同时挂了视频，则封面取第 1 个视频）；可上传 GIF（取首帧静态图）、不支持 WebP</div>
          </el-form-item>

          <el-form-item label="视频素材（可多个 · 第 1 个为封面 · 自动转码+抽封面）">
            <div class="video-add-row">
              <el-upload :http-request="uploadOne" :show-file-list="false" :disabled="videoUploading"
                         accept="video/mp4,video/quicktime,video/x-matroska,.mkv">
                <el-button type="primary" plain :loading="videoUploading">
                  {{ videoUploading ? (hashChecking ? `校验中 ${uploadPercent}%` : `上传中 ${uploadPercent}%`) : '＋ 添加视频' }}
                </el-button>
              </el-upload>
              <el-button type="success" plain @click="dirImportVisible = true">📁 选择目录批量导入</el-button>
              <span v-if="videos.length" class="muted">已挂 {{ videos.length }} 个视频 · 第 1 个为封面</span>
            </div>

            <div v-if="videos.length" class="video-list">
              <div v-for="(v, i) in videos" :key="v.id" class="video-row">
                <div class="vthumb">
                  <img v-if="v.status === 2 && v.posterPath" :src="assetUrl(v, 'poster')" alt="" class="no-drag" />
                  <span v-else-if="v.status === 3" class="vt-badge err">失败</span>
                  <span v-else class="vt-badge wait">转码中</span>
                </div>
                <div class="vmain">
                  <div class="vname" :title="v.originalName">{{ v.originalName }}</div>
                  <div class="vstate">
                    <el-tag v-if="i === 0" size="small" type="warning" effect="plain">封面</el-tag>
                    <span v-if="v.status === 2" class="ok">已就绪</span>
                    <span v-else-if="v.status === 3" class="bad">转码失败：{{ v.failReason || '原因未知' }}</span>
                    <span v-else class="wait">后台转码中…（可继续添加其它视频/图片）</span>
                  </div>
                </div>
                <div class="vops">
                  <el-button v-if="v.status === 2" link size="small" type="primary" @click="openVideoPreview(v)">播放</el-button>
                  <el-button link size="small" :disabled="i === 0" @click="moveVideo(i, -1)">上移</el-button>
                  <el-button link size="small" :disabled="i === videos.length - 1" @click="moveVideo(i, 1)">下移</el-button>
                  <el-button v-if="v.status === 3" link size="small" type="primary" @click="retryVideo(v)">重新转码</el-button>
                  <el-button link size="small" type="danger" @click="removeVideo(v)">移除</el-button>
                </div>
              </div>
            </div>

            <div class="hint">mp4/mov/mkv ≤300MB ≤15 分钟；<b>上传成功后自动保存为草稿</b>（标题留空则取文件名），封面取第 1 个视频，全部转码完成后才能发布；<b>已上传过的文件会自动跳过</b>（先校验 md5 再决定是否上传）</div>
            <div class="hint dir-hint">
              📁 目录导入：文件夹里每个视频都<b>加进当前这条内容</b>（同一个标题，序号即内容内顺序，第 1 个为封面），非视频文件自动忽略
            </div>
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

    <!-- 目录批量导入视频：视频全部加入当前内容（同一标题，第 1 个为封面） -->
    <VideoDirectoryImportDialog v-model="dirImportVisible" :operator-id="operatorId()"
                                @uploaded="onDirVideoUploaded" @finished="onDirFinished" />

    <!-- 编辑器中单个视频预览（运营校对用） -->
    <el-dialog v-model="previewVideoVisible" title="视频预览" width="640px" destroy-on-close>
      <video v-if="previewVideo" :src="assetUrl(previewVideo, 'main')" controls autoplay
             style="width:100%;max-height:420px;background:#000;border-radius:8px" />
    </el-dialog>

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

    <!-- 批量发布：勾选多条草稿 → 全部发到同一个频道 -->
    <el-dialog v-model="batchVisible" title="📌 批量发布到订阅频道" width="560px"
               :close-on-click-modal="false" @closed="resetBatch">
      <div class="publish-info">
        <b>已勾选 {{ batchRows.length }} 条内容</b>
        <span class="muted">
          可发布 {{ batchPublishable.length }} 条<template v-if="batchSkipped.length">，{{ batchSkipped.length }} 条素材未就绪（转码中/失败）将跳过</template>
        </span>
        <span class="muted">频道数据源：订阅频道管理（仅已发布频道可选）；每条内容仍只挂这一个频道</span>
      </div>
      <el-radio-group v-model="batchChannelId" class="channel-picker" :disabled="batchPublishing">
        <el-radio v-for="c in channels" :key="c.id" :value="c.id" class="channel-opt">
          {{ c.name }} <span class="muted">· {{ c.status === 1 ? '已发布' : '已下架' }}</span>
        </el-radio>
      </el-radio-group>
      <div v-if="batchProgress.total" class="batch-progress">
        <span :class="batchFailures.length ? 'warn' : 'ok'">{{ batchProgress.text }}</span>
        <ul v-if="batchFailures.length" class="batch-failures">
          <li v-for="f in batchFailures" :key="f.id"><b>{{ f.title }}</b>：{{ f.reason }}</li>
        </ul>
      </div>
      <template #footer>
        <el-button :disabled="batchPublishing" @click="batchVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchPublishing" :disabled="!batchPublishable.length"
                   @click="doBatchPublish">
          ✔ 发布 {{ batchPublishable.length }} 条
        </el-button>
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
import { isVideoFile, resolveDraftTitle, resolveGroupTitle } from '../utils/media';
import { publishDraftsSequentially, splitPublishable } from '../utils/mediaPublish';
import { findExistingAssetByFile } from '../utils/uploadDedupe';
import VideoDirectoryImportDialog from '../components/VideoDirectoryImportDialog.vue';

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

/** 草稿列表多选（批量发布到同一个频道） */
const draftTable = ref(null);
const selectedRows = ref([]);
const batchVisible = ref(false);
/** 打开弹窗时的勾选快照：列表刷新后仍能展示「可发布/跳过」与失败原因 */
const batchRows = ref([]);
const batchChannelId = ref(null);
const batchPublishing = ref(false);
const batchFailures = ref([]);
const batchProgress = reactive({ total: 0, done: 0, text: '' });
const batchSplit = computed(() => splitPublishable(batchRows.value));
const batchPublishable = computed(() => batchSplit.value.publishable);
const batchSkipped = computed(() => batchSplit.value.skipped);

const operatorId = () => Number(localStorage.getItem('mini_admin_operator_id') || 1);

const editorVisible = ref(false);
const editorTitle = ref('');
const editId = ref(null);
/** 目录批量导入视频弹窗（视频全部加入当前内容，同一标题） */
const dirImportVisible = ref(false);
/** 编辑器内视频预览弹窗（切封面/校对用） */
const previewVideoVisible = ref(false);
const previewVideo = ref(null);
const form = reactive({ title: '' });
const imageAssets = ref([]);
/** 视频素材：顺序=内容内的 seq，第 1 个为封面；数量不限 */
const videos = ref([]);
/** 进行中的上传数量（>0 时禁止保存，避免素材还没入库就提交帖） */
const uploading = ref(0);
/** 视频上传进度（%），仅用于按钮文案 */
const uploadPercent = ref(0);
const videoUploading = ref(false);
/** 是否正在算 md5 查重（大视频按块校验，按钮文案显示「校验中」） */
const hashChecking = ref(false);

const publishVisible = ref(false);
const publishRow = ref(null);
const publishChannelId = ref(null);

const detailVisible = ref(false);
const detail = ref(null);
/** 本次编辑器会话上传的素材 id（未保存即取消时回收，避免磁盘残留） */
const sessionUploaded = ref([]);
/** 已被某次保存（含视频上传后自动保存）写进帖子的素材 id：关闭时这些不能回收 */
const persistedIds = ref(new Set());
const savedThisSession = ref(false);
/** 编辑器会话序号（非响应式）：上传是异步的，回来时要确认还是同一个会话、抽屉还开着 */
let editorSession = 0;

/** 全部素材：视频在前（封面取第 1 个视频）→ 图片按上传顺序 */
const allAssets = computed(() => [...videos.value, ...imageAssets.value]);

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
    clearSelection(); // 换页/换 Tab/刷新后清空勾选，避免对着旧数据批量发布
  }
}

/** 清空草稿列表勾选（「批量发布」按钮随之置灰） */
function clearSelection() {
  selectedRows.value = [];
  draftTable.value?.clearSelection?.();
}

function onSelectionChange(selection) {
  selectedRows.value = selection;
}

/** 手切 Tab：回到第一页并重新拉数据（顺手清掉草稿勾选，避免停在上一个列表的选择上） */
function onTabChange() {
  page.value = 1;
  loadRows();
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
  editorSession += 1; // 上传回调靠它判断会话是否已经结束
  editId.value = row?.id || null;
  form.title = row?.title || '';
  imageAssets.value = [];
  videos.value = [];
  sessionUploaded.value = []; // 本次编辑器会话上传的素材（取消/未采用时回收，避免磁盘残留）
  persistedIds.value = new Set(); // 已被某次保存写进帖子的素材（关闭时无需回收）
  if (row) {
    // 编辑：复用 detail 数据装载素材（视频顺序=seq，第 1 个即封面）
    adminApi.get(`/media/posts/${row.id}`).then((d) => {
      form.title = d.post?.title || row.title;
      videos.value = [];
      imageAssets.value = [];
      d.assets.forEach((a) => {
        if (a.fileType === 1) imageAssets.value.push(a);
        else videos.value.push(a);
      });
      persistedIds.value = new Set(d.assets.map((a) => a.id));
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

/** 关闭前拦截：视频还没保存且仍在转码时关闭会丢弃素材（白跑一次转码），先确认 */
function onEditorClose(done) {
  // 已经（自动/手动）存过草稿：素材已在帖子里，关闭不会丢东西
  if (savedThisSession.value) {
    done();
    return;
  }
  if (videos.value.some((v) => v.status === 1)) {
    ElMessageBox.confirm('视频还在后台转码，现在关闭会丢弃这些视频，需要重新上传。确认关闭？', '视频转码中', {
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

/** 抽屉关闭：回收「本次上传但没被任何保存写进帖子」的素材（记录 + 磁盘文件） */
async function onEditorClosed() {
  const persisted = persistedIds.value;
  const pending = sessionUploaded.value.filter((id) => !persisted.has(id));
  sessionUploaded.value = [];
  persistedIds.value = new Set();
  if (savedThisSession.value) {
    savedThisSession.value = false;
    // 已保存但仍有视频在转码：保留轮询，转码完成后自动刷新草稿列表（「转码中」→ 可发布）
    if (!videos.value.some((v) => v.status === 1)) {
      stopAllPolls();
    }
  } else {
    stopAllPolls();
  }
  if (pending.length) {
    await discardAssets(pending);
  }
}

async function uploadOne(opt) {
  const file = opt.file;
  const isVideo = isVideoFile(file);
  const session = editorSession; // 上传是异步的：回来时要确认还是同一个编辑器会话
  uploading.value += 1;
  if (isVideo) {
    videoUploading.value = true;
    uploadPercent.value = 0;
  }
  /** 视频就绪素材：finally 之后自动存草稿（此时 uploading 已归零，不会再被"上传中"拦住） */
  let uploadedVideo = null;
  try {
    // 视频先算 md5 查重：素材库里已有同文件（READY）就直接复用，省掉整包重传
    if (isVideo) {
      hashChecking.value = true;
      const existing = await findExistingAssetByFile(file, {
        onProgress: (ratio) => {
          uploadPercent.value = Math.min(99, Math.round(ratio * 100));
        }
      });
      hashChecking.value = false;
      if (existing && existing.fileType === 2) {
        // 校验期间抽屉被关闭/已换会话 → 不改变界面，也不产生草稿
        if (session !== editorSession || !editorVisible.value) {
          return;
        }
        videos.value.push({
          id: existing.id,
          fileType: 2,
          status: existing.status,
          originalName: file.name,
          posterPath: existing.posterPath || null
        });
        // 取数组里的响应式代理（后续轮询/排序才响应）
        uploadedVideo = videos.value[videos.value.length - 1];
        ElMessage.success(`《${file.name}》已在素材库中，跳过重复上传`);
      }
    }
    if (!uploadedVideo) {
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
      // 上传期间抽屉被关闭或已换到别的编辑会话 → 按「取消编辑」处理：
      // 回收这次上传的素材，避免凭空多出一条草稿（自动存草稿）或磁盘孤儿
      if (session !== editorSession || !editorVisible.value) {
        await discardAssets([r.assetId]);
        return;
      }
      // 图片后端同步压缩完返回 READY；视频返回 PROCESSING，后台转码，按 id 轮询
      const asset = { id: r.assetId, fileType: r.fileType, status: r.status, originalName: file.name };
      sessionUploaded.value.push(r.assetId);
      if (r.fileType === 2) {
        videos.value.push(asset);
        // 取数组里的响应式代理再轮询：直接改原始对象不会触发视图更新
        const added = videos.value[videos.value.length - 1];
        uploadedVideo = added;
        ElMessage.success('视频已上传，正在后台转码，完成后即可发布');
        pollAsset(added);
      } else {
        imageAssets.value.push(asset);
        ElMessage.success('图片已上传（自动压缩）');
      }
    }
  } catch (e) {
    ElMessage.error(e.message || '上传失败');
  } finally {
    uploading.value -= 1;
    if (isVideo) {
      videoUploading.value = false;
      hashChecking.value = false;
      uploadPercent.value = 0;
    }
  }
  // 视频上传成功 → 自动保存草稿（标题留空则取文件名），不把素材悬在编辑器里等运营点保存
  if (uploadedVideo) {
    await persistDraft(resolveDraftTitle(form.title, uploadedVideo.originalName));
  }
}

/**
 * 保存草稿（自动保存统一入口）：串行排队，避免并发写同一条帖。
 * 手动点「保存草稿」走 saveDraft()，自动触发（上传完/移除/排序）走这里。
 */
let saveQueue = Promise.resolve();
function persistDraft(resolvedTitle) {
  saveQueue = saveQueue.catch(() => {}).then(() => doPersistDraft(resolvedTitle));
  return saveQueue;
}

/**
 * 自动保存草稿的实际动作。
 *
 * - 标题：调用方已按「已填优先、留空取兜底」算好（单视频取文件名，目录导入取目录名）；
 * - 新建态 → POST；已有草稿/编辑态 → PUT（不会重复建帖）；
 * - 视频此刻可能还在 PROCESSING：草稿允许挂未就绪素材（发布时才强制 READY），所以能立刻存；
 * - 失败不吞素材：素材仍留在编辑器里，提示手动点「保存草稿」重试。
 */
async function doPersistDraft(resolvedTitle) {
  if (saving.value || !allAssets.value.length) return false;
  saving.value = true;
  try {
    form.title = String(resolvedTitle || form.title || '').trim() || '未命名视频';
    const usedIds = allAssets.value.map((a) => a.id);
    const body = { title: form.title, assetIds: usedIds, operatorId: operatorId() };
    let postId = editId.value;
    if (postId) {
      await adminApi.put(`/media/posts/${postId}`, body);
    } else {
      const created = await adminApi.post('/media/posts', body);
      postId = created?.id ?? null;
      editId.value = postId;
      if (postId) {
        editorTitle.value = '✏️ 编辑内容（已自动保存草稿）';
      }
    }
    savedThisSession.value = true;
    persistedIds.value = new Set([...persistedIds.value, ...usedIds]);
    tab.value = 'draft';
    page.value = 1;
    loadRows().catch(() => { /* 列表刷新失败不影响本次保存 */ });
    return true;
  } catch (e) {
    ElMessage.warning(`自动保存草稿失败：${e.message || '未知原因'}，请点「保存草稿」重试`);
    return false;
  } finally {
    saving.value = false;
  }
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
        // 转码完成才有封面帧：补上 posterPath，列表里的视频缩略图才会出现
        asset.posterPath = fresh.posterPath || asset.posterPath;
        if (fresh.status === 2) {
          stopPoll(asset.id);
          if (editorVisible.value) {
            const total = videos.value.length;
            const ready = videos.value.filter((v) => v.status === 2).length;
            ElMessage.success(total > 1
              ? `视频转码完成（${ready}/${total}）${ready >= total ? '，全部就绪可以发布了' : '，其余仍在转码'}`
              : '视频转码完成，可以发布了');
          } else {
            await refreshListQuietly();
          }
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

/**
 * 移除某个视频：未保存过的新素材立即回收；已保存过的交给下次自动保存（PUT 解引用）去回收。
 * 封面随第 1 个视频变化，所以移除/排序后都自动落草稿。
 */
function removeVideo(v) {
  const i = videos.value.indexOf(v);
  if (i < 0) return;
  videos.value.splice(i, 1);
  stopPoll(v.id); // 转码中移除也要停轮询；后端会在转码完成时发现记录已不可用并回收产物文件
  if (!persistedIds.value.has(v.id)) {
    discardAssets([v.id]);
    sessionUploaded.value = sessionUploaded.value.filter((id) => id !== v.id);
    return;
  }
  if (editId.value) {
    persistDraft();
  }
}

/** 调整视频顺序（第 1 个为封面）：自动保存，避免「界面顺序变了但草稿没变」。 */
function moveVideo(index, delta) {
  const target = index + delta;
  if (target < 0 || target >= videos.value.length) return;
  const next = [...videos.value];
  const [item] = next.splice(index, 1);
  next.splice(target, 0, item);
  videos.value = next;
  if (editId.value) {
    persistDraft();
  }
}

/** 编辑器内播放某个视频（运营校对/确认封面） */
function openVideoPreview(video) {
  previewVideo.value = video;
  previewVideoVisible.value = true;
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
    persistedIds.value = usedIds;
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

/** 目录导入：每上传成功/命中复用 一个视频就挂进当前内容（顺序=上传顺序，第 1 个为封面） */
function onDirVideoUploaded(asset) {
  videos.value.push({
    id: asset.id,
    fileType: 2,
    status: asset.status,
    originalName: asset.originalName,
    posterPath: asset.posterPath || null,
    failReason: null
  });
  const added = videos.value[videos.value.length - 1];
  // 只有本次真正上传的素材才记入「待回收」；复用的已有素材关闭编辑器时绝不能删
  if (!asset.reused) {
    sessionUploaded.value.push(asset.id);
  }
  if (added.status === 1) {
    pollAsset(added);
  }
}

/**
 * 目录导入结束：视频已全部加入当前内容 → 自动存一次草稿。
 * 标题口径：已填标题优先，留空取目录名（同一批视频共用这个标题）。
 */
async function onDirFinished({ uploaded, failed, dirName }) {
  if (!uploaded) {
    return;
  }
  await persistDraft(resolveGroupTitle(form.title, dirName));
  ElMessage.success(`已将 ${uploaded} 个视频加入本内容并保存草稿${failed ? `（${failed} 个失败）` : ''}`);
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

/** 打开批量发布：把当前勾选快照下来（列表刷新后弹窗仍能展示结果） */
function openBatchPublish() {
  if (!selectedRows.value.length) return;
  batchRows.value = [...selectedRows.value];
  batchChannelId.value = null;
  batchFailures.value = [];
  batchProgress.total = 0;
  batchProgress.done = 0;
  batchProgress.text = '';
  batchVisible.value = true;
}

function resetBatch() {
  batchRows.value = [];
  batchFailures.value = [];
  batchProgress.total = 0;
  batchProgress.done = 0;
  batchProgress.text = '';
}

/**
 * 批量发布：勾选的多条草稿逐条发到**同一个频道**（每条内容仍只挂一个频道，接口与单条发布一致）。
 * 素材未就绪的先跳过并提示；单条失败不影响其余，失败原因在弹窗里逐条列出。
 */
async function doBatchPublish() {
  if (!batchChannelId.value) {
    ElMessage.warning('请选择挂载频道');
    return;
  }
  const list = batchPublishable.value;
  if (!list.length) {
    ElMessage.warning('勾选内容的素材都未就绪（转码中/失败），暂不能发布');
    return;
  }
  const skippedCount = batchSkipped.value.length;
  batchPublishing.value = true;
  batchFailures.value = [];
  batchProgress.total = list.length;
  batchProgress.done = 0;
  batchProgress.text = `正在发布 0 / ${list.length}…`;
  try {
    const res = await publishDraftsSequentially({
      items: list,
      publish: (row) => adminApi.post(`/media/posts/${row.id}/publish`, {
        channelId: batchChannelId.value, operatorId: operatorId()
      }),
      onItemChange: () => {
        batchProgress.done += 1;
        batchProgress.text = `正在发布 ${batchProgress.done} / ${batchProgress.total}…`;
      }
    });
    batchFailures.value = res.failures;
    if (res.failures.length) {
      batchProgress.text = `发布完成：成功 ${res.published.length} 条，失败 ${res.failures.length} 条（原因见下）`;
      ElMessage.warning(`批量发布完成：成功 ${res.published.length} 条，失败 ${res.failures.length} 条`);
    } else {
      batchProgress.text = `已完成：${res.published.length} 条已发布`;
      ElMessage.success(`已发布 ${res.published.length} 条 → 已发布列表${skippedCount ? `（跳过 ${skippedCount} 条素材未就绪）` : ''}`);
      batchVisible.value = false;
      tab.value = 'published';
      page.value = 1;
    }
    await loadRows();
    if (res.failures.length && skippedCount) {
      ElMessage.warning(`另有 ${skippedCount} 条素材未就绪已跳过`);
    }
  } finally {
    batchPublishing.value = false;
  }
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
.toolbar-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.batch-progress { margin-top: 14px; font-size: 12px; }
.batch-progress .ok { color: #1e8e50; }
.batch-progress .warn { color: #a0812d; }
.batch-failures { margin: 8px 0 0; padding-left: 18px; color: #c0392b; font-size: 12px; line-height: 1.7; max-height: 160px; overflow: auto; }
.batch-failures b { color: #26384c; }
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
.hint { font-size: 11px; color: #98a5b5; margin-top: 6px; line-height: 1.7; }
.video-add-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
/* 视频清单（可多个，顺序=内容内顺序，第 1 个为封面） */
.video-list { display: flex; flex-direction: column; gap: 8px; margin-top: 10px; width: 100%; }
.video-row { display: flex; align-items: center; gap: 10px; padding: 8px 10px; border: 1px solid #e5eaf0; border-radius: 8px; background: #fbfcfd; }
.vthumb { width: 64px; height: 44px; flex-shrink: 0; border-radius: 6px; overflow: hidden; background: #eef1f5;
  display: flex; align-items: center; justify-content: center; }
.vthumb img { width: 100%; height: 100%; object-fit: cover; display: block; }
.vthumb .vt-badge { font-size: 10px; padding: 1px 5px; border-radius: 4px; }
.vthumb .vt-badge.wait { background: rgba(230,180,34,.95); color: #5c4300; }
.vthumb .vt-badge.err { background: #fdecea; color: #c0392b; }
.vmain { flex: 1; min-width: 0; }
.vname { font-size: 12px; color: #26384c; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.vstate { display: flex; align-items: center; gap: 6px; margin-top: 4px; font-size: 11px; }
.vstate .ok { color: #1e8e50; }
.vstate .wait { color: #a0812d; }
.vstate .bad { color: #c0392b; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.vops { display: flex; align-items: center; gap: 2px; flex-shrink: 0; }
.no-drag { -webkit-user-drag: none; user-select: none; }
.dir-hint { color: #8a7a4a; }
.editor-footer { display: flex; justify-content: flex-end; gap: 8px; padding: 4px 0; }
.publish-info { background: #f7f9fb; border-radius: 10px; padding: 12px 14px; display: flex; flex-direction: column; gap: 6px; margin-bottom: 14px; }
.publish-info b { font-size: 14px; }
.channel-picker { display: flex; flex-direction: column; gap: 8px; width: 100%; }
.channel-opt { height: auto; padding: 9px 10px; border: 1px solid #e3e9f1; border-radius: 8px; margin-right: 0 !important; width: 100%; }
.detail-media { margin: 10px 0; }
.dt { font-size: 16px; color: #1f2d3d; }
</style>
