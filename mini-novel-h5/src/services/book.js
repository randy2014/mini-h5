import http from './http';

export function fetchHome() {
  return http.get('/home');
}

export function fetchBook(id) {
  return http.get(`/novels/${id}`);
}

export function fetchChapters(bookId) {
  return http.get(`/novels/${bookId}/chapters`);
}

export function fetchChapter(chapterId) {
  return http.get(`/novels/chapters/${chapterId}`);
}
