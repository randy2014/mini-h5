<template>
  <section class="page subscribe-channel-page">
    <van-nav-bar :title="channelName" left-arrow @click-left="$router.back()" />

    <div class="chan-head">
      <div class="nm">{{ channelName }}</div>
      <div class="cnt">{{ restricted ? '🔒 未订阅 · 仅可预览小说' : `共 ${total} 条内容 · 已订阅` }}</div>
    </div>

    <div v-if="restricted" class="filter-hint">🔇 媒体图文/视频为订阅专属，订阅后可见</div>
    <div v-else-if="readHiddenCount > 0" class="filter-hint">🔇 已隐藏已读小说 {{ readHiddenCount }} 本 · 媒体内容不参与已读过滤</div>

    <!-- 工具栏：网格/列表切换（沿用现有交互） -->
    <div class="toolbar">
      <span class="count">{{ total }} 条内容</span>
      <div class="view-toggle">
        <div :class="['vt', { active: viewMode === 'grid' }]" @click="viewMode = 'grid'">▦ 网格</div>
        <div :class="['vt', { active: viewMode === 'list' }]" @click="viewMode = 'list'">☰ 列表</div>
      </div>
    </div>

    <!-- 网格：统一混排 -->
    <div v-if="viewMode === 'grid'" class="grid">
      <div v-for="item in visible" :key="item.kind + '-' + item.id" class="gcell" @click="open(item)">
        <div class="gcover-wrap">
          <div v-if="coverFailed(item)" class="gcover" :class="coverClass(item)">{{ coverText(item) }}</div>
          <img v-else-if="coverSrc(item)" :src="coverSrc(item)" class="gcover-img" @error="markCoverFailed(item)" />
          <div v-else class="gcover" :class="coverClass(item)">{{ coverText(item) }}</div>
          <span v-if="item.kind === 'NOVEL'" class="badge novel">📖</span>
          <span v-else-if="item.kind === 'VIDEO'" class="badge video">▶</span>
          <span v-else-if="item.kind === 'MIXED'" class="badge mixed">图+▶</span>
          <span v-else class="badge image">🖼</span>
          <span v-if="item.videoDurationMs" class="dur">{{ fmtDur(item.videoDurationMs) }}</span>
        </div>
        <div class="gname">{{ item.title }}</div>
        <div v-if="item.kind === 'NOVEL'" class="gauthor">{{ item.author || '' }}</div>
      </div>
    </div>

    <!-- 列表：统一混排 -->
    <div v-else class="list">
      <div v-for="item in visible" :key="item.kind + '-' + item.id" class="book-row" @click="open(item)">
        <div class="lcover-wrap">
          <div v-if="coverFailed(item)" class="bcover" :class="coverClass(item)">{{ coverText(item) }}</div>
          <img v-else-if="coverSrc(item)" :src="coverSrc(item)" class="bcover-img" @error="markCoverFailed(item)" />
          <div v-else class="bcover" :class="coverClass(item)">{{ coverText(item) }}</div>
        </div>
        <div class="info">
          <div class="bt">
            {{ item.title }}
            <span class="type-chip" :class="'t-' + item.kind.toLowerCase()">
              {{ typeName(item.kind) }}
            </span>
          </div>
          <div class="ba">{{ item.author || mediaDesc(item) }}</div>
        </div>
        <span class="go">›</span>
      </div>
    </div>

    <div v-if="visible.length === 0 && !loading" class="empty-list">
      {{ restricted ? '订阅后查看该频道内容' : '频道还没有内容' }}
    </div>

    <div v-if="hasMore" class="more" @click="loadMore">加载更多</div>

    <!-- 未订阅引导 -->
    <div v-if="restricted" class="footbar">
      <div class="hint">🔒 订阅后可查看本频道全部小说与媒体内容<br />当前可预览部分小说</div>
      <van-button round color="#e6b422" @click="onSubscribe">订阅频道</van-button>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showToast } from 'vant';
import { fetchChannelFeed, mediaFileUrl, subscribeChannel } from '../services/subscribe';
import { markSubscribeRead, subscribeReadIds } from '../services/subscribeReadStatus';

const route = useRoute();
const router = useRouter();
const channelId = Number(route.params.id);
const channelName = ref('频道详情');
const feed = ref([]);
const total = ref(0);
const restricted = ref(false);
const loading = ref(false);
const viewMode = ref('grid');
const page = ref(1);
const pageSize = 20;

