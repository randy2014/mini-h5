# Mini H5 项目汇总（单文件总览）

> 用途：把仓库内的项目文档压缩成一份可通读的汇总，供快速接手、对齐口径、review 时使用。
> 定位：**汇总/导航**，不替代原文档。任何与原文冲突之处，以原文档（见第 10 节）为准。
> 整理时间：2026-09 ｜ 事实依据：`main` 分支代码、`pom.xml`、`deploy/`、`sql/schema.sql`，以及仓库内各 md 文档。

---

## 1. 一句话与关键坐标

移动端小说阅读产品：**Java 多模块单体后端 + 独立 H5 阅读端 + 独立管理后台 + 独立爬虫服务**，
付费形态为 **VIP 分类订阅频道** 与 **多媒体池子（频道内图文/视频）**，生产跑在一台 VPS 的 6 个 Docker 容器上，
部署由 GitHub Actions 驱动。

| 项 | 值 |
|---|---|
| 域名 | `xs2026.site` |
| 生产主机 | `64.90.19.6`（Ubuntu 24.04），SSH 端口 `52527`，部署路径 `/opt/mini-h5` |
| 旧主机（已停用） | `43.161.222.78`，SSH `2222` |
| H5 阅读端 | `http://64.90.19.6:5173/h5/home` |
| 管理后台 | `http://64.90.19.6:5180/admin/login` |
| 后端 API | `http://64.90.19.6:8080/api/home` |
| 接口文档 | `http://64.90.19.6:8080/swagger-ui.html` |
| 生产版本 | VPS 上 `/opt/mini-h5/.env` 的 `DEPLOY_SHA`（仓库最后一个代码提交为 `1e0f693`） |
| 生产机器规格（现状） | 内存升级至 7.9G（2026-09-01 事故后）；磁盘约 29G，媒体上线后使用率上升 |

> 生产实际状态一律以「`main` 代码 → VPS `.env` 的 `DEPLOY_SHA` → 容器与数据库实际内容」为准。

---

## 2. 业务与产品口径

### 2.1 订阅频道（付费阅读俱乐部 · 当前产品主线）

模式：**邀请制准入 + 按分类订阅**，赌「读者认分类」。设计哲学是书店逻辑而非会员制套路。

已定关键决策（摘自 `TASKS.md`）：

- 周期 1 周 / 1 月 / 1 季 / 1 年；**不默认续费**；到期回收 ≠ 自动扣费。
- 一键订阅 = 批量订阅所有未订阅分类；**余额不足全量拦截**，不做部分订阅。
- 邀请码 = 自动 VIP（免费、稀缺、绑定、可溯源）；VIP 是「资格」不是「权益」。
- 新 VIP 欢迎礼：1 周内所有订阅模块 100% 开放，到期干净回收。
- 快乐币：目前**仅后台充值**，属冷启动临时权宜（邀请码赠送/活动补偿/内测发放）。
- 订阅频道独立建表 `subscribe_channel`，**不复用** `vip_category`。
- 已读定义：点开正文即算已读；读过的书从分类列表隐藏，去「阅读历史」找回。
- 分类详情：网格/列表可切换；未订阅仅可预览第一页小说。
- 到期倒计时：临近 3 天，C 端本地计算，**不建表**。
- 下架流程：停止新订阅 → 等存量订阅自然到期 → 真正下架。
- 主色墨绿 `#1f6f64`（深色 `#154f4b`、金色 `#e6b422`、浅底 `#f3f5f1`），后台与 H5 共用同一套 token。

### 2.2 多媒体池子（已上线）

运营在后台创建「一条内容」（**标题 + 素材，无正文字段**），草稿 → 发布（挂载订阅频道）→ 下架回草稿。

- 图片数量不限，**每帖最多 1 个视频**；类型自动判定 IMAGE / VIDEO / MIXED。
- 图片服务端自动压缩（≈1MB、不模糊、长边≤2400、质量下限 75），生成列表缩略图（长边 400），首图即封面，
  **不保留原图**，同文件 md5 去重。
- 视频统一转码 **H.264 Main + AAC + mp4 + faststart + yuv420p**（保证 Android/iOS 可播），自动抽封面帧，
  单视频 ≤300MB、≤15 分钟，异步低并发。
