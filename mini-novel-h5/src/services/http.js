import axios from 'axios';
import { showToast } from 'vant';

const http = axios.create({
  baseURL: '/api',
  timeout: 10000
});

http.interceptors.request.use((config) => {
  const userId = localStorage.getItem('mini_novel_user_id') || '1';
  config.headers['X-User-Id'] = userId;
  return config;
});

http.interceptors.response.use(
  (response) => {
    const payload = response.data;
    if (payload?.code === 0) {
      return payload.data;
    }
    throw new Error(payload?.message || '请求失败');
  },
  (error) => {
    const message = error.response?.data?.message || error.message || '网络异常';
    showToast(message);
    return Promise.reject(new Error(message));
  }
);

export default http;