// 响应式已读集合：点击小说标记后即时从列表移除（媒体内容不参与已读过滤）
const readIds = ref(new Set(subscribeReadIds()));
const readHiddenCount = ref(0);

function refreshReadIds() {
  readIds.value = new Set(subscribeReadIds());
}

const visible = computed(() => feed.value.filter((item) => item.kind !== 'NOVEL' || !readIds.value.has(String(item.id))));
const hasMore = computed(() => !restricted.value && feed.value.length < total.value);

function typeName(kind) {
  return { NOVEL: '小说', IMAGE: '图文', VIDEO: '视频', MIXED: '图文+视频' }[kind] || kind;
}

function mediaDesc(item) {
  const parts = [];
  if (item.imageCount) parts.push(`${item.imageCount} 图`);
  if (item.videoCount) parts.push(`${item.videoCount} 视频`);
  if (item.videoDurationMs) parts.push(fmtDur(item.videoDurationMs));
  return parts.join(' · ');
}

function coverText(item) {
  if (item.kind === 'NOVEL') return item.title.slice(0, 6);
  if (item.kind === 'VIDEO') return '▶';
  return item.title.slice(0, 4);
}

function coverClass(item) {
  const n = item.id || 0;
  if (item.kind === 'NOVEL') return ['g1', 'g2', 'g3', 'g4', 'g5', 'g6'][n % 6];
  if (item.kind === 'VIDEO') return 'media-video';
  return ['mg1', 'mg2', 'mg3', 'mg4'][n % 4];
}

function coverSrc(item) {
  if (item.kind === 'NOVEL') {
    return `/api/cover/${item.id}`; // 小说封面代理（无封面回退像素）
  }
  if (item.coverAssetId) {
    return mediaFileUrl(channelId, item.coverAssetId, item.coverKind === 'poster' ? 'poster' : 'thumb');
  }
  return '';
}

function coverFailed(item) {
  return item._coverFailed === true;
}

function markCoverFailed(item) {
  item._coverFailed = true;
}

function fmtDur(ms) {
  if (!ms) return '';
  const s = Math.round(ms / 1000);
  const m = Math.floor(s / 60);
  const sec = String(s % 60).padStart(2, '0');
  return `${m}:${sec}`;
}

function open(item) {
  if (restricted.value) {
    showToast('订阅后查看');
    return;
  }
  if (item.kind === 'NOVEL') {
    // 点开正文即视为已读：标记后从列表移除（与既有已读隐藏语义一致）
    markSubscribeRead(item.id, { title: item.title, author: item.author || '' });
    readHiddenCount.value += 1;
    refreshReadIds();
    router.push(`/h5/read/${item.id}`);
    return;
  }
  router.push(`/h5/subscribe/${channelId}/media/${item.id}`);
}

async function onSubscribe() {
  try {
    await subscribeChannel(channelId, 'MONTH');
    showToast('订阅成功');
    page.value = 1;
    restricted.value = false;
    await load();
  } catch {
    // toast handled by interceptor
  }
}

async function loadMore() {
  page.value += 1;
  await load(true);
}

