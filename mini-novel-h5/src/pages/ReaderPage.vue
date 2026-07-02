<template>
  <section class="reader-page" :class="readerClass">
    <van-nav-bar :title="chapter.title || '阅读'" left-arrow fixed placeholder @click-left="goCatalog" />

    <van-loading v-if="loading" class="center-loading" />
    <article v-else class="reader-content" :style="readerStyle">
      <p>第 {{ chapter.chapterNo }} 章</p>
      <h1>{{ chapter.title }}</h1>
      <div>{{ formatTextLineBreaks(chapter.content) }}</div>
    </article>

    <div class="reader-toolbar">
      <van-button plain hairline size="small" icon="bars" @click="goCatalog">目录</van-button>
      <van-button plain hairline size="small" icon="arrow-left" @click="goCatalog">返回</van-button>
      <van-button plain hairline size="small" icon="setting-o" @click="settingsOpen = true">设置</van-button>
      <van-button plain hairline size="small" icon="arrow" :loading="nextLoading" @click="readNext">下一章</van-button>
      <van-button plain hairline size="small" icon="diamond-o" to="/h5/vip">VIP</van-button>
    </div>

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
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showConfirmDialog } from 'vant';
import { fetchChapter, fetchNextChapter } from '../services/book';
import { formatTextLineBreaks } from '../utils/text';

const route = useRoute();
const router = useRouter();
const loading = ref(true);
const nextLoading = ref(false);
const settingsOpen = ref(false);
const chapter = ref({});
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

async function loadChapter() {
  loading.value = true;
  try {
    chapter.value = await fetchChapter(route.params.id);
    saveProgress(chapter.value);
    window.scrollTo({ top: 0 });
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

onMounted(loadChapter);
watch(() => route.params.id, loadChapter);
watch(settings, saveSettings);

async function readNext() {
  nextLoading.value = true;
  try {
    const next = await fetchNextChapter(route.params.id);
    router.push(`/h5/read/${next.id}?bookId=${next.novelId}`);
  } finally {
    nextLoading.value = false;
  }
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
</script>
