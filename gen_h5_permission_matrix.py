# -*- coding: utf-8 -*-
"""Generate the H5 permission matrix workbook (state x feature) as a real .xlsx.

No third-party deps: raw OOXML in a zip, same convention as gen_product_checklist.py.

Output: docs/h5-permission-matrix.xlsx  (9 sheets, frozen header + autofilter)

NOTE
  The generated workbook is meant to be EDITED by hand (add columns, tick rows).
  Re-running this script OVERWRITES it. If you have hand edits you want to keep,
  copy the file first, or move your edit back into this script.
"""
import zipfile

OUT = r"F:\AI-PROD\mini-h5\docs\h5-permission-matrix.xlsx"

SHEETS = []  # (name, data, widths, center_cols)


def esc(s):
    return (str(s)
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace('"', "&quot;"))


def col_letter(n):
    s = ""
    while n > 0:
        n, r = divmod(n - 1, 26)
        s = chr(65 + r) + s
    return s


def cell(ref, value, style=None):
    s = f' s="{style}"' if style is not None else ""
    if isinstance(value, int):
        return f'<c r="{ref}"{s}><v>{value}</v></c>'
    if value is None:
        value = ""
    return (f'<c r="{ref}"{s} t="inlineStr"><is>'
            f'<t xml:space="preserve">{esc(value)}</t></is></c>')


def build_sheet(data, widths, center_cols=()):
    cols = ""
    if widths:
        cols = "<cols>" + "".join(
            f'<col min="{i + 1}" max="{i + 1}" width="{w}" customWidth="1"/>'
            for i, w in enumerate(widths)) + "</cols>"

    header = data[0]
    rows = ["<row>" + "".join(
        cell(f"{col_letter(j + 1)}1", header[j], style=1)
        for j in range(len(header))) + "</row>"]

    for i, r in enumerate(data[1:]):
        rn = i + 2
        rows.append("<row>" + "".join(
            cell(f"{col_letter(j + 1)}{rn}", r[j], style=3 if j in center_cols else 2)
            for j in range(len(r))) + "</row>")

    last_col = col_letter(len(header))
    last_row = len(data)

    return ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
            f'<dimension ref="A1:{last_col}{last_row}"/>'
            '<sheetViews><sheetView workbookViewId="0">'
            '<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>'
            '</sheetView></sheetViews>'
            '<sheetFormatPr defaultRowHeight="14"/>'
            + cols
            + "<sheetData>" + "".join(rows) + "</sheetData>"
            + f'<autoFilter ref="A1:{last_col}{last_row}"/>'
            + "</worksheet>")


# =====================================================================
# Sheet 1 - 待办任务（精简版，打开即见）
# =====================================================================
SHEETS.append(("待办任务", [
    ["阶段", "编号", "待办任务", "归属", "来源编号", "状态"],
    ["1 前端可开工", "1", "建前端权限层 src/permission/：状态唯一来源（登录/VIP/试用/订阅/余额）+ can() 能力判定",
     "前端", "A1 A2 A3", "已完成"],
    ["1 前端可开工", "2", "落路由守卫：meta.require 整页级拦截，不再靠页面自觉",
     "前端", "A4", "已完成"],
    ["1 前端可开工", "3", "修错误处理：http.js 保留业务 code；401 同步清 store 并跳登录",
     "前端", "A5 A6", "已完成"],
    ["1 前端可开工", "4", "统一提示出口：门禁原因 → 层级 + 文案 + 落点",
     "前端", "A7 A8 A9", "已完成"],
    ["1 前端可开工", "5", "修三处提示错乱：阅读器误报「需要 VIP」、订阅页匿名文案、快乐币/工单页无门禁",
     "前端", "B1 B2 B3", "已完成"],
    ["1 前端可开工", "6", "补 npm test 脚本（现有 15 个用例全通过，但没有 test 脚本）",
     "前端", "E1", "已完成"],
    ["2 产品拍板", "7", "订阅频道是否要求 VIP 资格（后端只校验登录+币，前端却整页 VIP 门禁）",
     "产品", "D1", "待决策"],
    ["2 产品拍板", "8", "未订阅频道「预览第 1 页小说」是否允许点开（当前看得见、点不开）",
     "产品", "D2", "待决策"],
    ["2 产品拍板", "9", "VIP 专区「仅前 3 页」是安全限制还是体验降级（当前前端截断，可绕过）",
     "产品", "D3", "待决策"],
    ["2 产品拍板", "10", "快乐币入口是否对非VIP 开放（订阅需要币，但入口只对 VIP 显示）",
     "产品", "D4", "待决策"],
    ["2 产品拍板", "11", "订阅阅读历史页是否要求登录（当前对匿名也开放）",
     "产品", "D5", "待决策"],
    ["3 后端契约", "12", "新增「需订阅」业务码 + VIP 字段归一（当前 403 与「账号被禁用」同码）",
     "后端", "C1 C2 C3", "待开始"],
    ["3 后端契约", "13", "新增能力聚合接口：一次返回登录/VIP/试用/订阅/余额/价格",
     "后端", "C4 C5", "待开始"],
    ["3 后端契约", "14", "补 read-history 与 subscribe/history 接口（前端已在调用，后端 404）",
     "后端", "C9", "待开始"],
    ["3 后端契约", "15", "统一未订阅可见范围：/novels 限第 1 页 vs /feed 不限；媒体缩略图策略；VIP 书目录 403 修正",
     "后端", "C6 C7 C8", "待开始"],
    ["3 后端契约", "16", "修三处一致性：试用期被后台重置、订阅到期两套时间源、搜索补受限标记",
     "后端", "C10 C11 C12", "待开始"],
    ["4 加固回归", "17", "安全收口：Admin/Crawler 补鉴权（P0，单独立项）、封面代理 SSRF 收敛、工单越权返回码",
     "后端", "F1 F4 F5", "待开始"],
    ["4 加固回归", "18", "测试与收尾：权限层单测 + 五状态验收清单；已读过滤迁账户级；清理 X-User-Id 死代码",
     "前端/后端", "E2 E3 E4 F3 A11 B7 B8", "待开始"],
], [14, 6, 78, 12, 22, 10], (1, 5)))

