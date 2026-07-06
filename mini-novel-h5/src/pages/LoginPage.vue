<template>
  <section class="page login-page">
    <van-nav-bar title="登录" left-arrow @click-left="goBack" />
    <div class="login-panel">
      <h1>欢迎回来</h1>
      <p>登录后同步书架、VIP 状态和阅读权益。</p>
      <van-field
        v-model="mobile"
        clearable
        maxlength="11"
        type="tel"
        label="手机号"
        placeholder="请输入 11 位手机号"
      />
      <van-button block round color="#1f6f64" :loading="loading" @click="submit">登录</van-button>
      <div class="demo-actions">
        <van-button plain size="small" :disabled="loading" @click="quickLogin('13800000001')">普通用户</van-button>
        <van-button plain size="small" :disabled="loading" @click="quickLogin('13800000002')">VIP 用户</van-button>
      </div>
    </div>
  </section>
</template>

<script setup>
import { ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showToast } from 'vant';
import { useUserStore } from '../stores/user';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const mobile = ref('13800000001');
const loading = ref(false);

async function submit() {
  const value = mobile.value.trim();
  if (!/^1\d{10}$/.test(value)) {
    showToast('请输入正确的手机号');
    return;
  }
  loading.value = true;
  try {
    await userStore.signIn(value);
    showToast('登录成功');
    router.replace(String(route.query.redirect || '/h5/profile'));
  } catch {
    // The HTTP layer already shows the error message.
  } finally {
    loading.value = false;
  }
}

function quickLogin(value) {
  mobile.value = value;
  submit();
}

function goBack() {
  if (window.history.length > 1) {
    router.back();
    return;
  }
  router.replace('/h5/profile');
}
</script>
