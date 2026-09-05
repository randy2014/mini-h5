<template>
  <section class="page with-tab subscribe-page">
    <van-nav-bar title="订阅频道" />

    <div v-if="!isVip && loaded" class="no-perm">
      <div class="lock">🔒</div>
      <h3>暂无权限查看该频道内容</h3>
      <p>订阅频道为会员专享内容，<br />获得邀请码开通 VIP 后即可进入</p>
      <van-button round color="#1f6f64" to="/h5/vip">了解如何获得资格</van-button>
    </div>

    <template v-else-if="isVip">
      <div v-if="trialActive" class="trial-banner">VIP权益-试用期内 · 全频道开放(试用到期日：{{ trialEndDate }})</div>

      <div v-if="!trialActive && unsubscribedCount > 0" class="all-btn" @click="onSubscribeAll">
        <div>
          <div class="all-t">一键订阅全部</div>
          <div class="all-s">订阅 {{ unsubscribedCount }} 个未订阅分类</div>
        </div>
        <van-button round color="#e6b422" size="small" @click.stop="onSubscribeAll">一键订阅</van-button>
      </div>

      <div class="entry" @click="router.push('/h5/subscribe/history')">
        <span>🕘 阅读历史</span>
        <van-icon name="arrow" />
      </div>

      <div class="channel-list">
        <div v-for="c in channels" :key="c.id" class="channel-card" @click="goChannel(c)">
          <div class="cover" :class="coverClass(c.id)">{{ c.name.slice(0, 4) }}</div>
          <div class="info">
            <div class="nm">
              {{ c.name }}
              <span v-if="c.trial" class="tag trial">vip权益-试用中</span>
              <span v-else-if="c.subscribed" class="tag sub">已订阅</span>
              <span v-else class="tag locked">未订阅</span>
            </div>
            <div class="desc">{{ c.description || '暂无简介' }}</div>
            <div class="meta">{{ c.novelCount }} 本
              <template v-if="c.subscribed && c.endTime"> · 剩余 {{ c.daysLeft }} 天</template>
            </div>
            <div v-if="c.subscribed && c.endTime" class="countdown">{{ formatDate(c.endTime) }} 订阅到期</div>
          </div>
          <div v-if="!c.trial && !c.subscribed" class="price-box">
            <div class="price">300 <small>币/月</small></div>
            <van-button round color="#e6b422" size="mini" @click.stop="onSubscribe(c)">订阅</van-button>
          </div>
        </div>
        <div v-if="channels.length === 0" class="empty-list">暂无订阅频道</div>
      </div>
    </template>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { showToast } from 'vant';
import { useUserStore } from '../stores/user';
import { fetchChannels, subscribeAll, subscribeChannel } from '../services/subscribe';

const router = useRouter();
const userStore = useUserStore();
const channels = ref([]);
const loaded = ref(false);
const isVip = computed(() => userStore.isVip);

const trialActive = computed(() => channels.value.some((c) => c.trial));
const unsubscribedCount = computed(() => channels.value.filter((c) => !c.subscribed && !c.trial).length);
const trialEndDate = computed(() => {
  const c = channels.value.find((x) => x.trial && x.trialEndTime);
  return c ? formatDate(c.trialEndTime) : '';
});

function formatDate(t) {
  if (!t) return '';
  const s = String(t).replace('T', ' ').slice(0, 10);
  const parts = s.split('-');
  return parts.length === 3 ? `${parts[0]}年${parts[1]}月${parts[2]}日` : s;
}

function coverClass(id) {
  return ['g1', 'g2', 'g3', 'g4', 'g5', 'g6'][id % 6];
}

function goChannel(c) {
  router.push(`/h5/subscribe/${c.id}`);
}

async function onSubscribe(c) {
  try {
    await subscribeChannel(c.id, 'MONTH');
    showToast('订阅成功');
    await load();
  } catch {
    // toast handled by interceptor
  }
}

async function onSubscribeAll() {
  try {
    await subscribeAll('MONTH');
    showToast('一键订阅成功');
    await load();
  } catch {
    // toast handled by interceptor
  }
}

async function load() {
  try {
    channels.value = await fetchChannels();
  } finally {
    loaded.value = true;
  }
}

onMounted(async () => {
  if (userStore.isAuthenticated) {
    await userStore.loadProfile();
  }
  if (userStore.isVip) {
    await load();
  } else {
    loaded.value = true;
  }
});
</script>

<style scoped>
.subscribe-page { background: #f2f3f7; min-height: 100vh; }
.no-perm { padding: 80px 30px; text-align: left; display: flex; flex-direction: column; align-items: flex-start; gap: 12px; }
.no-perm .lock { font-size: 44px; }
.no-perm h3 { font-size: 17px; color: #2a2a34; }
.no-perm p { font-size: 13px; color: #8a92a3; line-height: 1.7; }
.trial-banner { margin: 12px 14px 0; background: #fdf3d3; color: #a97900; border-radius: 10px; padding: 10px 14px; font-size: 13px; font-weight: 600; }
.all-btn { margin: 12px 14px; background: linear-gradient(135deg, #1f6f64, #2e8b7a); color: #fff; border-radius: 12px; padding: 13px 14px; display: flex; align-items: center; justify-content: space-between; }
.all-t { font-size: 14px; font-weight: 700; }
.all-s { font-size: 11px; color: #cfe8e3; margin-top: 2px; }
.entry { margin: 0 14px 12px; background: #fff; border-radius: 12px; padding: 12px 14px; display: flex; align-items: center; justify-content: space-between; font-size: 13px; font-weight: 600; }
.channel-list { padding: 0 14px 20px; }
.channel-card { background: #fff; border-radius: 12px; padding: 12px; margin-bottom: 11px; display: flex; gap: 12px; }
.cover { width: 62px; height: 82px; border-radius: 8px; flex-shrink: 0; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; text-align: center; line-height: 1.3; }
.info { flex: 1; min-width: 0; }
.nm { font-size: 15px; font-weight: 700; display: flex; align-items: center; gap: 6px; }
.desc { font-size: 11px; color: #8a92a3; margin: 4px 0 6px; line-height: 1.5; }
.meta { font-size: 10px; color: #a6adb9; }
.countdown { font-size: 10px; color: #e07b39; font-weight: 700; margin-top: 4px; }
.tag { font-size: 10px; padding: 2px 7px; border-radius: 8px; font-weight: 600; }
.tag.trial { background: #fdf3d3; color: #a97900; }
.tag.sub { background: #e3f3ea; color: #2f7a4f; }
.tag.locked { background: #fdeaea; color: #c0392b; }
.price-box { display: flex; flex-direction: column; align-items: flex-start; justify-content: center; gap: 6px; }
.price { font-size: 13px; font-weight: 800; color: #d4a017; }
.price small { font-size: 10px; color: #8a92a3; font-weight: 400; }
.empty-list { text-align: left; color: #a6adb9; font-size: 13px; padding: 30px 0; }
.g1 { background: linear-gradient(135deg, #6a5acd, #8e7cc3); }
.g2 { background: linear-gradient(135deg, #2f80ed, #56a0f5); }
.g3 { background: linear-gradient(135deg, #e67e22, #f39c12); }
.g4 { background: linear-gradient(135deg, #16a085, #2ecc71); }
.g5 { background: linear-gradient(135deg, #c0392b, #e74c3c); }
.g6 { background: linear-gradient(135deg, #34495e, #5d6d7e); }
</style>