# =====================================================================
# Sheet 2 - 说明
# =====================================================================
SHEETS.append(("说明", [
    ["项", "内容"],
    ["文档名称", "H5 全局权限管理 · 状态 × 功能清单"],
    ["适用端", "mini-novel-h5（H5 阅读端）；后台 Admin 不适用"],
    ["用途", "H5 权限体系的设计输入与评审基线。矩阵写「目标规范」，当前实现的偏差集中在「现状差距」表"],
    ["口径基线", "main 分支前端代码 + mini-novel-api 全部 39 个 H5 端点的逐条守卫审计"],
    ["图例 ✅", "可用"],
    ["图例 🔒", "可见但被拦截（前端主动拦 + 引导，不消耗后端请求）"],
    ["图例 ⛔", "不可用（后端硬拒；前端应预拦，避免发注定失败的请求）"],
    ["图例 ⚠️", "当前实现与设计意图不一致，见「现状差距」表"],
    ["图例 —", "该状态下不涉及此功能"],
    ["权限分层", "前端负责「判定 + 拦截 + 提示」= 体验；后端负责逐条兜底 = 真正的安全边界。前端拦截不是安全措施"],
    ["硬规则 1", "禁止用异常反推权限（如 ReaderPage 把网络失败也判成「需要 VIP」）。必须先按业务 code 分类，再决定提示"],
    ["硬规则 2", "权限提示必须带明确落点（按钮 + 目标页面），只弹 toast 不给路 = 用户无路可走"],
    ["硬规则 3", "文案集中在 prompts 模块，不直接透出后端 message（code=403 同时表示「需订阅」和「账号被禁用」）"],
    ["硬规则 4", "同一门禁在同一会话内只弹一次，后续降级为按钮态或静默降级，避免反复打断"],
    ["硬规则 5", "可预判的不要等后端拒绝（Novel.vipRequired / Chapter.vip 自带标记，进页面前就能判定）"],
    ["维护规则", "判定真值只在「判定真值速查」表维护；文案只在「提示规范」表维护；其它表引用，不复制粘贴"],
    ["权威定义 · VIP", "VipAccessServiceImpl.hasActiveVip —— 仅依据 app_user.vip_expire_time > now()"],
    ["权威定义 · 订阅", "SubscribeServiceImpl.isAccessible / isTrialActive —— TRIAL_DAYS = 7"],
    ["关联文档", "docs/h5-permission-matrix.md（叙述版）· TASKS.md（订阅 M1）· docs/media-pool-design.md"],
], [22, 120]))

# =====================================================================
# Sheet 2 - 状态模型
# =====================================================================
SHEETS.append(("状态模型", [
    ["类型", "维度 / 字段", "取值", "权威判定依据", "前端可否可靠判定", "备注"],
    ["维度", "A 登录态", "A0 匿名 / A1 已登录", "Sa-Token token 有效（header Authorization 或同名 cookie）；后端 code=401",
     "✅", "过期 token 等价于未登录；后端无拦截器，39 个端点逐条 if"],
    ["维度", "B VIP 态", "B0 非VIP / B1 VIP有效", "app_user.vip_expire_time > now()（唯一依据）",
     "✅ /api/vip/status.active 或 profile.vipActive", "两处字段名不一致（active vs vipActive）"],
    ["维度", "C 试用态", "C0 非试用 / C1 试用中", "app_user.vip_activated_at + 7 天 > now()",
     "⚠️ 需前端自算（profile 返回 vipActivatedAt）", "管理员每次调整 VIP 都会刷新 vip_activated_at → 试用被重置"],
    ["维度", "D 频道订阅态", "D0 未订阅 / D1 已订阅有效", "user_subscribe：status=ACTIVE 且 end_time > now()",
     "⚠️ /subscribe/channels 的 subscribed 含试用污染；/subscribe/my 不含试用",
     "D 独立于 B：VIP 过期后订阅关系仍在（A1·B0·D1 是合法组合）"],
    ["维度", "E 快乐币余额", "E0 足够 / E1 不足", "/api/coin/balance 对比价格表 WEEK 100 / MONTH 300 / QUARTER 800 / YEAR 3000",
     "✅（价格未由接口下发，需前端常量）", "仅影响「订阅 / 一键订阅」动作"],
    ["维度", "F 成人内容确认", "F0 未确认 / F1 已确认", "本机 localStorage.mini_novel_vip_adult_confirmed",
     "✅", "仅影响 VIP 专区；换设备即失效"],
    ["禁用", "app_user.vip_status", "0 / 1 / 2", "不参与任何后端判定", "❌ 禁止使用",
     "VIP 自然过期无清理任务 → 长期失真，误用会显示错误会员状态"],
    ["禁用", "Chapter.readable", "恒为 null", "@TableField(exist=false) 且从未赋值", "❌ 死字段", "看似有「可读性标记」，实际无值"],
    ["禁用", "Chapter.reviewStatus", "恒为 null", "@TableField(exist=false) 且从未赋值", "❌ 死字段", "同上"],
    ["禁用", "Chapter.priceCoin", "VIP 章 = 10", "被写入但全仓无任何扣币/解锁逻辑", "❌ 禁止使用", "不存在「快乐币解锁单章」能力"],
    ["禁用", "Novel.freeChapterCount", "整数", "H5 读路径完全不使用", "❌ 禁止使用", "免费章节靠逐章 is_vip 标记实现"],
    ["禁用", "请求头 X-User-Id", "用户 ID", "后端刻意忽略（CurrentUserResolver.java:22）", "❌ 死代码", "前端 http.js:17 仍在发送"],
    ["禁用", "后端 message 字符串", "中文文案", "code=403 同时表示「需订阅」与「账号被禁用」", "❌ 禁止匹配字符串", "对文案改动极其脆弱"],
], [10, 24, 26, 46, 34, 52], (4,)))

# =====================================================================
# Sheet 3 - 组合态与能力域
# =====================================================================
SHEETS.append(("组合态与能力域", [
    ["类型", "编号 / 名称", "组合 / 判定式", "说明"],
    ["组合态", "S1", "A0", "匿名"],
    ["组合态", "S2", "A1 · B0", "已登录非 VIP（含 VIP 过期、被后台降级）"],
    ["组合态", "S2a", "A1 · B0 · vipExpireTime == null", "从未开通 VIP → 文案「用邀请码开通 VIP」"],
    ["组合态", "S2b", "A1 · B0 · vipExpireTime < now()", "VIP 已到期 → 文案「VIP 已到期，续期后继续」"],
    ["组合态", "S3", "A1 · B1 · C1", "VIP + 试用期内：7 天内全频道等价于「已订阅全部频道」"],
    ["组合态", "S4", "A1 · B1 · C0 · D0", "VIP + 试用结束 + 未订阅该频道"],
    ["组合态", "S5", "A1 · B1 · C0 · D1", "VIP + 试用结束 + 已订阅该频道"],
    ["组合态", "S2 · D1", "A1 · B0 · D1", "VIP 过期但频道订阅仍有效 → 媒体可看，频道内 vipRequired 小说一律不可读"],
    ["动作态", "E1", "币余额 < 所需", "仅影响「订阅 / 一键订阅」动作；后端返回 code 1000 + HTTP 200"],
    ["页面态", "F0", "未做 18 岁确认", "仅影响 VIP 专区"],
    ["能力域", "P0 公共阅读", "恒真", "首页、分类、榜单、搜索、免费书详情与章节"],
    ["能力域", "P1 VIP 阅读", "B1", "VIP 小说详情/目录、is_vip 章节正文、VIP 专区书单"],
    ["能力域", "P2 频道媒体", "C1 ∨ D1(该频道)", "频道 feed 内图文/视频、媒体帖详情、媒体字节（含缩略图）"],
    ["能力域", "P3 交易", "A1 ∧ E0", "订阅频道、一键订阅、续订"],
    ["能力域", "P4 账户", "A1", "书架、账户级阅读历史、工单、快乐币、昵称"],
], [10, 18, 34, 76]))

