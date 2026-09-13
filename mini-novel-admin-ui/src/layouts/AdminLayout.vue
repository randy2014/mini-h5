<template>
  <el-container class="admin-shell">
    <el-aside width="224px" class="admin-aside">
      <div class="brand">Mini Novel</div>
      <el-menu router :default-active="activeMenu" background-color="#154f4b" text-color="#cfe0da" active-text-color="#ffffff">
        <el-menu-item index="/admin/dashboard">首页</el-menu-item>
        <el-menu-item index="/admin/articles">VIP文章管理</el-menu-item>
        <el-menu-item index="/admin/categories">分类管理</el-menu-item>
        <el-menu-item index="/admin/vip-categories">VIP 分类管理</el-menu-item>
        <el-menu-item index="/admin/media-pool">🖼 多媒体池子</el-menu-item>
        <el-menu-item index="/admin/subscribe-channels">订阅频道管理</el-menu-item>
        <el-menu-item index="/admin/coins">快乐币管理</el-menu-item>
        <el-menu-item index="/admin/ticket">工单管理</el-menu-item>
        <el-menu-item index="/admin/users">用户管理</el-menu-item>
        <el-menu-item index="/admin/crawler">采集管理</el-menu-item>
        <el-menu-item index="/admin/content-review">内容审核</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="admin-header">
        <strong>{{ $route.meta.title }}</strong>
        <el-button @click="logout">退出</el-button>
      </el-header>
      <el-main class="admin-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';

const route = useRoute();
const router = useRouter();

// 子页面（如 /admin/subscribe-channels/3）也高亮所属一级菜单
const activeMenu = computed(() => '/' + route.path.split('/').filter(Boolean).slice(0, 2).join('/'));

function logout() {
  localStorage.removeItem('mini_admin_token');
  router.replace('/admin/login');
}
</script>
