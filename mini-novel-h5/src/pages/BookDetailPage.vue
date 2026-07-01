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
        <span>{{ chapters.length }} 章</span>
      </div>

      <van-cell
        v-for="chapter in chapters"
        :key="chapter.id"
        :title="chapter.title"
        is-link
        @click="read(chapter)"
      >
        <template #value>
          <van-tag v-if="chapter.vip" color="#8b5e2b">VIP</van-tag>
        </template>
      </van-cell>
    </template>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showToast } from 'vant';
import { fetchBook, fetchChapters } from '../services/book';
import { addBookshelf } from '../services/user';

const route = useRoute();
const router = useRouter();
const loading = ref(true);
const book = ref({});
const chapters = ref([]);
const fallbackCover = 'https://dummyimage.com/300x420/20232a/ffffff&text=Mini+Novel';

onMounted(async () => {
  try {
    book.value = await fetchBook(route.params.id);
    chapters.value = await fetchChapters(route.params.id);
  } finally {
    loading.value = false;
  }
});

function readFirst() {
  if (!chapters.value.length) {
    showToast('暂无章节');
    return;
  }
  read(chapters.value[0]);
}

function read(chapter) {
  router.push(`/h5/read/${chapter.id}?bookId=${book.value.id}`);
}

async function addToBookshelf() {
  await addBookshelf(book.value.id);
  showToast('已加入书架');
}
</script>
