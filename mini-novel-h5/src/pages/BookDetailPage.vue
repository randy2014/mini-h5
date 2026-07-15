<template>
  <section class="page detail-page">
    <van-nav-bar title="书籍详情" left-arrow fixed placeholder @click-left="$router.back()" @click-right="goHome">
      <template #right>
        <van-icon name="wap-home-o" size="20" />
      </template>
    </van-nav-bar>

    <van-loading v-if="loading" class="center-loading" />
    <template v-else>
      <header class="detail-hero">
        <div class="detail-backdrop" :style="{ backgroundImage: `url(${book.coverUrl || fallbackCover})` }"></div>
        <div class="detail-hero-main">
          <img :src="book.coverUrl || fallbackCover" :alt="book.title" @error="handleImgError" />
          <div>
            <i class="book-status" :class="{ completed: book.status === 2 }">
              {{ book.status === 2 ? '完结' : '连载' }}
            </i>
            <h1>{{ book.title }}</h1>
            <p>{{ book.author || '佚名' }}</p>
            <span>{{ wordCountLabel }} · 共 {{ chapterPage.total || 0 }} 章</span>
          </div>
        </div>
      </header>

      <div class="detail-actions">
        <van-button block round color="#1f6f64" icon="play-circle-o" @click="readContinue">
          {{ activeChapterId ? '继续阅读' : '开始阅读' }}
        </van-button>
        <van-button block round plain color="#1f6f64" icon="bookmark-o" @click="addToBookshelf">加入书架</van-button>
      </div>
      <div v-if="book.publishStatus" class="reading-progress-card">
        <span>{{ book.publishStatus === 'REVIEWING' ? '内容审核中' : '已审核开放' }}</span>
        <strong>已开放 {{ book.approvedChapterCount || 0 }} 章</strong>
      </div>

      <div v-if="activeChapterId" class="reading-progress-card">
        <span>上次读到</span>
        <strong>{{ progressTitle }}</strong>
        <van-button size="small" round plain color="#1f6f64" @click="scrollToChapter(activeChapterId)">定位目录</van-button>
      </div>

      <section class="soft-panel">
        <div class="section-title compact">
          <h2>简介</h2>
          <span>{{ book.status === 2 ? '已完结' : '持续更新' }}</span>
        </div>
        <p class="book-intro">{{ formatIntro(book.intro) || '暂无简介' }}</p>
      </section>

      <div class="section-title">
        <h2>目录</h2>
        <span>已加载 {{ chapters.length }} / {{ chapterPage.total || 0 }} 章</span>
      </div>

      <div class="chapter-tools">
        <van-search v-model="chapterKeyword" placeholder="搜索本页章节" shape="round" />
        <van-field v-model="chapterJumpNo" type="digit" placeholder="章号" />
        <van-button size="small" round color="#1f6f64" @click="jumpToChapterNo">跳转</van-button>
        <van-button size="small" round plain color="#1f6f64" :loading="orderLoading" @click="toggleCatalogOrder">
          {{ reverseCatalog ? '倒序' : '正序' }}
        </van-button>
      </div>

      <div v-if="chapterError && !chapters.length" class="vip-feedback vip-feedback--error chapter-load-error">
        <van-icon name="warning-o" />
        <span>目录加载失败，请稍后重试。</span>
        <button type="button" @click="retryChapters">重新加载</button>
      </div>
      <van-list
        v-else
        v-model:loading="chapterLoading"
        v-model:error="chapterError"
        :finished="chapterFinished"
        :immediate-check="false"
        error-text="目录加载失败，点击重试"
        :finished-text="chapters.length ? `已加载全部 ${chapterPage.total || chapters.length} 章` : ''"
        @load="loadNextChapterPage"
      >
        <van-loading v-if="chapterLoading && !chapters.length" class="center-loading" />
        <div v-else class="chapter-list">
          <button
            v-for="chapter in displayedChapters"
            :id="`chapter-${chapter.id}`"
            :key="chapter.id"
            type="button"
            :class="{ 'chapter-active': chapter.id === activeChapterId }"
            @click="read(chapter)"
          >
            <span>
              <b>{{ chapter.title }}</b>
              <small>第 {{ chapter.chapterNo }} 章</small>
            </span>
            <van-tag v-if="chapter.vip" color="#9b7a2f">VIP</van-tag>
          </button>
        </div>
        <van-empty v-if="chapterFinished && !chapters.length" description="暂无已发布章节" />
        <van-empty v-else-if="chapters.length && displayedChapters.length === 0" description="已加载目录中没有匹配章节" />
      </van-list>
    </template>
  </section>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showToast } from 'vant';
