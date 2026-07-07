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
            <span>{{ wordCountLabel }} · �?{{ chapterPage.total || 0 }} �?/span>
          </div>
        </div>
      </header>

      <div class="detail-actions">
        <van-button block round color="#1f6f64" icon="play-circle-o" @click="readContinue">
          {{ activeChapterId ? '继续阅读' : '开始阅�? }}
        </van-button>
        <van-button block round plain color="#1f6f64" icon="bookmark-o" @click="addToBookshelf">加入书架</van-button>
      </div>

      <div v-if="activeChapterId" class="reading-progress-card">
        <span>上次读到</span>
        <strong>{{ progressTitle }}</strong>
        <van-button size="small" round plain color="#1f6f64" @click="scrollToChapter(activeChapterId)">定位目录</van-button>
      </div>

      <section class="soft-panel">
        <div class="section-title compact">
          <h2>简�?/h2>
          <span>{{ book.status === 2 ? '已完�? : '持续更新' }}</span>
        </div>
        <p class="book-intro">{{ formatIntro(book.intro) || '暂无简�? }}</p>
      </section>

      <div class="section-title">
        <h2>目录</h2>
        <span>�?{{ chapterPage.current || pageNo }} / {{ chapterPage.pages || 1 }} �?/span>
      </div>

      <div class="chapter-tools">
        <van-search v-model="chapterKeyword" placeholder="搜索本页章节" shape="round" />
        <van-field v-model="chapterJumpNo" type="digit" placeholder="章号" />
        <van-button size="small" round color="#1f6f64" @click="jumpToChapterNo">跳转</van-button>
        <van-button size="small" round plain color="#1f6f64" @click="reverseCatalog = !reverseCatalog">
          {{ reverseCatalog ? '倒序' : '正序' }}
        </van-button>
      </div>

      <div class="chapter-list">
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
            <small>�?{{ chapter.chapterNo }} �?/small>
          </span>
          <van-tag v-if="chapter.vip" color="#9b7a2f">VIP</van-tag>
        </button>
      </div>
      <van-empty v-if="chapters.length && displayedChapters.length === 0" description="本页没有匹配章节" />

      <div v-if="chapterPage.pages > 1" class="chapter-pager">
        <van-button size="small" plain :disabled="pageNo <= 1" @click="loadChapters(pageNo - 1)">
          上一�?        </van-button>
        <span>{{ pageNo }} / {{ chapterPage.pages }}</span>
        <van-button size="small" plain :disabled="pageNo >= chapterPage.pages" @click="loadChapters(pageNo + 1)">
          下一�?        </van-button>
      </div>
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

const PAGE_SIZE = 80;
const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const loading = ref(true);
const book = ref({});
const chapters = ref([]);
const pageNo = ref(1);
const chapterPage = ref({ current: 1, pages: 1, total: 0, records: [] });
const activeChapterId = ref(Number(route.query.chapterId || 0));
const pendingScrollChapterId = ref(0);
const chapterKeyword = ref('');
const chapterJumpNo = ref('');
const reverseCatalog = ref(false);
const fallbackCover = FALLBACK_COVER;

const wordCountLabel = computed(() => {
  const count = Number(book.value.wordCount || 0);
  if (!count) {
    return '字数统计�?;
  }
  if (count >= 10000) {
    return `${(count / 10000).toFixed(1).replace('.0', '')}万字`;
  }
  return `${count}字`;
});
const progressTitle = computed(() => {
  const chapter = chapters.value.find((item) => item.id === activeChapterId.value);
  if (chapter) {
    return `�?${chapter.chapterNo} �?· ${chapter.title}`;
  }
  const progress = readProgress();
  return progress.chapterNo ? `�?${progress.chapterNo} 章` : '已记录阅读进�?;
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
    const targetChapterNo = Number(route.query.chapterNo || progress.chapterNo || 0);
    activeChapterId.value = targetChapterId;
    const targetPage = targetChapterNo ? Math.max(1, Math.ceil(targetChapterNo / PAGE_SIZE)) : 1;
    await loadChapters(targetPage);
    pendingScrollChapterId.value = targetChapterId;
  } finally {
    loading.value = false;
    if (pendingScrollChapterId.value) {
      scrollToChapter(pendingScrollChapterId.value);
      pendingScrollChapterId.value = 0;
    }
  }
});

async function loadChapters(targetPage = 1, focusChapterId = 0) {
  const data = await fetchChapters(route.params.id, targetPage, PAGE_SIZE);
  chapterPage.value = data || { current: targetPage, pages: 1, total: 0, records: [] };
  chapters.value = chapterPage.value.records || [];
  pageNo.value = Number(chapterPage.value.current || targetPage);
  if (focusChapterId) {
    activeChapterId.value = Number(focusChapterId);
    scrollToChapter(focusChapterId);
  }
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
  const targetPage = Math.max(1, Math.ceil(no / PAGE_SIZE));
  await loadChapters(targetPage);
  const target = chapters.value.find((chapter) => Number(chapter.chapterNo) === no);
  if (!target) {
    showToast('该页未找到章�?);
    return;
  }
  activeChapterId.value = target.id;
  scrollToChapter(target.id);
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
  showToast('已加入书�?);
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