- **已发布内容只能下架**，删除/编辑仅草稿可用（后端强制拒绝直接删改 PUBLISHED）。
- 删除内容时**磁盘文件同步回收**（4 条路径 + 每日兜底任务），不留孤儿。
- H5 侧：仅订阅/试用用户可见，媒体字节走鉴权流式（Range 206），**无未订阅预览态**，图片/视频禁下载。
- 后台侧：**运营可播放视频、查看图片**（不受 H5 禁下载约束）。
- H5 展示位：`[订阅 tab] → [频道列表] → [具体频道] → 统一网格/列表混排`（小说 📖 + 图文 🖼 + 视频 ▶，**无 Tab**），
  **首页零改动**；已读过滤仅作用于小说。

### 2.3 其他在线功能

- VIP 套餐/订单、邀请码邀请与审计、VIP 分类（历史货架，与订阅频道相互隔离）。
- 工单系统：`ticket` / `ticket_reply` + H5 工单页 + 后台工单页（**未登记在 `TASKS.md`**）。
- 阅读历史、书架、搜索、分类、榜单、登录。

---

## 3. 技术架构

### 3.1 模块（`pom.xml`，11 个 Maven 模块）

| 模块 | 职责 |
|---|---|
| `mini-novel-common` | 通用返回体、异常、分页对象 |
| `mini-novel-core` | 全局配置、异常处理、MyBatis-Plus、OpenAPI |
| `mini-novel-book` | 小说、章节、分类 |
| `mini-novel-media` | 多媒体池子：素材、图片压缩、视频转码、鉴权流式 |
| `mini-novel-user` | 前台用户、书架、阅读历史 |
| `mini-novel-vip` | VIP 套餐/订单、邀请码、订阅频道与用户订阅、快乐币 |
| `mini-novel-crawler` | 采集源/任务/清洗合并领域服务 |
| `mini-novel-api` | H5 前台接口（`/api/**`） |
| `mini-novel-admin` | 后台接口（`/admin/**`） |
| `mini-novel-crawler-service` | 独立爬虫运行时与调度器（8090，仅内网） |
| `mini-novel-application` | Spring Boot 启动模块 |

前端两个独立 npm 项目：`mini-novel-h5`（Vue 3 + Vite + Vant）、`mini-novel-admin-ui`（Vue 3 + Vite + Element Plus）。

### 3.2 技术栈

- Java 17 · Spring Boot 3.3.5 · MyBatis-Plus 3.5.7 · Sa-Token 1.39 · Springdoc OpenAPI 2.6 · Jsoup 1.18
- MySQL 8.4 · Redis 7.4 · Docker Compose · GitHub Actions
- 媒体：JDK ImageIO（图片）+ ffmpeg（视频）
- **明确不用** Elasticsearch、RabbitMQ、Spring Cloud

### 3.3 数据库

`mini_novel`（业务库）：

- 内容：`category`、`novel`、`chapter`
- 去重映射：`novel_identity`、`novel_source_mapping`、`chapter_source_mapping`
- 用户：`app_user`、`user_bookshelf`、`user_read_history`
- VIP/邀请：`vip_plan`、`user_vip`、`vip_order`、`vip_adjust_log`、`vip_invitation_code`、`vip_invitation_record`、`vip_operation_audit`
- VIP 分类（历史）：`vip_category`、`novel_vip_category_mapping`、`vip_source_category_mapping`
- 订阅：`subscribe_channel`、`subscribe_channel_novel`、`user_subscribe`
- 快乐币：`user_coin_balance`、`user_coin_log`
- 媒体：`media_asset`、`media_post`、`media_post_asset`
- 工单：`ticket`、`ticket_reply`

`mini_novel_crawler`（爬虫暂存库）：

- 配置：`crawl_source`、`crawl_rank_source`、`crawl_schedule`、`crawl_source_credential`
- 执行与原始数据：`crawl_task_v2`、`crawl_book_raw`、`crawl_chapter_raw`、`crawl_content_raw`
- 清洗合并：`crawl_merge_task`、`crawl_merge_item`

要点：

- 爬虫库**可以激进清理**，原始/失败数据在本阶段不需要长期保留。
- 授权书单相关表（`crawler_authorized_book` 等）已由 `20260901_remove_authorized_book_review_flow.sql` 删除。
- **MySQL binlog 已永久关闭**（`--disable-log-bin`），单机无主从，不再有 binlog 占盘风险。