import { fetchBook, fetchChapters } from '../services/book';
import { addBookshelf } from '../services/user';
import { useUserStore } from '../stores/user';
import { FALLBACK_COVER, handleImgError } from '../utils/cover';
import { formatTextLineBreaks } from '../utils/text';

const PAGE_SIZE = 50;
const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const loading = ref(true);
const book = ref({});
const chapters = ref([]);
const pageNo = ref(1);
const chapterPage = ref({ current: 1, pages: 1, total: 0, records: [] });
const chapterLoading = ref(false);
const chapterError = ref(false);
const chapterFinished = ref(false);
const chapterRequestInFlight = ref(false);
const orderLoading = ref(false);
const activeChapterId = ref(Number(route.query.chapterId || 0));
const pendingScrollChapterId = ref(0);
const chapterKeyword = ref('');
const chapterJumpNo = ref('');
const reverseCatalog = ref(false);
const fallbackCover = FALLBACK_COVER;

const wordCountLabel = computed(() => {
  const count = Number(book.value.wordCount || 0);
  if (!count) {
    return '字数统计中';
  }
  if (count >= 10000) {
    return `${(count / 10000).toFixed(1).replace('.0', '')}万字`;
  }
  return `${count}字`;
});

const progressTitle = computed(() => {
  const chapter = chapters.value.find((item) => item.id === activeChapterId.value);
  if (chapter) {
    return `第 ${chapter.chapterNo} 章 · ${chapter.title}`;
  }
  const progress = readProgress();
  return progress.chapterNo ? `第 ${progress.chapterNo} 章` : '已记录阅读进度';
});

const displayedChapters = computed(() => {
  const keyword = chapterKeyword.value.trim();
  const rows = keyword
    ? chapters.value.filter((chapter) => `${chapter.title} ${chapter.chapterNo}`.includes(keyword))
    : [...chapters.value];
  return reverseCatalog.value ? [...rows].reverse() : rows;
});

onMounted(async () => {
  try {
    book.value = await fetchBook(route.params.id);
    const progress = readProgress();
    const targetChapterId = Number(route.query.chapterId || progress.chapterId || 0);
    activeChapterId.value = targetChapterId;
    resetChapters();
    await loadNextChapterPage();
    await loadAllChapterPages();
    if (targetChapterId) {
      await loadUntilChapter((chapter) => chapter.id === targetChapterId);
    }
    pendingScrollChapterId.value = targetChapterId;
  } finally {
    loading.value = false;
    if (pendingScrollChapterId.value) {
      scrollToChapter(pendingScrollChapterId.value);
      pendingScrollChapterId.value = 0;
    }
  }
});

function resetChapters() {
  chapters.value = [];
  pageNo.value = 1;
  chapterPage.value = { current: 1, pages: 1, total: 0, records: [] };
  chapterFinished.value = false;
  chapterError.value = false;
}

