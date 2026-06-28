<template>
  <section class="page with-tab">
    <header class="home-hero">
      <div>
        <p>Mini Novel</p>
        <h1>发现下一本想读到深夜的小说</h1>
      </div>
      <van-button round size="small" color="#2f6f73" icon="search" to="/h5/search">搜索</van-button>
    </header>

    <div class="section-title">
      <h2>最近更新</h2>
      <span>{{ books.length }} 本</span>
    </div>

    <van-loading v-if="loading" class="center-loading" />
    <BookCard v-for="book in books" v-else :key="book.id" :book="book" @open="openBook" />
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import BookCard from '../components/BookCard.vue';
import { fetchHome } from '../services/book';

const router = useRouter();
const loading = ref(true);
const books = ref([]);

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
</script>
