<template>
  <section class="channel-detail">
    <el-card shadow="never">
      <div class="cd-head">
        <div class="cd-title">
          <el-button @click="$router.push('/admin/subscribe-channels')">← 返回列表</el-button>
          <h3>{{ channel.name || '-' }}</h3>
          <el-tag :type="channel.status === 'PUBLISHED' ? 'success' : 'info'">
            {{ channel.status === 'PUBLISHED' ? '已发布' : '已下架' }}
          </el-tag>
        </div>
        <div class="cd-stats">
          <div class="cd-stat"><b>{{ channel.novelCount ?? 0 }}</b><span>频道小说</span></div>
          <div class="cd-stat"><b>{{ channel.mediaCount ?? 0 }}</b><span>图文视频</span></div>
          <div class="cd-stat"><b>{{ (channel.novelCount ?? 0) + (channel.mediaCount ?? 0) }}</b><span>内容合计</span></div>
        </div>
      </div>
      <el-descriptions :column="4" size="small" border class="cd-desc">
        <el-descriptions-item label="频道 ID">{{ channel.id }}</el-descriptions-item>
        <el-descriptions-item label="排序">{{ channel.sort }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(channel.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatDateTime(channel.updatedAt) }}</el-descriptions-item>
        <el-descriptions-item label="简介" :span="4">{{ channel.description || '无简介' }}</el-descriptions-item>
      </el-descriptions>
      <p v-if="channel.status !== 'PUBLISHED'" class="cd-warn">
        该频道当前未发布，会员端不可见；在「订阅频道管理」发布后，下方内容才会对会员展示。
      </p>
    </el-card>

    <el-card shadow="never">
      <el-tabs v-model="tab">
        <el-tab-pane :label="`频道小说（${novelTotal}）`" name="novel">
          <div class="toolbar">
            <el-input
              v-model="novelKeyword"
              placeholder="搜索标题/作者"
              clearable
              style="width: 220px"
              @keyup.enter="searchNovels"
              @clear="searchNovels"
            />
            <el-button type="primary" @click="searchNovels">查询</el-button>
            <el-button @click="$router.push('/admin/articles')">去VIP文章管理加入小说</el-button>
          </div>
          <el-table :data="novels" v-loading="novelLoading" row-key="id">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
            <el-table-column prop="author" label="作者" width="120" show-overflow-tooltip />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 0 ? 'danger' : 'success'" size="small">
                  {{ statusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="VIP" width="80">
              <template #default="{ row }">
                <el-tag v-if="row.vipRequired" type="warning" size="small">VIP</el-tag>
                <span v-else class="muted">免费</span>
              </template>
            </el-table-column>
            <el-table-column prop="wordCount" label="字数" width="100" />
            <el-table-column prop="latestChapterTitle" label="最新章节" min-width="180" show-overflow-tooltip />
            <el-table-column label="加入时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.joinedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button link type="danger" @click="removeNovel(row)">移出频道</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="novelPage"
            :page-size="novelPageSize"
            :total="novelTotal"
            layout="total,prev,pager,next"
            class="cd-pager"
            @current-change="loadNovels"
          />
        </el-tab-pane>

        <el-tab-pane :label="`图文视频（${postTotal}）`" name="post">
          <div class="toolbar">
            <el-button @click="$router.push('/admin/media-pool')">去多媒体池子发布内容</el-button>
            <el-button @click="loadPosts">刷新</el-button>
          </div>
          <div v-loading="postLoading" class="cd-posts">
            <div v-for="item in posts" :key="item.post.id" class="cd-post" @click="openPreview(item)">
              <div class="cd-post-cover">
                <img v-if="coverUrl(item)" :src="coverUrl(item)" alt="封面" />
                <span v-else class="cd-post-empty">无封面</span>
              </div>
              <div class="cd-post-body">
                <div class="cd-post-title">{{ item.post.title }}</div>
                <div class="cd-post-tags">
                  <el-tag size="small" :type="typeTag(item.post.type)">{{ typeText(item.post.type) }}</el-tag>
                  <el-tag v-if="countOf(item, 'IMAGE')" size="small" effect="plain">图 {{ countOf(item, 'IMAGE') }}</el-tag>
                  <el-tag v-if="countOf(item, 'VIDEO')" size="small" effect="plain">视频 {{ countOf(item, 'VIDEO') }}</el-tag>
                </div>
                <div class="cd-post-time">{{ formatDateTime(item.post.publishedAt) }}</div>
              </div>
            </div>
            <p v-if="!posts.length && !postLoading" class="cd-empty">
              该频道还没有已发布的图文/视频内容，可在「多媒体池子」创建后发布到本频道
            </p>
          </div>
          <el-pagination
            v-model:current-page="postPage"
            :page-size="postPageSize"
            :total="postTotal"
            layout="total,prev,pager,next"
            class="cd-pager"
            @current-change="loadPosts"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="previewVisible" :title="preview?.post?.title || '内容预览'" width="760px">
      <div class="cd-preview">
        <template v-for="a in preview?.assets || []" :key="a.id">
          <video v-if="a.fileType === 'VIDEO'" :src="assetUrl(a, 'main')" controls preload="metadata" class="cd-preview-video" />
          <el-image v-else :src="assetUrl(a, 'main')" fit="contain" class="cd-preview-image" preview-teleported
                    :preview-src-list="previewImages" />
        </template>
        <p v-if="!(preview?.assets || []).length" class="cd-empty">该内容没有素材</p>
      </div>
      <template #footer>
        <span class="muted">发布 {{ formatDateTime(preview?.post?.publishedAt) }} · {{ typeText(preview?.post?.type) }}</span>
        <el-button @click="previewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { adminApi } from '../services/http';
import { formatDateTime } from '../utils/date';

const route = useRoute();
const channelId = route.params.id;

const channel = ref({});
const tab = ref('novel');

const novels = ref([]);
const novelLoading = ref(false);
const novelKeyword = ref('');
const novelPage = ref(1);
const novelPageSize = 20;
const novelTotal = ref(0);

const posts = ref([]);
const postLoading = ref(false);
const postPage = ref(1);
const postPageSize = 12;
const postTotal = ref(0);

const previewVisible = ref(false);
const preview = ref(null);

const previewImages = computed(() =>
  (preview.value?.assets || []).filter((a) => a.fileType === 'IMAGE').map((a) => assetUrl(a, 'main'))
);

function statusText(status) {
  return ({ 0: '下架', 1: '正常', 2: '完结', 3: '草稿', 4: '审核中' })[status] || '未知';
}

function typeText(type) {
  return ({ IMAGE: '图文', VIDEO: '视频', MIXED: '图文+视频' })[type] || '内容';
}

function typeTag(type) {
  return ({ IMAGE: 'success', VIDEO: 'warning', MIXED: 'primary' })[type] || 'info';
}

function assetUrl(asset, kind) {
  if (!asset) {
    return '';
  }
  return `/admin-api/media/assets/${asset.id}/file?kind=${kind}`;
}

function coverUrl(item) {
  const cover = item?.cover;
  if (!cover) {
    return '';
  }
  return assetUrl(cover, cover.fileType === 'VIDEO' ? 'poster' : 'thumb');
}

function countOf(item, fileType) {
  return (item?.assets || []).filter((a) => a.fileType === fileType).length;
}

function openPreview(item) {
  preview.value = item;
  previewVisible.value = true;
}

async function loadChannel() {
  channel.value = (await adminApi.get(`/subscribe-channels/${channelId}/detail`)) || {};
}

async function loadNovels() {
  novelLoading.value = true;
  try {
    const page = await adminApi.get(`/subscribe-channels/${channelId}/novels`, {
      params: { keyword: novelKeyword.value || undefined, page: novelPage.value, pageSize: novelPageSize }
    });
    novels.value = page?.records || [];
    novelTotal.value = page?.total || 0;
  } finally {
    novelLoading.value = false;
  }
}

function searchNovels() {
  novelPage.value = 1;
  loadNovels();
}

async function loadPosts() {
  postLoading.value = true;
  try {
    const page = await adminApi.get(`/subscribe-channels/${channelId}/posts`, {
      params: { page: postPage.value, pageSize: postPageSize }
    });
    posts.value = page?.records || [];
    postTotal.value = page?.total || 0;
  } finally {
    postLoading.value = false;
  }
}

async function removeNovel(row) {
  try {
    await ElMessageBox.confirm(`将《${row.title}》从本频道移出？`, '确认');
  } catch {
    return;
  }
  await adminApi.delete(`/subscribe-channels/${channelId}/novels/${row.id}`);
  ElMessage.success('已移出频道');
  await Promise.all([loadChannel(), loadNovels()]);
}

onMounted(async () => {
  await loadChannel();
  await Promise.all([loadNovels(), loadPosts()]);
});
</script>

<style scoped>
.channel-detail { display: flex; flex-direction: column; gap: 14px; }
.cd-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.cd-title { display: flex; align-items: center; gap: 10px; }
.cd-title h3 { margin: 0; font-size: 18px; color: #1f2d3d; }
.cd-stats { display: flex; gap: 22px; }
.cd-stat { display: flex; flex-direction: column; align-items: center; min-width: 76px; }
.cd-stat b { font-size: 20px; color: #1f6f64; }
.cd-stat span { font-size: 12px; color: #98a5b5; }
.cd-desc { margin-top: 14px; }
.cd-warn { margin: 12px 0 0; color: #b8860b; font-size: 13px; }
.toolbar { display: flex; gap: 10px; align-items: center; margin-bottom: 14px; }
.cd-pager { margin-top: 14px; justify-content: flex-end; }
.cd-posts { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 14px; min-height: 120px; }
.cd-post { border: 1px solid #e5eaf0; border-radius: 10px; overflow: hidden; cursor: pointer; background: #fff; }
.cd-post:hover { border-color: #1f6f64; }
.cd-post-cover { height: 140px; background: #eef1f5; display: flex; align-items: center; justify-content: center; }
.cd-post-cover img { width: 100%; height: 100%; object-fit: cover; display: block; }
.cd-post-empty { color: #a8b3c0; font-size: 12px; }
.cd-post-body { padding: 10px 12px; }
.cd-post-title { font-size: 14px; color: #1f2d3d; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cd-post-tags { display: flex; gap: 6px; flex-wrap: wrap; margin: 8px 0 6px; }
.cd-post-time { font-size: 12px; color: #98a5b5; }
.cd-empty { margin: 20px 0; text-align: center; color: #98a5b5; font-size: 13px; grid-column: 1 / -1; }
.cd-preview { display: flex; flex-direction: column; gap: 12px; max-height: 62vh; overflow: auto; }
.cd-preview-image { width: 100%; max-height: 420px; }
.cd-preview-video { width: 100%; max-height: 420px; background: #000; }
.muted { color: #98a5b5; font-size: 12px; margin-right: 10px; }
</style>
