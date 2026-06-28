<template>
  <main class="h5-shell">
    <router-view />
    <van-tabbar v-if="showTabbar" route safe-area-inset-bottom>
      <van-tabbar-item replace to="/h5/home" icon="wap-home-o">首页</van-tabbar-item>
      <van-tabbar-item replace to="/h5/category" icon="apps-o">分类</van-tabbar-item>
      <van-tabbar-item replace to="/h5/bookshelf" icon="bookmark-o">书架</van-tabbar-item>
      <van-tabbar-item replace to="/h5/profile" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </main>
</template>

<script setup>
import { computed } from 'vue';
import { useRoute } from 'vue-router';

const route = useRoute();
const showTabbar = computed(() => Boolean(route.meta.tab));
</script>
