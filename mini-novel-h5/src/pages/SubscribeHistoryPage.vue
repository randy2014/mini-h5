<template>
  <section class="page subscribe-history-page">
    <van-nav-bar title="阅读历史" left-arrow @click-left="$router.back()" />

    <div class="list">
      <div v-for="h in history" :key="h.id" class="hist-book" @click="read(h)">
        <div class="hcover" :class="coverClass(h.novelId)">{{ h.novelId }}</div>
        <div class="info">
          <div class="ht">小说 #{{ h.novelId }}</div>
          <div class="hs">章节 #{{ h.chapterId }} · {{ formatTime(h.readAt) }}</div>
          <div class="prog">进度 {{ h.progress ?? 0 }}%</div>
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
import { fetchHistory } from '../services/subscribe';

const router = useRouter();
const history = ref([]);

function coverClass(id) {
  return ['g1', 'g2', 'g3', 'g4', 'g5', 'g6'][id % 6];
}

function formatTime(t) {
  if (!t) return '';
  return String(t).replace('T', ' ').slice(0, 16);
}

function read(h) {
  router.push(`/h5/read/${h.novelId}`);
}

onMounted(async () => {
  history.value = await fetchHistory();
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
