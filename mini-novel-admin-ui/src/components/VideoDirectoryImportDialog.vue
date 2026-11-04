<template>
  <el-dialog v-model="visible" title="📁 选择目录批量导入视频" width="820px"
             :close-on-click-modal="false" :before-close="onBeforeClose" @closed="resetAll">
    <div class="dir-import">
      <div class="pick-row">
        <!-- 目录选择必须用原生 input：el-upload 不透传 webkitdirectory -->
        <input ref="dirInput" class="dir-input" type="file" webkitdirectory directory multiple @change="onDirPicked" />
        <el-button type="primary" :disabled="running" @click="pickDir">选择目录…</el-button>
        <template v-if="items.length || skippedNonVideo">
          <span class="dir-name" :title="dirName">📂 {{ dirName || '已选目录' }}</span>
          <span class="muted">
            视频 <b>{{ items.length }}</b> 个 · {{ formatFileSize(totalBytes) }}
            <template v-if="skippedNonVideo"> · 忽略 {{ skippedNonVideo }} 个非视频文件</template>
            <template v-if="oversizeCount"> · 超限跳过 {{ oversizeCount }} 个</template>
          </span>
        </template>
        <span v-else class="muted">文件夹里每个视频会各自生成一条视频内容（草稿），标题取文件名</span>
      </div>

      <el-table v-if="items.length" :data="items" height="300" size="small" class="file-table">
        <el-table-column type="index" label="#" width="46" />
        <el-table-column label="文件" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ row.path }}</template>
        </el-table-column>
        <el-table-column label="标题（取文件名）" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.title }}</template>
        </el-table-column>
        <el-table-column label="大小" width="92">
          <template #default="{ row }">{{ formatFileSize(row.size) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="['st', row.status]" :title="row.reason || ''">{{ statusText(row) }}</span>
            <el-progress v-if="row.status === 'uploading'" :percentage="row.percent" :show-text="false"
                         :stroke-width="4" class="st-bar" />
          </template>
        </el-table-column>
      </el-table>

      <div v-else class="empty">
        <p>{{ dirName ? '该目录内没有可导入的视频' : '还没有选择目录' }}</p>
        <p class="muted">支持 mp4 / mov / mkv / webm / avi / flv / m4v；单文件 ≤300MB；非视频文件自动忽略</p>
      </div>

      <div v-if="items.length" class="overall">
        <el-progress :percentage="overallPercent" :status="overallStatus" :stroke-width="8" />
        <span class="muted">{{ progressText }}</span>
      </div>
    </div>

    <template #footer>
      <div class="dir-footer">
        <span class="muted tip">导入结果是草稿；视频转码在后台进行，完成后即可在「草稿列表」发布到频道</span>
        <div class="dir-actions">
          <el-button :disabled="running" @click="visible = false">关闭</el-button>
          <el-button v-if="!running && failedItems" type="warning" plain @click="retryFailed">
            重试失败项（{{ failedItems }}）
          </el-button>
          <el-button v-if="!running" type="primary" :disabled="!pendingCount" @click="start">
            {{ pendingCount ? `开始导入（${pendingCount}）` : '导入完成' }}
          </el-button>
          <el-button v-else type="danger" plain @click="stopRequested = true">停止（当前文件上传完即停）</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { adminApi, adminUploadApi } from '../services/http';
import { buildDirectoryImportItems, formatFileSize } from '../utils/media';
import { importDirectoryItems } from '../utils/mediaImport';

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  operatorId: { type: Number, default: 1 }
});
const emit = defineEmits(['update:modelValue', 'imported']);

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
});

const dirInput = ref(null);
/** 待导入清单：[{ path, name, title, size, status, percent, reason, assetId, assetStatus, postId }] */
const items = ref([]);
const skippedNonVideo = ref(0);
const dirName = ref('');
const running = ref(false);
const stopRequested = ref(false);

