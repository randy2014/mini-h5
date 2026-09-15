import axios from 'axios';
import { showToast } from 'vant';
import { ApiError, API_CODE } from '../permission/errors.js';
import {
  TOKEN_KEY,
  TOKEN_NAME_KEY,
  USER_ID_KEY,
  clearAuthenticationStorage
} from './authStorage.js';

const http = axios.create({
  baseURL: '/api',
  timeout: 10000
});

/**
 * 会话失效回调。由 permission/session.js 注入（清 Pinia store + 跳登录）。
 * 用回调而不是直接 import store，避免 http → store → service → http 的循环依赖。
 */
let onUnauthorized = null;

export function setUnauthorizedHandler(handler) {
  onUnauthorized = typeof handler === 'function' ? handler : null;
}

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  const tokenName = localStorage.getItem(TOKEN_NAME_KEY) || 'Authorization';
  const userId = localStorage.getItem(USER_ID_KEY);
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
    if (isEnvelope(payload)) {
      if (payload.code === API_CODE.OK) {
        return payload.data;
      }
      return fail({
        code: payload.code,
        httpStatus: response.status,
        message: payload.message || '请求失败',
        payload,
        config: response.config
      });
    }
    return payload;
  },
  (error) => {
    const data = error.response?.data;
    const envelope = isEnvelope(data);
    return fail({
      code: envelope ? data.code : null,
      httpStatus: error.response?.status ?? null,
      message: (envelope && data.message) || error.message || '网络异常',
      payload: envelope ? data : null,
      config: error.config
    });
  }
);

/**
 * 统一失败出口：会话失效处理 → 提示 → 抛出带业务 code 的 ApiError。
 * 调用方可传 { silent: true } 自行处理提示（例如门禁由页面渲染整页说明）。
 */
function fail({ code, httpStatus, message, payload, config }) {
  if (code === API_CODE.UNAUTHORIZED || httpStatus === 401) {
    clearAuthenticationStorage();
    onUnauthorized?.();
  }
  if (config?.silent !== true) {
    showToast(message);
  }
  return Promise.reject(new ApiError({ code, httpStatus, message, payload }));
}

function isEnvelope(payload) {
  return Boolean(payload) && typeof payload === 'object' && typeof payload.code === 'number';
}

export { ApiError, API_CODE };
export default http;
