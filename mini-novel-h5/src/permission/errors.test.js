import test from 'node:test';
import assert from 'node:assert/strict';
import { GATE } from './constants.js';
import { ApiError, API_CODE, classifyApiError, isApiError } from './errors.js';

const err = (code, message, httpStatus) => new ApiError({ code, message, httpStatus });

test('2001 → 需 VIP（唯一的专属权限码，HTTP 403）', () => {
  const result = classifyApiError(err(API_CODE.VIP_REQUIRED, '该章节需要开通 VIP 后阅读', 403));
  assert.equal(result.gate, GATE.NEED_VIP);
  assert.equal(result.httpStatus, 403);
  assert.equal(result.retryable, false);
});

test('401 无论走 code 还是 HTTP 状态都判为需登录', () => {
  assert.equal(classifyApiError(err(API_CODE.UNAUTHORIZED, '请先登录', 401)).gate, GATE.NEED_LOGIN);
  assert.equal(classifyApiError(err(null, '请先登录', 401)).gate, GATE.NEED_LOGIN);
});

test('403 靠调用上下文区分「需订阅」与一般禁止', () => {
  assert.equal(classifyApiError(err(403, '订阅后查看', 403), { scope: 'CHANNEL' }).gate, GATE.NEED_CHANNEL_SUBSCRIBE);
  assert.equal(classifyApiError(err(403, '订阅后查看', 403)).gate, GATE.FORBIDDEN);
  assert.equal(
    classifyApiError(err(403, '账号已被禁用', 403), { scope: 'CHANNEL' }).gate,
    GATE.ACCOUNT_DISABLED,
    '禁用优先于订阅上下文'
  );
});

test('1000 是 HTTP 200 的业务失败，不能按 HTTP 状态判', () => {
  const result = classifyApiError(err(API_CODE.BUSINESS_ERROR, '快乐币余额不足', 200));
  assert.equal(result.gate, GATE.NEED_COIN);
  assert.equal(result.httpStatus, 200);
  assert.equal(classifyApiError(err(API_CODE.BUSINESS_ERROR, '参数校验失败', 200)).gate, GATE.BUSINESS);
  assert.equal(
    classifyApiError(err(API_CODE.BUSINESS_ERROR, '其它业务错误', 200), { scope: 'COIN' }).gate,
    GATE.NEED_COIN
  );
});

test('404 与网络异常分开', () => {
  assert.equal(classifyApiError(err(404, '章节不存在', 404)).gate, GATE.NOT_FOUND);
  assert.equal(classifyApiError(err(null, '章节不存在', 404)).gate, GATE.NOT_FOUND);

  const network = classifyApiError(new Error('Network Error'));
  assert.equal(network.gate, GATE.NETWORK_ERROR);
  assert.equal(network.retryable, true, '网络异常可重试');
  assert.equal(network.code, null);
});

test('ApiError 保留业务 code，message 仍可读', () => {
  const error = err(2001, '需要有效 VIP 资格', 403);
  assert.equal(isApiError(error), true);
  assert.equal(error instanceof Error, true);
  assert.equal(error.message, '需要有效 VIP 资格');
  assert.equal(error.code, 2001);
  assert.equal(isApiError(new Error('x')), false);
});
