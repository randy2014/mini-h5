import http from './http';

export function fetchVipStatus() {
  return http.get('/vip/status');
}

export function fetchVipBooks(page = 1, pageSize = 20, category = 'all') {
  return http.get('/vip/books', { params: { page, pageSize, category } });
}

export function fetchVipCategories() {
  return http.get('/vip/categories');
}