### 3.4 数据库脚本（单一入口）

全库只维护**一份**脚本：`sql/schema.sql`（2 千余行，幂等，可重复执行），由 `deploy/deploy.sh` 每次部署执行。

| 分区 | 内容 |
|---|---|
| §1 | 建库（两个库）+ 清理已废弃表（授权书单三张表） |
| §2 | 全部表结构「当前形态」：业务库 33 张 + 采集库 10 张（含字段注释、索引、表注释） |
| §3 | 预置配置与幂等数据修正：采集源/榜单入口/调度计划/VIP 分类/分类与 VIP 套餐等，按历史顺序 |
| §4 | 后续变更区（新变更写这里，附 `information_schema + PREPARE` 守卫模板） |
| §5 | 结构自检（只读 SELECT，便于在部署日志里核对表数量） |

关键约定：

- **所有语句必须幂等**：建表用 `CREATE TABLE IF NOT EXISTS`，加列/加索引用 `information_schema + PREPARE` 守卫，
  预置数据用 `ON DUPLICATE KEY UPDATE` 或 `INSERT ... SELECT ... WHERE NOT EXISTS`。
- 原先 31 个分散的 `sql/migrations/*.sql`（其中 22 个登记在 `deploy.sh`）已在 2026-09 合并进这一份脚本并删除，
  历史可从 git 记录的提交 `323dabe` 及更早版本追溯。
- 合并过程是机械的：逐条实现历史 `CREATE/ALTER/DROP`，把结果写成「当前形态」的建表语句，
  并把守卫字符串里的 DDL 也折进结构；`ADD COLUMN` 全部补齐了幂等守卫（原先 12 处依赖标记表或干脆无守卫）。
- 本地开发示例数据（示例小说《长夜书灯》、demo 用户、示例书源）已从脚本中剔除，不再污染生产初始化。

### 3.5 生产拓扑（6 容器）

| 容器 | 端口 | 说明 |
|---|---|---|
| `mini-novel-mysql` | `127.0.0.1:3306` | MySQL 8.4，`--mysql-native-password=ON --disable-log-bin`，仅本机可连 |
| `mini-novel-redis` | 内网 6379 | 缓存 |
| `mini-novel-app` | `8080` | 后端，挂 `mini-novel-cover-cache` 与 `mini-novel-media:/data/media` |
| `mini-novel-h5` | `5173` | H5 静态站 + nginx 反代 |
| `mini-novel-admin-ui` | `5180` | 后台静态站 + nginx 反代 |
| `mini-novel-crawler-service` | 内网 8090 | 爬虫运行时，受 `CRAWLER_SCHEDULE_ENABLED` 总开关控制 |

---

## 4. 爬虫与内容链路

### 4.1 源策略

- **主力源**：`23qb_public`（`https://www.23qb.net`），当前稳定可用。
- **授权源**：`h528_authorized`、`novel69h_authorized`（走内容审核队列）。
- **已禁用/不再推进**：`xbookcn_authorized`（解析器已移除）、Qidian、Shuqi。
- 新源必须先在**一本书**上跑通「元数据 + 目录 + 完整正文 + 合并进 H5」全链路，再按分类/榜单验证，最后才谈接入。

### 4.2 数据流

```text
榜单/分类页 → 书籍详情 → 目录（含分页） → 章节页（含分页）
   → 原始暂存表（crawl_book_raw / crawl_chapter_raw / crawl_content_raw）
   → 公开源(23qb)：章节置 CONTENT_READY → 清洗合并服务自动入库（auto_merge=1）
   → 授权源(h528/69hnovel)：章节置 PENDING_REVIEW → 人工审核 → 批准后发布入库（auto_merge=0）
   → 业务表 novel / chapter → H5 阅读端
```

- 合并服务**只处理 `CONTENT_READY`**，绝不触碰 `PENDING_REVIEW`。
- 一次任务成功 ≠ 一本完整性；完整性必须用「源目录 distinct URL / raw distinct id / 正文数 / 已批准数 / 映射数 / 正式章节数 / H5 可见数」逐本对账。

### 4.3 规则要点

