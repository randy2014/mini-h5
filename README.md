# Mini H5 · 移动端小说阅读平台

> 域名 `xs2026.site` ｜ 生产主机 `64.90.19.6`（SSH `52527`，部署路径 `/opt/mini-h5`）
> 文档地图与权威口径见 [`docs/README.md`](docs/README.md) ｜ 最后整理：2026-09

移动端小说阅读产品：Java 多模块单体后端 + 独立 H5 阅读端 + 独立管理后台 + 独立爬虫服务。
付费形态有两条：**VIP 分类订阅频道**（按分类订阅、邀请码准入、快乐币）与**多媒体池子**（频道内图文/视频内容）。

## 模块

后端为 Maven 多模块单体（见 `pom.xml`，11 个模块）；两个前端是独立的 npm 项目，不参与 Maven 构建。

| 模块 | 职责 |
|---|---|
| `mini-novel-common` | 通用返回体、异常、分页对象 |
| `mini-novel-core` | 全局配置、异常处理、MyBatis-Plus、OpenAPI |
| `mini-novel-book` | 小说、章节、分类领域 |
| `mini-novel-media` | 多媒体池子：素材、图片压缩、视频转码、鉴权流式下发 |
| `mini-novel-user` | 前台用户、书架、阅读历史 |
| `mini-novel-vip` | VIP 套餐/订单、邀请码、订阅频道与用户订阅、快乐币 |
| `mini-novel-crawler` | 采集源/采集任务/清洗合并等领域服务 |
| `mini-novel-api` | H5 前台接口（`/api/**`） |
| `mini-novel-admin` | 后台管理接口（`/admin/**`） |
| `mini-novel-crawler-service` | 独立爬虫运行时与调度器（端口 8090，仅内网） |
| `mini-novel-application` | Spring Boot 启动模块 |

| 前端 | 职责 |
|---|---|
| `mini-novel-h5` | H5 阅读端（Vue 3 + Vite） |
| `mini-novel-admin-ui` | 管理后台（Vue 3 + Vite + Element Plus） |

## 技术栈

- Java 17 · Spring Boot 3.3.5 · MyBatis-Plus 3.5.7 · Sa-Token 1.39 · Springdoc OpenAPI
- MySQL 8.4（业务库 `mini_novel` + 爬虫暂存库 `mini_novel_crawler`）· Redis 7.4
- 采集解析 Jsoup；媒体处理 JDK ImageIO（图片压缩）+ ffmpeg（视频转码）
- 部署：Docker Compose（6 容器）跑在 VPS 上，GitHub Actions 负责 CI/CD
- 明确不使用 Elasticsearch、RabbitMQ、Spring Cloud

## 本地启动

```bash
# 1) 建库并导入基础 schema
mysql -uroot -p < sql/schema.sql

# 2) 修改 mini-novel-application/src/main/resources/application.yml 的 MySQL / Redis 连接

# 3) 启动后端（接口文档 http://localhost:8080/swagger-ui.html）
mvn -pl mini-novel-application -am spring-boot:run
```

```bash
# H5 阅读端（Vite 会把 /api 代理到 http://localhost:8080）
cd mini-novel-h5 && npm install && npm run dev

# 管理后台
cd mini-novel-admin-ui && npm install && npm run dev
```

## 数据库脚本

全库只维护**一份**脚本：`sql/schema.sql`（幂等，可重复执行）。

```text
§1 建库 + 清理废弃表        §2 全部表结构（当前形态：业务库 33 张 + 采集库 10 张）
§3 预置配置与幂等数据修正    §4 后续变更区（新变更写这里）   §5 结构自检
```

- 由 `deploy/deploy.sh` 在每次部署时执行；本地全新环境由 compose 挂到 MySQL 初始化执行。
- 原先 31 个分散的 `sql/migrations/*.sql` 已在 2026-09 合并进该文件并删除，历史可从 git 记录追溯。
- **新增结构变更直接追加到文件 §4**，照抄其中的 `information_schema + PREPARE` 守卫写法，不要再新增迁移脚本；
  改完请在测试库连跑两遍，确认第二遍无报错、无数据变化。

## 生产入口

| 入口 | 地址 |
|---|---|
| H5 阅读端 | `http://64.90.19.6:5173/h5/home` |
| 管理后台 | `http://64.90.19.6:5180/admin/login` |
| 后端 API | `http://64.90.19.6:8080/api/home` |
| 接口文档 | `http://64.90.19.6:8080/swagger-ui.html` |
| 域名 | `xs2026.site` |

MySQL、Redis 与爬虫服务不对公网暴露：MySQL 绑定 `127.0.0.1:3306`，GUI 工具需走 SSH 隧道（见 `DEPLOYMENT.md`）。

## 部署

```text
本地提交 → push main → GitHub Actions（爬虫服务测试 + admin-ui 测试与构建）
        → rsync 到 VPS → deploy/deploy.sh：执行 sql/schema.sql、串行构建 6 容器、三入口健康检查
```

- 常规路径只能走 CI/CD；在 VPS 上手工构建应用代码不是允许的常规做法。
- VPS 手工操作仅限运维：日志排查、数据库检查、磁盘清理、调度开关、应急恢复。
- 生产部署版本记录在 VPS 的 `/opt/mini-h5/.env` 的 `DEPLOY_SHA`。

## 文档地图

完整索引、权威口径与文档状态见 [`docs/README.md`](docs/README.md)。速览：

- **汇总总览（先看这份）**：[`PROJECT_SUMMARY.md`](PROJECT_SUMMARY.md)
- 现状与架构：[`PROJECT_CONTEXT.md`](PROJECT_CONTEXT.md)
- 部署：[`DEPLOYMENT.md`](DEPLOYMENT.md) · [`deploy/README.md`](deploy/README.md)
- 爬虫：[`CRAWLER_DESIGN.md`](CRAWLER_DESIGN.md)
- 决策与经验：[`DECISIONS.md`](DECISIONS.md) · [`LESSONS_LEARNED.md`](LESSONS_LEARNED.md)
- 待办与路线：[`TASKS.md`](TASKS.md) · [`TODO.md`](TODO.md) · [`ROADMAP.md`](ROADMAP.md)
- 运维与事故：[`docs/ops-handbook.md`](docs/ops-handbook.md) · [`docs/incident-log-202609.md`](docs/incident-log-202609.md)
- 多媒体池子：[`docs/media-pool-requirements.md`](docs/media-pool-requirements.md) · [`docs/media-pool-design.md`](docs/media-pool-design.md)
