<template>
  <section class="page subscribe-history-page">
    <van-nav-bar title="阅读历史" left-arrow @click-left="$router.back()" />

    <div class="list">
      <div v-for="h in history" :key="h.novelId" class="hist-book" @click="read(h)">
        <div class="hcover" :class="coverClass(h.novelId)">{{ (h.title || '').slice(0, 4) }}</div>
        <div class="info">
          <div class="ht">{{ h.title || ('小说 #' + h.novelId) }}</div>
          <div class="hs">{{ h.author || '' }} · {{ formatTime(h.updatedAt) }}</div>
          <div v-if="h.chapterNo" class="prog">读到第 {{ h.chapterNo }} 章</div>
        </div>
        <div class="go">继续读 ›</div>
      </div>
      <div v-if="history.length === 0" class="empty">暂无阅读记录</div>
    </div>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { subscribeReadList } from '../services/subscribeReadStatus';

const router = useRouter();
const history = ref([]);

function coverClass(id) {
  return ['g1', 'g2', 'g3', 'g4', 'g5', 'g6'][id % 6];
}

function formatTime(t) {
  if (!t) return '';
  const d = new Date(Number(t));
  if (Number.isNaN(d.getTime())) return '';
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function read(h) {
  router.push(`/h5/read/${h.novelId}`);
}

onMounted(() => {
  history.value = subscribeReadList();
});
</script>

<style scoped>
.subscribe-history-page { background: #f2f3f7; min-height: 100vh; }
.list { padding: 14px; }
.hist-book { background: #fff; border-radius: 14px; padding: 12px; margin-bottom: 11px; display: flex; gap: 12px; align-items: center; }
.hcover { width: 44px; height: 58px; border-radius: 6px; flex-shrink: 0; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 10px; font-weight: 700; }
.info { flex: 1; min-width: 0; }
.ht { font-size: 13px; font-weight: 600; }
.hs { font-size: 10px; color: #a6adb9; margin-top: 3px; }
.prog { font-size: 10px; color: #d4a017; font-weight: 700; margin-top: 4px; }
.go { font-size: 11px; font-weight: 700; color: #1f6f64; }
.empty { text-align: left; color: #a6adb9; font-size: 13px; padding: 30px 0; }
.g1 { background: linear-gradient(135deg, #6a5acd, #8e7cc3); }
.g2 { background: linear-gradient(135deg, #2f80ed, #56a0f5); }
.g3 { background: linear-gradient(135deg, #e67e22, #f39c12); }
.g4 { background: linear-gradient(135deg, #16a085, #2ecc71); }
.g5 { background: linear-gradient(135deg, #c0392b, #e74c3c); }
.g6 { background: linear-gradient(135deg, #34495e, #5d6d7e); }
</style>
