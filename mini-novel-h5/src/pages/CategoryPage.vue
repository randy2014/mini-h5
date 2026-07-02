<template>
  <section class="page with-tab category-page">
    <van-nav-bar title="分类" />
    <van-loading v-if="loading" class="center-loading" />
    <template v-else>
      <div class="category-grid">
        <button
          v-for="item in categories"
          :key="item.id"
          type="button"
          :class="{ active: item.id === activeCategoryId }"
          @click="selectCategory(item)"
        >
          <strong>{{ item.name }}</strong>
          <span>查看书单</span>
        </button>
      </div>

      <div class="section-title">
        <h2>{{ activeCategoryName || '分类书籍' }}</h2>
        <span>{{ books.length }} 本</span>
      </div>

      <BookCard v-for="book in books" :key="book.id" :book="book" @open="openBook" />
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
