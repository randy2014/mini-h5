<template>
  <el-drawer
    :model-value="modelValue"
    :title="`查看文章 · ${novel?.title || ''}`"
    size="1080px"
    destroy-on-close
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <div v-loading="viewLoading" class="article-view">
      <header class="av-head">
        <div class="av-cover">
          <img v-if="viewNovel.coverUrl && !coverFailed" :src="viewNovel.coverUrl" alt="封面" @error="coverFailed = true" />
          <span v-else class="av-cover-empty">无封面</span>
        </div>
        <div class="av-meta">
          <h3>{{ viewNovel.title || '-' }}</h3>
          <div class="av-tags">
            <el-tag size="small" type="info">{{ categoryName(viewNovel.categoryId) }}</el-tag>
            <el-tag size="small" :type="viewNovel.status === 0 ? 'danger' : 'success'">{{ statusText(viewNovel.status) }}</el-tag>
            <el-tag v-if="viewNovel.vipRequired" size="small" type="warning">整本 VIP</el-tag>
            <el-tag v-else size="small">免费</el-tag>
          </div>
          <el-descriptions :column="3" size="small" border class="av-desc">
            <el-descriptions-item label="作者">{{ viewNovel.author || '佚名' }}</el-descriptions-item>
            <el-descriptions-item label="字数">{{ viewNovel.wordCount || 0 }}</el-descriptions-item>
            <el-descriptions-item label="章节数">{{ viewChapters.length }}</el-descriptions-item>
            <el-descriptions-item label="免费章节">{{ viewNovel.freeChapterCount ?? 0 }} 章</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ formatDateTime(viewNovel.updatedAt) }}</el-descriptions-item>
            <el-descriptions-item label="最新章节">{{ viewNovel.latestChapterTitle || '-' }}</el-descriptions-item>
          </el-descriptions>
          <p class="av-intro">{{ viewNovel.intro || '暂无简介' }}</p>
        </div>
      </header>

      <div class="av-body">
        <aside class="av-catalog">
          <div class="av-catalog-head">
            <el-input v-model="chapterKeyword" size="small" placeholder="搜索章号 / 标题" clearable />
            <span class="av-muted">共 {{ viewChapters.length }} 章</span>
          </div>
          <div class="av-catalog-list">
            <div
              v-for="item in visibleChapters"
              :key="item.id"
              class="av-chapter"
              :class="{ active: item.id === activeChapter?.id }"
              @click="openViewChapter(item)"
            >
              <span class="no">{{ item.chapterNo }}</span>
              <span class="tt">{{ item.title || '(无标题)' }}</span>
              <el-tag v-if="item.vip" size="small" type="warning" effect="plain">{{ item.priceCoin || 0 }} 币</el-tag>
            </div>
            <p v-if="!viewChapters.length" class="av-muted av-empty">该文章没有章节</p>
            <p v-else-if="!filteredChapters.length" class="av-muted av-empty">没有匹配的章节</p>
            <el-button
              v-if="filteredChapters.length > visibleChapters.length"
              link
              type="primary"
              class="av-more"
              @click="chapterRenderLimit += 300"
            >
              加载更多（已显示 {{ visibleChapters.length }} / {{ filteredChapters.length }}）
            </el-button>
          </div>
        </aside>

        <section class="av-content">
          <template v-if="activeChapter">
            <div class="av-content-head">
              <div class="av-content-title">
                <b>{{ activeChapter.title || '(无标题)' }}</b>
                <span class="av-muted">第 {{ activeChapter.chapterNo }} 章 · {{ activeContent.length }} 字</span>
              </div>
              <div class="av-content-actions">
                <el-tag v-if="activeChapter.vip" size="small" type="warning">VIP {{ activeChapter.priceCoin || 0 }} 币</el-tag>
                <el-button size="small" :disabled="!chapterNav.prev" @click="openViewChapter(chapterNav.prev)">上一章</el-button>
                <el-button size="small" :disabled="!chapterNav.next" @click="openViewChapter(chapterNav.next)">下一章</el-button>
                <el-button size="small" :disabled="!activeChapter" @click="copyContent">复制正文</el-button>
              </div>
            </div>
            <div v-loading="contentLoading" class="av-content-body">{{ activeContent || '本章没有正文内容' }}</div>
          </template>
          <p v-else class="av-muted av-empty">点击左侧目录查看正文</p>
        </section>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { adminApi } from '../services/http';
import { formatDateTime } from '../utils/date';

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  // 传入小说行（至少含 id / title）；详情以接口为准
  novel: { type: Object, default: null }
});
const emit = defineEmits(['update:modelValue']);

const VIEW_PAGE_SIZE = 300;
const viewLoading = ref(false);
const contentLoading = ref(false);
const viewNovel = ref({});
const viewChapters = ref([]);
const activeChapter = ref(null);
const activeContent = ref('');
const chapterKeyword = ref('');
const chapterRenderLimit = ref(VIEW_PAGE_SIZE);
const coverFailed = ref(false);
const categories = ref([]);

function statusText(status) {
  return ({ 0: '下架', 1: '正常', 2: '完结', 3: '草稿', 4: '审核中' })[status] || '未知';
}

function categoryName(categoryId) {
  const hit = categories.value.find((item) => item.id === categoryId);
  return hit ? hit.name : categoryId ? `分类 ${categoryId}` : '未分类';
}

const filteredChapters = computed(() => {
  const keyword = chapterKeyword.value.trim();
  if (!keyword) {
    return viewChapters.value;
  }
  return viewChapters.value.filter(
    (item) => String(item.chapterNo ?? '').includes(keyword) || (item.title || '').includes(keyword)
  );
});

