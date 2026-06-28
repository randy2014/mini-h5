<template>
  <section class="page with-tab">
    <van-nav-bar title="书架" />
    <van-loading v-if="loading" class="center-loading" />
    <template v-else>
      <BookCard v-for="book in books" :key="book.id" :book="book" @open="openBook" />
      <van-empty v-if="books.length === 0" description="书架还是空的" />
    </template>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import BookCard from '../components/BookCard.vue';
import { fetchBookshelf } from '../services/user';

const router = useRouter();
const loading = ref(true);
const books = ref([]);

onMounted(async () => {
  try {
    books.value = await fetchBookshelf();
  } finally {
    loading.value = false;
  }
});

function openBook(book) {
  router.push(`/h5/book/${book.id}`);
}
</script>
