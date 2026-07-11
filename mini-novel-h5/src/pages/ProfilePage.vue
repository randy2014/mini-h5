<template>
  <section class="page with-tab profile-page">
    <van-nav-bar title="我的" />
    <div class="profile-card">
      <div>
        <p>{{ statusLabel }}</p>
        <h1>{{ profile?.nickname || '未登录' }}</h1>
        <span>{{ statusText }}</span>
      </div>
      <div class="profile-actions">
        <van-button v-if="isAuthenticated" round size="small" plain color="#fffdf6" @click="signOut">退出</van-button>
        <van-button v-else round size="small" color="#1f6f64" to="/h5/login">登录</van-button>
      </div>
    </div>

    <div class="setting-list">
      <van-cell title="VIP 会员" value="查看权益" is-link to="/h5/vip" />
      <van-cell v-if="!profile?.vipActive" title="升级 VIP" value="联系客服升级" />
      <van-cell v-if="profile?.vipActive" title="专属邀请码" :value="profile?.exclusiveInviteCode || '生成中'" is-link @click="copyInviteCode" />
      <van-cell v-if="profile?.vipActive" title="剩余名额" :value="String(profile?.inviteQuotaLeft ?? 0)" />
      <van-cell title="阅读历史" value="待接入" />
      <van-cell title="账号设置" value="待接入" />
    </div>

    <section class="soft-panel profile-reader-settings">
      <div class="section-title compact">
        <h2>阅读设置</h2>
        <span>{{ settings.fontSize }}px</span>
      </div>

      <div class="setting-row">
        <span>字号</span>
        <div class="setting-actions">
          <van-button size="small" plain @click="changeFont(-1)">A-</van-button>
          <van-button size="small" plain @click="changeFont(1)">A+</van-button>
        </div>
      </div>

      <div class="setting-row">
        <span>背景</span>
        <div class="theme-swatches">
          <button
            v-for="theme in themes"
            :key="theme.value"
            type="button"
            :class="{ active: settings.theme === theme.value }"
            :style="{ background: theme.color }"
            @click="setTheme(theme.value)"
          >
            {{ theme.label }}
          </button>
        </div>
      </div>
    </section>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, watch } from 'vue';
import { useRouter } from 'vue-router';
import { showToast } from 'vant';
import { useUserStore } from '../stores/user';

const router = useRouter();
const userStore = useUserStore();
const profile = computed(() => userStore.profile);
const isAuthenticated = computed(() => userStore.isAuthenticated);
const statusLabel = computed(() => {
  if (!isAuthenticated.value) return '未登录';
  return profile.value?.vipActive ? 'VIP 会员' : '普通用户';
});
const statusText = computed(() => {
  if (!isAuthenticated.value) return '登录后同步书架和阅读记录';
  return profile.value?.vipActive ? '会员权益已生效' : '可同步书架和阅读记录';
});
const settings = reactive(loadSettings());
const themes = [
  { value: 'paper', label: '纸', color: '#f7f0e4' },
  { value: 'green', label: '绿', color: '#e8f2ea' },
  { value: 'white', label: '白', color: '#f8faf7' },
  { value: 'night', label: '夜', color: '#1d2224' }
];

onMounted(() => {
  if (userStore.isAuthenticated) {
    userStore.loadProfile();
  }
});

watch(settings, saveSettings);

function changeFont(step) {
  settings.fontSize = Math.max(16, Math.min(24, settings.fontSize + step));
}

function setTheme(theme) {
  settings.theme = theme;
}

async function signOut() {
  await userStore.signOut();
  showToast('已退出登录');
  router.replace('/h5/home');
}

async function copyInviteCode() {
  if (!profile.value?.exclusiveInviteCode) {
    showToast('邀请码生成中');
    return;
  }
  try {
    await navigator.clipboard.writeText(profile.value.exclusiveInviteCode);
    showToast('邀请码已复制');
  } catch {
    showToast(profile.value.exclusiveInviteCode);
  }
}

function loadSettings() {
  try {
    return { fontSize: 20, theme: 'paper', ...JSON.parse(localStorage.getItem('mini_novel_reader_settings') || '{}') };
  } catch {
    return { fontSize: 20, theme: 'paper' };
  }
}

function saveSettings() {
  localStorage.setItem('mini_novel_reader_settings', JSON.stringify({
    fontSize: settings.fontSize,
    theme: settings.theme
  }));
}
</script>
