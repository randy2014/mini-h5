import axios from 'axios';
import { showToast } from 'vant';

const http = axios.create({
  baseURL: '/api',
  timeout: 10000
});

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('mini_novel_auth_token');
  const tokenName = localStorage.getItem('mini_novel_auth_token_name') || 'Authorization';
  const userId = localStorage.getItem('mini_novel_user_id');
  if (token) {
    config.headers[tokenName] = token;
  }
  if (userId) {
    config.headers['X-User-Id'] = userId;
  }
  return config;
});

http.interceptors.response.use(
  (response) => {
    const payload = response.data;
    if (payload?.code === 0) {
      return payload.data;
    }
    const message = payload?.message || '请求失败';
    if (payload?.code === 401) {
      clearSession();
    }
    showToast(message);
    const error = new Error(message);
    error.code = payload?.code;
    error.payload = payload;
    throw error;
  },
  (error) => {
    const message = error.response?.data?.message || error.message || '网络异常';
    if (error.response?.status === 401) {
      clearSession();
    }
    showToast(message);
    return Promise.reject(new Error(message));
  }
);

function clearSession() {
  localStorage.removeItem('mini_novel_user_id');
  localStorage.removeItem('mini_novel_auth_token');
  localStorage.removeItem('mini_novel_auth_token_name');
}

export default http;