const visibleChapters = computed(() => filteredChapters.value.slice(0, chapterRenderLimit.value));

const chapterNav = computed(() => {
  const index = activeChapter.value ? viewChapters.value.findIndex((item) => item.id === activeChapter.value.id) : -1;
  return {
    prev: index > 0 ? viewChapters.value[index - 1] : null,
    next: index >= 0 && index < viewChapters.value.length - 1 ? viewChapters.value[index + 1] : null
  };
});

// 目录接口只取章节元信息（不带正文）；老版本后端没有该接口时退回完整列表接口
async function loadChapterList(novelId) {
  try {
    return await adminApi.get(`/novels/${novelId}/chapter-list`);
  } catch {
    return await adminApi.get(`/novels/${novelId}/chapters`);
  }
}

async function loadCategories() {
  if (categories.value.length) {
    return;
  }
  try {
    categories.value = (await adminApi.get('/categories')) || [];
  } catch {
    categories.value = [];
  }
}

async function openViewChapter(chapter) {
  if (!chapter) {
    return;
  }
  activeChapter.value = chapter;
  activeContent.value = '';
  contentLoading.value = true;
  try {
    const detail = await adminApi.get(`/novels/chapters/${chapter.id}/content`);
    // 防止快速连点章节时旧响应覆盖新选中章节
    if (activeChapter.value?.id === chapter.id) {
      activeContent.value = detail?.content || '';
    }
  } finally {
    contentLoading.value = false;
  }
}

async function copyContent() {
  if (!activeContent.value) {
    ElMessage.warning('本章没有正文内容');
    return;
  }
  try {
    await navigator.clipboard.writeText(activeContent.value);
    ElMessage.success('正文已复制');
  } catch {
    ElMessage.warning('当前浏览器不允许自动复制，请手动选择文本');
  }
}

async function load() {
  const novelId = props.novel?.id;
  if (!novelId) {
    return;
  }
  viewNovel.value = { ...(props.novel || {}) };
  viewChapters.value = [];
  activeChapter.value = null;
  activeContent.value = '';
  chapterKeyword.value = '';
  chapterRenderLimit.value = VIEW_PAGE_SIZE;
  coverFailed.value = false;
  viewLoading.value = true;
  try {
    const [detail, chapterList] = await Promise.all([
      adminApi.get(`/novels/${novelId}`),
      loadChapterList(novelId),
      loadCategories()
    ]);
    viewNovel.value = detail || viewNovel.value;
    viewChapters.value = chapterList || [];
    const first = viewChapters.value[0];
    if (first) {
      await openViewChapter(first);
    }
  } finally {
    viewLoading.value = false;
  }
}

watch(
  () => [props.modelValue, props.novel?.id],
  ([open]) => {
    if (open) {
      load();
    }
  }
);
</script>

<style scoped>
.article-view { display: flex; flex-direction: column; gap: 14px; min-height: 60vh; }
.av-head { display: flex; gap: 16px; }
.av-cover { width: 108px; height: 148px; flex-shrink: 0; border-radius: 8px; overflow: hidden; background: #eef1f5; }
.av-cover img { width: 100%; height: 100%; object-fit: cover; display: block; }
.av-cover-empty { display: flex; align-items: center; justify-content: center; height: 100%; color: #a8b3c0; font-size: 12px; }
.av-meta { flex: 1; min-width: 0; }
.av-meta h3 { margin: 0 0 8px; font-size: 17px; color: #1f2d3d; }
.av-tags { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 10px; }
.av-desc { margin-bottom: 10px; }
.av-intro { margin: 0; color: #55657a; font-size: 13px; line-height: 1.7; max-height: 84px; overflow: auto; }
.av-body { display: grid; grid-template-columns: 300px minmax(0, 1fr); gap: 14px; flex: 1; min-height: 0; }
.av-catalog { border: 1px solid #e5eaf0; border-radius: 10px; display: flex; flex-direction: column; min-height: 0; }
.av-catalog-head { display: flex; align-items: center; gap: 8px; padding: 10px; border-bottom: 1px solid #eef1f5; }
.av-catalog-list { overflow: auto; padding: 6px; max-height: 62vh; }
.av-chapter { display: flex; align-items: center; gap: 8px; padding: 7px 8px; border-radius: 6px; cursor: pointer; font-size: 13px; color: #33475e; }
.av-chapter:hover { background: #f4f7fa; }
.av-chapter.active { background: #e7f6f0; color: #1f6f64; font-weight: 600; }
.av-chapter .no { width: 34px; flex-shrink: 0; color: #98a5b5; font-size: 12px; }
.av-chapter.active .no { color: #1f6f64; }
.av-chapter .tt { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.av-more { margin: 6px 0 2px; }
.av-content { border: 1px solid #e5eaf0; border-radius: 10px; display: flex; flex-direction: column; min-height: 0; }
.av-content-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; flex-wrap: wrap; padding: 10px 12px; border-bottom: 1px solid #eef1f5; }
.av-content-title { display: flex; flex-direction: column; gap: 3px; }
.av-content-actions { display: flex; align-items: center; gap: 8px; }
.av-content-body { padding: 14px 16px; overflow: auto; max-height: 58vh; white-space: pre-wrap; line-height: 1.9; font-size: 14px; color: #26384c; }
.av-muted { color: #98a5b5; font-size: 12px; }
.av-empty { padding: 16px 4px; text-align: center; }
</style>
