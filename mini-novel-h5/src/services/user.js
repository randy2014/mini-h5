import http from './http';

export function login(payload) {
  return http.post('/auth/login', payload);
}

export function fetchCaptcha() {
  return http.get('/auth/captcha');
}

export function logout() {
  return http.post('/auth/logout');
}

export function fetchProfile() {
  return http.get('/user/profile');
}

export function fetchBookshelf() {
  return http.get('/user/bookshelf');
}

export function addBookshelf(novelId) {
  return http.post(`/user/bookshelf/${novelId}`);
}

export function removeBookshelf(novelId) {
  return http.delete(`/user/bookshelf/${novelId}`);
}
