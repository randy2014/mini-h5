import http from './http.js';

// 充值套餐 + 收款码 + 提示文案
export function fetchRechargePackages() {
  return http.get('/recharge/packages');
}

// 创建人工确认到账的充值订单
export function createRechargeOrder(payload) {
  return http.post('/recharge/orders', payload);
}

// 查询订单状态（前端轮询用）
export function fetchRechargeOrder(orderNo) {
  return http.get(`/recharge/orders/${orderNo}`);
}

// 我的充值记录
export function fetchMyRechargeOrders() {
  return http.get('/recharge/orders');
}