# =====================================================================
# Sheet 4 - 功能 × 状态矩阵（核心）
# =====================================================================
M = [
    ["页面", "功能项", "能力域", "S1 匿名", "S2 非VIP", "S3 VIP试用中", "S4 VIP未订阅", "S5 VIP已订阅",
     "门禁原因", "后端判定真值", "现状备注"],

    # --- 全局 ---
    ["全局", "底部 tab（首页/分类/订阅/书架/我的）", "—", "✅", "✅", "✅", "✅", "✅", "—", "—", "tab 常显，落地页各自拦截"],
    ["全局", "「我的」页登录入口", "—", "✅", "—", "—", "—", "—", "—", "—", ""],
    ["全局", "退出登录", "P4", "⛔", "✅", "✅", "✅", "✅", "NEED_LOGIN", "POST /auth/logout", "二次确认弹窗"],

    # --- 首页 ---
    ["首页", "今日推荐 / 继续阅读", "P0", "✅", "✅", "✅", "✅", "✅", "—", "GET /api/home/sections 匿名", ""],
    ["首页", "快捷入口（分类/书架/VIP/找书）", "P0", "✅", "✅", "✅", "✅", "✅", "—", "—", "入口常显"],
    ["首页", "四个榜单区块", "P0", "✅", "✅", "✅", "✅", "✅", "—", "GET /novels/rank 匿名", "受 SQL 层 23qb 来源过滤，前端无感"],

    # --- 分类 / 榜单 / 搜索 ---
    ["分类", "分类列表 / 分类书库", "P0", "✅", "✅", "✅", "✅", "✅", "—", "GET /categories 匿名", ""],
    ["榜单", "热榜 / 完结 / 更新 / 长篇", "P0", "✅", "✅", "✅", "✅", "✅", "—", "GET /novels/rank 匿名", ""],
    ["搜索", "搜索（结果随权限裁剪）", "P0", "✅", "✅", "✅", "✅", "✅", "—",
     "GET /novels/search 匿名；结果随 VIP/订阅变化",
     "⚠️ 响应无裁剪标记 → 用户误判「没有这本书」；S2 结果与 S1 完全相同"],

    # --- 书籍详情 ---
    ["书籍详情", "免费书详情页", "P0", "✅", "✅", "✅", "✅", "✅", "—", "GET /novels/{id} 无守卫", ""],
    ["书籍详情", "VIP 书详情页（vipRequired=true）", "P1", "⛔", "⛔", "✅", "✅", "✅", "NEED_VIP",
     "vipRequired && !vipActive → 403 / code 2001", "整页 L0 拦截"],
    ["书籍详情", "章节目录（VIP 书）", "P1", "⛔", "⛔", "✅", "✅", "✅", "NEED_VIP",
     "GET /novels/{id}/chapters 同门禁", "⚠️ 连「免费章节」的目录也拿不到（与正文可读自相矛盾）"],
    ["书籍详情", "开始阅读 / 继续阅读", "P1", "✅", "✅", "✅", "✅", "✅", "—", "按目标章节判定", ""],
    ["书籍详情", "加入书架", "P4", "🔒", "✅", "✅", "✅", "✅", "NEED_LOGIN", "POST /user/bookshelf/{id}", "跳登录并带 redirect"],
    ["书籍详情", "本页章节搜索 / 跳转 / 排序", "P0", "✅", "✅", "✅", "✅", "✅", "—", "前端本地", ""],

    # --- 阅读器 ---
    ["阅读器", "免费章节正文", "P0", "✅", "✅", "✅", "✅", "✅", "—", "GET /novels/chapters/{id} 无守卫", ""],
    ["阅读器", "is_vip 章节正文", "P1", "⛔", "⛔", "✅", "✅", "✅", "NEED_VIP",
     "chapter.vip && !vipActive → 403 / 2001", "弹窗「该章节需要开通 VIP 后阅读」+ 去开通 / 返回"],
    ["阅读器", "上一章 / 下一章", "P1", "⛔", "⛔", "✅", "✅", "✅", "NEED_VIP", "按目标章判定",
     "⚠️ VIP 边界处可能突然被拒；需按目标章预判"],
    ["阅读器", "目录抽屉", "P1", "✅", "✅", "✅", "✅", "✅", "—", "随 /chapters 门禁", ""],
    ["阅读器", "阅读设置（字号/主题）、滚动位置", "P0", "✅", "✅", "✅", "✅", "✅", "—", "本地 localStorage", ""],
    ["阅读器", "返回首页", "P0", "✅", "✅", "✅", "✅", "✅", "—", "—", ""],

    # --- 书架 ---
    ["书架", "书架列表 / 续读 / 移出", "P4", "🔒", "✅", "✅", "✅", "✅", "NEED_LOGIN",
     "GET /user/bookshelf 需登录", "已是全项目最正确的门禁范式，建议作为模板"],

    # --- 我的 ---
    ["我的", "资料卡 / 昵称修改", "P4", "🔒", "✅", "✅", "✅", "✅", "NEED_LOGIN", "GET /user/profile、PUT /user/nickname", ""],
    ["我的", "VIP 会员入口", "—", "✅", "✅", "✅", "✅", "✅", "—", "—", "入口常显，落地页拦截"],
    ["我的", "升级 VIP（联系客服）", "—", "—", "✅", "—", "—", "—", "NEED_VIP", "vipActive == false", ""],
    ["我的", "VIP 有效期显示", "P1", "—", "—", "✅", "✅", "✅", "—", "vipExpireTime（null = 永久）", ""],
    ["我的", "专属邀请码 / 剩余名额", "P1", "⛔", "⛔", "✅", "✅", "✅", "NEED_VIP", "仅 vipActive 返回邀请码字段", "复制到剪贴板"],
    ["我的", "我的快乐币入口", "P4", "—", "✅", "✅", "✅", "✅", "NEED_LOGIN", "GET /coin/balance 需登录",
     "⚠️ 建议放开给 S2（订阅需要币，当前仅 vipActive 可见）"],
    ["我的", "工单服务入口", "P4", "✅", "✅", "✅", "✅", "✅", "—", "入口匿名可见", "⚠️ 落地页需拦 S1"],
    ["我的", "阅读历史（账户级）", "P4", "⛔", "⛔", "⛔", "⛔", "⛔", "NEED_LOGIN",
     "前端已调用 /user/read-history，后端 404", "⚠️ 未落地"],
    ["我的", "账号设置", "—", "—", "—", "—", "—", "—", "—", "—", "待接入"],
    ["我的", "阅读设置（字号/主题）", "P0", "✅", "✅", "✅", "✅", "✅", "—", "本地 localStorage", ""],
    ["我的", "退出登录", "P4", "⛔", "✅", "✅", "✅", "✅", "NEED_LOGIN", "POST /auth/logout", ""],

    # --- VIP 专区 ---
    ["VIP 专区", "门禁 L1：登录", "—", "🔒", "—", "—", "—", "—", "NEED_LOGIN", "—", "整页「登录后进入 VIP 专区」"],
    ["VIP 专区", "门禁 L2：VIP 资格", "—", "—", "🔒", "—", "—", "—", "NEED_VIP / VIP_EXPIRED",
     "vipActive == false", "整页「需要 VIP 资格」；S2b 改「VIP 已到期」"],
    ["VIP 专区", "门禁 L3：18 岁确认", "—", "—", "—", "🔒", "🔒", "🔒", "ADULT_CONFIRM",
     "本机 localStorage", "勾选「我确认已满 18 周岁并自愿查看」"],
    ["VIP 专区", "VIP 书单 / 分类切换", "P1", "⛔", "⛔", "✅", "✅", "✅", "NEED_VIP",
     "GET /vip/books、/vip/categories 需登录 + VIP", ""],
    ["VIP 专区", "权益状态条 / 到期日", "P1", "—", "—", "✅", "✅", "✅", "—", "vipExpireTime", ""],
    ["VIP 专区", "已阅读过滤 / 一键清理", "P1", "—", "—", "✅", "✅", "✅", "—", "本地 localStorage", "确认弹窗"],
    ["VIP 专区", "「仅展示最新 3 页」截断", "P1", "—", "—", "—", "⚠️", "不截断", "—",
     "后端返回全量，截断只是前端不去请求", "⚠️ 可被绕过：后端无页码上限"],
    ["VIP 专区", "频道订阅引导条（去解锁全部）", "P3", "—", "—", "—", "✅", "—", "NEED_CHANNEL_SUBSCRIBE",
     "hasSubscription == false", "跳 /h5/subscribe"],

    # --- 订阅频道主页 ---
    ["订阅频道主页", "页面进入", "P3", "🔒", "🔒", "✅", "✅", "✅", "NEED_LOGIN / NEED_VIP",
     "GET /subscribe/channels 需登录", "⚠️ S1 与 S2 文案必须分开（当前共用，匿名被引导去开通 VIP）"],
    ["订阅频道主页", "试用横幅（全频道开放 + 到期日）", "P2", "—", "—", "✅", "—", "—", "—", "trial / trialEndTime", ""],
    ["订阅频道主页", "一键订阅全部未订阅分类", "P3", "—", "—", "隐藏", "✅ (E0)", "✅ (E0)", "NEED_COIN",
     "POST /subscribe/subscribe-all 需登录 + 币", "⚠️ E1 → code 1000「快乐币余额不足，无法一键订阅」（HTTP 200）"],
    ["订阅频道主页", "阅读历史入口", "P2", "—", "—", "✅", "✅", "✅", "—", "本地 localStorage", ""],
    ["订阅频道主页", "频道列表（已订阅/未订阅/试用中）", "P2", "—", "—", "✅", "✅", "✅", "—",
     "subscribed（含试用）· trial · endTime · daysLeft", "⚠️ subscribed 与 /subscribe/my 对同一用户答案不同"],
    ["订阅频道主页", "单频道订阅（300 币 / 月）", "P3", "—", "🔒", "隐藏", "✅ (E0)", "✅ (E0)", "NEED_COIN",
     "POST /subscribe/channels/{id} 需登录 + 币", "⚠️ 后端不校验 VIP，前端整页门禁 → 口径分歧，需拍板"],
    ["订阅频道主页", "订阅到期倒计时（≤3 天）", "P2", "—", "—", "—", "✅", "✅", "—", "endTime 前端自算", ""],

    # --- 频道详情 ---
    ["频道详情", "页面进入", "P2", "🔒", "🔒", "✅", "✅", "✅", "NEED_LOGIN", "GET /channels/{id}/feed 需登录", ""],
    ["频道详情", "频道头 / 网格·列表切换", "P0", "—", "—", "✅", "✅", "✅", "—", "—", ""],
    ["频道详情", "小说项（点击进阅读器）", "P0", "—", "—", "✅", "⚠️", "✅", "NEED_CHANNEL_SUBSCRIBE",
     "/feed 未订阅仍返回小说", "⚠️ 当前看得见点不开 → 与「预览第 1 页」语义冲突，需拍板"],
    ["频道详情", "媒体项（图文 / 视频卡片）", "P2", "—", "—", "✅", "⛔", "✅", "NEED_CHANNEL_SUBSCRIBE",
     "未订阅时后端不返回媒体项", "L4 静默降级，区块整体不渲染"],
    ["频道详情", "未订阅提示条 + 底部订阅条", "P2", "—", "—", "—", "✅", "—", "NEED_CHANNEL_SUBSCRIBE",
     "restricted=true", ""],
    ["频道详情", "已读过滤（点开即已读，从列表移除）", "P2", "—", "—", "✅", "✅", "✅", "—",
     "本地 localStorage，仅作用于小说", ""],
    ["频道详情", "加载更多", "P2", "—", "—", "✅", "⛔", "✅", "NEED_CHANNEL_SUBSCRIBE", "hasMore", ""],

    # --- 媒体详情 ---
    ["媒体详情", "进入媒体帖详情", "P2", "🔒", "⛔", "✅", "⛔", "✅", "NEED_CHANNEL_SUBSCRIBE",
     "isAccessible → 否则 403「订阅后查看」", "⚠️ 403 与「账号已被禁用」同码，只能靠上下文区分"],
    ["媒体详情", "图片画廊 / 全屏预览", "P2", "—", "—", "✅", "—", "✅", "NEED_CHANNEL_SUBSCRIBE",
     "字节端点同门禁（缩略图也 403）", ""],
    ["媒体详情", "视频播放 / 封面帧", "P2", "—", "—", "✅", "—", "✅", "NEED_CHANNEL_SUBSCRIBE", "同上", ""],
    ["媒体详情", "禁下载（右键/长按/拖拽/画中画）", "P2", "—", "—", "✅", "—", "✅", "—", "前端 UI 层", ""],

    # --- 订阅阅读历史 / 快乐币 / 工单 ---
    ["订阅阅读历史", "列表 / 继续读 / 一键清理", "P4", "✅", "✅", "✅", "✅", "✅", "—",
     "纯本地 localStorage", "⚠️ 当前对匿名也开放；账户级历史后端未落地"],
    ["快乐币", "余额 / 流水", "P4", "🔒", "✅", "✅", "✅", "✅", "NEED_LOGIN", "GET /coin/* 需登录",
     "⚠️ 当前无守卫 → 匿名直达表现为空壳 + 401 toast"],
    ["工单", "列表 / 新增 / 详情 / 关闭", "P4", "🔒", "✅", "✅", "✅", "✅", "NEED_LOGIN", "GET/POST /ticket/* 需登录",
     "⚠️ 当前无守卫"],
    ["工单", "查看他人工单", "P4", "—", "⛔", "⛔", "⛔", "⛔", "—",
     "返回 code 1000「工单不存在」", "⚠️ 不区分「不存在」与「非本人」，语义混淆"],

    # --- 登录 ---
    ["登录", "访问页面 + 提交登录", "—", "✅", "✅", "✅", "✅", "✅", "—", "POST /auth/login 匿名（需图形验证码）", ""],
    ["登录", "邀请码（选填）", "—", "✅", "✅", "✅", "✅", "✅", "—", "有效 → 自动开通永久 VIP（2099-12-31）", ""],
    ["登录", "登录成功但邀请码无效", "—", "✅", "✅", "✅", "✅", "✅", "—",
     "HTTP 200 / code 0 + loginErrorCode=INVITE_INVALID", "⚠️ 必须当成功处理并 toast「邀请码无效，已按普通用户登录」"],
    ["登录", "登录后回跳 redirect", "—", "✅", "✅", "✅", "✅", "✅", "—", "safeRedirect() 白名单校验", ""],
]
SHEETS.append(("功能×状态矩阵", M,
               [14, 34, 10, 9, 10, 12, 12, 12, 22, 42, 52],
               (3, 4, 5, 6, 7)))

