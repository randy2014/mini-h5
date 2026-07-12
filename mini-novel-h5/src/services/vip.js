import http from './http';

export function fetchVipPlans() {
  return http.get('/vip/plans');
}

export function fetchVipStatus() {
  return http.get('/vip/status');
}

export function fetchVipBooks() {
  return http.get('/vip/books');
}