const pendingCount = computed(() => items.value.filter((i) => i.status === 'idle').length);
const failedItems = computed(() => items.value.filter((i) => i.status === 'failed').length);
const oversizeCount = computed(() => items.value.filter((i) => i.status === 'skip').length);
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
  const skipped = oversizeCount.value;
  if (running.value) {
    return `导入中：已完成 ${settledCount.value} / ${items.value.length}（成功 ${done}，失败 ${failed}）`;
  }
  if (settledCount.value !== items.value.length) {
    if (done || failed) {
      return `已停止：成功 ${done} 个${failed ? `，失败 ${failed} 个` : ''}，待导入 ${pendingCount.value} 个`;
    }
    return `待导入 ${pendingCount.value} 个`;
  }
  const parts = [`成功 ${done} 个`];
  if (failed) parts.push(`失败 ${failed} 个`);
  if (skipped) parts.push(`跳过 ${skipped} 个`);
  return `导入结束：${parts.join('，')}`;
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
  const { items: list, skippedNonVideo: skipped } = buildDirectoryImportItems(picked);
  items.value = list;
  skippedNonVideo.value = skipped;
  if (!list.length) {
    ElMessage.warning(`这个目录里没有可导入的视频${skipped ? `（已忽略 ${skipped} 个非视频文件）` : ''}`);
  }
}

function statusText(row) {
  switch (row.status) {
    case 'uploading':
      return `上传中 ${row.percent}%`;
    case 'creating':
      return '创建内容…';
    case 'done':
      return row.assetStatus === 1 ? '已完成 · 转码中' : '已完成';
    case 'failed':
      return `失败：${row.reason || '未知原因'}`;
    case 'skip':
      return row.reason || '已跳过';
    default:
      return '等待导入';
  }
}

/** 逐条导入：流式上传 → 以「标题=文件名」建一条草稿（流程与单测见 utils/mediaImport.js）。 */
async function start() {
  if (running.value) return;
  const todo = items.value.filter((i) => i.status === 'idle');
  if (!todo.length) return;
  running.value = true;
  stopRequested.value = false;
  let counts = { created: 0, failed: 0 };
  try {
    counts = await importDirectoryItems({
      items: todo,
      shouldStop: () => stopRequested.value,
      // 与「添加视频」共用流式上传入口：关超时 + 请求体直写媒体卷（只写一遍盘）
      upload: (item, onPercent) => adminUploadApi.post('/media/assets/upload-stream', item.file, {
        params: { fileName: item.name, operatorId: props.operatorId },
        headers: { 'Content-Type': 'application/octet-stream' },
        maxBodyLength: Infinity,
        maxContentLength: Infinity,
        onUploadProgress: (e) => {
          if (e.total) {
            onPercent(Math.round((e.loaded / e.total) * 100));
          }
        }
      }),
      createPost: (item) => adminApi.post('/media/posts', {
        title: item.title,
        assetIds: [item.assetId],
        operatorId: props.operatorId
      }),
      removeAsset: (assetId) => adminApi.delete(`/media/assets/${assetId}`)
    });
  } finally {
    running.value = false;
  }
  // 用本轮返回值计数：用户中途关弹窗时 items 已被 reset，不能靠遍历 items 统计
  if (counts.created) {
    ElMessage.success(`已生成 ${counts.created} 条视频内容草稿（后台转码中，完成后可在草稿列表发布）`);
    emit('imported', { created: counts.created, failed: counts.failed });
  }
  if (counts.failed) {
    ElMessage.warning(`${counts.failed} 个视频导入失败，可点「重试失败项」再试`);
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
  ElMessageBox.confirm('正在导入，关闭会中断剩余文件（已完成的内容草稿不受影响）。确认关闭？', '导入进行中', {
    confirmButtonText: '中断并关闭',
    cancelButtonText: '继续导入',
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
  skippedNonVideo.value = 0;
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
.st.creating { color: #7a3fe0; }
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
