<template>
  <section class="page login-page">
    <van-nav-bar title="登录" left-arrow @click-left="goBack" />
    <div class="login-panel">
      <h1>欢迎回来</h1>
      <p>邀请码有效时自动开通 VIP，未填写或无效均可正常登录。</p>
      <van-field
        v-model="mobile"
        clearable
        maxlength="11"
        type="tel"
        name="mobile"
        autocomplete="username"
        label="手机号"
        placeholder="请输入 11 位手机号"
      />
      <van-field
        v-model="password"
        clearable
        type="password"
        name="password"
        autocomplete="current-password"
        maxlength="72"
        label="密码"
        placeholder="请输入密码（至少 8 位）"
      />
      <van-field
        v-model="verifyCode"
        clearable
        maxlength="4"
        name="captcha"
        autocomplete="off"
        label="验证码"
        placeholder="请输入图中字符"
      >
        <template #button>
          <button class="captcha-image-button" type="button" :disabled="captchaLoading" @click="loadCaptcha">
            <img v-if="captchaImage" :src="captchaImage" alt="图形验证码，点击刷新" />
            <span v-else>{{ captchaLoading ? '加载中' : '点击刷新' }}</span>
          </button>
        </template>
      </van-field>
      <van-field
        v-model="invitationCode"
        clearable
        maxlength="32"
        name="invitation-code"
        autocomplete="off"
        label="邀请码"
        placeholder="选填，有效邀请码自动开通 VIP"
      />
      <van-button block round color="#1f6f64" :loading="loading" @click="submit()">登录</van-button>
    </div>
  </section>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showToast } from 'vant';
import { useUserStore } from '../stores/user';
import { fetchCaptcha } from '../services/user';
import {
  clearSavedInvitation,
  readRememberedMobile,
  readSavedInvitation,
  saveInvitation,
  saveRememberedMobile
} from '../services/loginPreferences';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const mobile = ref(readRememberedMobile());
const password = ref('');
const verifyCode = ref('');
const captchaId = ref('');
const captchaImage = ref('');
const captchaLoading = ref(false);
const invitationCode = ref(readSavedInvitation());
const loading = ref(false);

async function submit() {
  const value = mobile.value.trim();
  if (!/^1\d{10}$/.test(value)) {
    showToast('请输入正确的手机号');
    return;
  }
  if (!captchaId.value || !/^[0-9A-Za-z]{4}$/.test(verifyCode.value.trim())) {
    showToast('请输入 4 位图形验证码');
    return;
  }
  if (password.value.length < 8 || password.value.length > 72) {
    showToast('请输入 8 至 72 位密码');
    return;
  }
  loading.value = true;
  try {
    const data = await userStore.signIn({
      mobile: value,
      password: password.value,
      captchaId: captchaId.value,
      captchaCode: verifyCode.value.trim(),
      invitationCode: invitationCode.value.trim()
    });
    saveRememberedMobile(value);
    if (invitationCode.value.trim()) {
      invitationCode.value = '';
      clearSavedInvitation();
    }
    if (data.loginErrorCode === 'INVITE_INVALID') {
      showToast(data.message || '邀请码无效，已按普通用户登录');
    } else {
      showToast('登录成功');
    }
    router.replace(String(route.query.redirect || '/h5/profile'));
  } catch (error) {
    await loadCaptcha();
  } finally {
    loading.value = false;
  }
}

async function loadCaptcha() {
  if (captchaLoading.value) return;
  captchaLoading.value = true;
  try {
    const data = await fetchCaptcha();
    captchaId.value = data.captchaId;
    captchaImage.value = data.image;
    verifyCode.value = '';
  } finally {
    captchaLoading.value = false;
  }
}

watch(invitationCode, (value) => saveInvitation(value));

onMounted(() => {
  const linkedInvitation = String(route.query.invite || '').trim();
  if (linkedInvitation) {
    invitationCode.value = linkedInvitation;
    saveInvitation(linkedInvitation);
  }
  loadCaptcha();
});

function goBack() {
  if (window.history.length > 1) {
    router.back();
    return;
  }
  router.replace('/h5/profile');
}
</script>
