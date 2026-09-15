import test from 'node:test';
import assert from 'node:assert/strict';

// 先装好内存版 localStorage，再导入 http（响应拦截器会在 401 时清凭证）
const store = new Map();
globalThis.localStorage = {
  getItem: (k) => (store.has(k) ? store.get(k) : null),
  setItem: (k, v) => store.set(k, String(v)),
  removeItem: (k) => store.delete(k),
  clear: () => store.clear()
};

const { default: http, setUnauthorizedHandler } = await import('./http.js');

/** 取响应拦截器的两个分支（axios 公开的 handlers 列表） */
function interceptors() {
  const handlers = http.interceptors.response.handlers;
  return { fulfilled: handlers[0].fulfilled, rejected: handlers[0].rejected };
}

const response = (data, status = 200, config = { silent: true }) => ({ data, status, config });
const failure = (response_, config = { silent: true }) => ({ response: response_, config });

test('成功信封解包 data', async () => {
  const { fulfilled } = interceptors();
  const result = await fulfilled(response({ code: 0, message: 'success', data: { id: 1 } }));
  assert.deepEqual(result, { id: 1 });
});

test('业务失败保留 code —— 403 需订阅与 2001 需 VIP 必须可区分', async () => {
  const { fulfilled } = interceptors();

  await assert.rejects(
    () => fulfilled(response({ code: 2001, message: '该章节需要开通 VIP 后阅读' }, 403)),
    (error) => error.code === 2001 && error.httpStatus === 403
  );
  await assert.rejects(
    () => fulfilled(response({ code: 403, message: '订阅后查看' }, 403)),
    (error) => error.code === 403 && error.message === '订阅后查看'
  );
});

test('code 1000（HTTP 200）按失败处理，不当成成功', async () => {
  const { fulfilled } = interceptors();
  await assert.rejects(
    () => fulfilled(response({ code: 1000, message: '快乐币余额不足' }, 200)),
    (error) => error.code === 1000 && error.httpStatus === 200
  );
});

test('HTTP 层失败也带上业务 code', async () => {
  const { rejected } = interceptors();
  await assert.rejects(
    () => rejected(failure({ status: 403, data: { code: 403, message: '订阅后查看' } })),
    (error) => error.code === 403
  );
});

test('非业务异常（网关/网络）没有 code，归为可重试', async () => {
  const { rejected } = interceptors();
  await assert.rejects(
    () => rejected({ message: 'Network Error', config: { silent: true } }),
    (error) => error.code === null && error.message === 'Network Error'
  );
});

test('401 清 localStorage 并触发会话失效回调', async () => {
  const { fulfilled } = interceptors();
  localStorage.setItem('mini_novel_auth_token', 't');
  localStorage.setItem('mini_novel_user_id', '7');
  localStorage.setItem('mini_novel_auth_token_name', 'Authorization');

  let notified = 0;
  setUnauthorizedHandler(() => {
    notified += 1;
  });

  await assert.rejects(() => fulfilled(response({ code: 401, message: '请先登录' }, 401)));
  assert.equal(notified, 1, '回调被调用一次');
  assert.equal(localStorage.getItem('mini_novel_auth_token'), null);
  assert.equal(localStorage.getItem('mini_novel_user_id'), null);
  assert.equal(localStorage.getItem('mini_novel_auth_token_name'), null);

  setUnauthorizedHandler(null);
});

test('HTTP 401 同样走会话失效分支', async () => {
  const { rejected } = interceptors();
  let notified = 0;
  setUnauthorizedHandler(() => {
    notified += 1;
  });
  await assert.rejects(() => rejected(failure({ status: 401, data: null })));
  assert.equal(notified, 1);
  setUnauthorizedHandler(null);
});

test('silent 请求不会因提示而抛错，且错误仍带 code', async () => {
  const { fulfilled } = interceptors();
  await assert.rejects(
    () => fulfilled(response({ code: 404, message: '章节不存在' }, 404, { silent: true })),
    (error) => error.code === 404
  );
});
