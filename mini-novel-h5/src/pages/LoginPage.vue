<template>
  <section class="page login-page">
    <van-nav-bar title="登录" left-arrow @click-left="$router.back()" />
    <div class="login-panel">
      <h1>欢迎回来</h1>
      <p>当前是演示登录，手机号会映射到后端示例用户。</p>
      <van-field v-model="mobile" label="手机号" placeholder="13800000001 或 13800000002" />
      <van-button block round color="#1f6f64" @click="submit">登录</van-button>
      <div class="demo-actions">
        <van-button plain size="small" @click="quickLogin('13800000001')">普通用户</van-button>
        <van-button plain size="small" @click="quickLogin('13800000002')">VIP 用户</van-button>
      </div>
    </div>
  </section>
</template>

<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '../stores/user';

const router = useRouter();
const userStore = useUserStore();
const mobile = ref('13800000001');

async function submit() {
  await userStore.signIn(mobile.value);
  router.replace('/h5/profile');
}

function quickLogin(value) {
  mobile.value = value;
  submit();
}
</script>
