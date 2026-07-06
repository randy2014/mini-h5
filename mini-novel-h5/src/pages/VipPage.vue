<template>
  <section class="page vip-page">
    <van-nav-bar title="VIP 会员" left-arrow @click-left="$router.back()" />
    <div class="vip-banner">
      <p>会员权益</p>
      <h1>{{ status.active ? 'VIP 已生效' : '开通后畅读 VIP 章节' }}</h1>
      <span v-if="status.vipExpireTime">有效期至 {{ status.vipExpireTime }}</span>
    </div>

    <van-loading v-if="loading" class="center-loading" />
    <div v-else class="plan-list">
      <button v-for="plan in plans" :key="plan.id" type="button" class="plan-card">
        <strong>{{ plan.name }}</strong>
        <span>{{ plan.durationDays }} 天</span>
        <em>¥{{ plan.price }}</em>
      </button>
      <van-empty v-if="plans.length === 0" description="暂无套餐" />
    </div>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { fetchVipPlans, fetchVipStatus } from '../services/vip';
import { useUserStore } from '../stores/user';

const userStore = useUserStore();
const loading = ref(true);
const plans = ref([]);
const status = ref({ active: false });

onMounted(async () => {
  try {
    plans.value = await fetchVipPlans();
    if (userStore.isAuthenticated) {
      status.value = await fetchVipStatus();
    }
  } finally {
    loading.value = false;
  }
});
</script>
