<template>
  <section class="page with-tab vip-page">
    <van-nav-bar title="VIP 专区" left-arrow @click-left="$router.back()" />

    <div v-if="!confirmed" class="vip-adult-gate">
      <div class="vip-gate-icon"><van-icon name="shield-o" /></div>
      <div class="vip-gate-copy">
        <h1>成人内容提示</h1>
        <p>本频道包含成人主题内容，仅面向已满 18 周岁的用户。</p>
      </div>
      <van-checkbox v-model="adult" shape="square">我确认已满 18 周岁并自愿查看</van-checkbox>
      <div class="vip-gate-actions">
        <van-button type="primary" :disabled="!adult" @click="confirm">确认进入</van-button>
        <van-button plain @click="$router.replace('/h5/home')">返回首页</van-button>
      </div>
    </div>

    <template v-else>
      <section class="vip-channel-head">
        <div>
          <p>精选频道</p>
          <h1>VIP 专区</h1>
          <span>已审批上架的成人内容书单</span>
        </div>
        <van-icon name="diamond-o" />
      </section>

      <section class="vip-status-strip" :class="{ inactive: !status.active }">
        <span class="vip-status-dot" />
        <div>
          <strong>{{ status.active ? 'VIP 权益已生效' : '当前为普通用户' }}</strong>
          <small v-if="status.active">有效期至 {{ expiryLabel }}</small>
          <small v-else>可浏览书单，开通后阅读正文</small>
        </div>
        <van-button v-if="!status.active" size="small" plain to="/h5/profile">获取资格</van-button>
      </section>

      <div class="vip-age-note">
        <van-icon name="info-o" />
        <span>仅限 18 岁以上用户，请理性阅读。</span>
      </div>

      <div v-if="statusError" class="vip-feedback vip-feedback--error">
        <van-icon name="warning-o" />
        <span>VIP 身份校验失败，请稍后重试或重新登录。</span>
        <button type="button" @click="load">重试</button>
      </div>

      <div class="section-title vip-list-title">
        <h2>精选书单</h2>
        <span>{{ books.length ? `${books.length} 本` : '专属内容' }}</span>
      </div>

      <div v-if="loading" class="vip-book-skeletons" aria-label="正在加载 VIP 书单">
        <div v-for="item in 3" :key="item" class="vip-book-skeleton">
          <van-skeleton-image />
          <van-skeleton :row="3" />
        </div>
      </div>
      <div v-else-if="booksError" class="vip-feedback vip-feedback--error">
        <van-icon name="warning-o" />
        <span>VIP 书单加载失败，请稍后再试。</span>
        <button type="button" @click="load">重新加载</button>
      </div>
      <van-empty v-else-if="!books.length" image="search" description="暂无已审批上架的 VIP 书籍">
        <van-button size="small" plain @click="load">刷新书单</van-button>
      </van-empty>
      <div v-else class="vip-books">
        <BookCard
          v-for="book in books"
          :key="book.id"
          :book="book"
          @open="$router.push(`/h5/book/${book.id}`)"
        />
      </div>
    </template>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import BookCard from '../components/BookCard.vue';
import { fetchVipBooks, fetchVipStatus } from '../services/vip';

const key = 'mini_novel_vip_adult_confirmed';
const adult = ref(false);
const confirmed = ref(localStorage.getItem(key) === 'yes');
const loading = ref(false);
const books = ref([]);
const status = ref({ active: false, vipExpireTime: null });
const statusError = ref(false);
const booksError = ref(false);

const expiryLabel = computed(() => {
  if (!status.value.vipExpireTime) return '长期有效';
  const date = new Date(status.value.vipExpireTime);
  if (Number.isNaN(date.getTime())) return status.value.vipExpireTime;
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }).format(date);
});

async function load() {
  loading.value = true;
  statusError.value = false;
  booksError.value = false;
  const results = await Promise.allSettled([fetchVipStatus(), fetchVipBooks()]);
  if (results[0].status === 'fulfilled') status.value = results[0].value;
  else statusError.value = true;
  if (results[1].status === 'fulfilled') books.value = results[1].value || [];
  else booksError.value = true;
  loading.value = false;
}

