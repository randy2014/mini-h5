<template>
  <section class="page with-tab home-page">
    <header class="home-top">
      <div>
        <p>Mini Novel</p>
        <h1>今天读点好故事</h1>
      </div>
      <van-button round size="small" color="#1f6f64" icon="search" to="/h5/search">搜索</van-button>
    </header>

    <button v-if="heroBook" class="daily-card" type="button" @click="openBook(heroBook)">
      <img :src="heroBook.coverUrl || fallbackCover" :alt="heroBook.title" />
      <span>
        <small>今日推荐</small>
        <strong>{{ heroBook.title }}</strong>
        <em>{{ heroBook.author || '佚名' }} · {{ heroBook.status === 2 ? '完结' : '连载' }}</em>
        <b>{{ formatIntro(heroBook.intro) || '进入详情开始阅读' }}</b>
      </span>
    </button>

    <div class="quick-nav">
      <router-link to="/h5/category">分类</router-link>
      <router-link to="/h5/bookshelf">书架</router-link>
      <router-link to="/h5/vip">VIP</router-link>
      <router-link to="/h5/search">找书</router-link>
    </div>

    <van-loading v-if="loading" class="center-loading" />
    <template v-else>
      <div class="section-title">
        <h2>热榜精选</h2>
        <span>{{ books.length }} 本</span>
      </div>
      <div class="rank-list">
        <BookCard
          v-for="(book, index) in rankBooks"
          :key="book.id"
          :book="book"
          :rank="index + 1"
          @open="openBook"
        />
      </div>

      <div v-if="completedBooks.length" class="section-title">
        <h2>完结优先</h2>
        <span>一口气读完</span>
      </div>
      <div v-if="completedBooks.length" class="cover-row">
        <button v-for="book in completedBooks" :key="book.id" type="button" @click="openBook(book)">
          <img :src="book.coverUrl || fallbackCover" :alt="book.title" />
          <strong>{{ book.title }}</strong>
          <small>{{ book.author || '佚名' }}</small>
        </button>
      </div>
    </template>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import BookCard from '../components/BookCard.vue';
import { fetchHome } from '../services/book';
import { formatTextLineBreaks } from '../utils/text';

const router = useRouter();
const loading = ref(true);
const books = ref([]);
const fallbackCover = 'https://dummyimage.com/300x420/1f2933/ffffff&text=Mini+Novel';

const heroBook = computed(() => books.value[0]);
const rankBooks = computed(() => books.value.slice(0, 12));
const completedBooks = computed(() => books.value.filter((book) => book.status === 2).slice(0, 8));

onMounted(async () => {
  try {
    books.value = await fetchHome();
  } finally {
    loading.value = false;
  }
});

function openBook(book) {
  router.push(`/h5/book/${book.id}`);
}

function formatIntro(value) {
  return formatTextLineBreaks(value);
}
</script>
