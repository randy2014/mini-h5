<template>
  <section class="page with-tab home-page">
    <header class="home-top">
      <div>
        <p>Mini Novel</p>
        <h1>今天读点好故事</h1>
      </div>
      <van-button round size="small" color="#1f6f64" icon="search" to="/h5/search">搜索</van-button>
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

    <van-loading v-if="loading" class="center-loading" />
    <template v-else>
      <BookSection title="热榜精选" subtitle="近期活跃" :books="sections.hot" @open="openBook" />
      <BookSection title="完结优先" subtitle="一口气读完" :books="sections.completed" layout="cover" @open="openBook" />
      <BookSection title="最近更新" subtitle="追更入口" :books="sections.latest" @open="openBook" />
      <BookSection title="长篇精选" subtitle="字数充足" :books="sections.long" layout="cover" @open="openBook" />
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

const BookSection = defineComponent({
  name: 'BookSection',
  props: {
    title: { type: String, required: true },
    subtitle: { type: String, default: '' },
    books: { type: Array, default: () => [] },
    layout: { type: String, default: 'list' }
  },
  emits: ['open'],
  setup(props, { emit }) {
    return () => {
      if (!props.books.length) {
        return null;
      }
      const title = h('div', { class: 'section-title' }, [
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

function formatIntro(value) {
  return formatTextLineBreaks(value);
}
</script>
