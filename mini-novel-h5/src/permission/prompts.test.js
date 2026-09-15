import test from 'node:test';
import assert from 'node:assert/strict';
import { GATE, PROMPT_LEVEL } from './constants.js';
import { createPromptOnce, promptFor } from './prompts.js';

const ALL_GATES = Object.values(GATE);

test('每个门禁原因都有标题、文案与提示层级', () => {
  for (const gate of ALL_GATES) {
    const prompt = promptFor(gate);
    assert.equal(prompt.gate, gate);
    assert.ok(prompt.title && prompt.title.length > 0, `${gate} 缺标题`);
    assert.ok(prompt.text && prompt.text.length > 0, `${gate} 缺文案`);
    assert.ok(Object.values(PROMPT_LEVEL).includes(prompt.level), `${gate} 层级非法`);
    assert.ok(prompt.toast && prompt.toast.length > 0, `${gate} 缺 toast 兜底`);
  }
});

test('未知门禁回退为网络异常，不产生空白提示', () => {
  const prompt = promptFor('NOPE');
  assert.equal(prompt.gate, GATE.NETWORK_ERROR);
  assert.ok(prompt.text);
});

test('登录引导带着原始去向，且拒绝站外跳转', () => {
  const withRedirect = promptFor(GATE.NEED_LOGIN, { redirect: '/h5/coin' });
  assert.deepEqual(withRedirect.action.to, { path: '/h5/login', query: { redirect: '/h5/coin' } });

  const unsafe = promptFor(GATE.NEED_LOGIN, { redirect: 'https://evil.example.com' });
  assert.equal(unsafe.action.to, '/h5/login', '非法 redirect 退回纯登录页');

  const empty = promptFor(GATE.NEED_LOGIN);
  assert.equal(empty.action.to, '/h5/login');
});

test('每个门禁都给出可落地点的动作或明确无动作', () => {
  assert.equal(promptFor(GATE.NEED_VIP).action.to, '/h5/vip');
  assert.equal(promptFor(GATE.VIP_EXPIRED).action.to, '/h5/vip');
  assert.equal(promptFor(GATE.NEED_CHANNEL_SUBSCRIBE).action.to, '/h5/subscribe');
  assert.equal(promptFor(GATE.NEED_COIN).action.to, '/h5/coin');
  assert.equal(promptFor(GATE.ACCOUNT_DISABLED).action, null);
});

test('余额不足提示带上余额与所需数量', () => {
  const prompt = promptFor(GATE.NEED_COIN, { balance: 120, need: 300 });
  assert.match(prompt.text, /120/);
  assert.match(prompt.text, /300/);
});

test('同一门禁在同一会话内只提示一次', () => {
  const once = createPromptOnce();
  let calls = 0;
  const run = () => {
    calls += 1;
  };
  assert.equal(once(GATE.NEED_VIP, run), true);
  assert.equal(once(GATE.NEED_VIP, run), false);
  assert.equal(once(GATE.NEED_COIN, run), true, '不同门禁互不影响');
  assert.equal(calls, 2);
});
