import { createApp } from 'vue';
import { createPinia } from 'pinia';
import Vant from 'vant';
import 'vant/lib/index.css';
import App from './App.vue';
import router from './router';
import { installPermission } from './permission';
import './styles.css';

const app = createApp(App);

app.use(createPinia());
// 权限层要在 router 安装之前注册守卫，否则首次导航可能绕过拦截
installPermission(router);
app.use(router);
app.use(Vant);

app.mount('#app');
