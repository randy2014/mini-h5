/**
 * H5 权限层 · 常量
 *
 * 口径见 docs/h5-permission-matrix.md：
 *  - VIP 与订阅是两个正交权限域（章节只看 VIP，媒体只看订阅）
 *  - 状态唯一来源是 usePermissionStore + useUserStore，页面不得直接读 localStorage
 */

/** 能力域（页面只问能力，不问状态） */
export const CAPABILITY = {
  /** P0 公共阅读：首页 / 分类 / 榜单 / 搜索 / 免费书详情与章节 */
  PUBLIC_READ: 'PUBLIC_READ',
  /** P1 VIP 阅读：VIP 小说详情、目录、is_vip 章节正文、VIP 专区书单 */
  VIP_READ: 'VIP_READ',
  /** P2 频道媒体：频道 feed 内图文/视频、媒体帖详情、媒体字节 */
  CHANNEL_MEDIA: 'CHANNEL_MEDIA',
  /** P3 交易：订阅频道、一键订阅、续订 */
  TRADE: 'TRADE',
  /** P4 账户：书架、阅读历史、工单、快乐币、昵称 */
  ACCOUNT: 'ACCOUNT'
};

/** 门禁原因（提示文案与引导落点的唯一键，见 prompts.js） */
export const GATE = {
  NEED_LOGIN: 'NEED_LOGIN',
  NEED_VIP: 'NEED_VIP',
  VIP_EXPIRED: 'VIP_EXPIRED',
  NEED_CHANNEL_SUBSCRIBE: 'NEED_CHANNEL_SUBSCRIBE',
  NEED_COIN: 'NEED_COIN',
  ACCOUNT_DISABLED: 'ACCOUNT_DISABLED',
  FORBIDDEN: 'FORBIDDEN',
  BUSINESS: 'BUSINESS',
  NOT_FOUND: 'NOT_FOUND',
  NETWORK_ERROR: 'NETWORK_ERROR'
};

/** VIP 态细分（NONE 从未开通 / ACTIVE 有效 / EXPIRED 已过期） */
export const VIP_STATE = {
  NONE: 'NONE',
  ACTIVE: 'ACTIVE',
  EXPIRED: 'EXPIRED'
};

/** 提示层级（见文档第四章） */
export const PROMPT_LEVEL = {
  /** 整页替换 */
  PAGE: 'L0',
  /** 区块占位 */
  BLOCK: 'L1',
  /** 操作级 toast / 弹窗 */
  ACTION: 'L2',
  /** 按钮态 */
  BUTTON: 'L3',
  /** 静默降级（不渲染） */
  SILENT: 'L4'
};

/** 订阅周期对应的天数（与后端 daysFor 对齐，仅用于前端展示与预判） */
export const PERIOD_DAYS = {
  WEEK: 7,
  MONTH: 30,
  QUARTER: 90,
  YEAR: 365
};

/**
 * 订阅价格（快乐币）。
 * ⚠️ 后端价格目前只存在于 application.yml，未由接口下发（契约诉求 C5）。
 * 这里保留兜底值，一旦 /api/permission/snapshot 下发 prices 则以后端为准。
 */
export const FALLBACK_PRICES = {
  WEEK: 100,
  MONTH: 300,
  QUARTER: 800,
  YEAR: 3000
};

/** 新 VIP 试用期天数（后端 SubscribeServiceImpl.TRIAL_DAYS） */
export const TRIAL_DAYS = 7;

/** 成人内容确认的本机存储键（与历史实现保持一致） */
export const ADULT_CONFIRMED_KEY = 'mini_novel_vip_adult_confirmed';
