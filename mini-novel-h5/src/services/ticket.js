import http from './http';

export function fetchTickets() {
  return http.get('/ticket/list');
}

export function fetchTicketDetail(id) {
  return http.get(`/ticket/${id}`);
}

export function createTicket(title, content) {
  return http.post('/ticket', { title, content });
}

export function closeTicket(id) {
  return http.put(`/ticket/${id}/close`);
}