# =====================================================================
# Sheet 5 - 提示规范
# =====================================================================
SHEETS.append(("提示规范", [
    ["门禁原因", "触发条件", "提示层级", "文案", "主按钮 / 引导落点"],
    ["NEED_LOGIN", "A0 且能力需登录", "L0 整页替换",
     "「登录后同步你的书架和阅读记录」/「登录后可继续」", "立即登录 → /h5/login?redirect=<当前全路径>"],
    ["NEED_VIP（从未开通）", "B0 且 vipExpireTime == null", "L0 / L1",
     "「该内容需要 VIP 资格，可用邀请码开通」", "使用邀请码 → /h5/vip（登录页邀请码入口）"],
    ["VIP_EXPIRED", "B0 且 vipExpireTime < now()", "L0",
     "「VIP 已到期，续期后可继续阅读」", "去续期 / 联系客服"],
    ["NEED_CHANNEL_SUBSCRIBE（媒体）", "P2 不成立（媒体帖、媒体字节）", "L1 区块占位 / L4 静默降级",
     "「订阅后可查看本频道的图文与视频」", "订阅本频道（300 币 / 月）"],
    ["NEED_CHANNEL_SUBSCRIBE（小说预览）", "P2 不成立且走小说预览（S4）", "L2 操作提示 / L3 按钮态",
     "「订阅后查看该频道全部小说」", "同上"],
    ["NEED_COIN", "E1 且动作需扣币", "L2 toast + 引导",
     "「快乐币余额不足」+ 当前余额 / 所需数量", "去快乐币页（或联系客服充值）"],
    ["ADULT_CONFIRM", "F0 且进入 VIP 专区", "L0（页内门禁）",
     "「本频道含成人主题内容，仅面向已满 18 周岁用户」", "勾选确认 → 进入"],
    ["ACCOUNT_DISABLED", "后端 403「账号已被禁用」", "L0",
     "「账号已被禁用，请联系客服」", "联系客服"],
    ["NETWORK_ERROR", "非权限类异常（超时 / 5xx / 无 code 字段）", "L2 toast",
     "「网络异常，请重试」", "重试按钮"],
    ["LAYER L0", "整页替换：页面主体换成门禁卡", "适用：整页都不可用",
     "现有实现：VipPage.vip-access-denied、BookshelfPage.no-login", ""],
    ["LAYER L1", "区块占位：页面内某区块换成锁定卡", "适用：页面部分可用",
     "现有实现：SubscribeChannelPage.footbar 订阅条", ""],
    ["LAYER L2", "操作级提示：toast / 确认弹窗", "适用：可见但不可点",
     "现有实现：ReaderPage 的「需要 VIP」弹窗", ""],
    ["LAYER L3", "按钮态：置灰 / 加锁图标 / 禁用态文案", "适用：入口保留但不可用",
     "现有实现：VipPage.subscribe-prompt", ""],
    ["LAYER L4", "静默降级：该内容不渲染", "适用：不需要解释的裁剪",
     "现有实现：未订阅时媒体项不出现", ""],
], [30, 40, 24, 56, 48]))

