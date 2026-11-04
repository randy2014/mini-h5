<template>
  <el-dialog v-model="visible" :title="dialogTitle" width="820px"
             :close-on-click-modal="false" :before-close="onBeforeClose" @closed="resetAll">
    <div class="dir-import">
      <div class="pick-row">
        <!-- 目录选择必须用原生 input：el-upload 不透传 webkitdirectory -->
        <input ref="dirInput" class="dir-input" type="file" webkitdirectory directory multiple @change="onDirPicked" />
        <el-button type="primary" :disabled="running" @click="pickDir">选择目录…</el-button>
        <template v-if="items.length || skippedOther">
          <span class="dir-name" :title="dirName">📂 {{ dirName || '已选目录' }}</span>
          <span class="muted">
            {{ noun }} <b>{{ items.length }}</b> 个 · {{ formatFileSize(totalBytes) }}
            <template v-if="skippedOther"> · 忽略 {{ skippedOther }} 个非{{ noun }}</template>
            <template v-if="skippedCount"> · 跳过 {{ skippedCount }} 个</template>
          </span>
        </template>
        <span v-else class="muted">{{ emptyHint }}</span>
      </div>

      <el-table v-if="items.length" :data="items" height="300" size="small" class="file-table">
        <el-table-column type="index" label="序号" width="64" />
        <el-table-column label="文件" min-width="280" show-overflow-tooltip>
          <template #default="{ row }">{{ row.path }}</template>
        </el-table-column>
        <el-table-column label="大小" width="92">
          <template #default="{ row }">{{ formatFileSize(row.size) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="['st', row.status]" :title="row.reason || ''">{{ statusText(row) }}</span>
            <el-progress v-if="row.status === 'hashing' || row.status === 'uploading'" :percentage="row.percent"
                         :show-text="false" :stroke-width="4" class="st-bar" />
          </template>
        </el-table-column>
      </el-table>

      <div v-else class="empty">
        <p>{{ dirName ? `该目录内没有可加入的${noun}` : '还没有选择目录' }}</p>
        <p class="muted">{{ acceptHint }}</p>
      </div>

      <div v-if="items.length" class="overall">
        <el-progress :percentage="overallPercent" :status="overallStatus" :stroke-width="8" />
        <span class="muted">{{ progressText }}</span>
        <span class="muted">{{ orderHint }}</span>
      </div>
    </div>

    <template #footer>
      <div class="dir-footer">
        <span class="muted tip">
          加入后会自动保存为草稿；<b>已上传过的{{ noun }}会先查重并跳过上传</b><template v-if="isVideo">，转码完成后即可发布</template>
        </span>
        <div class="dir-actions">
          <el-button :disabled="running" @click="visible = false">关闭</el-button>
          <el-button v-if="!running && failedItems" type="warning" plain @click="retryFailed">
            重试失败项（{{ failedItems }}）
          </el-button>
          <el-button v-if="!running" type="primary" :disabled="!pendingCount" @click="start">
            {{ pendingCount ? `加入当前内容（${pendingCount}）` : '已全部加入' }}
          </el-button>
          <el-button v-else type="danger" plain @click="stopRequested = true">停止（当前文件传完即停）</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { adminUploadApi } from '../services/http';
import { buildDirectoryImportItems, formatFileSize, MAX_IMAGES_PER_POST } from '../utils/media';
import { uploadDirectoryItems } from '../utils/mediaImport';
import { findExistingAssetByFile } from '../utils/uploadDedupe';

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  operatorId: { type: Number, default: 1 },
  /** 'video' | 'image'：同一个弹窗服务两种目录导入 */
  kind: { type: String, default: 'video' },
  /** 还能加入多少条（图片=单帖剩余名额）；null/undefined 表示不限 */
  maxCount: { type: Number, default: null }
});
const emit = defineEmits(['update:modelValue', 'uploaded', 'finished']);

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
});

const isVideo = computed(() => props.kind !== 'image');
const noun = computed(() => (isVideo.value ? '视频' : '图片'));
const dialogTitle = computed(() => `📁 选择目录（${noun.value}加入当前内容）`);
const emptyHint = computed(() => (isVideo.value
  ? '选一个本地目录：里面每个视频都会加进当前这条内容（同一个标题，第 1 个为封面）'
  : `选一个本地目录：里面每张图片都会加进当前这条内容（单帖最多 ${MAX_IMAGES_PER_POST} 张）`));
