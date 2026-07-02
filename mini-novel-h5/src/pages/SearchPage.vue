<template>
  <section class="page search-page">
    <van-nav-bar title="搜索" left-arrow @click-left="$router.back()" />
    <van-search v-model="keyword" placeholder="搜索书名或作者" shape="round" autofocus />
    <div class="empty-panel">搜索接口下一步接入，目前先保留入口和路由。</div>
  </section>
</template>

<script setup>
import { ref } from 'vue';

const keyword = ref('');
</script>
