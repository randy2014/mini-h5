import axios from 'axios';
import { ElMessage } from 'element-plus';

function createClient(baseURL, timeout = 15000) {
  const client = axios.create({ baseURL, timeout });
  client.interceptors.request.use((config) => {
    config.headers['X-Operator-Id'] = localStorage.getItem('mini_admin_operator_id') || '1';
    config.headers['X-Admin-Token'] = localStorage.getItem('mini_admin_token') || '';
    return config;
  });
  client.interceptors.response.use(
    (response) => {
      if (response.config.responseType === 'blob') {
        return response.data;
      }
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

/**
 * 大文件上传专用客户端（关闭客户端超时 timeout: 0）。
 *
 * 全局 15s 超时对普通接口合适，但视频 ≤300MB，仅传输就可能远超 15s：
 * 之前「添加视频」约 15 秒必失败（timeout of 15000ms exceeded）就是因为上传走了全局超时。
 * 上传时长改由服务端链路兜底：gateway / admin-ui nginx 各 600s，Spring multipart 300MB。
 */
export const adminUploadApi = createClient('/admin-api', 0);
