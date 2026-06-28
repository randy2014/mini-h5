import http from './http';

export function login(payload) {
  return http.post('/auth/login', payload);
}

export function fetchProfile() {
  return http.get('/user/profile');
}

export function fetchBookshelf() {
  return http.get('/user/bookshelf');
}
