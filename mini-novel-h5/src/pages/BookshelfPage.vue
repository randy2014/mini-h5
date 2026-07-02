<template>
  <section class="page with-tab bookshelf-page">
    <van-nav-bar title="我的书架" />
    <van-loading v-if="loading" class="center-loading" />
    <template v-else>
      <div class="section-title first">
        <h2>继续阅读</h2>
        <span>{{ books.length }} 本</span>
      </div>
      <div v-for="book in books" :key="book.id" class="bookshelf-row">
        <BookCard :book="book" @open="openBook" />
        <van-button size="small" plain icon="delete-o" type="danger" @click.stop="removeBook(book)">移出</van-button>
      </div>
      <van-empty v-if="books.length === 0" description="书架还是空的" />
    </template>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { showConfirmDialog, showToast } from 'vant';
import BookCard from '../components/BookCard.vue';
import { fetchBookshelf, removeBookshelf } from '../services/user';

const router = useRouter();
const loading = ref(true);
const books = ref([]);

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
