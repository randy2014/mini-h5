<template>
  <section class="reader-page">
    <van-nav-bar :title="chapter.title || '阅读'" left-arrow fixed placeholder @click-left="$router.back()" />

    <van-loading v-if="loading" class="center-loading" />
    <article v-else class="reader-content">
      <p>第 {{ chapter.chapterNo }} 章</p>
      <h1>{{ chapter.title }}</h1>
      <div>{{ chapter.content }}</div>
    </article>

    <div class="reader-toolbar">
      <van-button plain hairline size="small" @click="$router.back()">目录</van-button>
      <van-button plain hairline size="small" to="/h5/vip">VIP</van-button>
      <van-button plain hairline size="small" to="/h5/home">首页</van-button>
    </div>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showConfirmDialog } from 'vant';
import { fetchChapter } from '../services/book';

const route = useRoute();
const router = useRouter();
const loading = ref(true);
const chapter = ref({});

onMounted(async () => {
  try {
    chapter.value = await fetchChapter(route.params.id);
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
});
</script>
