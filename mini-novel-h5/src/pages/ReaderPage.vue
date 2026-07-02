<template>
  <section class="reader-page" :class="readerClass">
    <van-nav-bar :title="chapter.title || '阅读'" left-arrow fixed placeholder @click-left="goCatalog" />

    <van-loading v-if="loading" class="center-loading" />
    <article v-else class="reader-content" :style="readerStyle">
      <p>第 {{ chapter.chapterNo }} 章</p>
      <h1>{{ chapter.title }}</h1>
      <div>{{ formatTextLineBreaks(chapter.content) }}</div>
    </article>

    <div v-if="!loading" class="reader-progress-pill">{{ readingProgressText }}</div>

    <div class="reader-toolbar">
      <van-button plain hairline size="small" icon="bars" @click="openCatalog">目录</van-button>
      <van-button plain hairline size="small" icon="arrow-left" :loading="prevLoading" @click="readPrevious">上一章</van-button>
      <van-button plain hairline size="small" icon="setting-o" @click="settingsOpen = true">设置</van-button>
      <van-button plain hairline size="small" icon="arrow" :loading="nextLoading" @click="readNext">下一章</van-button>
      <van-button plain hairline size="small" icon="diamond-o" to="/h5/vip">VIP</van-button>
    </div>

    <van-popup v-model:show="catalogOpen" position="right" class="catalog-popup">
      <div class="catalog-drawer">
        <div class="section-title compact">
          <h2>目录</h2>
          <span>{{ catalogPage.current || 1 }} / {{ catalogPage.pages || 1 }}</span>
        </div>
        <van-loading v-if="catalogLoading" class="center-loading" />
        <template v-else>
          <button
            v-for="item in catalogChapters"
            :key="item.id"
            type="button"
            :class="{ active: item.id === chapter.id }"
            @click="readCatalog(item)"
          >
            <b>{{ item.title }}</b>
            <small>第 {{ item.chapterNo }} 章</small>
          </button>
          <div v-if="catalogPage.pages > 1" class="chapter-pager">
            <van-button size="small" plain :disabled="catalogPageNo <= 1" @click="loadCatalog(catalogPageNo - 1)">上一页</van-button>
            <span>{{ catalogPageNo }} / {{ catalogPage.pages }}</span>
            <van-button size="small" plain :disabled="catalogPageNo >= catalogPage.pages" @click="loadCatalog(catalogPageNo + 1)">下一页</van-button>
          </div>
        </template>
      </div>
    </van-popup>

    <van-popup v-model:show="settingsOpen" round position="bottom">
      <div class="reader-settings">
        <div class="section-title compact">
          <h2>阅读设置</h2>
          <span>{{ settings.fontSize }}px</span>
        </div>

        <div class="setting-row">
          <span>字号</span>
          <div class="setting-actions">
            <van-button size="small" plain @click="changeFont(-1)">A-</van-button>
            <van-button size="small" plain @click="changeFont(1)">A+</van-button>
          </div>
        </div>

        <div class="setting-row">
          <span>背景</span>
          <div class="theme-swatches">
            <button
              v-for="theme in themes"
              :key="theme.value"
              type="button"
              :class="{ active: settings.theme === theme.value }"
              :style="{ background: theme.color }"
              @click="setTheme(theme.value)"
            >
              {{ theme.label }}
            </button>
          </div>
        </div>

        <van-button block round color="#1f6f64" @click="settingsOpen = false">完成</van-button>
      </div>
    </van-popup>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showConfirmDialog, showToast } from 'vant';
import { fetchChapter, fetchChapters, fetchNextChapter, fetchPreviousChapter } from '../services/book';
import { formatTextLineBreaks } from '../utils/text';

const CATALOG_PAGE_SIZE = 80;
const route = useRoute();
const router = useRouter();
const loading = ref(true);
const nextLoading = ref(false);
const prevLoading = ref(false);
const catalogLoading = ref(false);
const settingsOpen = ref(false);
const catalogOpen = ref(false);
const chapter = ref({});
const catalogChapters = ref([]);
const catalogPageNo = ref(1);
const catalogPage = ref({ current: 1, pages: 1, total: 0, records: [] });
const settings = reactive(loadSettings());
const themes = [
  { value: 'paper', label: '纸', color: '#f7f0e4' },
  { value: 'green', label: '绿', color: '#e8f2ea' },
  { value: 'white', label: '白', color: '#f8faf7' },
  { value: 'night', label: '夜', color: '#1d2224' }
];