# =====================================================================
# Sheet 6 - 错误码映射
# =====================================================================
SHEETS.append(("错误码映射", [
    ["后端 code", "HTTP 状态", "语义", "前端动作", "备注"],
    ["401", "401", "需登录", "清会话（含 Pinia store）→ 跳登录（带 redirect）", "HTTP 与 code 恰好一致，可直接用任一判断"],
    ["2001", "403", "需 VIP", "走 NEED_VIP / VIP_EXPIRED 引导，不弹通用错误", "唯一有专属业务码的权限失败"],
    ["403", "403", "需订阅该频道 或 账号被禁用", "按调用上下文区分；建议后端拆码", "⚠️ 二者同码同状态，无法靠 code 区分"],
    ["1000", "200", "业务失败（余额不足 / 验证码 / 参数 / 限流）", "必须按 code 判，不能按 HTTP 状态判", "⚠️ HTTP 200！按状态判会把失败当成功"],
    ["404", "404", "资源不存在", "常规空态", ""],
    ["（无 code 字段）", "任意", "非业务异常 / 网关错误", "归为 NETWORK_ERROR", "GlobalExceptionHandler 不捕获非 BusinessException"],
    ["现状缺陷 1", "—", "http.js 错误分支丢弃业务 code", "归一化为 { code, httpStatus, message }",
     "⚠️ 导致 403-需订阅 与 403-需VIP(2001) 无法区分"],
    ["现状缺陷 2", "—", "http.js 401 只清 localStorage，不清 store", "同步清 Pinia 并跳登录",
     "⚠️ 出现「store 认为已登录、页面拿不到数据」的错乱态"],
], [16, 12, 40, 46, 60]))

# =====================================================================
# Sheet 7 - 现状差距
# =====================================================================
SHEETS.append(("现状差距", [
    ["编号", "严重度", "问题", "位置", "影响", "建议"],
    ["6.1", "🔴 严重", "路由守卫失效：meta.auth 在所有路由上都未声明", "src/router/index.js:50", "无第一道防线，全靠页面自觉", "在 meta 声明最低要求，守卫做整页级拦截"],
    ["6.2", "🔴 严重", "误把「订阅」当「小说权限」", "全局认知", "若按「已订阅 ⇒ 全部可读」渲染，会大面积 403 / 2001", "能力域分离：P1 只看 VIP，P2 只看订阅"],
    ["6.3", "🔴 严重", "SubscribePage 让匿名与非VIP 共用一套文案", "src/pages/SubscribePage.vue:5-10",
     "匿名用户被引导去「用邀请码开通 VIP」，正确动作是先登录", "按 NEED_LOGIN / NEED_VIP 分两套文案"],
    ["6.4", "🔴 严重", "http.js 丢失业务 code；401 不清 store", "src/services/http.js:29-31, 38-45",
     "权限错误无法分类处理；登录态错乱", "统一错误对象 + errors 映射"],
    ["6.5", "🟠 高", "ReaderPage 任何加载错误都弹「需要 VIP」", "src/pages/ReaderPage.vue:137-143",
     "网络故障被误报成权限问题", "先按 code 分类，再决定提示"],
    ["6.6", "🟠 高", "VipPage 直接读 localStorage 判登录；vipActive / active 字段名不一致",
     "src/pages/VipPage.vue:170；UserProfileVo vs VipStatusVo", "登录态双源；VIP 状态取值易错", "统一以 permissionStore 为唯一状态源"],
    ["6.7", "🟠 高", "VIP 专区「未订阅仅前 3 页」是前端截断，后端返回全量", "src/pages/VipPage.vue:174, 231-235",
     "可绕过（直接翻页请求）", "后端加页码上限，或明确为「体验降级」并记录"],
    ["6.8", "🟠 高", "频道未订阅态「预览」只展示不可点", "src/pages/SubscribeChannelPage.vue:156-160",
     "第 1 页小说全部可见但点击一律 toast，与「预览」语义冲突", "产品拍板：预览是否允许点开"],
    ["6.9", "🟡 中", "CoinPage / TicketPage / SubscribeHistoryPage 无守卫", "各页 onMounted",
     "匿名可直达，表现为空壳 + 401 toast", "补 NEED_LOGIN 门禁"],
    ["6.10", "🟡 中", "媒体字节靠登录时种的 Cookie，token 由他途注入时图片全裂",
     "AuthController.java:61-62, 75-82 + services/subscribe.js:49-51", "纯前端无法自检",
     "登录后做 Cookie 存在性自检"],
    ["6.11", "🟡 中", "X-User-Id 请求头是死代码（后端刻意忽略）", "src/services/http.js:17", "噪音，易误导", "删除"],
    ["6.12", "🟡 中", "试用期会被管理员操作重置（vip_activated_at 被刷新）",
     "VipInvitationServiceImpl.java:147", "前端「未订阅」状态不稳定；/channels 与 /subscribe/my 答案不一致",
     "后端区分「首次激活」与「调整」"],
    ["6.13", "🟢 低", "搜索结果被权限裁剪但无任何标记", "NovelController.java:44-70", "用户误以为「没有这本书」",
     "响应补 restricted 类标记"],
    ["6.14", "🟢 低", "已读过滤是本地 localStorage，VIP 与订阅各一套 key", "vipReadStatus.js / subscribeReadStatus.js",
     "换设备即「已读」重置", "接入账户级阅读历史后统一"],
    ["6.15", "🟢 低", "订阅到期判定两套时间源（MySQL NOW() vs JVM now()）",
     "UserSubscribeMapper.java:11 vs SubscribeServiceImpl.java:258", "到期边界可能给出不同答案", "统一时间源"],
], [8, 12, 42, 44, 42, 40]))