- **必须采到真实正文**：只有 ID/URL/目录不算成功；占位文本视为脏数据。
- **分页必须合并**：源站把一章拆多页时，按顺序拼接全部分页。
- **去重**：书级用「归一化书名 + 作者 + 源 book id/URL + 分类/状态 + 字数/最新章」多信号；章级用「源章节 id/URL + 序号 + 归一化标题 + 内容 hash」。
- **已采集章节跳过**：源映射已确认的章节不再重复抓取/合并。
- **完本优先**：源显示完本 → 业务状态标完本 → 全部章节映射完后减少/停止跟进抓取。
- **质量校验**：非空、长度阈值、非登录/报错/反爬页、标题正文未互换、样板文本比例可控；不合格不得写入业务章节表。
- **调度**：迁移登记 `23qb_public` 每日 `04:00`（Asia/Shanghai，`auto_merge=1`），
  `h528_authorized` / `novel69h_authorized` 每日 `02:00`（`crawl_vip=1`、`auto_merge=0`）；
  实际启用状态以 `crawl_schedule` 表为准；同一源存在 `PENDING/RUNNING` 任务时不再创建重复任务。
- **失败保留**：保留任务级汇总与计数，不留大规模失败正文；合并成功后清理暂存表。

---

## 5. 部署与运维

### 5.1 CI/CD 流程（唯一常规变更路径）

```text
本地改动 → commit → push main
  → GitHub Actions：爬虫服务测试（ContentReviewControllerTest）+ admin-ui npm test/build
  → 校验部署 Secrets（缺失则跳过部署）
  → rsync --delete 同步到 VPS（排除 .git/.github/.env/target/node_modules/dist）
  → 写生产 .env → 执行 deploy/deploy.sh → 记录 DEPLOY_SHA
```

**禁止**在 VPS 上手工构建应用代码作为常规路径；VPS 手工操作仅限运维（日志、库检查、磁盘清理、调度开关、应急恢复）。

需要 6 个仓库 Secrets：`VPS_HOST`、`VPS_PORT`、`VPS_USER`、`VPS_SSH_KEY`、`VPS_DEPLOY_PATH`、`MYSQL_ROOT_PASSWORD`。

### 5.2 `deploy/deploy.sh` 做什么

1. 启动 MySQL / Redis，等待 MySQL healthy。
2. 执行唯一数据库脚本 `sql/schema.sql`（幂等，覆盖建库、当前表结构、预置配置与必要的数据修正）。
3. `COMPOSE_PARALLEL_LIMIT=1 compose up -d --build` —— **串行构建**，避免并发多镜像构建打爆内存。
4. 等后端 `/api/home` 可达；随后 **重启 h5 与 admin-ui 容器**，让 nginx 重新解析后端新 IP。
5. 校验三入口：`/api/home`、`/h5/home`、`/admin/login`。

### 5.3 部署后必查（摘自 `docs/incident-log-202609.md`）

- `grep DEPLOY_SHA /opt/mini-h5/.env`
- 授权表残留应为 0；`crawl_schedule` 的 `auto_merge`（23qb=1，授权源=0）
- `crawl_chapter_raw.content_status` 分布不应有大量非预期 `PENDING_REVIEW`
- 三入口 HTTP 200；前端 nginx 日志无 `502 / Connection refused`
- 磁盘、容器状态、构建缓存（`docker builder prune -f`）

### 5.4 应急与常用操作

```bash
# 磁盘定位
df -h / ; docker system df
docker exec mini-novel-mysql sh -lc 'du -h -d 1 /var/lib/mysql | sort -h'
docker exec mini-novel-app  sh -lc 'du -h -d 2 /data/media | sort -h'

# 暂存表清理（binlog 已关闭，无需 sql_log_bin 开关）
TRUNCATE mini_novel_crawler.crawl_content_raw;
TRUNCATE mini_novel_crawler.crawl_chapter_raw;
TRUNCATE mini_novel_crawler.crawl_book_raw;
TRUNCATE mini_novel_crawler.crawl_merge_item;

# 手动触发采集
docker exec mini-novel-crawler-service sh -lc \
  'curl -s -X POST http://127.0.0.1:8090/crawler/config/schedules/13/run-now'
```