const acceptHint = computed(() => (isVideo.value
  ? '支持 mp4 / mov / mkv / webm / avi / flv / m4v；单文件 ≤300MB；非视频文件自动忽略'
  : `支持 jpg / png / gif / bmp（不支持 WebP）；单帖最多 ${MAX_IMAGES_PER_POST} 张，超出自动跳过`));
const orderHint = computed(() => (isVideo.value
  ? '序号即这支内容里的顺序（按文件名自然排序），第 1 个视频为封面'
  : '序号即内容内图片顺序（按文件名自然排序），第 1 张为封面（若同时挂了视频则以第 1 个视频为封面）'));

const dirInput = ref(null);
/** 待上传清单：[{ path, name, size, status, percent, reason, assetId, assetStatus }] */
const items = ref([]);
const skippedOther = ref(0);
const dirName = ref('');
const running = ref(false);
const stopRequested = ref(false);

const pendingCount = computed(() => items.value.filter((i) => i.status === 'idle').length);
const failedItems = computed(() => items.value.filter((i) => i.status === 'failed').length);
const skippedCount = computed(() => items.value.filter((i) => i.status === 'skip').length);
/** 已在素材库、直接复用（没重传）的条数 */
const reusedCount = computed(() => items.value.filter((i) => i.status === 'done' && i.reused).length);
const settledCount = computed(() => items.value.filter((i) => ['done', 'failed', 'skip'].includes(i.status)).length);
const totalBytes = computed(() => items.value.reduce((sum, i) => sum + i.size, 0));
const overallPercent = computed(() =>
  items.value.length ? Math.round((settledCount.value / items.value.length) * 100) : 0
);
const overallStatus = computed(() => {
  if (running.value || !items.value.length) return '';
  if (failedItems.value) return 'exception';
  return settledCount.value === items.value.length ? 'success' : '';
});
const progressText = computed(() => {
  if (!items.value.length) return '';
  const done = items.value.filter((i) => i.status === 'done').length;
  const failed = failedItems.value;
  if (running.value) {
    return `校验/上传中：已完成 ${settledCount.value} / ${items.value.length}（成功 ${done}，失败 ${failed}）`;
  }
  if (settledCount.value !== items.value.length) {
    if (done || failed) {
      return `已停止：成功 ${done} 个${failed ? `，失败 ${failed} 个` : ''}，待加入 ${pendingCount.value} 个`;
    }
    return `待加入 ${pendingCount.value} 个`;
  }
  const parts = [`成功 ${done} 个`];
  if (reusedCount.value) parts.push(`其中 ${reusedCount.value} 个已在素材库（跳过上传）`);
  if (failed) parts.push(`失败 ${failed} 个`);
  if (skippedCount.value) parts.push(`跳过 ${skippedCount.value} 个`);
  return `上传结束：${parts.join('，')}`;
});

function pickDir() {
  dirInput.value?.click();
}

function onDirPicked(event) {
  const picked = Array.from(event.target?.files || []);
  // 清空 value，允许再次选择同一个目录
  event.target.value = '';
  if (!picked.length) return;
  const firstPath = picked[0].webkitRelativePath || '';
  dirName.value = firstPath.includes('/') ? firstPath.split('/')[0] : '';
  const result = buildDirectoryImportItems(picked, {
    kind: props.kind,
    maxCount: Number.isFinite(Number(props.maxCount)) ? Number(props.maxCount) : null
  });
  items.value = result.items;
  skippedOther.value = result.skippedOther;
  if (!result.items.length) {
    ElMessage.warning(`这个目录里没有可加入的${noun.value}${result.skippedOther ? `（已忽略 ${result.skippedOther} 个其它文件）` : ''}`);
  } else if (result.overCap) {
    ElMessage.warning(`单帖最多 ${MAX_IMAGES_PER_POST} 张，超出 ${result.overCap} 张已自动跳过`);
  }
}

function statusText(row) {
  switch (row.status) {
    case 'hashing':
      return `校验中 ${row.percent}%`;
    case 'uploading':
      return `上传中 ${row.percent}%`;
    case 'done':
      if (row.reused) return '已存在 · 跳过上传';
      return isVideo.value && row.assetStatus === 1 ? '已加入 · 转码中' : '已加入';
    case 'failed':
      return `失败：${row.reason || '未知原因'}`;
    case 'skip':
      return row.reason || '已跳过';
    default:
      return '等待上传';
  }
}

