# mini-h5 订阅频道 · 任务清单

> 生成自产品共创沟通。覆盖：订阅核心（C 端 + 后台 + 数据层）+ 精品内容 + 埋点 + 支付。
> 状态约定：`[ ]` 未开始 · `[x]` 已完成
> 文档地图：[`docs/README.md`](docs/README.md) ｜ 本文件是当前产品主线（对比：[`TODO.md`](TODO.md) 为早期工程主线）

## 零、实施状态核实（2026-09 整理）

> 逐项勾选状态**未回填**，以下表为准。依据：`main` 分支代码、`sql/schema.sql`（原迁移文件已合并进该脚本）、`docs/media-pool-design.md` 的生产验收记录。

| 批次 | 状态 | 证据 |
|---|---|---|
| A 数据层 | 已落地 | `sql/schema.sql` §2：`subscribe_channel` / `subscribe_channel_novel` / `user_coin_balance` / `user_coin_log` / `user_subscribe`（原 `20260910_subscribe_channel.sql`） |
| B 订阅后端 API | 已落地 | `SubscribeController`（channels / feed / novels / 订阅 / 一键订阅 / my）、`CoinController`（`/api/coin` 余额 + 流水）、`AdminCoinController`（后台充值）、`SubscribeExpireJob`（每小时到期回收）、`SubscribeService`（访问控制 + 试用期） |
| C C 端 UI | 已落地 | `H5Layout` 底部 tab「订阅」；`SubscribePage` / `SubscribeChannelPage` / `SubscribeHistoryPage` / `CoinPage` / `SubscribeMediaPostPage` |
| D 后台 UI | 已落地 | `SubscribeChannelView` / `CoinView`；`ArticleView` 已有「查看」（后台内看简介/目录/正文）、「章节 → 查看正文」与「加入频道」 |
| E 精品内容机制 | 未落地 | 13 个分类入口当前用的是 `lastupdate` 列表源，未接完本/热门/推荐榜；无质量评分、无归位审核 |
| F 数据埋点 | 未落地 | H5 代码中没有埋点上报 |
| G 自助充值支付 | 未落地 | 无支付网关；快乐币目前只能由后台充值（冷启动权宜仍在） |

已在线上、但本清单未登记的功能：

- **工单系统**：`sql/schema.sql` §2 的 `ticket` / `ticket_reply` 表、`TicketPage.vue`、`TicketView.vue`、`AdminTicketController`。需要时补入里程碑。
- **多媒体池子**：已上线，属独立需求线，见 [`docs/media-pool-requirements.md`](docs/media-pool-requirements.md) 与 [`docs/media-pool-design.md`](docs/media-pool-design.md)。

## 一、已定关键决策（避免后续反复）

- **模式**：邀请制准入 + 按分类订阅的付费阅读俱乐部，赌"读者认分类"。
- **设计哲学**：书店逻辑（反套路）——不默认续费、币价透明、到期回收≠自动扣费、邀请码是准入凭证不是拉人头。
- **订阅**：周期 1周/1月/1季/1年；无天花板、无全网会员；一键订阅 = 批量订阅所有未订阅分类。
- **准入**：邀请码 = 自动 VIP（免费、稀缺、绑定、可溯源），VIP 是"资格"不是"权益"。
- **欢迎礼**：新 VIP 1 周内所有订阅模块 100% 开放，到期自动回收（不扣费）。
- **快乐币**：仅后台充值 = **冷启动临时权宜**（限邀请码赠送/活动补偿/内测发放），M4 接自助充值后转为真实付费。
- **订阅频道**：独立新建 `subscribe_channel` 表，**不复用** `vip_category`。
- **精品入分类**：冷启动人工审核加入订阅频道，后期读者数据（完读率/订阅）接管。
- **到期倒计时**：临近 3 天，C 端本地计算，**不建表**。
- **分类详情**：网格/列表可切换；未订阅仅预览第一页（封面网格或列表），不可看详情。
- **已读过滤**：读过的书不在分类列表显示，去「阅读历史」找回。
- **下架流程**：停止新订阅 → 等全部订阅自然时效 → 真正下架。
- **主色**：墨绿 `#1f6f64`（对齐现有 App）；对齐统一左对齐。
- **已读定义**：开始阅读即算已读（点开正文即已读，不需要读完 100%）。
- **一键订阅余额不足**：全量拦截（不做部分订阅）。
- **订阅频道封面/简介**：非必填。

## 二、里程碑总览

| 里程碑 | 内容 | 任务数 |
|---|---|---|
| **M1 第一批 · 模式跑通** | 数据层 + 订阅核心后端 + C 端 UI + 后台基础 | 27 |
| **M2 第二批 · 内容命门** | 精品内容机制 | 3 |
| **M3 第三批 · 体验与数据** | 阅读历史 + 埋点 | 2 |
| **M4 第四批 · 变现** | 自助充值支付 | 1 |

---

## 三、任务明细

### A. 数据层（M1 · 后端）

- [ ] A1 新建 `subscribe_channel` 表（id/name/sort/status[发布·下架]/cover/description）
- [ ] A2 新建 `subscribe_channel_novel` 表（channel_id/novel_id/operator_id/created_at）
- [ ] A3 新建 `user_coin_balance` 表（user_id/balance）
- [ ] A4 新建 `user_coin_log` 表（user_id/±amount/balance_after/biz_type/operator_id/remark）
- [ ] A5 新建 `user_subscribe` 表（user_id/channel_id/period/start·end/status/cost_coins）
- [ ] A6 订阅频道状态机（新增默认下架 → 发布 → 下架三步流程）

