<template>
  <section class="page subscribe-channel-page">
    <van-nav-bar :title="channelName" left-arrow @click-left="$router.back()" />

    <div class="toolbar">
      <div class="view-toggle">
        <div :class="['vt', { active: viewMode === 'grid' }]" @click="viewMode = 'grid'">▦ 网格</div>
        <div :class="['vt', { active: viewMode === 'list' }]" @click="viewMode = 'list'">☰ 列表</div>
      </div>
    </div>

    <div v-if="readCount > 0" class="filter-hint">🔇 已隐藏已读 {{ readCount }} 本 · 仅展示未读</div>
    <div v-if="restricted" class="filter-hint">🔇 未订阅 · 仅预览第一页</div>

    <!-- 网格 -->
    <div v-if="viewMode === 'grid'" class="grid">
      <div v-for="n in novels" :key="n.id" class="gcell" @click="read(n)">
        <div class="gcover" :class="coverClass(n.id)">{{ n.title.slice(0, 6) }}</div>
        <div class="gname">{{ n.title }}</div>
      </div>
    </div>

    <!-- 列表 -->
    <div v-else class="list">
      <div v-for="n in novels" :key="n.id" class="book-row" @click="read(n)">
        <div class="bcover" :class="coverClass(n.id)">{{ n.title.slice(0, 6) }}</div>
        <div class="info">
          <div class="bt">{{ n.title }}</div>
          <div class="ba">{{ n.author }}</div>
          <div class="bb">{{ n.intro || '' }}</div>
        </div>
      </div>
    </div>

    <div v-if="novels.length === 0" class="empty-list">暂无内容</div>

    <div v-if="hasMore" class="more" @click="loadMore">加载更多</div>

    <div v-if="restricted" class="footbar">
      <div class="hint">🔒 <b>订阅后查看全部 {{ total }} 本</b><br />当前仅预览第一页</div>
      <van-button round color="#e6b422" @click="onSubscribe">1280 币订阅</van-button>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showToast } from 'vant';
import { fetchChannelNovels, subscribeChannel } from '../services/subscribe';

const route = useRoute();
const router = useRouter();
const channelId = Number(route.params.id);
const channelName = ref('频道详情');
const novels = ref([]);
const total = ref(0);
const restricted = ref(false);
const readCount = ref(0);
const viewMode = ref('grid');
const page = ref(1);
const pageSize = 20;

const hasMore = computed(() => !restricted.value && novels.value.length < total.value);

function coverClass(id) {
  return ['g1', 'g2', 'g3', 'g4', 'g5', 'g6'][id % 6];
}

function read(n) {
  if (restricted.value) {
    showToast('订阅后查看');
    return;
  }
  router.push(`/h5/read/${n.id}`);
}

async function onSubscribe() {
  try {
    await subscribeChannel(channelId, 'MONTH');
    showToast('订阅成功');
    page.value = 1;
    await load();
  } catch {
    // toast handled
  }
}

async function loadMore() {
  page.value += 1;
  await load(true);
}

async function load(append = false) {
  const data = await fetchChannelNovels(channelId, page.value, pageSize);
  novels.value = append ? novels.value.concat(data.records) : data.records;
  total.value = data.total;
  restricted.value = data.restricted;
  readCount.value = data.readCount || 0;
}

onMounted(load);
</script>

<style scoped>
.subscribe-channel-page { background: #f2f3f7; min-height: 100vh; padding-bottom: 20px; }
.toolbar { padding: 12px 14px 0; }
.view-toggle { display: flex; background: #e9ecf2; border-radius: 10px; padding: 3px; }
.vt { flex: 1; text-align: center; padding: 6px 0; font-size: 12px; font-weight: 600; color: #8a92a3; border-radius: 8px; }
.vt.active { background: #fff; color: #1f6f64; }
.filter-hint { padding: 8px 14px 0; font-size: 11px; color: #a6adb9; }
.grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; padding: 12px 14px; }
.gcell { display: flex; flex-direction: column; gap: 6px; }
.gcover { height: 118px; border-radius: 8px; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; text-align: center; line-height: 1.3; }
.gname { font-size: 11px; font-weight: 600; text-align: left; color: #4b5563; }
.list { padding: 12px 14px; }
.book-row { display: flex; gap: 10px; padding: 10px 0; border-bottom: 1px solid #f0f2f7; }
.book-row:last-child { border-bottom: none; }
.bcover { width: 46px; height: 60px; border-radius: 6px; flex-shrink: 0; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 10px; font-weight: 700; text-align: center; line-height: 1.3; }
.info { flex: 1; min-width: 0; }
.bt { font-size: 13px; font-weight: 600; }
.ba { font-size: 10px; color: #8a92a3; margin: 3px 0; }
.bb { font-size: 10px; color: #a6adb9; line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.empty-list { text-align: left; color: #a6adb9; font-size: 13px; padding: 30px 14px; }
.more { text-align: center; color: #1f6f64; font-size: 13px; padding: 12px; }
.footbar { position: fixed; left: 0; right: 0; bottom: 52px; background: #fff; border-top: 1px solid #e9ecf2; padding: 11px 14px; display: flex; align-items: center; gap: 10px; }
.footbar .hint { flex: 1; font-size: 11px; color: #8a92a3; line-height: 1.5; }
.footbar .hint b { color: #e07b39; }
.g1 { background: linear-gradient(135deg, #6a5acd, #8e7cc3); }
.g2 { background: linear-gradient(135deg, #2f80ed, #56a0f5); }
.g3 { background: linear-gradient(135deg, #e67e22, #f39c12); }
.g4 { background: linear-gradient(135deg, #16a085, #2ecc71); }
.g5 { background: linear-gradient(135deg, #c0392b, #e74c3c); }
.g6 { background: linear-gradient(135deg, #34495e, #5d6d7e); }
</style>