# =====================================================================
# Sheet 8 - 后端契约诉求
# =====================================================================
SHEETS.append(("后端契约诉求", [
    ["编号", "诉求", "理由", "优先级"],
    ["7.1", "新增独立业务码表示「需订阅」（如 4031）", "当前与「账号被禁用」共用 code 403，前端只能靠中文字符串区分", "P0"],
    ["7.2", "VIP 状态字段归一：统一 vipActive；vipStatus 标注「不可用于判权」或不返回",
     "三处重复实现：AuthController:100、VipInvitationServiceImpl:519、VipAccessServiceImpl:23", "P0"],
    ["7.3", "新增「当前能力」聚合接口：{ authenticated, vipActive, vipExpireTime, vipActivatedAt, trialActive, subscribes[], coinBalance, prices }",
     "前端权限层只依赖一个数据源，避免首屏 N 个请求 + 状态互相矛盾", "P0"],
    ["7.4", "价格表下发", "价格只存在于 application.yml（100/300/800/3000），前端硬编码会漂移", "P1"],
    ["7.5", "统一未订阅分页策略", "/channels/{id}/novels 强制第 1 页，/channels/{id}/feed 不限制 → 未订阅可翻遍频道内全部小说", "P1"],
    ["7.6", "明确媒体缩略图策略；修正 VIP 小说目录接口整体 403 的问题",
     "方法注释说 thumb/poster 仅需登录、实现一律 403；且 VIP 书连「免费章节」目录也拿不到", "P1"],
    ["7.7", "补 /api/user/read-history、/api/subscribe/history", "前端已在调用，后端返回 404（账户级阅读历史未落地）", "P1"],
    ["7.8", "明确「订阅是否需要 VIP 前置」", "后端只要求登录 + 币，前端却整页 VIP 门禁 → 口径分歧", "P0（产品决策）"],
    ["7.9", "（超出 H5 但必须单独立项）Admin /admin/** 与 Crawler /crawler/** 无任何鉴权，且与 H5 同进程同端口",
     "与「全局权限管理」直接冲突", "P0（安全）"],
], [8, 62, 58, 16]))

# =====================================================================
# Sheet 9 - 判定真值速查
# =====================================================================
SHEETS.append(("判定真值速查", [
    ["功能", "后端判定式", "代码位置"],
    ["免费书详情 / 章节", "无守卫", "NovelController.java:78, 95"],
    ["VIP 书详情 / 目录", "novel.vipRequired && !vipActive → 403 / code 2001", "NovelController.java:82, 91, 128-133"],
    ["is_vip 章节正文 / 上下章", "chapter.vip && !vipActive → 403 / code 2001", "NovelController.java:100, 111, 122"],
    ["VIP 专区书单 / 分类", "需登录 + hasActiveVip；并排除已加入「已发布」频道的小说（subscribe_channel_novel + subscribe_channel.status=PUBLISHED），频道下架后自动回归", "VipController.java:66, 83, 97-116"],
    ["频道列表", "需登录；subscribed = 订阅有效 ∨ 试用中", "SubscribeServiceImpl.java:96"],
    ["频道小说列表", "需登录；未订阅 → restricted=true + 限第 1 页", "SubscribeServiceImpl.java:136-143"],
    ["频道 feed", "需登录；未订阅 → 媒体剔除、小说不限页", "ChannelFeedServiceImpl.java:57-75"],
    ["频道媒体列表", "需登录；未订阅 → 空列表 + restricted=true", "SubscribeMediaController.java:59-64"],
    ["媒体帖详情 / 媒体字节", "isAccessible → 否则 403「订阅后查看」", "SubscribeMediaController.java:77-79, 96-100"],
    ["订阅 / 一键订阅", "需登录 + 币余额足够", "SubscribeServiceImpl.java:152, 193-195"],
    ["书架 / 快乐币 / 工单 / 昵称", "需登录（requireUser）", "UserController / CoinController / TicketController"],
    ["搜索", "匿名；结果随 VIP / 订阅裁剪（不随 VIP 专区货架变化）", "NovelController.java:44-70"],
    ["VIP 判定（权威）", "userId != null && vip_expire_time != null && vip_expire_time > now()", "VipAccessServiceImpl.java:18-26"],
    ["试用判定（权威）", "vip_activated_at != null && now() < vip_activated_at + 7d（TRIAL_DAYS = 7）", "SubscribeServiceImpl.java:40, 209-215"],
    ["订阅判定（权威）", "isTrialActive ∨ selectActive(userId, channelId) != null", "SubscribeServiceImpl.java:218-223"],
], [28, 62, 52]))


