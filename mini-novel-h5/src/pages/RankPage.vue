<template>
  <section class="page rank-page">
    <van-nav-bar :title="rankMeta.title" left-arrow @click-left="$router.back()" />

    <div class="rank-hero">
      <strong>{{ rankMeta.title }}</strong>
      <span>{{ rankMeta.subtitle }}</span>
    </div>

    <van-loading v-if="loading" class="center-loading" />
    <template v-else>
      <BookCard
        v-for="(book, index) in books"
        :key="book.id"
        :book="book"
        :rank="index + 1"
        @open="openBook"
      />
      <van-empty v-if="books.length === 0" description="暂无榜单数据" />
    </template>
  </section>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import BookCard from '../components/BookCard.vue';
import { fetchRankNovels } from '../services/book';

const route = useRoute();
const router = useRouter();
const loading = ref(true);
const books = ref([]);

const rankMap = {
  hot: { apiType: 'HOT', title: '热榜精选', subtitle: '近期更新活跃的优质作品' },
  completed: { apiType: 'COMPLETED', title: '完结优先', subtitle: '适合一口气读完的完结作品' },
  latest: { apiType: 'LATEST', title: '最近更新', subtitle: '刚刚入库或持续更新的作品' },
  long: { apiType: 'LONG', title: '长篇精选', subtitle: '章节和字数更充足的长篇作品' }
};

const rankMeta = computed(() => rankMap[String(route.params.type || '').toLowerCase()] || rankMap.hot);

onMounted(loadRank);
watch(() => route.params.type, loadRank);

async function loadRank() {
  loading.value = true;
  try {
    books.value = await fetchRankNovels(rankMeta.value.apiType, 80);
  } finally {
    loading.value = false;
  }
}

function openBook(book) {
  router.push(`/h5/book/${book.id}`);
}
</script>
