<template>
  <section class="page with-tab category-page">
    <van-nav-bar title="分类" />
    <van-loading v-if="loading" class="center-loading" />
    <template v-else>
      <div class="category-strip">
        <button
          v-for="item in categories"
          :key="item.id"
          type="button"
          :class="{ active: item.id === activeCategoryId }"
          @click="selectCategory(item)"
        >
          <strong>{{ item.name }}</strong>
          <span>{{ item.id === activeCategoryId ? '正在看' : '书单' }}</span>
        </button>
      </div>

      <div class="category-summary">
        <div>
          <small>分类书库</small>
          <h1>{{ activeCategoryName || '分类书籍' }}</h1>
        </div>
        <span>{{ books.length }} 本</span>
      </div>

      <div class="rank-list">
        <BookCard v-for="(book, index) in books" :key="book.id" :book="book" :rank="index + 1" @open="openBook" />
      </div>
      <van-empty v-if="books.length === 0" description="该分类暂无书籍" />
    </template>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import BookCard from '../components/BookCard.vue';
import { fetchCategories, fetchCategoryBooks } from '../services/book';

const router = useRouter();
const loading = ref(true);
const categories = ref([]);
const activeCategoryId = ref(null);
const books = ref([]);

const activeCategoryName = computed(() => {
  return categories.value.find((item) => item.id === activeCategoryId.value)?.name || '';
});

onMounted(async () => {
  try {
    categories.value = await fetchCategories();
    if (categories.value.length) {
      await selectCategory(categories.value[0]);
    }
  } finally {
    loading.value = false;
  }
});

async function selectCategory(category) {
  activeCategoryId.value = category.id;
  books.value = await fetchCategoryBooks(category.id);
}

function openBook(book) {
  router.push(`/h5/book/${book.id}`);
}
</script>