- 数据库不对公网暴露，Navicat 走 **SSH 隧道**（`64.90.19.6:52527` → `127.0.0.1:3306`）。
- 磁盘清理前先确认无 `PENDING/RUNNING` 采集任务与 `PENDING/MERGING` 合并任务。
- 所有删除类操作先 `mysqldump` 备份到 `/opt/backup/`。

---

## 6. 事故与经验教训汇总

### 6.1 2026-08 月末（部署上线期，详见 `docs/ops-handbook.md`）

| 问题 | 根因 | 处理 |
|---|---|---|
| 后台白屏、JS/CSS 404 | admin-ui `vite base=/admin/` 与 nginx 根路径不匹配 | dist 放 `/usr/share/nginx/html/admin`，根路径 302 → `/admin/` |
| **磁盘写满两次、服务器僵死** | 爬虫持续写库 5 天写满 29G + binlog 增长 5G+ + 暂存表 1.4G | 清 binlog/暂存表；根治：调度总开关 + 关闭 binlog |
| Navicat 2059 认证失败 | MySQL 8 默认 `caching_sha2_password` | `--mysql-native-password=ON` + SSH 隧道 |
| Maven 构建失败（SSL 握手） | Dockerfile 强制阿里云镜像，海外服务器不通 | 改用 Maven Central |
| SSH 反复断连 | sshd 配置与并发限制不稳 | 调整 sshd、专用端口、提高 `MaxStartups` |
| 定时任务不启动 | 陈旧 `RUNNING/MERGING` 任务阻塞同源新任务 | 标记失败清空后恢复；需陈旧任务恢复机制 |

### 6.2 2026-09（详见 `docs/incident-log-202609.md`）

| # | 问题 | 根因 | 修复 |
|---|---|---|---|
| 1 | 并发 Docker 构建导致 VPS OOM 失联，部署卡 40+ 分钟 | 4 个镜像构建缓存同时失效 → 4 路并发构建 + 12 容器超出 3.8G 内存 | 部署串行构建（`COMPOSE_PARALLEL_LIMIT=1`）+ 内存升到 7.9G |
| 2 | 前端 502（admin-ui / H5） | nginx 启动时缓存 upstream IP，后端容器重建换 IP 后仍连旧地址 | 部署后强制重启前端容器（deploy.sh 已内置） |
| 3 | 内容审核「全部源」查询 500 | 数组变量声明为 `Object` 传入 varargs 被整体包裹成单参数 | 改显式 if/else 分支 |
| 4 | 批准/拒绝章节 500 | 原生 SQL 写 `c.vip`，实际列名是 `is_vip` | 修正列名；原生 SQL 前核对 DDL |
| 5 | 已批准内容每次部署被翻回待审 | 迁移含一次性数据翻转，而 deploy.sh 每次部署无条件重跑迁移 | 移除一次性语句，改为幂等条件 UPDATE；**迁移必须幂等** |
| 6 | 媒体上线后后端启动崩溃 | `mediaProcessExecutor` 抢占 `@ConditionalOnMissingBean(Executor.class)`，crawler 依赖的 `applicationTaskExecutor` 装配失败 | 显式定义同名 bean |
| 7 | 媒体图片裂图 | 自定义扩展名 `.img/.thumb/.poster` 未识别 → `application/octet-stream` + `nosniff` | `MediaStreamer` 按扩展名返回 `image/jpeg` |
| 8 | 新建视频报 `main_path` 无默认值 | 转码前尚无成品路径，而列定义为 NOT NULL | 迁移改为可空 + 视频 md5 流式计算 |

### 6.3 可复用的教训

- **磁盘是最大风险源**：爬虫写入 + binlog + 构建缓存是两次僵死的根因。binlog 已关闭，仍要盯暂存表与媒体卷。
- **一切修复走 CI/CD**：不要手工改生产容器/文件（紧急运维除外），生产状态要能用 commit 追溯。
- **迁移必须幂等**：这是唯一一次「部署本身破坏数据」的事故类别。
- **容器重建后前端必须重启**：任何「只改后端」的部署都要考虑 nginx upstream 缓存。
- **本机网络特殊**：`github.com:443` 间歇阻断（`api.github.com` 可用，可用 REST API 兜底推送）；
  git/curl 走 schannel 报 `SEC_E_NO_CREDENTIALS` 时改 `git -c http.sslBackend=openssl`。
