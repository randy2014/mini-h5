import { createRouter, createWebHistory } from 'vue-router';

const routes = [
  {
    path: '/',
    redirect: '/h5/home'
  },
  {
    path: '/h5',
    component: () => import('../layouts/H5Layout.vue'),
    children: [
      { path: 'home', name: 'home', component: () => import('../pages/HomePage.vue'), meta: { title: '首页', tab: true } },
      { path: 'category', name: 'category', component: () => import('../pages/CategoryPage.vue'), meta: { title: '分类', tab: true } },
      { path: 'bookshelf', name: 'bookshelf', component: () => import('../pages/BookshelfPage.vue'), meta: { title: '书架', tab: true, auth: true } },
      { path: 'profile', name: 'profile', component: () => import('../pages/ProfilePage.vue'), meta: { title: '我的', tab: true } },
      { path: 'search', name: 'search', component: () => import('../pages/SearchPage.vue'), meta: { title: '搜索' } },
      { path: 'rank/:type', name: 'rank', component: () => import('../pages/RankPage.vue'), meta: { title: '榜单' } },
      { path: 'book/:id', name: 'book-detail', component: () => import('../pages/BookDetailPage.vue'), meta: { title: '书籍详情' } },
      { path: 'read/:id', name: 'reader', component: () => import('../pages/ReaderPage.vue'), meta: { title: '阅读' } },
      { path: 'login', name: 'login', component: () => import('../pages/LoginPage.vue'), meta: { title: '登录' } },
      { path: 'vip', name: 'vip', component: () => import('../pages/VipPage.vue'), meta: { title: 'VIP 专区', auth: true, tab: true } }
    ]
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition;
    }
    return { top: 0 };
  }
});

router.beforeEach((to) => {
  document.title = `${to.meta.title || 'Mini Novel'} - Mini Novel`;
  if (to.meta.auth && !localStorage.getItem('mini_novel_auth_token')) {
    return {
      path: '/h5/login',
      query: { redirect: to.fullPath }
    };
  }
});

export default router;
