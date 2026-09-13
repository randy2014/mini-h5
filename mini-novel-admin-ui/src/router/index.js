import { createRouter, createWebHistory } from 'vue-router';
import AdminLayout from '../layouts/AdminLayout.vue';
import CrawlerView from '../views/CrawlerView.vue';

const routes = [
  { path: '/', redirect: '/admin/login' },
  { path: '/admin/login', component: () => import('../views/LoginView.vue'), meta: { title: '后台登录' } },
  {
    path: '/admin',
    component: AdminLayout,
    redirect: '/admin/dashboard',
    children: [
      { path: 'dashboard', component: () => import('../views/DashboardView.vue'), meta: { title: '首页' } },
      { path: 'articles', component: () => import('../views/ArticleView.vue'), meta: { title: '文章管理' } },
      { path: 'categories', component: () => import('../views/CategoryView.vue'), meta: { title: '分类管理' } },
      { path: 'vip-categories', component: () => import('../views/VipCategoryView.vue'), meta: { title: 'VIP 分类管理' } },
      { path: 'users', component: () => import('../views/UserView.vue'), meta: { title: '用户管理' } },
      { path: 'crawler', component: CrawlerView, meta: { title: '采集管理' } },
      { path: 'content-review', component: () => import('../views/ContentReviewView.vue'), meta: { title: '内容审核' } },
      { path: 'subscribe-channels', component: () => import('../views/SubscribeChannelView.vue'), meta: { title: '订阅频道管理' } },
      { path: 'subscribe-channels/:id', component: () => import('../views/ChannelDetailView.vue'), meta: { title: '频道详情' } },
      { path: 'media-pool', component: () => import('../views/MediaPoolView.vue'), meta: { title: '多媒体池子' } },
      { path: 'coins', component: () => import('../views/CoinView.vue'), meta: { title: '快乐币管理' } },
      { path: 'ticket', component: () => import('../views/TicketView.vue'), meta: { title: '工单管理' } }
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
