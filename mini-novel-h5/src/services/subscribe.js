import http from './http';

// 订阅频道（沿用现有）
export function fetchChannels() {
  return http.get('/subscribe/channels');
}

export function fetchChannelNovels(channelId, page = 1, pageSize = 20) {
  return http.get(`/subscribe/channels/${channelId}/novels`, { params: { page, pageSize } });
}

export function fetchChannelFeed(channelId, page = 1, pageSize = 20) {
  return http.get(`/subscribe/channels/${channelId}/feed`, { params: { page, pageSize } });
}

export function fetchFeedPostDetail(channelId, postId) {
  return http.get(`/subscribe/channels/${channelId}/media/posts/${postId}`);
}

export function subscribeChannel(channelId, periodType = 'MONTH') {
  return http.post(`/subscribe/channels/${channelId}`, null, { params: { periodType } });
}

export function subscribeAll(periodType = 'MONTH') {
  return http.post('/subscribe/subscribe-all', null, { params: { periodType } });
}

export function fetchMySubscribes() {
  return http.get('/subscribe/my');
}

export function fetchHistory() {
  return http.get('/subscribe/history');
}

// 快乐币
export function fetchBalance() {
  return http.get('/coin/balance');
}

export function fetchCoinLogs() {
  return http.get('/coin/logs');
}

/**
 * 媒体字节 URL（<img>/<video> 同源直用；登录态由同名 Cookie 自动携带，
 * 后端按订阅判定 full 权限，见 AuthController login 种 cookie 的实现）。
 */
export function mediaFileUrl(channelId, assetId, kind) {
  return `/api/subscribe/channels/${channelId}/media/assets/${assetId}/file?kind=${kind}`;
}