# =====================================================================
# Sheet 11 - 任务明细
# =====================================================================
SHEETS.append(("任务明细", [
    ["批次", "编号", "分组", "任务", "归属", "优先级", "预估", "依赖", "验收标准", "关联编号", "状态"],

    # ---------- 批次 1 ----------
    ["1 骨架", "A1", "A 权限层建设", "新建 src/permission/ 骨架（constants / store / useCapability / guard / prompts / errors）",
     "前端", "P0", "M", "无", "目录存在并在 main.js 注册 permissionStore；单测可跑", "", "待开始"],
    ["1 骨架", "A2", "A 权限层建设", "permissionStore 作为状态唯一来源（登录态 / VIP / 试用 / 订阅集合 / 余额）",
     "前端", "P0", "M", "A1", "页面不再直接读 localStorage token；grep 仅 authStorage/http 命中", "6.6", "待开始"],
    ["1 骨架", "A3", "A 权限层建设", "能力域常量与 can() 判定（P0–P4 → 判定式）",
     "前端", "P0", "S", "A1", "can('VIP_READ') 等 5 个能力可调用，单测覆盖 S1–S5 组合", "6.2", "待开始"],
    ["1 骨架", "A5", "A 权限层建设", "http.js 错误归一化，保留 { code, httpStatus, message }",
     "前端", "P0", "S", "无", "403-需订阅 与 403-2001 可区分；code 1000（HTTP 200）被识别为失败", "6.4 / 7.1", "待开始"],
    ["1 骨架", "A6", "A 权限层建设", "401 时同步清 Pinia store 并跳登录（带 redirect）",
     "前端", "P0", "S", "A2, A5", "token 失效后 store 与 localStorage 同时清空；不出现「已登录但无数据」", "6.4", "待开始"],
    ["1 骨架", "A7", "A 权限层建设", "errors.js：后端 code → 门禁原因映射",
     "前端", "P0", "S", "A5", "401 / 2001 / 403 / 1000 / 无 code 五类各有一条单测", "7.1", "待开始"],
    ["1 骨架", "B1", "B 缺陷修复", "ReaderPage 去掉 catch-all「需要 VIP」，改按 code 分类",
     "前端", "P0", "S", "A5, A7", "网络失败提示网络错误；仅 2001 弹 VIP；404 提示章节不存在", "6.5", "待开始"],
    ["1 骨架", "B2", "B 缺陷修复", "SubscribePage 拆分 S1 / S2 文案",
     "前端", "P0", "S", "A7", "匿名看到「登录后查看订阅频道」+ 立即登录；非VIP 看到 VIP 引导", "6.3", "待开始"],
    ["1 骨架", "B3", "B 缺陷修复", "CoinPage / TicketPage 补 NEED_LOGIN 门禁",
     "前端", "P0", "S", "A7", "匿名直达显示登录引导，不再出现「空壳 + toast」", "6.9", "待开始"],
    ["1 骨架", "E1", "E 测试回归", "补 npm test 脚本（node --test）",
     "前端", "P0", "S", "无", "package.json 有 test 脚本；现有 15 个用例可跑通（已核实 15/15 通过）", "", "待开始"],

    # ---------- 批次 2 ----------
    ["2 口径", "D1", "D 产品决策", "订阅频道是否要求 VIP 资格（后端补校验 或 前端放开整页门禁）",
     "产品", "P0", "—", "无", "结论写入矩阵；前后端口径一致", "7.8", "待决策"],
    ["2 口径", "D2", "D 产品决策", "未订阅频道「预览第 1 页小说」是否允许点开",
     "产品", "P0", "—", "无", "结论与 C6 / C7 一起定稿并写入矩阵", "6.8", "待决策"],
    ["2 口径", "C13", "C 后端协同", "订阅接口按 D1 结论调整（补 VIP 校验 或 维持仅需登录）",
     "后端", "P0", "S", "D1", "接口语义与前端门禁一致", "7.8", "待开始"],
    ["2 口径", "C1", "C 后端协同", "新增业务码 4031「需订阅」",
     "后端", "P0", "S", "无", "媒体 403 返回 4031；前端可精确映射，不再匹配中文字符串", "7.1", "待开始"],
    ["2 口径", "C2", "C 后端协同", "VIP 状态字段归一（统一 vipActive；vipStatus 停用或标注）",
     "后端", "P0", "S", "无", "profile 与 vip/status 字段同名同义；文档声明 vipStatus 不可判权", "7.2", "待开始"],
    ["2 口径", "C3", "C 后端协同", "三处 VIP 判定合并到 VipAccessService",
     "后端", "P1", "S", "C2", "AuthController / VipInvitationServiceImpl 不再手写时间比较", "7.2", "待开始"],
    ["2 口径", "C4", "C 后端协同", "新增能力聚合接口 GET /api/permission/snapshot",
     "后端", "P1", "M", "无", "一次返回 authenticated / vipActive / vipExpireTime / vipActivatedAt / trialActive / subscribes / coinBalance / prices", "7.3", "待开始"],
    ["2 口径", "C5", "C 后端协同", "价格表下发（含在 C4 或独立接口）",
     "后端", "P1", "S", "C4", "前端不再硬编码 100 / 300 / 800 / 3000", "7.4", "待开始"],
    ["2 口径", "C8", "C 后端协同", "修正 VIP 书目录接口整体 403（免费章节目录应可取）",
     "后端", "P1", "M", "无", "vipRequired 小说的前 N 章目录可匿名获取，与「免费章节正文可读」一致", "7.6", "待开始"],
    ["2 口径", "A4", "A 权限层建设", "路由守卫实现整页级拦截（meta.require）",
     "前端", "P0", "M", "A2, A3, A7", "S1 直达 /h5/coin、/h5/ticket 被拦到登录并带 redirect；S2 直达 /h5/vip 被拦", "6.1 / 6.9", "待开始"],
    ["2 口径", "A8", "A 权限层建设", "prompts.js：门禁原因 → 层级 + 文案 + 落点（唯一文案出口）",
     "前端", "P1", "S", "A7", "页面内无硬编码权限文案；改文案只改一处", "", "待开始"],
    ["2 口径", "A9", "A 权限层建设", "通用门禁组件（<PermGate> / <LockedBlock>，覆盖 L0 / L1）",
     "前端", "P1", "M", "A8", "VipPage / BookshelfPage / SubscribePage 的空态改由组件渲染，样式统一", "", "待开始"],
    ["2 口径", "A10", "A 权限层建设", "会话变更后刷新能力（登录 / 退出 / 订阅成功 / VIP 变更）",
     "前端", "P1", "S", "A2", "订阅成功后无需刷新页面即可见媒体；退出后媒体立刻不可见", "", "待开始"],
    ["2 口径", "B5", "B 缺陷修复", "章节可读性预判（Chapter.vip + vipActive 本地判定）",
     "前端", "P1", "M", "A3", "目录中 VIP 章节显示锁标；点击前即知不可读，不发注定 403 的请求", "7.7", "待开始"],
    ["2 口径", "B6", "B 缺陷修复", "VIP 专区三级门禁收敛到守卫 + 组件",
     "前端", "P1", "M", "A4, A9", "登录 / VIP / 成人三层由统一机制表达，页面只剩内容渲染", "6.6", "待开始"],

    # ---------- 批次 3 ----------
    ["3 治理", "B4", "B 缺陷修复", "SubscribeHistoryPage 门禁口径明确并实现",
     "前端+产品", "P1", "S", "D5", "结论写入矩阵后按结论实现", "6.9", "待开始"],
    ["3 治理", "B9", "B 缺陷修复", "VIP 专区「仅前 3 页」截断定位澄清与实现调整",
     "前端+后端", "P1", "M", "D3", "后端加页码上限，或明确标注为体验降级并接受可绕过", "6.7", "待开始"],
    ["3 治理", "C6", "C 后端协同", "统一未订阅分页策略（/novels 限第 1 页 vs /feed 不限）",
     "后端", "P1", "M", "D2", "两个接口对未订阅用户的可见范围一致", "7.5", "待开始"],
    ["3 治理", "C7", "C 后端协同", "媒体缩略图策略定稿（thumb / poster 是否随列表放行）",
     "后端+产品", "P1", "S", "D2", "方法注释与实现一致；未订阅时列表封面行为明确", "7.6", "待开始"],
    ["3 治理", "C9", "C 后端协同", "补 GET /api/user/read-history 与 GET /api/subscribe/history",
     "后端", "P1", "M", "无", "前端调用不再 404；账户级阅读历史可用", "7.7", "待开始"],
    ["3 治理", "C10", "C 后端协同", "搜索结果补 restricted 类标记",
     "后端", "P2", "S", "无", "前端能区分「没有结果」与「有结果但不可见」", "6.13", "待开始"],
    ["3 治理", "C11", "C 后端协同", "试用期激活时间语义修正（区分首次激活与后台调整）",
     "后端", "P2", "M", "无", "后台调整 VIP 不再重置 7 天全频道试用", "6.12", "待开始"],
    ["3 治理", "C12", "C 后端协同", "订阅到期时间源统一（MySQL NOW() vs JVM now()）",
     "后端", "P2", "S", "无", "/channels 与 /subscribe/my 在到期边界答案一致", "6.15", "待开始"],
    ["3 治理", "A11", "A 权限层建设", "登录后 Cookie 自检（预防媒体字节 401「图片全裂」）",
     "前端", "P2", "S", "A2", "登录后校验 Authorization cookie 存在，缺失时提示重新登录", "6.10", "待开始"],
    ["3 治理", "B7", "B 缺陷修复", "提示频次控制（同一门禁同一会话只弹一次）",
     "前端", "P2", "S", "A8", "连续点击锁定项不重复弹窗", "", "待开始"],
    ["3 治理", "B8", "B 缺陷修复", "移除 X-User-Id 死代码",
     "前端", "P2", "S", "无", "http.js 不再发送该请求头", "6.11", "待开始"],
    ["3 治理", "D3", "D 产品决策", "VIP 专区「仅前 3 页」是安全限制还是体验降级",
     "产品", "P1", "—", "无", "结论写入矩阵，驱动 B9", "6.7", "待决策"],
    ["3 治理", "D4", "D 产品决策", "快乐币入口是否对非VIP（S2）开放",
     "产品", "P2", "—", "无", "结论写入矩阵", "", "待决策"],
    ["3 治理", "D5", "D 产品决策", "订阅阅读历史页是否要求登录",
     "产品", "P2", "—", "无", "结论写入矩阵", "6.9", "待决策"],
    ["3 治理", "E2", "E 测试回归", "permission 层单测（guard / can / errors / prompts）",
     "前端", "P0", "M", "A2, A3, A7", "S1–S5 × 5 个能力域的判定矩阵有测试覆盖", "", "待开始"],
    ["3 治理", "E3", "E 测试回归", "错误码 → 提示的契约测试",
     "前端", "P1", "S", "A5, A7", "每个 code 至少一条用例", "", "待开始"],
    ["3 治理", "E4", "E 测试回归", "五状态端到端人工验收清单",
     "前端+产品", "P1", "S", "A4", "5 个状态 × 关键页面可逐项打勾", "", "待开始"],
    ["3 治理", "E5", "E 测试回归", "把权限矩阵接入回归流程（新增功能必须更新矩阵 + 用例）",
     "团队", "P2", "S", "E2, E4", "流程写入 docs/README 维护规则", "", "待开始"],
    ["3 治理", "F1", "F 安全一致性", "Admin /admin/** 与 Crawler /crawler/** 补鉴权（单独立项）",
     "后端", "P0", "L", "无", "未授权请求被拒；同端口暴露问题关闭", "7.9", "待开始"],
    ["3 治理", "F4", "F 安全一致性", "封面代理 SSRF 收敛（GET /api/cover?url=）",
     "后端", "P1", "S", "无", "目标主机白名单校验；匿名不可代发任意请求", "", "待开始"],
    ["3 治理", "F2", "F 安全一致性", "媒体字节 Cookie 安全属性复核（HttpOnly / Secure）",
     "后端", "P2", "M", "C7", "给出改为 HttpOnly 后对 <img>/<video> 的影响评估与方案", "6.10", "待开始"],
    ["3 治理", "F3", "F 安全一致性", "已读过滤迁移到账户级（跨端一致）",
     "前后端", "P2", "M", "C9", "换设备后已读状态保留", "6.14", "待开始"],
    ["3 治理", "F5", "F 安全一致性", "工单越权返回码语义修正（1000 → 403 / 404）",
     "后端", "P2", "S", "C1", "区分「不存在」与「非本人」", "7.x", "待开始"],
], [10, 7, 16, 52, 12, 9, 7, 12, 56, 12, 10], (1, 5, 6, 10)))

