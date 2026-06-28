import { createRouter, createWebHistory } from 'vue-router';

const routes = [
  { path: '/', redirect: '/admin/login' },
  { path: '/admin/login', component: () => import('../views/LoginView.vue'), meta: { title: '后台登录' } },
  {
    path: '/admin',
    component: () => import('../layouts/AdminLayout.vue'),
    redirect: '/admin/dashboard',
    children: [
      { path: 'dashboard', component: () => import('../views/DashboardView.vue'), meta: { title: '首页' } },
      { path: 'articles', component: () => import('../views/ArticleView.vue'), meta: { title: '文章管理' } },
      { path: 'categories', component: () => import('../views/CategoryView.vue'), meta: { title: '分类管理' } },
      { path: 'users', component: () => import('../views/UserView.vue'), meta: { title: '用户管理' } },
      { path: 'vip', component: () => import('../views/VipView.vue'), meta: { title: 'VIP 管理' } },
      { path: 'orders', component: () => import('../views/OrderView.vue'), meta: { title: '付费管理' } },
      { path: 'crawler', component: () => import('../views/CrawlerView.vue'), meta: { title: '采集任务' } }
    ]
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach((to) => {
  document.title = `${to.meta.title || '管理端'} - Mini Novel Admin`;
  if (to.path !== '/admin/login' && !localStorage.getItem('mini_admin_token')) {
    return '/admin/login';
  }
});

export default router;
