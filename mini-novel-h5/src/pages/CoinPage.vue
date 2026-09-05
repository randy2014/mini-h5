<template>
  <section class="page coin-page">
    <van-nav-bar title="我的快乐币" left-arrow @click-left="$router.back()" />

    <div class="balance-card">
      <div class="bv">{{ balance }}</div>
      <div class="bl">🪙 当前快乐币余额</div>
    </div>

    <div class="hist">
      <div v-for="log in logs" :key="log.id" class="hrow">
        <div class="ht">
          {{ bizLabel(log) }}
          <small>{{ formatTime(log.createdAt) }}</small>
        </div>
        <div :class="['hv', log.changeAmount >= 0 ? 'plus' : 'minus']">
          {{ log.changeAmount >= 0 ? '+' : '' }}{{ log.changeAmount }}
        </div>
      </div>
      <div v-if="logs.length === 0" class="empty">暂无流水</div>
    </div>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { fetchBalance, fetchCoinLogs } from '../services/subscribe';

const balance = ref(0);
const logs = ref([]);

function bizLabel(log) {
  const map = {
    RECHARGE: '后台充值',
    SUBSCRIBE: '订阅扣费',
    REFUND: '冲正',
    GRANT: '赠送'
  };
  const base = map[log.bizType] || log.bizType;
  return log.remark ? `${base} · ${log.remark}` : base;
}

function formatTime(t) {
  if (!t) return '';
  return String(t).replace('T', ' ').slice(0, 16);
}

onMounted(async () => {
  const b = await fetchBalance();
  balance.value = b.balance;
  logs.value = await fetchCoinLogs();
});
</script>

<style scoped>
.coin-page { background: #f2f3f7; min-height: 100vh; }
.balance-card { margin: 14px; background: linear-gradient(135deg, #1f6f64, #2e8b7a); border-radius: 16px; padding: 22px; color: #fff; text-align: left; }
.bv { font-size: 36px; font-weight: 800; color: #f5c542; }
.bl { font-size: 11px; color: #cfe8e3; margin-top: 4px; }
.hist { margin: 0 14px; background: #fff; border-radius: 14px; overflow: hidden; }
.hrow { display: flex; align-items: center; padding: 12px 14px; border-bottom: 1px solid #f0f2f7; gap: 10px; }
.hrow:last-child { border-bottom: none; }
.ht { font-size: 12px; font-weight: 600; flex: 1; }
.ht small { display: block; font-size: 10px; color: #a6adb9; font-weight: 400; margin-top: 2px; }
.hv { font-size: 13px; font-weight: 800; }
.hv.plus { color: #2f7a4f; }
.hv.minus { color: #c0392b; }
.empty { text-align: left; padding: 20px 14px; color: #a6adb9; font-size: 13px; }
</style>
