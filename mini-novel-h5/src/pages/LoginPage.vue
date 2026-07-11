<template>
  <section class="page login-page">
    <van-nav-bar title="登录" left-arrow @click-left="goBack" />
    <div class="login-panel">
      <h1>欢迎回来</h1>
      <p>登录即注册，新账号未填邀请码将创建普通账号。</p>
      <van-field
        v-model="mobile"
        clearable
        maxlength="11"
        type="tel"
        label="手机号"
        placeholder="请输入 11 位手机号"
      />
      <van-field
        v-model="verifyCode"
        clearable
        maxlength="6"
        type="digit"
        label="验证码"
        placeholder="一期免校验，可留空"
      />
      <van-cell title="邀请码" :value="showInvite ? '收起' : inviteSummary" is-link @click="showInvite = !showInvite" />
      <van-field
        v-if="showInvite"
        v-model="invitationCode"
        clearable
        maxlength="32"
        label="邀请码"
        placeholder="新账号可填写 VIP 邀请码"
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
import { showConfirmDialog, showToast } from 'vant';
import { useUserStore } from '../stores/user';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const mobile = ref('13800000001');
const verifyCode = ref('');
const invitationCode = ref('');
const showInvite = ref(false);
const loading = ref(false);
const inviteSummary = '选填';

async function submit(confirmCreateNormal = false) {
  const value = mobile.value.trim();
  if (!/^1\d{10}$/.test(value)) {
    showToast('请输入正确的手机号');
    return;
  }
  loading.value = true;
  try {
    const data = await userStore.signIn({
      mobile: value,
      invitationCode: invitationCode.value.trim(),
      confirmCreateNormal
    });
    if (data.loginErrorCode === 'INVITE_ONLY_ON_CREATE') {
      showToast(data.message || '邀请码仅首次创建生效，请联系客服升级');
    }
    showToast('登录成功');
    router.replace(String(route.query.redirect || '/h5/profile'));
  } catch (error) {
    if (String(error.message || '').includes('新账号未填邀请码')) {
      confirmNormalAccount();
      return;
    }
    if (String(error.message || '').includes('邀请码不可用')) {
      showInvite.value = true;
    }
  } finally {
    loading.value = false;
  }
}

function quickLogin(value) {
  mobile.value = value;
  submit(true);
}

async function confirmNormalAccount() {
  try {
    await showConfirmDialog({
      title: '创建普通账号',
      message: '新账号未填写邀请码，将创建普通账号，后续只能联系客服或后台人工升级 VIP。',
      confirmButtonText: '创建普通账号',
      cancelButtonText: '返回填写邀请码'
    });
    submit(true);
  } catch {
    showInvite.value = true;
  }
}

function goBack() {
  if (window.history.length > 1) {
    router.back();
    return;
  }
  router.replace('/h5/profile');
}
</script>
