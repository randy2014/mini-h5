<template>
  <button class="book-card" :class="[`book-card--${variant}`]" type="button" @click="$emit('open', book)">
    <span class="book-cover-wrap">
      <img :src="book.coverUrl || fallbackCover" :alt="book.title" />
      <i v-if="rank" class="rank-badge">{{ rank }}</i>
    </span>
    <span class="book-card-body">
      <span class="book-card-topline">
        <i class="book-status" :class="{ completed: book.status === 2 }">
          {{ book.status === 2 ? '完结' : '连载' }}
        </i>
        <small v-if="book.categoryName">{{ book.categoryName }}</small>
      </span>
      <strong>{{ book.title }}</strong>
      <small>{{ bookMetaLine }}</small>
      <em>{{ formatIntro(book.intro) || '暂无简介' }}</em>
      <span v-if="book.latestChapterTitle" class="latest-line">{{ book.latestChapterTitle }}</span>
    </span>
  </button>
</template>

<script setup>
import { computed } from 'vue';
import { formatTextLineBreaks } from '../utils/text';

const props = defineProps({
  book: {
    type: Object,
    required: true
  },
  rank: {
    type: Number,
    default: 0
  },
  variant: {
    type: String,
    default: 'list'
  }
});

defineEmits(['open']);

const fallbackCover = 'https://dummyimage.com/300x420/1f2933/ffffff&text=Mini+Novel';

const wordCountLabel = computed(() => {
  const count = Number(props.book.wordCount || 0);
  if (!count) {
    return '';
  }
  if (count >= 10000) {
    return `${(count / 10000).toFixed(1).replace('.0', '')}万字`;
  }
  return `${count}字`;
});

const bookMetaLine = computed(() => {
  return [props.book.author || '佚名', wordCountLabel.value].filter(Boolean).join(' · ');
});

function formatIntro(value) {
  return formatTextLineBreaks(value);
}
</script>