### B. 订阅核心后端 API（M1）

- [ ] B1 订阅频道 CRUD（新增/改名/发布/下架 + 下架校验"是否有未时效订阅"）
- [ ] B2 订阅操作（订阅/续订/取消/查询）
- [ ] B3 一键订阅（批量订阅未订阅分类 + 扣币，余额不足拦截）
- [ ] B4 订阅访问控制（付费正文接口校验订阅状态，防 URL 直连）
- [ ] B5 到期自动回收（定时任务，回收≠自动扣费）
- [ ] B6 试用期（VIP 激活自动开 1 周全开 + 到期干净回收）
- [ ] B7 快乐币余额/流水查询 API
- [ ] B8 后台充值 API（按手机号/昵称查用户 + 充值 + 写流水留痕）
- [ ] B9 阅读历史 API（`user_read_history` 接上，账户级）
- [ ] B10 分类列表已读过滤（过滤已读书，返回未读）

### C. C 端 UI（M1 · H5）

- [ ] C1 底部 tab 加「订阅频道」（第 5 个，已达移动端上限）
- [ ] C2 订阅频道主页（四态：普通无权限 / 试用全开 / 订阅用户已订阅+未订阅 / 一键订阅 + 阅读历史入口）
- [ ] C3 分类详情页（网格/列表切换 + 未订阅仅第一页预览 + 底部订阅按钮）
- [ ] C4 阅读历史页（订阅频道内，进度 + 继续读）
- [ ] C5 「我的」页面追加「我的快乐币」入口（金色高亮 + 余额）
- [ ] C6 我的快乐币详情页（余额卡 + 充值/扣费履历，+/- 颜色区分）
- [ ] C7 订阅到期倒计时（≤3 天显示"剩余 X 天"，C 端本地计算，不建表）

### D. 后台 UI（M1 · Admin）

- [ ] D0 菜单「文章管理」更名 **VIP文章管理**（`AdminLayout` / 路由标题），且列表只显示
      **未加入任何订阅频道**的小说（`GET /admin/novels` 加 `NOT EXISTS subscribe_channel_novel`）；
      已入频道的文章在「订阅频道管理 → 查看频道详情」查看/移出，移出后自动回到本列表
- [ ] D1 订阅频道管理页 `SubscribeChannelView`（新增/改名/发布/下架）
- [ ] D2 快乐币管理页 `CoinView`（查用户 + 充值弹窗 + 充值记录）
- [ ] D3 小说内容查看（`ArticleView` 章节抽屉加「查看正文」→ 读 `chapter_content`）
- [ ] D4 VIP 小说加入频道（`ArticleView` 操作列加「加入频道」→ 写 `subscribe_channel_novel`；
      弹窗按 `GET /admin/subscribe-channels/novels/{novelId}` 展示「已加入/加入/移出」，
      后端加入接口幂等，重复加入不再 500 —— 见 `docs/incident-log-202609.md` §7）
- [ ] D5 文章列表查看文章（`ArticleView` 操作列加「查看」→ 后台内抽屉：封面/元信息/简介 + 目录 + 正文预览，
      目录走新增的 `GET /admin/novels/{id}/chapter-list`（只取章节元信息，不带正文，避免数千章书籍 MB 级响应），
      正文按需 `GET /admin/novels/chapters/{id}/content`；不跳 H5 前台）
- [ ] D6 批量加入频道（`ArticleView` 加多选列 + 「批量加入频道」→ `POST /admin/subscribe-channels/{id}/novels/batch`，
      整批幂等，返回 新增/已在频道/不存在 数量；单次上限 200）
- [ ] D7 频道详情（`SubscribeChannelView` 操作列「查看频道详情」→ `/admin/subscribe-channels/:id`）：
      频道基本信息 + 内容统计（小说/图文视频）+「频道小说」与「图文视频」两个 tab（含移出小说、内容预览），
      数据走 `GET /admin/subscribe-channels/{id}/detail` · `/{id}/novels` · `/{id}/posts`；
      频道列表接口顺带返回 `novelCount`/`mediaCount`（FR-17 内容数量）

### E. 精品内容机制（M2）

- [ ] E1 精品榜采集（采集源 `lastupdate` → 完本/热门/推荐榜）
- [ ] E2 质量评分（字数/完本/错别字/广告/章节完整 0-100 负向过滤）
- [ ] E3 分类归位（源分类→业务分类映射 + 人工审核加入订阅频道）

### F. 数据埋点（M3）

- [ ] F1 核心埋点（完读率/订阅转化/分类留存/阅读时长）——"精品标准由读者数据接管"的前提

### G. 支付（M4）

- [ ] G1 自助充值支付（快乐币购买，冷启动临时权宜的终结）

---

## 四、落地顺序

```text
M1（27 项）：先把订阅模式跑通 —— 数据层 A → 后端 B → C端 C + 后台 D
   → M2（3 项）：精品入分类，保证货架质量
      → M3（2 项）：阅读历史 + 埋点，让数据接管精品标准
         → M4（1 项）：自助充值，收口成真实付费
```

## 五、已全部确认

- 已读定义、一键订阅余额不足策略、封面/简介必填性，三项均已确认，见「一、已定关键决策」。