- **沙箱限制**：本机沙箱禁止子进程管道通信（`spawn EPERM`），此类测试以 GitHub Actions 结果为准。
- **数据库操作先备份**；**部署后必查**磁盘/容器/三入口/构建缓存。
- **爬虫开发是「源链路优先，代码其次」**：源没验证完不要写解析器。

---

## 7. 关键决策速查（合并版）

架构与部署：

- Java/Spring Boot 模块化单体，**不引入 Spring Cloud**；H5、Admin、爬虫运行时三者独立。
- H5 与 Admin 是两个独立前端项目，不合并。
- **CI/CD 是唯一常规部署路径**；VPS 手工只做运维。
- 当前阶段**不要求历史数据兼容**，可直接改/清数据。
- 业务库与爬虫暂存库**物理分离**；原始爬虫数据是临时的。
- **MySQL binlog 关闭**（取代早期「保留 1 天」的决策）。

爬虫：

- `23qb_public` 为当前稳定源；Qidian/Shuqi 保持禁用。
- 必须采到**完整正文**（含分页合并），占位/不完整不算有效内容。
- 跨源重复书必须经映射表归并，不能盲目插入重复书。
- 已采集章节跳过；完本标记并减少抓取。
- **发布分两级**：公开源直接发布（`CONTENT_READY` + auto_merge），授权源必须人工审核后发布（`PENDING_REVIEW`）。
- 调度：只跑启用计划 + 同源活跃任务去重 + 需要陈旧任务恢复 + 保留全局总开关。

产品：

- 订阅频道采用邀请制 + 按分类订阅；不默认续费；到期回收不等于扣费。
- 订阅频道独立建表，不复用 VIP 分类。
- 媒体内容仅订阅可见、无未订阅预览态；H5 禁下载（尽力而为，不防截屏/抓包）。
- 后台运营必须能播放/查看媒体素材。
- 后台与 H5 共用同一套品牌视觉 token。
- 后台必须支持文章下架；核心运营动作不依赖直接 SQL。

延后项：凭证式 VIP 爬虫、手动 TXT 导入、多源加权合并与冲突复核、支付网关、域名 HTTPS/CDN（**注意：域名与 HTTPS 在 Phase 6 仍列为待办，但生产已用 `xs2026.site` 域名**）。

---

## 8. 当前进度与状态（2026-09 核实）

| 批次 | 状态 | 证据 |
|---|---|---|
| 订阅频道 A 数据层 | 已落地 | `sql/schema.sql` §2 中 5 张表（`subscribe_channel` 等） |
| 订阅频道 B 后端 API | 已落地 | `SubscribeController`、`CoinController`、`AdminCoinController`、`SubscribeExpireJob`、`SubscribeService` |
| 订阅频道 C C 端 UI | 已落地 | `H5Layout` 订阅 tab + `SubscribePage` / `SubscribeChannelPage` / `SubscribeHistoryPage` / `CoinPage` |
| 订阅频道 D 后台 UI | 已落地 | `SubscribeChannelView` / `CoinView`；`ArticleView` 已含「查看正文」「加入频道」 |
| 多媒体池子 P1–P8 | 已上线 | 2026-09-10 生产验收；最新代码提交 `1e0f693` |
| 工单系统 | 已上线（未登记在 TASKS） | `20260912_ticket.sql` + `TicketPage.vue` / `TicketView.vue` / `AdminTicketController` |
| E 精品内容机制 | **未落地** | 13 个分类入口用的是 `lastupdate` 列表源，未接完本/热门/推荐榜；无质量评分与归位审核 |
| F 数据埋点 | **未落地** | H5 代码中无埋点上报 |
| G 自助充值支付 | **未落地** | 无支付网关，快乐币仍只能后台充值 |

生产数据快照（`ops-handbook` 2026-08-31 记录，仅作量级参考）：`novel` 3735 本、`chapter` 73643 章。

---

## 9. 待办与技术债

**产品线（`TASKS.md`）**：E1 精品榜采集（`lastupdate` → 完本/热门/推荐榜）、E2 质量评分（0–100 负向过滤）、
E3 分类归位 + 人工审核入频道、F1 核心埋点（完读率/订阅转化/分类留存/阅读时长）、G1 自助充值支付。

**工程线（`TODO.md` / `ROADMAP.md`）**：