async function start() {
  if (running.value) return;
  const todo = items.value.filter((i) => i.status === 'idle');
  if (!todo.length) return;
  // 目录名快照：用户中途关弹窗会 resetAll 清空 dirName，标题兜底还要用
  const dirNameSnapshot = dirName.value;
  const fileType = isVideo.value ? 2 : 1;
  running.value = true;
  stopRequested.value = false;
  let counts = { uploaded: 0, failed: 0 };
  try {
    counts = await uploadDirectoryItems({
      items: todo,
      shouldStop: () => stopRequested.value,
      // 每个文件先算 md5 查重：素材库已有（READY）就复用，跳过整包上传；否则走流式上传
      upload: async (item, onPercent) => {
        item.status = 'hashing';
        item.percent = 0;
        const existing = await findExistingAssetByFile(item.file, {
          onProgress: (ratio) => {
            item.percent = Math.min(99, Math.round(ratio * 100));
          }
        });
        if (existing && existing.fileType === fileType) {
          item.reused = true;
          item.posterPath = existing.posterPath || null;
          return { ok: true, assetId: existing.id, status: existing.status, reused: true };
        }
        item.status = 'uploading';
        item.percent = 0;
        // 与「添加素材」共用流式上传入口：关超时 + 请求体直写媒体卷（只写一遍盘）
        return adminUploadApi.post('/media/assets/upload-stream', item.file, {
          params: { fileName: item.name, operatorId: props.operatorId },
          headers: { 'Content-Type': 'application/octet-stream' },
          maxBodyLength: Infinity,
          maxContentLength: Infinity,
          onUploadProgress: (e) => {
            if (e.total) {
              onPercent(Math.round((e.loaded / e.total) * 100));
            }
          }
        });
      },
      onUploaded: (item) => {
        // 交给父级挂进当前内容（父级负责建草稿：同一标题，视频第 1 个为封面）
        emit('uploaded', {
          id: item.assetId,
          fileType,
          status: item.assetStatus,
          originalName: item.name,
          posterPath: item.posterPath || null,
          failReason: null,
          reused: !!item.reused
        });
      }
    });
  } finally {
    running.value = false;
  }
  if (counts.uploaded) {
    emit('finished', { uploaded: counts.uploaded, failed: counts.failed, dirName: dirNameSnapshot });
  } else if (counts.failed) {
    ElMessage.warning(`${counts.failed} 个${noun.value}上传失败，可点「重试失败项」再试`);
  }
}

function retryFailed() {
  items.value.forEach((i) => {
    if (i.status === 'failed') {
      i.status = 'idle';
      i.reason = '';
      i.percent = 0;
    }
  });
  start();
}

function onBeforeClose(done) {
  if (!running.value) {
    done();
    return;
  }
  ElMessageBox.confirm(`正在上传，关闭会中断剩余文件（已加入的${noun.value}已保存）。确认关闭？`, '上传进行中', {
    confirmButtonText: '中断并关闭',
    cancelButtonText: '继续上传',
    type: 'warning'
  })
    .then(() => {
      stopRequested.value = true;
      done();
    })
    .catch(() => {});
}

function resetAll() {
  // 只清展示数据：running / stopRequested 归 start() 与「中断并关闭」管，
  // 否则弹窗一关就把中断标记抹掉，后台循环会继续把剩余文件全传完。
  items.value = [];
  skippedOther.value = 0;
  dirName.value = '';
}
</script>

<style scoped>
.dir-input { display: none; }
.pick-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; min-height: 32px; }
.dir-name { font-size: 12px; color: #26384c; font-weight: 600; max-width: 240px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-table { margin-top: 12px; width: 100%; }
.st { font-size: 12px; color: #55657a; }
.st.done { color: #1e8e50; font-weight: 600; }
.st.failed { color: #c0392b; font-weight: 600; }
.st.skip { color: #a0812d; }
.st.uploading { color: #2f6fd8; }
.st.hashing { color: #8a6d3b; }
.st-bar { margin-top: 3px; }
.empty { padding: 26px 0; text-align: center; color: #55657a; }
.empty p { margin: 4px 0; }
.overall { margin-top: 14px; display: flex; flex-direction: column; gap: 6px; }
.dir-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.dir-footer .tip { font-size: 11px; }
.dir-actions { display: flex; gap: 8px; }
.muted { color: #98a5b5; font-size: 12px; }
.muted b { color: #e07b39; }
</style>
