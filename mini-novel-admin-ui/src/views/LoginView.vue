<template>
  <main class="login-screen">
    <section class="login-panel">
      <h1>后台管理</h1>
      <p>文章、分类、用户、VIP、付费与采集任务管理</p>
      <el-form @submit.prevent>
        <el-form-item>
          <el-input v-model="username" size="large" placeholder="管理员账号" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="password" size="large" type="password" show-password placeholder="密码" />
        </el-form-item>
        <el-button type="primary" size="large" class="full" @click="login">登录</el-button>
      </el-form>
    </section>
  </main>
</template>

<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';

const router = useRouter();
const username = ref('admin');
const password = ref('admin123');

function login() {
  if (!username.value || !password.value) {
    ElMessage.warning('请输入账号和密码');
    return;
  }
  localStorage.setItem('mini_admin_token', 'dev-admin-token');
  router.replace('/admin/dashboard');
}
</script>