async function load(append = false) {
  loading.value = true;
  refreshReadIds();
  try {
    const data = await fetchChannelFeed(channelId, page.value, pageSize);
    const incoming = data.records || [];
    const next = append ? feed.value.concat(incoming) : incoming;
    feed.value = next;
    total.value = data.total || 0;
    restricted.value = data.restricted;
    const novelItems = incoming.filter((i) => i.kind === 'NOVEL');
    const hidden = novelItems.filter((i) => readIds.value.has(String(i.id))).length;
    readHiddenCount.value = append ? readHiddenCount.value + hidden : hidden;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.subscribe-channel-page { background: #f2f3f7; min-height: 100vh; padding-bottom: 20px; }
.chan-head { background: linear-gradient(135deg, #1f6f64, #2e8b7a); color: #fff; padding: 14px 16px 20px; }
.chan-head .nm { font-size: 19px; font-weight: 700; }
.chan-head .cnt { font-size: 11px; opacity: .85; margin-top: 6px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; padding: 10px 14px 0; }
.toolbar .count { font-size: 12px; color: #55657a; font-weight: 600; }
.view-toggle { display: flex; background: #e9ecf2; border-radius: 10px; padding: 3px; }
.vt { padding: 6px 16px; font-size: 12px; font-weight: 600; color: #8a92a3; border-radius: 8px; }
.vt.active { background: #fff; color: #1f6f64; box-shadow: 0 1px 3px rgba(0,0,0,.08); }
.filter-hint { padding: 8px 14px 0; font-size: 11px; color: #a6adb9; }
.grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; padding: 12px 14px; }
.gcell { display: flex; flex-direction: column; gap: 5px; }
.gcover-wrap { position: relative; border-radius: 9px; overflow: hidden; aspect-ratio: 3 / 4; }
.gcover-img { width: 100%; height: 100%; object-fit: cover; display: block; background: #e4eaf1; }
.gcover { width: 100%; height: 100%; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; text-align: center; line-height: 1.3; }
.gname { font-size: 11px; font-weight: 600; color: #4b5563; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.gauthor { font-size: 10px; color: #98a5b5; }
.badge { position: absolute; top: 5px; left: 5px; font-size: 9px; padding: 1px 6px; border-radius: 8px; background: rgba(0,0,0,.55); color: #fff; }
.badge.video { background: rgba(47,111,216,.92); }
.badge.mixed { background: rgba(122,63,224,.92); }
.badge.image { background: rgba(31,122,92,.92); }
.dur { position: absolute; right: 5px; bottom: 5px; font-size: 9px; background: rgba(0,0,0,.6); color: #fff; padding: 1px 6px; border-radius: 7px; }
.list { padding: 8px 14px; }
.book-row { display: flex; gap: 11px; padding: 11px 0; border-bottom: 1px solid #eef1f6; background: #fff; border-radius: 10px; margin-bottom: 8px; padding: 10px; box-shadow: 0 1px 3px rgba(0,0,0,.04); }
.lcover-wrap { width: 62px; height: 84px; flex-shrink: 0; border-radius: 7px; overflow: hidden; position: relative; }
.bcover-img { width: 100%; height: 100%; object-fit: cover; display: block; }
.bcover { width: 100%; height: 100%; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 700; text-align: center; line-height: 1.3; }
.info { flex: 1; min-width: 0; display: flex; flex-direction: column; justify-content: center; gap: 4px; }
.bt { font-size: 13px; font-weight: 600; }
.type-chip { font-size: 9px; padding: 1px 6px; border-radius: 8px; margin-left: 5px; vertical-align: 1px; }
.type-chip.t-novel { background: #fdf3dd; color: #b07f13; }
.type-chip.t-image { background: #e7f6f0; color: #1f7a5c; }
.type-chip.t-video { background: #eaf2ff; color: #2f6fd8; }
.type-chip.t-mixed { background: #f6eefe; color: #7a3fe0; }
.ba { font-size: 10px; color: #98a5b5; }
.go { color: #c4ccd6; font-size: 16px; align-self: center; }
.empty-list { text-align: center; color: #a6adb9; font-size: 13px; padding: 40px 0; }
.more { text-align: center; color: #1f6f64; font-size: 13px; padding: 12px; }
.footbar { position: fixed; left: 0; right: 0; bottom: 52px; background: #fff; border-top: 1px solid #e9ecf2; padding: 11px 14px; display: flex; align-items: center; gap: 10px; }
.footbar .hint { flex: 1; font-size: 11px; color: #8a92a3; line-height: 1.6; }
.g1 { background: linear-gradient(135deg, #6a5acd, #8e7cc3); }
.g2 { background: linear-gradient(135deg, #2f80ed, #56a0f5); }
.g3 { background: linear-gradient(135deg, #e67e22, #f39c12); }
.g4 { background: linear-gradient(135deg, #16a085, #2ecc71); }
.g5 { background: linear-gradient(135deg, #c0392b, #e74c3c); }
.g6 { background: linear-gradient(135deg, #34495e, #5d6d7e); }
.mg1 { background: linear-gradient(135deg, #b5432f, #e07b39); }
.mg2 { background: linear-gradient(135deg, #236a5c, #2f9e8f); }
.mg3 { background: linear-gradient(135deg, #4a3fa8, #7a6fe0); }
.mg4 { background: linear-gradient(135deg, #c9a10f, #e6c53f); }
.media-video { background: linear-gradient(135deg, #20313f, #3a5568); }
</style>
