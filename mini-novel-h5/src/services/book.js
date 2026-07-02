import http from './http';

export function fetchHome() {
  return http.get('/home');
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
