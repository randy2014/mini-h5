import http from './http';

export function fetchHome() {
  return http.get('/home');
}

export function fetchHomeSections() {
  return http.get('/home/sections');
}

export function searchNovels(keyword, limit = 50) {
  return http.get('/novels/search', { params: { keyword, limit } });
}

export function fetchRankNovels(type = 'HOT', limit = 50) {
  return http.get('/novels/rank', { params: { type, limit } });
}

export function fetchCategories() {
  return http.get('/categories');
}

export function fetchCategoryBooks(categoryId, limit = 50) {
  return http.get(`/categories/${categoryId}/novels`, { params: { limit } });
}

export function fetchBook(id) {
  return http.get(`/novels/${id}`);
}

export function fetchChapters(bookId, page = 1, size = 80) {
  return http.get(`/novels/${bookId}/chapters`, { params: { page, size } });
}

export function fetchChapter(chapterId) {
  return http.get(`/novels/chapters/${chapterId}`);
}

export function fetchNextChapter(chapterId) {
  return http.get(`/novels/chapters/${chapterId}/next`);
}
