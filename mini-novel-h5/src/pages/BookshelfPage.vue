<template>
  <section class="page with-tab bookshelf-page">
    <van-nav-bar title="我的书架" />
    <van-loading v-if="loading" class="center-loading" />
    <template v-else>
      <div class="section-title first">
        <h2>继续阅读</h2>
        <span>{{ books.length }} 本</span>
      </div>
      <div v-for="book in sortedBooks" :key="book.id" class="bookshelf-row">
        <BookCard :book="book" @open="openBook" />
        <div class="bookshelf-progress">
          <span>{{ progressText(book) }}</span>
          <small>{{ progressTime(book) }}</small>
        </div>
        <div class="bookshelf-actions">
          <van-button size="small" round color="#1f6f64" icon="play-circle-o" @click.stop="continueRead(book)">
            {{ progressLabel(book) }}
          </van-button>
          <van-button size="small" round plain icon="delete-o" type="danger" @click.stop="removeBook(book)">移出</van-button>
        </div>
      </div>
      <van-empty v-if="sortedBooks.length === 0" description="书架还是空的" />
    </template>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { showConfirmDialog, showToast } from 'vant';
import BookCard from '../components/BookCard.vue';
import { fetchBookshelf, removeBookshelf } from '../services/user';

const router = useRouter();
const loading = ref(true);
const books = ref([]);
const sortedBooks = computed(() => {
  return [...books.value].sort((left, right) => {
    return Number(readProgress(right.id).updatedAt || 0) - Number(readProgress(left.id).updatedAt || 0);
  });
});

onMounted(loadBookshelf);

async function loadBookshelf() {
  loading.value = true;
  try {
    books.value = await fetchBookshelf();
  } finally {
    loading.value = false;
  }
}

function openBook(book) {
  router.push(`/h5/book/${book.id}`);
}

function continueRead(book) {
  const progress = readProgress(book.id);
  if (progress.chapterId) {
    router.push(`/h5/read/${progress.chapterId}?bookId=${book.id}`);
    return;
  }
  openBook(book);
}

function progressLabel(book) {
  const progress = readProgress(book.id);
  return progress.chapterNo ? `续读 ${progress.chapterNo}` : '阅读';
}

function progressText(book) {
  const progress = readProgress(book.id);
  if (!progress.chapterNo) {
    return '尚未开始阅读';
  }
  const total = progress.totalChapters ? ` / ${progress.totalChapters}` : '';
  return `读到第 ${progress.chapterNo}${total} 章${progress.title ? ` · ${progress.title}` : ''}`;
}

function progressTime(book) {
  const updatedAt = Number(readProgress(book.id).updatedAt || 0);
  if (!updatedAt) {
    return '加入后未阅读';
  }
  const minutes = Math.max(1, Math.round((Date.now() - updatedAt) / 60000));
  if (minutes < 60) {
    return `${minutes} 分钟前`;
  }
  const hours = Math.round(minutes / 60);
  if (hours < 24) {
    return `${hours} 小时前`;
  }
  return `${Math.round(hours / 24)} 天前`;
}

function readProgress(bookId) {
  try {
    return JSON.parse(localStorage.getItem(`mini_novel_read_${bookId}`) || '{}');
  } catch {
    return {};
  }
}

async function removeBook(book) {
  await showConfirmDialog({
    title: '移出书架',
    message: `确认移出《${book.title}》？`,
    confirmButtonText: '移出',
    cancelButtonText: '取消'
  });
  await removeBookshelf(book.id);
  books.value = books.value.filter((item) => item.id !== book.id);
  showToast('已移出书架');
}
</script>
