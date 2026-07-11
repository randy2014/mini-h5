import axios from 'axios';
import { ElMessage } from 'element-plus';

function createClient(baseURL) {
  const client = axios.create({ baseURL, timeout: 15000 });
  client.interceptors.request.use((config) => {
    config.headers['X-Operator-Id'] = localStorage.getItem('mini_admin_operator_id') || '1';
    return config;
  });
  client.interceptors.response.use(
    (response) => {
      const payload = response.data;
      if (payload?.code === 0) {
        return payload.data;
      }
      throw new Error(payload?.message || '请求失败');
    },
    (error) => {
      const message = error.response?.data?.message || error.message || '网络异常';
      ElMessage.error(message);
      return Promise.reject(new Error(message));
    }
  );
  return client;
}

export const adminApi = createClient('/admin-api');
export const crawlerApi = createClient('/crawler-api');
