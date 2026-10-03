<template>
  <section class="page subscribe-channel-page">
    <van-nav-bar :title="channelName" left-arrow @click-left="$router.back()" />

    <div class="chan-head">
      <div class="nm">{{ channelName }}</div>
      <div class="cnt">{{ restricted ? '🔒 未订阅 · 仅可预览小说' : `共 ${total} 条内容 · 已订阅` }}</div>
    </div>

    <div v-if="restricted" class="filter-hint">🔇 媒体图文/视频为订阅专属，订阅后可见</div>
    <div v-else-if="readHiddenCount > 0" class="filter-hint">🔇 已隐藏已读小说 {{ readHiddenCount }} 本 · 媒体内容不参与已读过滤</div>

    <!-- 工具栏 -->
    <div class="toolbar">
      <span class="count">{{ total }} 条内容</span>
    </div>

    <!-- 列表：统一混排（卡片式，参考 VIP 专区精选书单） -->
    <div class="list">
      <div v-for="item in visible" :key="item.kind + '-' + item.id" class="feed-card" @click="open(item)">
        <div class="fc-cover-wrap">
          <div v-if="coverFailed(item)" class="fc-cover" :class="coverClass(item)">{{ coverText(item) }}</div>
          <img v-else-if="coverSrc(item)" :src="coverSrc(item)" class="fc-cover-img" @error="markCoverFailed(item)" />
          <div v-else class="fc-cover" :class="coverClass(item)">{{ coverText(item) }}</div>
        </div>
        <div class="fc-body">
          <div class="fc-topline">
            <span class="type-chip" :class="'t-' + item.kind.toLowerCase()">{{ typeName(item.kind) }}</span>
          </div>
          <strong class="fc-title">{{ item.title }}</strong>
          <em class="fc-sub">{{ subline(item) }}</em>
        </div>
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

function subline(item) {
  if (item.kind === 'NOVEL') {
    const author = item.author;
    if (author && author !== 'Unknown' && author !== 'unknown') {
      return author;
    }
    return item.intro || '';
  }
  return mediaDesc(item);
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
    router.push(`/h5/book/${item.id}`);
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
.filter-hint { padding: 8px 14px 0; font-size: 11px; color: #a6adb9; }
.list { padding: 10px 14px; }
.feed-card { display: grid; grid-template-columns: 78px minmax(0, 1fr); gap: 13px; margin-bottom: 12px; padding: 12px; border: 1px solid rgba(31, 37, 40, .08); border-radius: var(--radius); background: var(--panel); box-shadow: 0 8px 24px rgba(31, 37, 40, .04); }
.feed-card:active { transform: scale(.99); }
.fc-cover-wrap { width: 78px; }
.fc-cover-img { width: 78px; aspect-ratio: 5 / 7; border-radius: 6px; object-fit: cover; display: block; background: #d9dfdc; }
.fc-cover { width: 78px; aspect-ratio: 5 / 7; border-radius: 6px; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 700; text-align: center; line-height: 1.3; }
.fc-body { min-width: 0; display: flex; flex-direction: column; }
.fc-topline { display: flex; align-items: center; gap: 8px; margin-bottom: 5px; }
.type-chip { display: inline-flex; align-items: center; width: fit-content; padding: 2px 7px; border-radius: 999px; font-size: 11px; line-height: 1.25; }
.type-chip.t-novel { border: 1px solid rgba(155, 122, 47, .28); color: var(--gold); background: #fff6d9; }
.type-chip.t-image { border: 1px solid rgba(31, 111, 100, .25); color: var(--brand); background: #e7f3ef; }
.type-chip.t-video { border: 1px solid rgba(47, 111, 216, .25); color: #2f6fd8; background: #eaf2ff; }
.type-chip.t-mixed { border: 1px solid rgba(122, 63, 224, .25); color: #7a3fe0; background: #f6eefe; }
.fc-title { display: block; overflow: hidden; margin: 0 0 6px; font-size: 17px; line-height: 1.25; text-overflow: ellipsis; white-space: nowrap; }
.fc-sub { display: -webkit-box; overflow: hidden; color: #485351; font-size: 13px; font-style: normal; line-height: 1.48; white-space: pre-line; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
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
