<template>
  <section class="reader-page">
    <van-nav-bar :title="chapter.title || '阅读'" left-arrow fixed placeholder @click-left="goCatalog" />

    <van-loading v-if="loading" class="center-loading" />
    <article v-else class="reader-content">
      <p>第 {{ chapter.chapterNo }} 章</p>
      <h1>{{ chapter.title }}</h1>
      <div>{{ chapter.content }}</div>
    </article>

    <div class="reader-toolbar">
      <van-button plain hairline size="small" @click="goCatalog">目录</van-button>
      <van-button plain hairline size="small" :loading="nextLoading" @click="readNext">下一章</van-button>
      <van-button plain hairline size="small" to="/h5/vip">VIP</van-button>
      <van-button plain hairline size="small" to="/h5/home">首页</van-button>
    </div>
  </section>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showConfirmDialog } from 'vant';
import { fetchChapter, fetchNextChapter } from '../services/book';

const route = useRoute();
const router = useRouter();
const loading = ref(true);
const nextLoading = ref(false);
const chapter = ref({});

async function loadChapter() {
  loading.value = true;
  try {
    chapter.value = await fetchChapter(route.params.id);
    saveProgress(chapter.value);
  } catch (error) {
    await showConfirmDialog({
      title: '需要 VIP',
      message: error.message || '该章节需要开通 VIP 后阅读',
      confirmButtonText: '去开通',
      cancelButtonText: '返回'
    }).then(() => router.push('/h5/vip')).catch(() => router.back());
  } finally {
    loading.value = false;
  }
}

onMounted(loadChapter);
watch(() => route.params.id, loadChapter);

async function readNext() {
  nextLoading.value = true;
  try {
    const next = await fetchNextChapter(route.params.id);
    router.push(`/h5/read/${next.id}?bookId=${next.novelId}`);
  } finally {
    nextLoading.value = false;
  }
}

function goCatalog() {
  const bookId = chapter.value.novelId || route.query.bookId;
  if (!bookId) {
    router.back();
    return;
  }
  router.push(`/h5/book/${bookId}?chapterId=${chapter.value.id || route.params.id}&chapterNo=${chapter.value.chapterNo || ''}`);
}

function saveProgress(current) {
  if (!current?.novelId || !current?.id) {
    return;
  }
  localStorage.setItem(`mini_novel_read_${current.novelId}`, JSON.stringify({
    chapterId: current.id,
    chapterNo: current.chapterNo,
    updatedAt: Date.now()
  }));
}
</script>
