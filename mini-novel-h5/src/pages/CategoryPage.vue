<template>
  <section class="page with-tab">
    <van-nav-bar title="分类" />
    <div class="category-grid">
      <button v-for="item in categories" :key="item" type="button">
        <strong>{{ item }}</strong>
        <span>热门更新</span>
      </button>
    </div>
  </section>
</template>

<script setup>
const categories = ['玄幻', '都市', '仙侠', '悬疑', '科幻', '历史', '游戏', '轻小说'];
</script>