async function loadNextChapterPage() {
  if (chapterFinished.value || chapterRequestInFlight.value) {
    chapterLoading.value = false;
    return;
  }
  chapterRequestInFlight.value = true;
  chapterLoading.value = true;
  chapterError.value = false;
  try {
    const data = await fetchChapters(route.params.id, pageNo.value, PAGE_SIZE);
    const incoming = data?.records || [];
    const knownIds = new Set(chapters.value.map((chapter) => chapter.id));
    incoming.forEach((chapter) => {
      if (!knownIds.has(chapter.id)) {
        knownIds.add(chapter.id);
        chapters.value.push(chapter);
      }
    });
    chapterPage.value = data || chapterPage.value;
    const current = Number(data?.current || pageNo.value);
    const pages = Number(data?.pages || 0);
    chapterFinished.value = pages === 0 || current >= pages;
    if (!chapterFinished.value) pageNo.value = current + 1;
  } catch {
    chapterError.value = true;
  } finally {
    chapterRequestInFlight.value = false;
    chapterLoading.value = false;
  }
}

async function loadUntilChapter(predicate) {
  while (!chapters.value.some(predicate) && !chapterFinished.value && !chapterError.value) {
    await loadNextChapterPage();
  }
}

async function loadAllChapterPages() {
  while (!chapterFinished.value && !chapterError.value) {
    await loadNextChapterPage();
  }
}

function retryChapters() {
  if (!chapters.value.length) resetChapters();
  chapterError.value = false;
  loadNextChapterPage();
}

function readContinue() {
  const target = chapters.value.find((chapter) => chapter.id === activeChapterId.value) || chapters.value[0];
  if (!target) {
    showToast('暂无章节');
    return;
  }
  read(target);
}

function goHome() {
  router.push('/h5/home');
}

async function jumpToChapterNo() {
  const no = Number(chapterJumpNo.value || 0);
  if (!no || no < 1) {
    showToast('请输入章节号');
    return;
  }
  await loadUntilChapter((chapter) => Number(chapter.chapterNo) === no);
  const target = chapters.value.find((chapter) => Number(chapter.chapterNo) === no);
  if (!target) {
    showToast('未找到已发布的该章节');
    return;
  }
  activeChapterId.value = target.id;
  scrollToChapter(target.id);
}

async function toggleCatalogOrder() {
  if (!reverseCatalog.value && !chapterFinished.value) {
    orderLoading.value = true;
    while (!chapterFinished.value && !chapterError.value) {
      await loadNextChapterPage();
    }
    orderLoading.value = false;
    if (chapterError.value) return;
  }
  reverseCatalog.value = !reverseCatalog.value;
}

function read(chapter) {
  saveProgress(chapter);
  router.push(`/h5/read/${chapter.id}?bookId=${book.value.id}`);
}

async function addToBookshelf() {
  if (!userStore.isAuthenticated) {
    router.push({ path: '/h5/login', query: { redirect: route.fullPath } });
    return;
  }
  await addBookshelf(book.value.id);
  showToast('已加入书架');
}

function saveProgress(chapter) {
  localStorage.setItem(progressKey(book.value.id), JSON.stringify({
    chapterId: chapter.id,
    chapterNo: chapter.chapterNo,
    updatedAt: Date.now()
  }));
}

function readProgress() {
  try {
    return JSON.parse(localStorage.getItem(progressKey(route.params.id)) || '{}');
  } catch {
    return {};
  }
}

function progressKey(bookId) {
  return `mini_novel_read_${bookId}`;
}

function scrollToChapter(chapterId) {
  nextTick(() => {
    window.requestAnimationFrame(() => {
      const target = document.getElementById(`chapter-${chapterId}`);
      if (!target) {
        return;
      }
      const top = target.getBoundingClientRect().top + window.scrollY - 112;
      window.scrollTo({ top: Math.max(0, top), behavior: 'smooth' });
    });
  });
}

function formatIntro(value) {
  return formatTextLineBreaks(value);
}
</script>

<style scoped>
.chapter-load-error { display:grid; grid-template-columns:auto minmax(0,1fr) auto; gap:8px; align-items:center; margin:0 14px; padding:12px; border:1px solid rgba(180,75,75,.16); border-radius:var(--radius); background:#fff1f0; color:var(--danger); font-size:13px; }
.chapter-load-error button { padding:4px 0; border:0; background:transparent; color:inherit; font-weight:800; }
</style>
