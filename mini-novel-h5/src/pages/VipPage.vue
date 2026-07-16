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

      <div class="vip-category-row" role="tablist" aria-label="VIP 书籍分类">
        <button
          v-for="category in categories"
          :key="category.key"
          type="button"
          role="tab"
          :aria-selected="selectedCategory === category.key"
          :class="{ active: selectedCategory === category.key }"
          @click="selectCategory(category.key)"
        >
          <span>{{ category.categoryName }}</span>
          <small>{{ category.count }}</small>
        </button>
      </div>
      <div v-if="categoryError" class="vip-category-error">
        <span>分类加载失败，当前仍可浏览全部书籍</span>
        <button type="button" @click="loadCategories">重试</button>
      </div>

      <div v-if="statusError" class="vip-feedback vip-feedback--error">
        <van-icon name="warning-o" />
        <span>VIP 身份校验失败，请稍后重试或重新登录。</span>
        <button type="button" @click="loadStatus">重试</button>
      </div>

      <div class="section-title vip-list-title">
        <h2>{{ currentCategoryName === '全部' ? '精选书单' : currentCategoryName }}</h2>
        <span>{{ total ? `已加载 ${books.length} / ${total} 本` : '专属内容' }}</span>
      </div>

      <div v-if="listError && !books.length" class="vip-feedback vip-feedback--error">
        <van-icon name="warning-o" />
        <span>VIP 书单加载失败，请稍后再试。</span>
        <button type="button" @click="retryBooks">重新加载</button>
      </div>
      <van-list
        v-else
        v-model:loading="loading"
        v-model:error="listError"
        :finished="finished"
        error-text="加载失败，点击重试"
        :finished-text="books.length ? `已加载全部 ${total} 本` : ''"
        @load="loadNextPage"
      >
        <div v-if="loading && !books.length" class="vip-book-skeletons" aria-label="正在加载 VIP 书单">
          <div v-for="item in 3" :key="item" class="vip-book-skeleton">
            <van-skeleton-image />
            <van-skeleton :row="3" />
          </div>
        </div>
        <van-empty v-else-if="finished && !books.length" image="search" :description="`${currentCategoryName}暂无已审批上架的 VIP 书籍`">
          <van-button size="small" plain @click="retryBooks">刷新书单</van-button>
        </van-empty>
        <div v-else class="vip-books">
          <BookCard
            v-for="book in books"
            :key="book.novelId || book.id"
            :book="book"
            @open="$router.push(`/h5/book/${book.novelId || book.id}`)"
          />
        </div>
      </van-list>
    </template>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import BookCard from '../components/BookCard.vue';
import { fetchVipBooks, fetchVipCategories, fetchVipStatus } from '../services/vip';

const key = 'mini_novel_vip_adult_confirmed';
const adult = ref(false);
const confirmed = ref(localStorage.getItem(key) === 'yes');
const loading = ref(false);
const books = ref([]);
const page = ref(1);
const total = ref(0);
const pages = ref(0);
const finished = ref(false);
const listError = ref(false);
const requestInFlight = ref(false);
const categories = ref([{ key: 'all', categoryId: null, categoryName: '全部', count: 0 }]);
const selectedCategory = ref('all');
const categoryError = ref(false);
const status = ref({ active: false, vipExpireTime: null });
const statusError = ref(false);
const pageSize = 20;
let requestVersion = 0;

const currentCategoryName = computed(() => {
  return categories.value.find((category) => category.key === selectedCategory.value)?.categoryName || '全部';
});

const expiryLabel = computed(() => {
  if (!status.value.vipExpireTime) return '长期有效';
  const date = new Date(status.value.vipExpireTime);
  if (Number.isNaN(date.getTime())) return status.value.vipExpireTime;
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }).format(date);
});

async function loadStatus() {
  statusError.value = false;
  try {
    status.value = await fetchVipStatus();
  } catch {
    statusError.value = true;
  }
}

async function loadCategories() {
  categoryError.value = false;
  try {
    const result = await fetchVipCategories();
    categories.value = Array.isArray(result) && result.length
      ? result
      : [{ key: 'all', categoryId: null, categoryName: '全部', count: 0 }];
    if (!categories.value.some((category) => category.key === selectedCategory.value)) {
      selectCategory('all');
    }
  } catch {
    categoryError.value = true;
  }
}

async function loadNextPage() {
  if (finished.value || requestInFlight.value) {
    loading.value = false;
    return;
  }
  requestInFlight.value = true;
  listError.value = false;
  const version = requestVersion;
  const category = selectedCategory.value;
  try {
    const result = await fetchVipBooks(page.value, pageSize, category);
    if (version !== requestVersion || category !== selectedCategory.value) return;
    const incoming = result.records || [];
    const knownIds = new Set(books.value.map((book) => book.novelId || book.id));
    incoming.forEach((book) => {
      const novelId = book.novelId || book.id;
      if (!knownIds.has(novelId)) {
        knownIds.add(novelId);
        books.value.push(book);
      }
    });
    total.value = Number(result.total || 0);
    pages.value = Number(result.pages || 0);
    const currentPage = Number(result.page || page.value);
    finished.value = !result.hasMore || currentPage >= pages.value;
    if (!finished.value) page.value = currentPage + 1;
  } catch {
    if (version === requestVersion) listError.value = true;
  } finally {
    if (version === requestVersion) {
      requestInFlight.value = false;
      loading.value = false;
    }
  }
}

function resetBooks() {
  books.value = [];
  page.value = 1;
  total.value = 0;
  pages.value = 0;
  finished.value = false;
  listError.value = false;
}

function selectCategory(category) {
  if (category === selectedCategory.value) return;
  selectedCategory.value = category;
  requestVersion += 1;
  requestInFlight.value = false;
  resetBooks();
  loading.value = true;
  loadNextPage();
}

function retryBooks() {
  if (!books.value.length) {
    requestVersion += 1;
    requestInFlight.value = false;
    resetBooks();
  }
  listError.value = false;
  loading.value = true;
  loadNextPage();
}

function confirm() {
  localStorage.setItem(key, 'yes');
  confirmed.value = true;
  loadStatus();
  loadCategories();
}

onMounted(() => {
  if (confirmed.value) {
    loadStatus();
    loadCategories();
  }
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
.vip-category-row { display:flex; gap:8px; margin:15px -2px 0; padding:2px; overflow-x:auto; scrollbar-width:none; -webkit-overflow-scrolling:touch; }
.vip-category-row::-webkit-scrollbar { display:none; }
.vip-category-row button { display:inline-flex; flex:0 0 auto; gap:6px; align-items:center; min-height:34px; padding:7px 12px; border:1px solid rgba(31,37,40,.1); border-radius:999px; background:var(--panel); color:var(--muted); font-size:13px; white-space:nowrap; }
.vip-category-row button.active { border-color:var(--brand); background:#e9f4f0; color:var(--brand); font-weight:800; }
.vip-category-row small { min-width:17px; padding:1px 5px; border-radius:999px; background:rgba(31,37,40,.06); color:inherit; font-size:10px; line-height:1.5; text-align:center; }
.vip-category-row button.active small { background:rgba(31,111,100,.12); }
.vip-category-error { display:flex; justify-content:space-between; gap:10px; margin-top:8px; color:var(--danger); font-size:12px; }
.vip-category-error button { padding:0; border:0; background:transparent; color:inherit; font-weight:800; }
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
