import { createRouter, createWebHistory } from 'vue-router';
import { consumeInvitationQuery } from '../services/loginPreferences';

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
      // tab 页保留页内门禁（避免点 tab 被弹走）：书架 / 订阅 在页面里渲染登录引导
      { path: 'bookshelf', name: 'bookshelf', component: () => import('../pages/BookshelfPage.vue'), meta: { title: '书架', tab: true } },
      { path: 'profile', name: 'profile', component: () => import('../pages/ProfilePage.vue'), meta: { title: '我的', tab: true } },
      { path: 'search', name: 'search', component: () => import('../pages/SearchPage.vue'), meta: { title: '搜索' } },
      { path: 'rank/:type', name: 'rank', component: () => import('../pages/RankPage.vue'), meta: { title: '榜单' } },
      { path: 'book/:id', name: 'book-detail', component: () => import('../pages/BookDetailPage.vue'), meta: { title: '书籍详情' } },
      // 免费章节匿名可读，VIP 章节由后端 2001 + 页面提示处理，故不设整页门禁
      { path: 'read/:id', name: 'reader', component: () => import('../pages/ReaderPage.vue'), meta: { title: '阅读' } },
      { path: 'login', name: 'login', component: () => import('../pages/LoginPage.vue'), meta: { title: '登录' } },
      // VIP 专区有自己更细的三层门禁（登录 / VIP 资格 / 成人确认），故不设整页门禁
      { path: 'vip', name: 'vip', component: () => import('../pages/VipPage.vue'), meta: { title: 'VIP 专区', tab: true } },
      { path: 'subscribe', name: 'subscribe', component: () => import('../pages/SubscribePage.vue'), meta: { title: '订阅频道', tab: true } },
      { path: 'subscribe/history', name: 'subscribe-history', component: () => import('../pages/SubscribeHistoryPage.vue'), meta: { title: '阅读历史' } },
      { path: 'subscribe/:id', name: 'subscribe-channel', component: () => import('../pages/SubscribeChannelPage.vue'), meta: { title: '频道详情', require: ['AUTH'] } },
      { path: 'subscribe/:channelId/media/:postId', name: 'subscribe-media-post', component: () => import('../pages/SubscribeMediaPostPage.vue'), meta: { title: '图文/视频详情', require: ['AUTH'] } },
      { path: 'coin', name: 'coin', component: () => import('../pages/CoinPage.vue'), meta: { title: '我的快乐币', require: ['AUTH'] } },
      { path: 'ticket', name: 'ticket', component: () => import('../pages/TicketPage.vue'), meta: { title: '工单服务', require: ['AUTH'] } }
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
  const consumed = consumeInvitationQuery(to.query);
  if (consumed.found) {
    return { path: to.path, query: consumed.query, hash: to.hash, replace: true };
  }
  // 登录态要求由权限层守卫处理：见 src/permission/guard.js（meta.require: ['AUTH']）
});

export default router;
