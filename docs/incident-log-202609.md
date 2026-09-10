# 事故与问题记录（2026-09）

> 状态：现役（文档地图：[`README.md`](README.md)）
> 本文件按时间记录部署/运维过程中出现的问题、根因、修复与预防措施，
> 供后续排障与开发参考，避免同类问题再次出现。
> 关联大改造：2026-09-01 爬虫链路改造（移除授权书单/过滤规则、统一/分级发布策略）。

---

## 1. 并发 Docker 构建导致 VPS OOM 失联（09-01）

**现象**
- GitHub Actions「Deploy containers」步骤卡死 40+ 分钟（正常应 ~3.5 分钟）；
- 服务器内存耗尽：后端 API 无响应（TCP 通但 HTTP 超时）、SSH 连接被对端关闭；
- H5（纯静态 nginx）仍 200，仅后端/SSH 受影响。

**根因**
- `docker compose up -d --build` 默认**并行构建全部镜像**；
- 一次大改造使 4 个镜像（app / crawler-service / h5 / admin-ui）的构建缓存全部失效；
- 4 个并发构建（2×Maven JVM + 2×npm/esbuild）+ 12 个运行中容器，超出 VPS 3.8G 内存 → OOM thrash。
- 此前部署只改少量文件、镜像缓存命中，故仅需 3.5 分钟，掩盖了风险。

**修复**
- deploy.sh 增加 `COMPOSE_PARALLEL_LIMIT=1 compose up -d --build`（串行构建）— commit 54a5496。
- 服务商侧将服务器内存升级为 7.9G。

**预防**
- 不要并发构建多镜像；改动面大（改到多个子模块/前端）时部署耗时预期拉长；
- 低配 VPS 优先串行构建；观察构建期 `free -m` 与 `uptime` 负载。

---

## 2. 前端 nginx 502 —— 后端容器重建后 IP 变化（09-01 admin-ui / 09-05 H5）

**现象**
- 经前端 nginx 代理到后端的请求返回 **502 Bad Gateway**（nginx error log:
  `connect() failed (111: Connection refused) while connecting to upstream`）；
- 页面本身（静态）可访问，依赖 `/api/*`、`/crawler-api/*` 的请求全部失败。

**根因**
- nginx 在**容器启动时解析一次** `upstream` 主机名（如 `mini-novel-app:8080`）并缓存 IP；
- 每次部署 `compose up` 重建后端容器会分配**新 IP**（Docker 内网 DHCP 式分配，如 172.19.0.4 → 172.19.0.2）；
- 前端容器镜像未变化时**不会被重建**，仍连旧 IP → Connection refused → 502。
- 检查方法：比对 `docker logs mini-novel-h5` 中 upstream 地址与 `docker inspect mini-novel-app` 的实际 IP。

**修复**
- 立即恢复：`docker restart mini-novel-h5` / `docker restart mini-novel-admin-ui`（nginx 重新解析）；
- 根治：deploy.sh 在 `compose up -d --build` 后追加
  `compose restart mini-novel-h5 mini-novel-admin-ui` — commit ddb11ef。

**预防**
- 任何"只改了后端代码"的部署，都必须让前端容器重启一次（或依赖 deploy.sh 的 restart）；
- 排查 502 时先看 nginx 日志的 upstream IP 是否为旧地址；
- 前端 nginx 对后端服务名均属此类（H5→app，admin-ui→app 与 crawler-service）。

---

## 3. 内容审核 books 接口「全部源」查询 500（09-01）

**现象**
- `GET /crawler/content-review/books`（不带 sourceCode）返回 500；带单个 sourceCode 正常。

**根因**
- 参数绑定 bug：`Object countParam = ... ? new Object[]{source} : new Object[0];`
  声明为 `Object` 类型传入 varargs 方法时会被**整体包裹成单个参数**，
  空数组被当作参数绑定到 SQL → JDBC 异常（带源时静默返回错误的 total=0）。

**修复**
- 改为显式分支：`source 非空 ? queryForObject(sql, Long.class, source) : queryForObject(sql, Long.class)`
  — commit 82f82e1。

**预防**
- 向 `queryForObject/queryForList(sql, args...)` 传参时，数组变量必须声明为
  `Object[]`（或直接传元素），切勿声明为 `Object` 再整体传入；
- SQL 动态拼装 + 可选参数时优先用显式 if/else 分支。

---

## 4. 原生 SQL 列名与实体映射不一致导致 500（09-01）

**现象**
- 审核「批准/拒绝」章节时返回 500；批量接口能捕获到
  `PreparedStatementCallback; bad SQL grammar ... c.vip vip`。

**根因**
- 实体 `CrawlChapterRaw.vip` 通过 `@TableField("is_vip")` 映射到列 **`is_vip`**；
- 手写原生 SQL 用了 `c.vip` → 未知列 → SQL 语法错误（MyBatis-Plus 自动映射与原生 SQL 列名是两回事）。

**修复**
- chapterForUpdate 查询 `c.vip vip` → `c.is_vip vip` — commit 0355377。

**预防**
- 写原生 SQL 前核对建表 DDL / 实体 `@TableField` 的真实列名（snake_case 而非驼峰）；
- 涉及表结构列名时以 `sql/schema.sql` 与 `SHOW COLUMNS` 为准。

