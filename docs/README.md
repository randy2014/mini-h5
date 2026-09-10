# 项目文档地图

> 本文件是项目的文档入口，说明每份文档的适用范围与权威口径。
> 整理时间：2026-09 ｜ 新增文档请在本文件「二、文档状态」中登记。

## 一、按主题找文档

| 我想知道… | 权威文档 |
|---|---|
| **想一次通读整个项目（汇总版）** | [`../PROJECT_SUMMARY.md`](../PROJECT_SUMMARY.md) |
| 项目是什么、模块怎么分、怎么本地跑 | [`../README.md`](../README.md) |
| 当前架构、库表、产品范围、爬虫方向 | [`../PROJECT_CONTEXT.md`](../PROJECT_CONTEXT.md) |
| 怎么部署、需要哪些 Secrets、迁移与应急清理 | [`../DEPLOYMENT.md`](../DEPLOYMENT.md) · [`../deploy/README.md`](../deploy/README.md) |
| 爬虫怎么设计：源策略、数据流、去重、完整性、调度 | [`../CRAWLER_DESIGN.md`](../CRAWLER_DESIGN.md) |
| 为什么这样做（避免重复争论） | [`../DECISIONS.md`](../DECISIONS.md) |
| 踩过的坑与处理方式 | [`../LESSONS_LEARNED.md`](../LESSONS_LEARNED.md)（早期）· [`incident-log-202609.md`](incident-log-202609.md)（9 月） |
| 8 月末部署与故障全过程、常用运维命令 | [`ops-handbook.md`](ops-handbook.md) |
| 9 月事故根因 + 部署后核对清单 | [`incident-log-202609.md`](incident-log-202609.md) |
| 订阅频道（付费阅读俱乐部）要做什么 | [`../TASKS.md`](../TASKS.md) |
| 多媒体池子的需求与设计 | [`media-pool-requirements.md`](media-pool-requirements.md) · [`media-pool-design.md`](media-pool-design.md) |
| 早期主线的待办与路线（爬虫 + H5 体验） | [`../TODO.md`](../TODO.md) · [`../ROADMAP.md`](../ROADMAP.md) |
| 授权源爬虫的作业规范与复盘（DSH 技能） | [`../.agents/skills/authorized-adult-content-crawler/SKILL.md`](../.agents/skills/authorized-adult-content-crawler/SKILL.md) |

## 二、文档状态

| 文档 | 状态 | 说明 |
|---|---|---|
| `PROJECT_SUMMARY.md` | 现役 | 单文件总览：业务/架构/爬虫/部署/事故/决策/进度/待办汇总；**是汇总与导航，不替代原文** |
| `README.md` | 现役 | 2026-09 重写为合法 UTF-8 并同步现状（此前编码损坏且内容过期） |
| `PROJECT_CONTEXT.md` | 现役 | 生产地址、产品范围、库表清单已订正 |
| `DECISIONS.md` | 现役 | 架构/部署/爬虫/产品决策仍然有效 |
| `ROADMAP.md` | 部分过期 | Phase 1–3 大体完成，Phase 4–6 仍适用；未纳入订阅频道与多媒体池子 |
| `TODO.md` | 部分过期 | 早期英文主线待办，与 `TASKS.md` 并行、口径不同 |
| `TASKS.md` | 现役 | 订阅频道 M1 + 精品/埋点/支付；含「实施状态核实」表 |
| `LESSONS_LEARNED.md` | 历史 | 保留原始复盘不再追加；新事故记入 `docs/incident-log-YYYYMM.md` |
| `CRAWLER_DESIGN.md` | 现役 | binlog 策略段已订正为「已永久关闭」 |
| `DEPLOYMENT.md` | 现役 | 主机/SSH 端口、binlog、串行构建、媒体卷已订正 |
| `deploy/README.md` | 现役 | 生产部署说明与 Secrets 清单 |
| `docs/ops-handbook.md` | 历史快照 | 2026-08-24 ~ 08-31 记录，常用命令仍可用；服务器/内存/磁盘数据以 `incident-log-202609.md` 为准 |
| `docs/incident-log-202609.md` | 现役 | 9 月事故记录 + 部署后运维核对清单 |
| `docs/media-pool-requirements.md` | 现役 | 多媒体池子需求说明书（已上线） |
| `docs/media-pool-design.md` | 现役 | 多媒体池子技术设计 + 实施与验收记录 |
| `.agents/skills/authorized-adult-content-crawler/` | 工具文档 | DSH 技能：授权源爬虫作业规范与 Xbookcn 复盘 |

## 三、两条主线的口径

- **产品主线（当前）**：`TASKS.md`（订阅频道 M1–M4）+ `docs/media-pool-*.md`（已上线）。
- **工程主线（早期）**：`ROADMAP.md` / `TODO.md`（爬虫稳定性 + H5 阅读体验）。
  其中未完成项视为**技术债**，不再当作产品需求认领；已完成的不要重复开工。

生产实际状态一律以三个来源为准，文档只做说明：
`main` 分支代码 → VPS `/opt/mini-h5/.env` 的 `DEPLOY_SHA` → 容器与数据库实际内容。

## 四、维护规则

1. **事实只写一处**：地址、端口、容器名、调度时间、迁移清单等，只在最相关的一份文档维护，其它文档用链接指向，不复制粘贴。
2. **服务器地址与 SSH 端口以 `DEPLOYMENT.md` 为准**；生产实际情况以 VPS 上的 `.env` 与容器为准。
3. **事故先记流水**：写入 `docs/incident-log-YYYYMM.md`，可复用的结论再提炼进 `LESSONS_LEARNED.md` 或 `DECISIONS.md`。
4. **数据库只有一份脚本**：`sql/schema.sql`（幂等，可重复执行）。新变更追加到该文件 §4「后续变更区」，
   不要再新增 `sql/migrations/*.sql`，也不要改 `deploy.sh` 的脚本列表（见 2026-09-01 非幂等迁移事故）。
5. **新增 md 文档在本文件登记**，并说明它是「现役 / 历史 / 部分过期」。

## 五、已知遗留不一致

| 项 | 现状与处理 |
|---|---|
| `TODO.md` / `ROADMAP.md` 与 `TASKS.md` 并行 | 两条主线未合并；已在本文件第三节明确各自适用范围，暂不强行合并 |
| `ROADMAP.md` 未纳入订阅频道与多媒体池子 | 需要时再补章节，避免与 `TASKS.md` 重复维护 |
| `TASKS.md` 的 `[ ]` 勾选状态未逐项回填 | 以该文件「实施状态核实」表为准 |
| `ops-handbook.md` 中的机器规格与磁盘数据 | 已被 `incident-log-202609.md` 更新（内存 7.9G、binlog 关闭），保留为历史快照 |
