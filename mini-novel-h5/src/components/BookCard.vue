<template>
  <button class="book-card" type="button" @click="$emit('open', book)">
    <img :src="book.coverUrl || fallbackCover" :alt="book.title" />
    <span>
      <strong>{{ book.title }}</strong>
      <small>{{ book.author }} · {{ book.latestChapterTitle || '暂无更新' }}</small>
      <em>{{ book.intro || '暂无简介' }}</em>
    </span>
  </button>
</template>

<script setup>
defineProps({
  book: {
    type: Object,
    required: true
  }
});

defineEmits(['open']);

const fallbackCover = 'https://dummyimage.com/300x420/20232a/ffffff&text=Mini+Novel';
</script>
