import http from './http';

// 订阅频道
export function fetchChannels() {
  return http.get('/subscribe/channels');
}

export function fetchChannelNovels(channelId, page = 1, pageSize = 20) {
  return http.get(`/subscribe/channels/${channelId}/novels`, { params: { page, pageSize } });
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
