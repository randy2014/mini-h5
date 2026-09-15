/**
 * H5 权限层 · 门禁提示（文案与引导落点的唯一出口）
 *
 * 硬规则（见文档第四章）：
 *  1. 权限提示必须带明确落点，不能只弹 toast
 *  2. 页面不得硬编码权限文案，也不得直接透出后端 message
 *  3. 同一门禁在同一会话内只弹一次（由调用方用 once() 控制）
 */
import { GATE, PROMPT_LEVEL } from './constants.js';
import { safeRedirect } from '../services/loginPreferences.js';

const LOGIN_PATH = '/h5/login';
const VIP_PATH = '/h5/vip';
const SUBSCRIBE_PATH = '/h5/subscribe';
const COIN_PATH = '/h5/coin';

/**
 * @param {string} gate GATE 之一
 * @param {{ redirect?: string, balance?: number, need?: number, channelName?: string, message?: string }} [ctx]
 * @returns {{ gate: string, level: string, title: string, text: string, action: { label: string, to: any }|null, toast: string }}
 */
export function promptFor(gate, ctx = {}) {
  switch (gate) {
    case GATE.NEED_LOGIN:
      return {
        gate,
        level: PROMPT_LEVEL.PAGE,
        title: '登录后可继续',
        text: '登录后可同步书架、阅读记录与订阅权益。',
        action: { label: '立即登录', to: loginTarget(ctx.redirect) },
        toast: '请先登录'
      };

    case GATE.NEED_VIP:
      return {
        gate,
        level: PROMPT_LEVEL.PAGE,
        title: '需要 VIP 资格',
        text: '该内容需要有效 VIP 资格，可用邀请码开通。',
        action: { label: '了解如何获得资格', to: VIP_PATH },
        toast: '该内容需要 VIP 资格'
      };

    case GATE.VIP_EXPIRED:
      return {
        gate,
        level: PROMPT_LEVEL.PAGE,
        title: 'VIP 已到期',
        text: 'VIP 已到期，续期后可继续阅读该内容。',
        action: { label: '去续期', to: VIP_PATH },
        toast: 'VIP 已到期'
      };

    case GATE.NEED_CHANNEL_SUBSCRIBE:
      return {
        gate,
        level: PROMPT_LEVEL.BLOCK,
        title: '订阅后可查看',
        text: ctx.channelName
          ? `订阅「${ctx.channelName}」后可查看本频道的图文与视频。`
          : '订阅该频道后可查看图文与视频内容。',
        action: { label: '订阅频道', to: SUBSCRIBE_PATH },
        toast: '订阅后查看'
      };

    case GATE.NEED_COIN: {
      const need = Number(ctx.need) || 0;
      const balance = Number(ctx.balance) || 0;
      return {
        gate,
        level: PROMPT_LEVEL.ACTION,
        title: '快乐币余额不足',
        text: need
          ? `当前余额 ${balance}，本次需要 ${need} 快乐币。`
          : '快乐币余额不足，无法完成订阅。',
        action: { label: '查看我的快乐币', to: COIN_PATH },
        toast: '快乐币余额不足'
      };
    }

    case GATE.ACCOUNT_DISABLED:
      return {
        gate,
        level: PROMPT_LEVEL.PAGE,
        title: '账号不可用',
        text: '账号已被禁用，请联系客服处理。',
        action: null,
        toast: '账号已被禁用'
      };

    case GATE.NOT_FOUND:
      return {
        gate,
        level: PROMPT_LEVEL.ACTION,
        title: '内容不存在',
        text: ctx.message || '内容不存在或已下架。',
        action: null,
        toast: ctx.message || '内容不存在或已下架'
      };

    case GATE.FORBIDDEN:
      return {
        gate,
        level: PROMPT_LEVEL.ACTION,
        title: '暂无权限',
        text: ctx.message || '当前账号无权访问该内容。',
        action: null,
        toast: ctx.message || '暂无权限'
      };

    case GATE.BUSINESS:
      return {
        gate,
        level: PROMPT_LEVEL.ACTION,
        title: '操作未完成',
        text: ctx.message || '操作未完成，请稍后再试。',
        action: null,
        toast: ctx.message || '操作未完成'
      };

    case GATE.NETWORK_ERROR:
    default:
      return {
        gate: GATE.NETWORK_ERROR,
        level: PROMPT_LEVEL.ACTION,
        title: '网络异常',
        text: ctx.message || '网络异常，请检查后重试。',
        action: null,
        toast: ctx.message || '网络异常，请稍后重试'
      };
  }
}

/** 一次会话内只提示一次，避免同一门禁反复打断 */
export function createPromptOnce() {
  const seen = new Set();
  return function once(key, run) {
    if (seen.has(key)) return false;
    seen.add(key);
    run();
    return true;
  };
}

function loginTarget(redirect) {
  const target = safeRedirect(redirect, '');
  return target ? { path: LOGIN_PATH, query: { redirect: target } } : LOGIN_PATH;
}
