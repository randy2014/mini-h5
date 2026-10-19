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

// 我的充值记录（分页）
export function fetchMyRechargeOrders(page = 1, pageSize = 10) {
  return http.get('/recharge/orders', { params: { page, pageSize } });
}

// 记录“我已付款”时间，便于后台核对、减少误取消
export function markOrderPaid(orderNo) {
  return http.post(`/recharge/orders/${orderNo}/paid-hint`);
}