const readerClass = computed(() => `reader-theme-${settings.theme}`);
const readerStyle = computed(() => ({
  '--reader-font-size': `${settings.fontSize}px`
}));
const readingProgressText = computed(() => {
  const title = chapter.value.title ? ` · ${chapter.value.title}` : '';
  return `第 ${chapter.value.chapterNo || '-'} 章${title}`;
});

onMounted(() => {
  loadChapter();
  window.addEventListener('scroll', saveScrollPosition, { passive: true });
});
onBeforeUnmount(() => {
  saveScrollPosition();
  window.removeEventListener('scroll', saveScrollPosition);
});
watch(() => route.params.id, loadChapter);
watch(settings, saveSettings);

async function loadChapter() {
  saveScrollPosition();
  loading.value = true;
  try {
    chapter.value = await fetchChapter(route.params.id);
    saveProgress(chapter.value);
    await nextTick();
    restoreScrollPosition();
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

async function readPrevious() {
  prevLoading.value = true;
  try {
    const previous = await fetchPreviousChapter(route.params.id);
    router.push(`/h5/read/${previous.id}?bookId=${previous.novelId}`);
  } catch (error) {
    showToast(error.message || '已经是第一章');
  } finally {
    prevLoading.value = false;
  }
}

async function readNext() {
  nextLoading.value = true;
  try {
    const next = await fetchNextChapter(route.params.id);
    router.push(`/h5/read/${next.id}?bookId=${next.novelId}`);
  } catch (error) {
    showToast(error.message || '已经是最后一章');
  } finally {
    nextLoading.value = false;
  }
}

async function openCatalog() {
  catalogOpen.value = true;
  const targetPage = chapter.value.chapterNo ? Math.max(1, Math.ceil(chapter.value.chapterNo / CATALOG_PAGE_SIZE)) : 1;
  await loadCatalog(targetPage);
}

async function loadCatalog(page = 1) {
  const bookId = chapter.value.novelId || route.query.bookId;
  if (!bookId) {
    return;
  }
  catalogLoading.value = true;
  try {
    const data = await fetchChapters(bookId, page, CATALOG_PAGE_SIZE);
    catalogPage.value = data || { current: page, pages: 1, total: 0, records: [] };
    catalogChapters.value = catalogPage.value.records || [];
    catalogPageNo.value = Number(catalogPage.value.current || page);
  } finally {
    catalogLoading.value = false;
  }
}

function readCatalog(item) {
  catalogOpen.value = false;
  router.push(`/h5/read/${item.id}?bookId=${item.novelId}`);
}

function goCatalog() {
  const bookId = chapter.value.novelId || route.query.bookId;
  if (!bookId) {
    router.back();
    return;
  }
  router.push(`/h5/book/${bookId}?chapterId=${chapter.value.id || route.params.id}&chapterNo=${chapter.value.chapterNo || ''}`);
}

function changeFont(step) {
  settings.fontSize = Math.max(16, Math.min(24, settings.fontSize + step));
}

function setTheme(theme) {
  settings.theme = theme;
}

function loadSettings() {
  try {
    return { fontSize: 20, theme: 'paper', ...JSON.parse(localStorage.getItem('mini_novel_reader_settings') || '{}') };
  } catch {
    return { fontSize: 20, theme: 'paper' };
  }
}

function saveSettings() {
  localStorage.setItem('mini_novel_reader_settings', JSON.stringify({
    fontSize: settings.fontSize,
    theme: settings.theme
  }));
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

function saveScrollPosition() {
  const currentId = chapter.value?.id || route.params.id;
  if (!currentId) {
    return;
  }
  localStorage.setItem(`mini_novel_scroll_${currentId}`, String(Math.max(0, Math.round(window.scrollY || 0))));
}

function restoreScrollPosition() {
  const saved = Number(localStorage.getItem(`mini_novel_scroll_${chapter.value.id}`) || 0);
  window.requestAnimationFrame(() => {
    window.scrollTo({ top: Math.max(0, saved), behavior: saved > 0 ? 'smooth' : 'auto' });
  });
}
</script>
