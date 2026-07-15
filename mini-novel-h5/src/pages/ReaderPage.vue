<template>
  <section class="reader-page" :class="readerClass" @click="toggleControls">
    <van-nav-bar
      v-show="controlsVisible"
      :title="chapter.title || '阅读'"
      left-arrow
      right-text="首页"
      fixed
      placeholder
      @click-left.stop="goCatalog"
      @click-right.stop="goHome"
    />

    <van-loading v-if="loading" class="center-loading" />
    <article v-else class="reader-content" :style="readerStyle">
      <p>第 {{ chapter.chapterNo }} 章</p>
      <h1>{{ chapter.title }}</h1>
      <div>{{ formatTextLineBreaks(chapter.content) }}</div>
    </article>

    <div v-if="!loading" class="reader-progress-pill">{{ readingProgressText }}</div>
    <div v-if="!loading" class="reader-progress-track">
      <span :style="{ width: `${readingPercent}%` }"></span>
    </div>

    <div v-show="controlsVisible" class="reader-toolbar" @click.stop>
      <van-button plain hairline size="small" icon="wap-home-o" @click="goHome">首页</van-button>
      <van-button plain hairline size="small" icon="bars" @click="openCatalog">目录</van-button>
      <van-button plain hairline size="small" icon="arrow-left" :loading="prevLoading" @click="readPrevious">上一章</van-button>
      <van-button plain hairline size="small" icon="arrow" :loading="nextLoading" @click="readNext">下一章</van-button>
    </div>

    <van-popup v-model:show="catalogOpen" position="right" class="catalog-popup">
      <div class="catalog-drawer" @click.stop>
        <div class="section-title compact">
          <h2>目录</h2>
          <span>已加载 {{ catalogChapters.length }} / {{ catalogPage.total || 0 }}</span>
        </div>
        <div v-if="catalogError && !catalogChapters.length" class="catalog-load-error">
          <span>目录加载失败</span>
          <van-button size="small" plain @click="retryCatalog">重试</van-button>
        </div>
        <van-list
          v-else
          v-model:loading="catalogLoading"
          v-model:error="catalogError"
          :finished="catalogFinished"
          :immediate-check="false"
          error-text="目录加载失败，点击重试"
          :finished-text="catalogChapters.length ? `已加载全部 ${catalogPage.total || catalogChapters.length} 章` : ''"
          @load="loadNextCatalogPage"
        >
          <van-loading v-if="catalogLoading && !catalogChapters.length" class="center-loading" />
          <button
            v-else
            v-for="item in catalogChapters"
            :key="item.id"
            type="button"
            :class="{ active: item.id === chapter.id }"
            @click="readCatalog(item)"
          >
            <b>{{ item.title }}</b>
            <small>第 {{ item.chapterNo }} 章</small>
          </button>
          <van-empty v-if="catalogFinished && !catalogChapters.length" description="暂无已发布章节" />
        </van-list>
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

const CATALOG_PAGE_SIZE = 50;
const route = useRoute();
const router = useRouter();
const loading = ref(true);
const nextLoading = ref(false);
const prevLoading = ref(false);
const catalogLoading = ref(false);
const catalogOpen = ref(false);
const controlsVisible = ref(true);
const chapter = ref({});
const catalogChapters = ref([]);
const catalogPageNo = ref(1);
const catalogPage = ref({ current: 1, pages: 1, total: 0, records: [] });
const catalogFinished = ref(false);
const catalogError = ref(false);
const catalogRequestInFlight = ref(false);
const totalChapters = ref(0);
const settings = reactive(loadSettings());

const readerClass = computed(() => `reader-theme-${settings.theme}`);
const readerStyle = computed(() => ({
  '--reader-font-size': `${settings.fontSize}px`
}));
const readingProgressText = computed(() => {
  const total = totalChapters.value ? ` / ${totalChapters.value}` : '';
  const title = chapter.value.title ? ` · ${chapter.value.title}` : '';
  return `第 ${chapter.value.chapterNo || '-'}${total} 章${title}`;
});
const readingPercent = computed(() => {
  if (!chapter.value.chapterNo || !totalChapters.value) {
    return 0;
  }
  return Math.min(100, Math.max(0, Math.round((chapter.value.chapterNo / totalChapters.value) * 100)));
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
    loadTotalChapters();
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

async function loadTotalChapters() {
  const bookId = chapter.value.novelId || route.query.bookId;
  if (!bookId) {
    return;
  }
  try {
    const data = await fetchChapters(bookId, 1, 1);
    totalChapters.value = Number(data?.total || totalChapters.value || 0);
  } catch {
    totalChapters.value = 0;
  }
}

function toggleControls() {
  if (catalogOpen.value || loading.value) {
    return;
  }
  controlsVisible.value = !controlsVisible.value;
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
  resetCatalog();
  await loadNextCatalogPage();
  if (chapter.value.id) {
    while (!catalogChapters.value.some((item) => item.id === chapter.value.id) && !catalogFinished.value && !catalogError.value) {
      await loadNextCatalogPage();
    }
  }
  await loadAllCatalogPages();
}

function resetCatalog() {
  catalogChapters.value = [];
  catalogPageNo.value = 1;
  catalogPage.value = { current: 1, pages: 1, total: 0, records: [] };
  catalogFinished.value = false;
  catalogError.value = false;
}

async function loadNextCatalogPage() {
  const bookId = chapter.value.novelId || route.query.bookId;
  if (!bookId || catalogFinished.value || catalogRequestInFlight.value) {
    catalogLoading.value = false;
    return;
  }
  catalogRequestInFlight.value = true;
  catalogLoading.value = true;
  catalogError.value = false;
  try {
    const data = await fetchChapters(bookId, catalogPageNo.value, CATALOG_PAGE_SIZE);
    const knownIds = new Set(catalogChapters.value.map((item) => item.id));
    (data?.records || []).forEach((item) => {
      if (!knownIds.has(item.id)) {
        knownIds.add(item.id);
        catalogChapters.value.push(item);
      }
    });
    catalogPage.value = data || catalogPage.value;
    const current = Number(data?.current || catalogPageNo.value);
    const pages = Number(data?.pages || 0);
    catalogFinished.value = pages === 0 || current >= pages;
    if (!catalogFinished.value) catalogPageNo.value = current + 1;
  } catch {
    catalogError.value = true;
  } finally {
    catalogRequestInFlight.value = false;
    catalogLoading.value = false;
  }
}

async function loadAllCatalogPages() {
  while (!catalogFinished.value && !catalogError.value) {
    await loadNextCatalogPage();
  }
}

function retryCatalog() {
  if (!catalogChapters.value.length) resetCatalog();
  catalogError.value = false;
  loadNextCatalogPage();
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

function goHome() {
  saveScrollPosition();
  router.push('/h5/home');
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
    title: current.title,
    totalChapters: totalChapters.value || 0,
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

<style scoped>
.catalog-load-error { display:flex; align-items:center; justify-content:space-between; gap:12px; padding:14px 0; color:var(--danger); font-size:13px; }
</style>
