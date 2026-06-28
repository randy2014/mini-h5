<template>
  <section class="page with-tab">
    <van-nav-bar title="我的" />
    <div class="profile-card">
      <div>
        <h1>{{ profile?.nickname || '未登录' }}</h1>
        <p>{{ profile?.vipActive ? 'VIP 会员' : '普通用户' }}</p>
      </div>
      <van-button round size="small" color="#2f6f73" to="/h5/login">切换登录</van-button>
    </div>

    <van-cell title="VIP 会员" is-link to="/h5/vip" />
    <van-cell title="阅读历史" value="待接入" />
    <van-cell title="账号设置" value="待接入" />
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