---

## 5. deploy.sh 重跑非幂等迁移，重复翻转数据状态（09-01）

**现象**
- 用户已在审核页批准的内容（290 章 CONTENT_READY）在**下一次部署后被翻回 PENDING_REVIEW**，
  重新出现在待审队列。

**根因**
- `20260901_remove_authorized_book_review_flow.sql` 含一次性数据翻转
  `CONTENT_READY → PENDING_REVIEW`（用于把存量数据纳入审核队列）；
- deploy.sh 对迁移文件是**每次部署无条件重跑**（`run_migration` 直接执行），
  该语句非幂等 → 每次部署都把已批准内容再次翻转。

**修复**
- 从常驻迁移中移除一次性翻转语句（只保留幂等的 DROP/UPDATE）；
- 幂等纠正移入 `20260901_23qb_direct_publish.sql`：凡在
  `novel_source_mapping` + `chapter_source_mapping` 中已存在发布映射的章节，
  一律纠正回 `CONTENT_READY`（已发布=无需再审核）— commit 964f5fd / 6c172f2。

**预防**
- **写入数据库脚本的语句必须幂等**（每次重跑结果一致）；数据迁移要么做成"条件 UPDATE 自身幂等"的形式，
  要么用 `information_schema` + `PREPARE` 守卫；
- 迁移执行后立刻核对状态分布（本章节末尾的核对 SQL）。
- **2026-09 整理后的现状**：31 个分散迁移已合并为唯一的 `sql/schema.sql`（§4 为后续变更区），
  `deploy.sh` 只执行这一份脚本；本节的 `sql/migrations/*.sql`、`run_migration_if_table_missing` 均为当时形态。

---

## 6. 爬虫策略分级（免审核/审核）的最终形态（09-01）

- **23qb 公开源**：爬取 → 章节 `CONTENT_READY` → 自动合并直接进小说库（无需审核）。
  计划 auto_merge=1，由调度器周期 mergePending 入库。
- **h528 / 69hnovel 授权源**：爬取 → 章节 `PENDING_REVIEW` → 内容审核页 →
  批准后经 `publishChapter` 入库（含 VIP 标记、VIP 分类）。
- merge 服务只处理 `CONTENT_READY` 书，绝不触碰 `PENDING_REVIEW`（isReviewOnlySource 保护）。
- commit 11003e8（大改造）/ 8f8d8f0（分级策略）。

---

## 运维核对清单（部署后必查）

```bash
# 1) 部署版本
grep DEPLOY_SHA /opt/mini-h5/.env

# 2) 授权表残留（应 0）
MYSQLPW=$(grep MYSQL_ROOT_PASSWORD /opt/mini-h5/.env | cut -d= -f2)
docker exec mini-novel-mysql mysql -uroot -p"$MYSQLPW" -N -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='mini_novel_crawler'
   AND table_name IN ('crawler_authorized_book','crawler_authorized_book_audit','xbookcn_raw_repair_cursor')"

# 3) 计划 auto_merge（23qb=1，授权源=0）
docker exec mini-novel-mysql mysql -uroot -p"$MYSQLPW" -N -e \
  "SELECT s.id,s.auto_merge,src.source_type FROM mini_novel_crawler.crawl_schedule s
   JOIN mini_novel_crawler.crawl_source src ON src.id=s.source_id WHERE s.enabled=1"

# 4) 章节状态分布（不应有大量非预期 PENDING_REVIEW）
docker exec mini-novel-mysql mysql -uroot -p"$MYSQLPW" -N -e \
  "SELECT content_status,COUNT(*) FROM mini_novel_crawler.crawl_chapter_raw GROUP BY content_status"

# 5) 三入口 HTTP 200
curl -s -o /dev/null -w 'api=%{http_code}\n' http://127.0.0.1:8080/api/home
curl -s -o /dev/null -w 'h5=%{http_code}\n' http://127.0.0.1:5173/h5/home
curl -s -o /dev/null -w 'admin=%{http_code}\n' http://127.0.0.1:5180/admin/login

# 6) 前端代理无 502（重点看 nginx error log 是否有旧 upstream IP）
docker logs mini-novel-h5 2>&1 | grep -E '502|Connection refused' | tail -5
docker logs mini-novel-admin-ui 2>&1 | grep -E '502|Connection refused' | tail -5
```

---

## 附：本地/环境注意事项（非生产）

- 本机 git/curl 走 schannel 报 `SEC_E_NO_CREDENTIALS`：改用 `git -c http.sslBackend=openssl ...`
  或 Python（自带 OpenSSL）发起 HTTPS；Windows OpenSSH 不支持命令行传密码，用 plink 或 paramiko。
- 沙箱/CI 差异：本机沙箱禁止子进程管道通信（node:test、esbuild、Mockito 自附加均受影响，
  spawn EPERM），此类测试以 GitHub Actions 结果为准；沙箱内先做 `mvn compile` + Vue SFC 语法校验。
- 服务器对代理出口 IP（如 198.18.0.1）曾出现短暂 SSH 拒绝（疑似云防护/fail2ban），
  属临时封禁，等待自动解除或由管理员 `fail2ban-client set sshd unbanip <ip>` 处理。
