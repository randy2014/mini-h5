<template>
  <section class="page rank-page">
    <van-nav-bar :title="rankMeta.title" left-arrow @click-left="goBackToParent" />

    <div class="rank-hero">
      <strong>{{ rankMeta.title }}</strong>
      <span>{{ rankMeta.subtitle }}</span>
    </div>

    <van-loading v-if="loading" class="center-loading" />
    <template v-else>
      <BookCard
        v-for="(book, index) in books"
        :key="book.id"
        :data-list-key="'rank-' + rankType + '-' + book.id"
        :book="book"
        :rank="index + 1"
        @open="openBook"
      />
      <van-empty v-if="books.length === 0" description="暂无榜单数据" />
    </template>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import BookCard from '../components/BookCard.vue';
import { fetchRankNovels } from '../services/book';
import { useListRestore } from '../composables/useListRestore';
import { goBack } from '../utils/navigation';

const route = useRoute();
const router = useRouter();
const loading = ref(true);
const books = ref([]);
const rankType = computed(() => String(route.params.type || 'hot').toLowerCase());

/** 返回上一层；直接打开榜单时兜底回首页。 */
function goBackToParent() {
  goBack(router, '/h5/home');
}

const rankMap = {
  hot: { apiType: 'HOT', title: '热榜精选', subtitle: '近期更新活跃的优质作品' },
  completed: { apiType: 'COMPLETED', title: '完结优先', subtitle: '适合一口气读完的完结作品' },
  latest: { apiType: 'LATEST', title: '最近更新', subtitle: '刚刚入库或持续更新的作品' },
  long: { apiType: 'LONG', title: '长篇精选', subtitle: '章节和字数更充足的长篇作品' }
};

const rankMeta = computed(() => rankMap[rankType.value] || rankMap.hot);

/** 拉当前榜单最新数据；返回时静默刷新。 */
async function loadRank({ silent = false } = {}) {
  if (!silent) {
    loading.value = true;
  }
  try {
    books.value = await fetchRankNovels(rankMeta.value.apiType, 80);
  } finally {
    loading.value = false;
  }
}

function captureState() {
  return { type: rankType.value, books: [...books.value] };
}

function restoreState(state) {
  if (!state || !Array.isArray(state.books) || state.type !== rankType.value) {
    return false;
  }
  books.value = state.books;
  loading.value = false;
  return true;
}

useListRestore({
  key: () => `rank:${rankType.value}`,
  capture: captureState,
  restore: restoreState,
  load: loadRank
});

watch(rankType, () => loadRank());

function openBook(book) {
  router.push(`/h5/book/${book.id}`);
}
</script>
