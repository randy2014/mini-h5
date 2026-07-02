<template>
  <section class="page">
    <van-nav-bar title="书籍详情" left-arrow @click-left="$router.back()" />

    <van-loading v-if="loading" class="center-loading" />
    <template v-else>
      <div class="detail-head">
        <img :src="book.coverUrl || fallbackCover" :alt="book.title" />
        <div>
          <h1>{{ book.title }}</h1>
          <p>{{ book.author }}</p>
          <span>{{ book.status === 2 ? '完结' : '连载' }} · {{ book.wordCount || 0 }} 字</span>
        </div>
      </div>

      <p class="book-intro">{{ book.intro }}</p>

      <div class="detail-actions">
        <van-button block round color="#2f6f73" @click="readFirst">开始阅读</van-button>
        <van-button block round plain color="#2f6f73" @click="addToBookshelf">加入书架</van-button>
      </div>

      <div class="section-title">
        <h2>目录</h2>
        <span>第 {{ chapterPage.current || pageNo }} / {{ chapterPage.pages || 1 }} 页 · 共 {{ chapterPage.total || 0 }} 章</span>
      </div>

      <van-cell
        v-for="chapter in chapters"
        :id="`chapter-${chapter.id}`"
        :key="chapter.id"
        :class="{ 'chapter-active': chapter.id === activeChapterId }"
        :title="chapter.title"
        is-link
        @click="read(chapter)"
      >
        <template #label>
          第 {{ chapter.chapterNo }} 章
        </template>
        <template #value>
          <van-tag v-if="chapter.vip" color="#8b5e2b">VIP</van-tag>
        </template>
      </van-cell>

      <div v-if="chapterPage.pages > 1" class="chapter-pager">
        <van-button size="small" plain :disabled="pageNo <= 1" @click="loadChapters(pageNo - 1)">
          上一页
        </van-button>
        <span>{{ pageNo }} / {{ chapterPage.pages }}</span>
        <van-button size="small" plain :disabled="pageNo >= chapterPage.pages" @click="loadChapters(pageNo + 1)">
          下一页
        </van-button>
      </div>
    </template>
  </section>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showToast } from 'vant';
import { fetchBook, fetchChapters } from '../services/book';
import { addBookshelf } from '../services/user';

const PAGE_SIZE = 80;
const route = useRoute();
const router = useRouter();
const loading = ref(true);
const book = ref({});
const chapters = ref([]);
const pageNo = ref(1);
const chapterPage = ref({ current: 1, pages: 1, total: 0, records: [] });
const activeChapterId = ref(Number(route.query.chapterId || 0));
const fallbackCover = 'https://dummyimage.com/300x420/20232a/ffffff&text=Mini+Novel';

onMounted(async () => {
  try {
    book.value = await fetchBook(route.params.id);
    const progress = readProgress();
    const targetChapterId = Number(route.query.chapterId || progress.chapterId || 0);
    const targetChapterNo = Number(route.query.chapterNo || progress.chapterNo || 0);
    activeChapterId.value = targetChapterId;
    const targetPage = targetChapterNo ? Math.max(1, Math.ceil(targetChapterNo / PAGE_SIZE)) : 1;
    await loadChapters(targetPage, targetChapterId);
  } finally {
    loading.value = false;
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

function readFirst() {
  if (!chapters.value.length) {
    showToast('暂无章节');
    return;
  }
  read(chapters.value[0]);
}

function read(chapter) {
  saveProgress(chapter);
  router.push(`/h5/read/${chapter.id}?bookId=${book.value.id}`);
}

async function addToBookshelf() {
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
    document.getElementById(`chapter-${chapterId}`)?.scrollIntoView({ block: 'center' });
  });
}
</script>
