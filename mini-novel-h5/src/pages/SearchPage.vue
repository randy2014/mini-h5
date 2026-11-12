<template>
  <section class="page search-page">
    <van-nav-bar title="搜索" left-arrow @click-left="goBackToParent" />
    <van-search
      v-model="keyword"
      placeholder="搜索书名或作者"
      shape="round"
      autofocus
      clearable
      @search="submitSearch"
      @clear="clearSearch"
    />

    <div v-if="!searched" class="search-hint">
      <strong>找一本想读的书</strong>
      <span>输入书名或作者，直接从业务库检索。</span>
    </div>

    <van-loading v-if="loading" class="center-loading" />
    <template v-else>
      <div v-if="searched" class="section-title first">
        <h2>搜索结果</h2>
        <span>{{ results.length }} 本</span>
      </div>
      <BookCard v-for="book in results" :key="book.id" :data-list-key="'search-' + book.id" :book="book" @open="openBook" />
      <van-empty v-if="searched && results.length === 0" description="没有找到相关书籍" />
    </template>
  </section>
</template>

<script setup>
import { ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import BookCard from '../components/BookCard.vue';
import { searchNovels } from '../services/book';
import { useListRestore } from '../composables/useListRestore';
import { goBack } from '../utils/navigation';

const route = useRoute();
const router = useRouter();
const keyword = ref('');
const loading = ref(false);
const searched = ref(false);
const results = ref([]);

/** 返回上一层；直接打开搜索页时兜底回首页。 */
function goBackToParent() {
  goBack(router, '/h5/home');
}

/** 按当前关键词检索（返回时静默刷新，结果列表不闪空态）。 */
async function load({ silent = false } = {}) {
  const query = String(route.query.q || '').trim();
  if (!keyword.value && query) {
    keyword.value = query;
  }
  const value = keyword.value.trim();
  if (!value) {
    searched.value = false;
    results.value = [];
    loading.value = false;
    return;
  }
  if (!silent) {
    loading.value = true;
  }
  searched.value = true;
  try {
    results.value = await searchNovels(value, 50);
  } finally {
    loading.value = false;
  }
}

function captureState() {
  return { keyword: keyword.value, searched: searched.value, results: [...results.value] };
}

function restoreState(state) {
  if (!state || !Array.isArray(state.results)) {
    return false;
  }
  keyword.value = state.keyword || '';
  searched.value = Boolean(state.searched);
  results.value = state.results;
  loading.value = false;
  return true;
}

useListRestore({ key: 'search', capture: captureState, restore: restoreState, load });

async function submitSearch() {
  const value = keyword.value.trim();
  if (!value) {
    clearSearch();
    return;
  }
  loading.value = true;
  searched.value = true;
  router.replace({ path: '/h5/search', query: { q: value } });
  try {
    results.value = await searchNovels(value, 50);
  } finally {
    loading.value = false;
  }
}

function clearSearch() {
  keyword.value = '';
  searched.value = false;
  results.value = [];
  router.replace('/h5/search');
}

function openBook(book) {
  router.push(`/h5/book/${book.id}`);
}
</script>
