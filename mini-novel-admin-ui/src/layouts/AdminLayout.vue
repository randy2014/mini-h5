<template>
  <el-container class="admin-shell">
    <el-aside width="224px" class="admin-aside">
      <div class="brand">Mini Novel</div>
      <el-menu router :default-active="$route.path" background-color="#1f2937" text-color="#cbd5e1" active-text-color="#ffffff">
        <el-menu-item index="/admin/dashboard">首页</el-menu-item>
        <el-menu-item index="/admin/articles">文章管理</el-menu-item>
        <el-menu-item index="/admin/categories">分类管理</el-menu-item>
        <el-menu-item index="/admin/users">用户管理</el-menu-item>
        <el-menu-item index="/admin/vip">VIP 管理</el-menu-item>
        <el-menu-item index="/admin/orders">付费管理</el-menu-item>
        <el-menu-item index="/admin/crawler">采集管理</el-menu-item>
        <el-menu-item index="/admin/content-review">内容审核</el-menu-item>
        <el-menu-item index="/admin/authorized-books">授权书单</el-menu-item>
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
import { useRouter } from 'vue-router';

const router = useRouter();

function logout() {
  localStorage.removeItem('mini_admin_token');
  router.replace('/admin/login');
}
</script>
