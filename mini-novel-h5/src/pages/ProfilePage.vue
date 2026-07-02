<template>
  <section class="page with-tab profile-page">
    <van-nav-bar title="我的" />
    <div class="profile-card">
      <div>
        <p>{{ profile?.vipActive ? 'VIP 会员' : '普通用户' }}</p>
        <h1>{{ profile?.nickname || '未登录' }}</h1>
        <span>{{ profile?.vipActive ? '会员权益已生效' : '登录后同步书架和阅读记录' }}</span>
      </div>
      <van-button round size="small" color="#1f6f64" to="/h5/login">切换登录</van-button>
    </div>

    <div class="setting-list">
      <van-cell title="VIP 会员" value="查看权益" is-link to="/h5/vip" />
      <van-cell title="阅读历史" value="待接入" />
      <van-cell title="账号设置" value="待接入" />
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted } from 'vue';
import { useUserStore } from '../stores/user';

const userStore = useUserStore();
const profile = computed(() => userStore.profile);

onMounted(() => {
  userStore.loadProfile();
});
</script>