# =====================================================================
# build workbook
# =====================================================================
n = len(SHEETS)
sheet_overrides = "".join(
    f'<Override PartName="/xl/worksheets/sheet{i + 1}.xml" '
    'ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>'
    for i in range(n))

content_types = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
                 '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
                 '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
                 '<Default Extension="xml" ContentType="application/xml"/>'
                 '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>'
                 + sheet_overrides +
                 '<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>'
                 '</Types>')

rels = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>'
        '</Relationships>')

sheet_entries = "".join(
    f'<sheet name="{esc(SHEETS[i][0])}" sheetId="{i + 1}" r:id="rId{i + 1}"/>'
    for i in range(n))
workbook = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
            'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
            '<sheets>' + sheet_entries + '</sheets></workbook>')

workbook_rels = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
                 '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
                 + "".join(
                     f'<Relationship Id="rId{i + 1}" '
                     'Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" '
                     f'Target="worksheets/sheet{i + 1}.xml"/>'
                     for i in range(n))
                 + f'<Relationship Id="rId{n + 1}" '
                   'Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" '
                   'Target="styles.xml"/>'
                 + '</Relationships>')

styles = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
          '<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
          '<fonts count="2">'
          '<font><sz val="11"/><name val="Microsoft YaHei"/></font>'
          '<font><b/><sz val="11"/><name val="Microsoft YaHei"/></font>'
          '</fonts>'
          '<fills count="3">'
          '<fill><patternFill patternType="none"/></fill>'
          '<fill><patternFill patternType="gray125"/></fill>'
          '<fill><patternFill patternType="solid"><fgColor rgb="FFDDEBF7"/><bgColor indexed="64"/></patternFill></fill>'
          '</fills>'
          '<borders count="2">'
          '<border><left/><right/><top/><bottom/><diagonal/></border>'
          '<border><left style="thin"><color rgb="FFBFBFBF"/></left>'
          '<right style="thin"><color rgb="FFBFBFBF"/></right>'
          '<top style="thin"><color rgb="FFBFBFBF"/></top>'
          '<bottom style="thin"><color rgb="FFBFBFBF"/></bottom><diagonal/></border>'
          '</borders>'
          '<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>'
          '<cellXfs count="4">'
          '<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>'
          '<xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" '
          'applyBorder="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>'
          '<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1">'
          '<alignment vertical="top" wrapText="1"/></xf>'
          '<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1">'
          '<alignment horizontal="center" vertical="top" wrapText="1"/></xf>'
          '</cellXfs></styleSheet>')

def write_workbook(path):
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("[Content_Types].xml", content_types)
        z.writestr("_rels/.rels", rels)
        z.writestr("xl/workbook.xml", workbook)
        z.writestr("xl/_rels/workbook.xml.rels", workbook_rels)
        z.writestr("xl/styles.xml", styles)
        for i, (name, data, widths, *rest) in enumerate(SHEETS):
            center = rest[0] if rest else ()
            z.writestr(f"xl/worksheets/sheet{i + 1}.xml", build_sheet(data, widths, center))


try:
    write_workbook(OUT)
    print("written:", OUT)
except PermissionError:
    # Workbook is open in Excel / WPS: write beside it instead of failing silently.
    FALLBACK = OUT + ".new.xlsx"
    write_workbook(FALLBACK)
    print("!! 目标文件被占用（Excel/WPS 打开中），已改写到:", FALLBACK)
    print("!! 关闭表格软件后，把 .new.xlsx 改名覆盖回 h5-permission-matrix.xlsx 即可")

print("sheets:", ", ".join(s[0] for s in SHEETS))
by_name = {s[0]: s[1] for s in SHEETS}
print("matrix rows:", len(by_name["功能×状态矩阵"]) - 1)
print("todo rows:", len(by_name["待办任务"]) - 1)
print("task detail rows:", len(by_name["任务明细"]) - 1)
