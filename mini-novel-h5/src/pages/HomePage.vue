<template>
  <section class="page with-tab home-page">
    <header class="home-top">
      <div>
        <p>Mini Novel</p>
        <h1>今天读点好故事</h1>
      </div>
      <router-link class="home-search-link" to="/h5/search" aria-label="搜索">
        <van-icon name="search" />
      </router-link>
    </header>

    <button v-if="heroBook" class="daily-card" type="button" @click="openBook(heroBook)">
      <img :src="heroBook.coverUrl || fallbackCover" :alt="heroBook.title" />
      <span>
        <small>今日推荐</small>
        <strong>{{ heroBook.title }}</strong>
        <em>{{ heroBook.author || '佚名' }} · {{ heroBook.status === 2 ? '完结' : '连载' }}</em>
        <b>{{ formatIntro(heroBook.intro) || '进入详情开始阅读' }}</b>
      </span>
    </button>

    <div class="quick-nav">
      <router-link to="/h5/category">分类</router-link>
      <router-link to="/h5/bookshelf">书架</router-link>
      <router-link to="/h5/vip">VIP</router-link>
      <router-link to="/h5/search">找书</router-link>
    </div>

    <section v-if="recentBooks.length" class="continue-strip">
      <div class="section-title compact">
        <h2>继续阅读</h2>
        <span>最近 {{ recentBooks.length }} 本</span>
      </div>
      <button v-for="item in recentBooks" :key="item.book.id" type="button" @click="continueRead(item)">
        <img :src="item.book.coverUrl || fallbackCover" :alt="item.book.title" />
        <span>
          <strong>{{ item.book.title }}</strong>
          <small>读到第 {{ item.progress.chapterNo }} 章</small>
        </span>
      </button>
    </section>

    <van-loading v-if="loading" class="center-loading" />
    <template v-else>
      <BookSection title="热榜精选" subtitle="更多" rank-type="hot" :books="sections.hot" @open="openBook" @more="openRank" />
      <BookSection title="完结优先" subtitle="更多" rank-type="completed" :books="sections.completed" layout="cover" @open="openBook" @more="openRank" />
      <BookSection title="最近更新" subtitle="更多" rank-type="latest" :books="sections.latest" @open="openBook" @more="openRank" />
      <BookSection title="长篇精选" subtitle="更多" rank-type="long" :books="sections.long" layout="cover" @open="openBook" @more="openRank" />
    </template>
  </section>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import BookCard from '../components/BookCard.vue';
import { fetchHome, fetchHomeSections } from '../services/book';
import { formatTextLineBreaks } from '../utils/text';

const router = useRouter();
const loading = ref(true);
const sections = ref({ hot: [], completed: [], latest: [], long: [] });
const fallbackCover = 'https://dummyimage.com/300x420/1f2933/ffffff&text=Mini+Novel';

const heroBook = computed(() => sections.value.hot?.[0] || sections.value.latest?.[0]);
const recentBooks = computed(() => {
  const pool = [...sections.value.hot, ...sections.value.completed, ...sections.value.latest, ...sections.value.long];
  const seen = new Set();
  return pool
    .filter((book) => {
      if (!book?.id || seen.has(book.id)) {
        return false;
      }
      seen.add(book.id);
      return true;
    })
    .map((book) => ({ book, progress: readProgress(book.id) }))
    .filter((item) => item.progress.chapterId)
    .sort((left, right) => Number(right.progress.updatedAt || 0) - Number(left.progress.updatedAt || 0))
    .slice(0, 3);
});

const BookSection = defineComponent({
  name: 'BookSection',
  props: {
    title: { type: String, required: true },
    subtitle: { type: String, default: '' },
    rankType: { type: String, required: true },
    books: { type: Array, default: () => [] },
    layout: { type: String, default: 'list' }
  },
  emits: ['open', 'more'],
  setup(props, { emit }) {
    return () => {
      if (!props.books.length) {
        return null;
      }
      const title = h('button', { class: 'section-title section-title-link', type: 'button', onClick: () => emit('more', props.rankType) }, [
        h('h2', props.title),
        h('span', props.subtitle || `${props.books.length} 本`)
      ]);
      if (props.layout === 'cover') {
        return h('section', { class: 'book-section' }, [
          title,
          h('div', { class: 'cover-row' }, props.books.map((book) => h('button', {
            key: book.id,
            type: 'button',
            onClick: () => emit('open', book)
          }, [
            h('img', { src: book.coverUrl || fallbackCover, alt: book.title }),
            h('strong', book.title),
            h('small', book.author || '佚名')
          ])))
        ]);
      }
      return h('section', { class: 'book-section' }, [
        title,
        h('div', { class: 'rank-list' }, props.books.map((book, index) => h(BookCard, {
          key: book.id,
          book,
          rank: index + 1,
          onOpen: () => emit('open', book)
        })))
      ]);
    };
  }
});

onMounted(async () => {
  try {
    try {
      const data = await fetchHomeSections();
      sections.value = {
        hot: data.hot || [],
        completed: data.completed || [],
        latest: data.latest || [],
        long: data.long || []
      };
    } catch {
      const latest = await fetchHome();
      sections.value = { hot: latest.slice(0, 12), completed: latest.filter((book) => book.status === 2).slice(0, 8), latest, long: [] };
    }
  } finally {
    loading.value = false;
  }
});

function openBook(book) {
  router.push(`/h5/book/${book.id}`);
}

function openRank(type) {
  router.push(`/h5/rank/${type}`);
}

function continueRead(item) {
  router.push(`/h5/read/${item.progress.chapterId}?bookId=${item.book.id}`);
}

function readProgress(bookId) {
  try {
    return JSON.parse(localStorage.getItem(`mini_novel_read_${bookId}`) || '{}');
  } catch {
    return {};
  }
}

function formatIntro(value) {
  return formatTextLineBreaks(value);
}
</script>
