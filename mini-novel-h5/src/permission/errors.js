/**
 * H5 权限层 · 错误归一化
 *
 * 后端契约（见 docs/h5-permission-matrix.md 第五章）：
 *   401  → HTTP 401  需登录
 *   2001 → HTTP 403  需 VIP        （唯一的专属权限码）
 *   403  → HTTP 403  需订阅 或 账号被禁用（⚠️ 同码，无法靠 code 区分）
 *   1000 → HTTP 200  业务失败（余额不足 / 验证码 / 参数）—— 不能按 HTTP 状态判
 *   无 code 字段 → 非业务异常 / 网关错误
 */
import { GATE } from './constants.js';

export const API_CODE = {
  OK: 0,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  BUSINESS_ERROR: 1000,
  VIP_REQUIRED: 2001
};

/**
 * 归一化后的请求错误。所有页面只依赖它，不直接读 axios 的 error.response。
 */
export class ApiError extends Error {
  constructor({ code = null, httpStatus = null, message = '请求失败', payload = null } = {}) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.httpStatus = httpStatus;
    this.payload = payload;
  }
}

export function isApiError(error) {
  return error instanceof ApiError;
}

/**
 * 把任意异常归类为门禁原因。
 *
 * @param {unknown} error
 * @param {{ scope?: 'CHANNEL'|'COIN'|'READER'|'ACCOUNT' }} [ctx]
 *        403 与 1000 在后端无法自证语义，需要调用方提供上下文（文档硬规则：按调用上下文区分，
 *        不靠匹配文案）。文案兜底仅作为过渡，C1/C2 落地后应删除。
 * @returns {{ gate: string, code: number|null, httpStatus: number|null, message: string, retryable: boolean }}
 */
export function classifyApiError(error, ctx = {}) {
  const code = toFiniteOrNull(error?.code);
  const httpStatus = toFiniteOrNull(error?.httpStatus ?? error?.response?.status);
  const message = String(error?.message || '请求失败');
  const scope = ctx?.scope || null;

  if (code === API_CODE.VIP_REQUIRED) {
    return build(GATE.NEED_VIP, { code, httpStatus, message });
  }
  if (code === API_CODE.UNAUTHORIZED || httpStatus === 401) {
    return build(GATE.NEED_LOGIN, { code, httpStatus, message });
  }
  if (code === API_CODE.NOT_FOUND || httpStatus === 404) {
    return build(GATE.NOT_FOUND, { code, httpStatus, message });
  }
  if (code === API_CODE.FORBIDDEN || httpStatus === 403) {
    if (DISABLED_PATTERN.test(message)) {
      return build(GATE.ACCOUNT_DISABLED, { code, httpStatus, message });
    }
    if (scope === 'CHANNEL') {
      return build(GATE.NEED_CHANNEL_SUBSCRIBE, { code, httpStatus, message });
    }
    return build(GATE.FORBIDDEN, { code, httpStatus, message });
  }
  if (code === API_CODE.BUSINESS_ERROR) {
    if (scope === 'COIN' || COIN_PATTERN.test(message)) {
      return build(GATE.NEED_COIN, { code, httpStatus, message });
    }
    return build(GATE.BUSINESS, { code, httpStatus, message });
  }
  return build(GATE.NETWORK_ERROR, { code, httpStatus, message, retryable: true });
}

/** 该错误是否属于「权限/门禁」类（用于决定是否走统一提示，而不是通用 toast） */
export function isGateError(result) {
  return Boolean(
    result &&
      [
        GATE.NEED_LOGIN,
        GATE.NEED_VIP,
        GATE.VIP_EXPIRED,
        GATE.NEED_CHANNEL_SUBSCRIBE,
        GATE.NEED_COIN,
        GATE.ACCOUNT_DISABLED
      ].includes(result.gate)
  );
}

// 过渡期文案兜底：仅用于给出更准确的提示，不作为判定依据。
// C1（新增「需订阅」业务码）/ C2（VIP 字段归一）落地后删除。
const DISABLED_PATTERN = /账号已被禁用/;
const COIN_PATTERN = /快乐币/;

function build(gate, { code, httpStatus, message, retryable = false }) {
  return { gate, code: code ?? null, httpStatus: httpStatus ?? null, message, retryable };
}

function toFiniteOrNull(value) {
  const num = Number(value);
  return Number.isFinite(num) ? num : null;
}
