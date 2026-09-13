<template>
  <section>
    <el-card shadow="never">
      <div class="scope-tip">
        <span>列表中只显示<strong>尚未加入订阅频道</strong>的文章；已加入的文章请到「订阅频道管理 → 查看频道详情」查看或移出（移出后会重新出现在这里）。</span>
        <el-button link type="primary" @click="$router.push('/admin/subscribe-channels')">去订阅频道管理</el-button>
      </div>
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="搜索标题/作者" clearable @keyup.enter="load" />
        <el-select v-model="query.status" placeholder="状态" clearable>
          <el-option label="正常" :value="1" />
          <el-option label="完结" :value="2" />
          <el-option label="下架" :value="0" />
          <el-option label="草稿" :value="3" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="openEdit()">新增文章</el-button>
        <el-button type="success" @click="openImport">TXT 导入</el-button>
        <el-button type="warning" :disabled="!selectedNovels.length" @click="openBatchJoin">
          批量加入频道{{ selectedNovels.length ? `（${selectedNovels.length}）` : '' }}
        </el-button>
      </div>
      <el-table ref="tableRef" :data="rows" v-loading="loading" row-key="id" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="46" />
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column prop="author" label="作者" width="130" />
        <el-table-column prop="wordCount" label="字数" width="100" />
        <el-table-column prop="latestChapterTitle" label="最新章节" min-width="220" />
        <el-table-column prop="vipRequired" label="VIP" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.vipRequired" type="warning">VIP</el-tag>
            <span v-else>免费</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'danger' : 'success'">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="185"><template #default="{row}">{{formatDateTime(row.updatedAt)}}</template></el-table-column>
        <el-table-column label="操作" width="340" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openView(row)">查看</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="openChapters(row)">章节</el-button>
            <el-button link type="primary" @click="openJoinChannel(row)">加入频道</el-button>
            <el-button link :type="row.status === 0 ? 'success' : 'danger'" @click="toggleStatus(row)">
              {{ row.status === 0 ? '上架' : '下架' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="editVisible" title="文章编辑" width="680px">
      <el-form label-width="110px">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="作者"><el-input v-model="form.author" /></el-form-item>
        <el-form-item label="分类ID"><el-input-number v-model="form.categoryId" :min="1" /></el-form-item>
        <el-form-item label="整本VIP"><el-switch v-model="form.vipRequired" /></el-form-item>
        <el-form-item label="免费章节数"><el-input-number v-model="form.freeChapterCount" :min="0" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="下架" :value="0" />
            <el-option label="正常" :value="1" />
            <el-option label="完结" :value="2" />
            <el-option label="草稿" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="简介"><el-input v-model="form.intro" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importVisible" title="TXT 正文导入" width="840px">
      <el-form label-width="110px">
        <el-form-item label="标题"><el-input v-model="importForm.title" /></el-form-item>
        <el-form-item label="作者"><el-input v-model="importForm.author" /></el-form-item>
        <el-form-item label="分类ID"><el-input-number v-model="importForm.categoryId" :min="1" /></el-form-item>
        <el-form-item label="整本VIP"><el-switch v-model="importForm.vipRequired" /></el-form-item>
        <el-form-item label="免费章节数"><el-input-number v-model="importForm.freeChapterCount" :min="0" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="importForm.status">
            <el-option label="正常" :value="1" />
            <el-option label="完结" :value="2" />
            <el-option label="草稿" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="简介"><el-input v-model="importForm.intro" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="正文">
          <el-input
            v-model="importForm.content"
            type="textarea"
            :rows="16"
            placeholder="粘贴 TXT 正文。支持按“第一章 标题 / 第1章 标题 / Chapter 1”自动切分章节。"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="import-hint">当前字数：{{ importForm.content.length }}</span>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="submitImport">导入正文</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="chapterVisible" title="章节管理" size="720px">
      <el-table :data="chapters">
        <el-table-column prop="chapterNo" label="序号" width="80" />
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column prop="vip" label="VIP" width="100">
          <template #default="{ row }"><el-switch v-model="row.vip" @change="saveChapterVip(row)" /></template>
        </el-table-column>
        <el-table-column prop="priceCoin" label="价格" width="110">
          <template #default="{ row }"><el-input-number v-model="row.priceCoin" :min="0" size="small" @change="saveChapterVip(row)" /></template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="openContent(row)">查看正文</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <el-dialog v-model="joinVisible" :title="joinMode === 'batch' ? '批量加入订阅频道' : '加入订阅频道'" width="560px">
      <el-form label-width="100px">
        <el-form-item :label="joinMode === 'batch' ? '已选小说' : '小说'">
          <span v-if="joinMode === 'single'">{{ currentNovel?.title }}</span>
          <span v-else class="join-titles">
            共 {{ batchNovels.length }} 本：{{ batchNovels.map((n) => n.title).join('、') }}
          </span>
        </el-form-item>
        <el-form-item label="频道">
          <div v-loading="joinLoading" class="join-list">
            <div v-for="c in channels" :key="c.id" class="join-row">
              <span class="join-name">
                <span class="join-title">{{ c.name }}</span>
                <el-tag v-if="c.status !== 'PUBLISHED'" size="small" type="info" effect="plain">已下架</el-tag>
                <span class="join-count">小说 {{ c.novelCount ?? 0 }} · 图文视频 {{ c.mediaCount ?? 0 }}</span>
              </span>
              <template v-if="joinMode === 'single' && joinedChannelIds.includes(c.id)">
                <el-tag size="small" type="success" effect="plain">已加入</el-tag>
                <el-button link type="danger" @click="removeFromChannel(c)">移出</el-button>
              </template>
              <el-button
                v-else
                link
                type="primary"
                :loading="joiningChannelId === c.id"
                @click="joinChannel(c)"
              >
                加入
              </el-button>
            </div>
            <p v-if="!channels.length" class="join-empty">
              暂无订阅频道，请先在「订阅频道管理」新增并发布频道
            </p>
          </div>
        </el-form-item>
        <el-form-item v-if="joinMode === 'batch' && batchResult" label="加入结果">
          <span class="join-result">{{ batchResult }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="joinVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="contentVisible" title="章节正文" width="720px">
      <div class="content-body" style="white-space: pre-wrap; max-height: 60vh; overflow: auto; line-height: 1.8; font-size: 14px;">{{ chapterContent }}</div>
      <template #footer><el-button @click="contentVisible = false">关闭</el-button></template>
    </el-dialog>

    <!-- 查看文章抽屉已抽为公共组件，与「订阅频道详情」复用同一实现 -->
    <ArticleViewerDrawer v-model="viewVisible" :novel="viewNovel" />
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { adminApi } from '../services/http';
import { formatDateTime } from '../utils/date';
import ArticleViewerDrawer from '../components/ArticleViewerDrawer.vue';

const loading = ref(false);
const importing = ref(false);
const rows = ref([]);
const chapters = ref([]);
const editVisible = ref(false);
const importVisible = ref(false);
const chapterVisible = ref(false);
const query = reactive({ keyword: '', status: null });
const form = reactive({});
const importForm = reactive(defaultImportForm());
const channels = ref([]);
const joinVisible = ref(false);
const joinLoading = ref(false);
const joiningChannelId = ref(null);
const joinedChannelIds = ref([]);
const joinMode = ref('single');
const batchResult = ref('');
const currentNovel = ref(null);
const selectedNovels = ref([]);
const tableRef = ref(null);
// 批量弹窗的选中快照：加入成功后列表会刷新（已入频道的行消失），快照用于继续加入其它频道
const batchNovels = ref([]);
const contentVisible = ref(false);
const chapterContent = ref('');

// —— 查看文章 ——（抽屉实现见 components/ArticleViewerDrawer.vue）
const viewVisible = ref(false);
const viewNovel = ref({});

function defaultImportForm() {
  return {
    title: '手动导入示例小说',
    author: '人工导入',
    categoryId: 1,
    status: 1,
    vipRequired: false,
    freeChapterCount: 0,
    intro: '后台手动导入的正文内容',
    content: ''
  };
}

function statusText(status) {
  return ({ 0: '下架', 1: '正常', 2: '完结', 3: '草稿', 4: '审核中' })[status] || '未知';
}

function openView(row) {
  viewNovel.value = row;
  viewVisible.value = true;
}

async function load() {
  loading.value = true;
  try {
    rows.value = await adminApi.get('/novels', { params: query });
  } finally {
    loading.value = false;
  }
}

function openEdit(row) {
  Object.keys(form).forEach((key) => delete form[key]);
  Object.assign(form, row || { title: '', author: '', categoryId: 1, status: 1, vipRequired: false, freeChapterCount: 0 });
  editVisible.value = true;
}

async function save() {
  if (form.id) {
    await adminApi.put(`/novels/${form.id}`, form);
  } else {
    await adminApi.post('/novels', form);
  }
  ElMessage.success('已保存');
  editVisible.value = false;
  load();
}

function openImport() {
  Object.assign(importForm, defaultImportForm());
  importVisible.value = true;
}

async function submitImport() {
  importing.value = true;
  try {
    const novel = await adminApi.post('/novels/import-text', importForm);
    ElMessage.success(`导入成功：${novel.title}`);
    importVisible.value = false;
    await load();
  } finally {
    importing.value = false;
  }
}

async function toggleStatus(row) {
  const status = row.status === 0 ? 1 : 0;
  let reason = '';
  if (status === 0) {
    const result = await ElMessageBox.prompt('请输入下架原因', '文章下架', { inputPlaceholder: '版权/违规/人工处理' });
    reason = result.value;
  }
  await adminApi.put(`/novels/${row.id}/status`, { status, reason, operatorId: 1 });
  ElMessage.success(status === 0 ? '已下架' : '已上架');
  load();
}

async function openChapters(row) {
  chapters.value = await adminApi.get(`/novels/${row.id}/chapters`);
  chapterVisible.value = true;
}

async function saveChapterVip(row) {
  await adminApi.put(`/novels/chapters/${row.id}/vip`, { vip: row.vip, priceCoin: row.priceCoin });
  ElMessage.success('章节 VIP 已更新');
}

function onSelectionChange(rows) {
  selectedNovels.value = rows || [];
}

function openJoinChannel(row) {
  currentNovel.value = row;
  joinMode.value = 'single';
  batchResult.value = '';
  channels.value = [];
  joinedChannelIds.value = [];
  joinVisible.value = true;
  loadChannels(row.id);
}

// 批量：勾选多本小说后一次加入同一个频道（整批幂等，后端返回新增/跳过/不存在数量）
function openBatchJoin() {
  if (!selectedNovels.value.length) {
    ElMessage.warning('请先勾选要加入频道的小说');
    return;
  }
  currentNovel.value = null;
  joinMode.value = 'batch';
  batchResult.value = '';
  batchNovels.value = [...selectedNovels.value];
  channels.value = [];
  joinedChannelIds.value = [];
  joinVisible.value = true;
  loadChannels(null);
}

// 频道下拉（含内容统计）；单个模式下再取该小说已加入的频道
async function loadChannels(novelId) {
  joinLoading.value = true;
  try {
    const [list, joined] = await Promise.all([
      adminApi.get('/subscribe-channels'),
      novelId ? adminApi.get(`/subscribe-channels/novels/${novelId}`) : Promise.resolve([])
    ]);
    channels.value = list || [];
    joinedChannelIds.value = joined || [];
  } finally {
    joinLoading.value = false;
  }
}

async function joinChannel(channel) {
  joiningChannelId.value = channel.id;
  try {
    if (joinMode.value === 'batch') {
      const novelIds = batchNovels.value.map((n) => n.id);
      const r = await adminApi.post(`/subscribe-channels/${channel.id}/novels/batch`, {
        novelIds,
        operatorId: 1
      });
      batchResult.value =
        `「${channel.name}」新增 ${r.added} 本` +
        (r.skipped ? `，已在频道 ${r.skipped} 本` : '') +
        (r.notFound ? `，小说不存在 ${r.notFound} 本` : '');
      ElMessage.success(batchResult.value);
      await Promise.all([load(), loadChannels(null)]);
      tableRef.value?.clearSelection();
    } else {
      await adminApi.post(`/subscribe-channels/${channel.id}/novels`, {
        novelId: currentNovel.value.id,
        operatorId: 1
      });
      ElMessage.success(`《${currentNovel.value.title}》已加入「${channel.name}」`);
      // 加入后该文章不再属于本列表（已入频道），刷新后从列表消失
      await Promise.all([load(), loadChannels(currentNovel.value.id)]);
    }
  } finally {
    joiningChannelId.value = null;
  }
}

async function removeFromChannel(channel) {
  try {
    await ElMessageBox.confirm(`将《${currentNovel.value.title}》从「${channel.name}」移出？`, '确认');
  } catch {
    return;
  }
  await adminApi.delete(`/subscribe-channels/${channel.id}/novels/${currentNovel.value.id}`);
  ElMessage.success('已移出频道');
  // 移出后该文章重新回到本列表
  await Promise.all([load(), loadChannels(currentNovel.value.id)]);
}

async function openContent(row) {
  const chapter = await adminApi.get(`/novels/chapters/${row.id}/content`);
  chapterContent.value = chapter.content || '';
  contentVisible.value = true;
}

onMounted(load);
</script>

<style scoped>
.scope-tip {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  margin-bottom: 12px; padding: 8px 12px; border-radius: 8px;
  background: #f2f7f5; color: #55657a; font-size: 13px; line-height: 1.6;
}
.scope-tip strong { color: #1f6f64; }
.join-list { width: 100%; max-height: 320px; overflow: auto; }
.join-row { display: flex; align-items: center; gap: 8px; padding: 6px 2px; border-bottom: 1px dashed #eef1f5; }
.join-row:last-of-type { border-bottom: none; }
.join-name { flex: 1; min-width: 0; display: flex; align-items: center; gap: 6px; overflow: hidden; }
.join-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.join-count { color: #98a5b5; font-size: 12px; flex-shrink: 0; }
.join-titles { color: #55657a; font-size: 13px; line-height: 1.7; max-height: 66px; overflow: auto; display: block; }
.join-result { color: #1f6f64; font-size: 13px; line-height: 1.7; }
.join-empty { margin: 12px 0; text-align: center; color: #98a5b5; font-size: 12px; }
</style>
