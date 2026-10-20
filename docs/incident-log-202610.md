# 事故与问题记录 · 2026-10

> 文档地图：见 [`docs/README.md`](README.md)。新事故先记这里，可复用结论再提炼进 [`../DECISIONS.md`](../DECISIONS.md) / [`../LESSONS_LEARNED.md`](../LESSONS_LEARNED.md)。

## 1. 订阅频道金币购买成功后仍提示「订阅后查看」（10-21）

**现象**

- H5 端：用户用快乐币购买订阅频道成功后，进入频道详情列表点击内容仍提示「订阅后查看」，媒体图文/视频不可见、不可打开；
- 频道列表页却能正常显示「已订阅」（因为那里走的是另一条查询）。

**根因**

- 2026-10-04 的枚举整数化重构（`82116be`）把 `user_subscribe.status` 由字符串改为 `TINYINT`（`1=生效`），
  但 `UserSubscribeMapper.selectActive` 里的 SQL 仍是 `status = 'ACTIVE'`；
- MySQL 在比较 `TINYINT` 列与字符串 `'ACTIVE'` 时会把字符串转成 `0`，导致**永远匹配不到** →
  `SubscribeService.isAccessible` 恒为 `false`，付费用户被当作未订阅；
- 附带影响：续订时 `selectActive` 查不到已有有效订阅 → 不会延长 `end_time`，而是**再插入一行**（重复订阅、重复扣币）。

**修复**

- `UserSubscribeMapper.selectActive` 改为整数枚举 `status = 1`（复用 `UserSubscribe.STATUS_ACTIVE` 常量）；
- 新增 `UserSubscribeMapperSqlTest`：断言该 SQL 使用整数状态、不含 `'ACTIVE'` 等字符串字面量，防止回退。

**预防**

- 所有类型/状态枚举均已整数化；手写 SQL 里不要再出现字符串枚举字面量（`status = 'XXX'` / `type = 'XXX'` 等）；
- 提交前全局检索 `= '[A-Z_]+'`（限定在 Mapper / XML）确认无残留。

**验证**

- 本地 `mvn -pl mini-novel-vip -am -Dtest=UserSubscribeMapperSqlTest test` 通过；
- 上线后由 CI 部署，VPS 记录 `DEPLOY_SHA` 追溯。