- 爬虫：确认新合入章节正文完整、重复采集跳过、合并后自动清暂存、失败数据短保留、完成状态与分类关联。
- H5 体验：全局 UI 一致性（间距/按钮/导航/空态）、详情页与阅读器返回路径、阅读设置迁到「我的」、
  阅读历史恢复到上次章节并自动滚动、书架增删可逆、`br` 与段落换行正确渲染、长章节列表分页/虚拟加载。
- 后台：采集任务管理（立即执行/停止陈旧/改计划）、采集进度可视化、暂存清理入口、文章上下架、
  章节管理准确性、VIP/用户/支付可视化管理、内容审核。
- 数据质量：章节质量评分、缺失章节检测、重复书置信度、分类映射归一、完本跳过策略。
- 运维：磁盘监控/周检、备份与恢复方案、轻量部署校验脚本。

**剩余风险**：媒体卷随视频增长需容量监控；防盗下载只防「顺手保存」；源站 HTML 变化会打断选择器；
代理出口 IP 曾被云防护短暂拒绝（临时封禁，需等待或由管理员解封）。

---

## 10. 文档索引与阅读建议

| 文档 | 状态 | 什么时候看 |
|---|---|---|
| [`docs/README.md`](docs/README.md) | 入口 | 文档地图、权威口径、维护规则 |
| [`README.md`](README.md) | 现役 | 模块、技术栈、本地启动、入口 |
| [`PROJECT_CONTEXT.md`](PROJECT_CONTEXT.md) | 现役 | 架构、库表、产品范围、爬虫方向 |
| [`DECISIONS.md`](DECISIONS.md) | 现役 | 决策依据，避免重复争论 |
| [`DEPLOYMENT.md`](DEPLOYMENT.md) | 现役 | 部署、Secrets、迁移、应急清理（主机/端口权威） |
| [`CRAWLER_DESIGN.md`](CRAWLER_DESIGN.md) | 现役 | 爬虫规则、数据流、验收清单 |
| [`TASKS.md`](TASKS.md) | 现役 | 订阅频道产品线任务与已定决策 |
| [`TODO.md`](TODO.md) | 部分过期 | 早期工程线待办（技术债） |
| [`ROADMAP.md`](ROADMAP.md) | 部分过期 | Phase 4–6（爬虫质量 + 生产加固）仍有效 |
| [`LESSONS_LEARNED.md`](LESSONS_LEARNED.md) | 历史 | 早期复盘原文（binlog 段已被取代） |
| [`docs/ops-handbook.md`](docs/ops-handbook.md) | 历史快照 | 8 月末部署与故障全过程、常用命令 |
| [`docs/incident-log-202609.md`](docs/incident-log-202609.md) | 现役 | 9 月事故 + 部署后核对清单 |
| [`docs/media-pool-requirements.md`](docs/media-pool-requirements.md) | 现役 | 媒体池需求与验收标准 |
| [`docs/media-pool-design.md`](docs/media-pool-design.md) | 现役 | 媒体池技术设计与实施记录 |
| [`deploy/README.md`](deploy/README.md) | 现役 | 生产部署与 Secrets 速查 |

**推荐阅读顺序**：本文件 → `docs/README.md`（地图）→ `PROJECT_CONTEXT.md`（现状）→ `TASKS.md`（要做什么）→
按需进入 `DEPLOYMENT.md` / `CRAWLER_DESIGN.md` / `docs/media-pool-*.md`。

---

## 11. 遗留不一致（本汇总不掩盖的部分）

| 项 | 说明 |
|---|---|
| 两条主线并行 | `TODO.md`/`ROADMAP.md`（早期工程线）与 `TASKS.md`（当前产品线）未合并，口径不同 |
| `TASKS.md` 勾选未回填 | 以该文件「实施状态核实」表为准 |
| `ROADMAP.md` 未纳入订阅与媒体 | Phase 4–6 仍以爬虫/运维为主 |
| 域名与 HTTPS 口径 | 文档仍把「域名 + HTTPS」列为待办，但生产已在用 `xs2026.site`（未见 HTTPS 记录） |
| `TASKS.md` 未登记工单与媒体池子 | 已在 `TASKS.md` 中显式标注 |
| 历史文档中的机器数据 | `ops-handbook.md` 的 3.8G 内存 / 35% 磁盘已被 9 月记录取代 |