function confirm() {
  localStorage.setItem(key, 'yes');
  confirmed.value = true;
  load();
}

onMounted(() => {
  if (confirmed.value) load();
});
</script>

<style scoped>
.vip-page { padding-top: 0; }
.vip-channel-head { display:flex; align-items:center; justify-content:space-between; min-height:106px; margin:14px 0 10px; padding:18px; border-radius:var(--radius); background:var(--ink); color:#fffdf6; }
.vip-channel-head p,.vip-channel-head h1,.vip-channel-head span { margin:0; }
.vip-channel-head p { color:#a9d3ca; font-size:12px; font-weight:800; }
.vip-channel-head h1 { margin:5px 0; font-size:25px; line-height:1.18; }
.vip-channel-head span { color:rgba(255,253,246,.72); font-size:13px; }
.vip-channel-head > .van-icon { color:#d7c47a; font-size:28px; }
.vip-status-strip { display:grid; grid-template-columns:8px minmax(0,1fr); gap:10px; align-items:center; padding:12px 14px; border:1px solid rgba(31,111,100,.16); border-radius:var(--radius); background:#eef7f4; }
.vip-status-strip:has(.van-button) { grid-template-columns:8px minmax(0,1fr) auto; }
.vip-status-strip.inactive { border-color:rgba(31,37,40,.08); background:var(--panel); }
.vip-status-dot { width:8px; height:8px; border-radius:50%; background:var(--brand); }
.vip-status-strip.inactive .vip-status-dot { background:var(--muted); }
.vip-status-strip strong,.vip-status-strip small { display:block; }
.vip-status-strip strong { font-size:14px; }
.vip-status-strip small { margin-top:3px; color:var(--muted); font-size:12px; }
.vip-age-note { display:flex; gap:7px; align-items:center; margin-top:10px; color:var(--muted); font-size:12px; }
.vip-list-title { margin-top:18px; }
.vip-books .book-card:last-child { margin-bottom:0; }
.vip-feedback { display:grid; grid-template-columns:auto minmax(0,1fr) auto; gap:8px; align-items:center; margin-top:12px; padding:11px 12px; border-radius:var(--radius); font-size:13px; }
.vip-feedback--error { border:1px solid rgba(180,75,75,.16); background:#fff1f0; color:var(--danger); }
.vip-feedback button { padding:4px 0; border:0; background:transparent; color:inherit; font-weight:800; }
.vip-adult-gate { display:grid; grid-template-columns:40px minmax(0,1fr); gap:14px; margin-top:18px; padding:18px; border:1px solid rgba(31,37,40,.08); border-radius:var(--radius); background:var(--panel); }
.vip-gate-icon { display:flex; width:40px; height:40px; align-items:center; justify-content:center; border-radius:var(--radius); background:#e9f4f0; color:var(--brand); font-size:21px; }
.vip-gate-copy h1 { margin:0 0 6px; font-size:20px; }
.vip-gate-copy p { margin:0; color:var(--muted); font-size:13px; line-height:1.6; }
.vip-adult-gate .van-checkbox,.vip-gate-actions { grid-column:1/-1; }
.vip-gate-actions { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:10px; }
.vip-book-skeletons { display:grid; gap:12px; }
.vip-book-skeleton { display:grid; grid-template-columns:78px minmax(0,1fr); gap:13px; min-height:134px; padding:12px; border:1px solid rgba(31,37,40,.08); border-radius:var(--radius); background:var(--panel); }
.vip-book-skeleton :deep(.van-skeleton-image) { width:78px; height:110px; border-radius:6px; }
.vip-book-skeleton :deep(.van-skeleton) { padding:4px 0; }
@media (max-width:360px) { .vip-status-strip:has(.van-button) { grid-template-columns:8px minmax(0,1fr); } .vip-status-strip .van-button { grid-column:2; justify-self:start; } }
</style>
