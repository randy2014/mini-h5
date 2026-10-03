# 事故与问题记录（2026-09）

> 状态：现役（文档地图：[`README.md`](README.md)）
> 本文件按时间记录部署/运维过程中出现的问题、根因、修复与预防措施，
> 供后续排障与开发参考，避免同类问题再次出现。
> 关联大改造：2026-09-01 爬虫链路改造（移除授权书单/过滤规则、统一/分级发布策略）。
> 关联改造：2026-09-15 统一 80/443 入口网关 + HTTPS 上线（见 [`domain-tls-setup-202609.md`](domain-tls-setup-202609.md)，
> 对应问题记录见 §8–§12）。

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

## 7. 文章管理「加入订阅频道」重复提交返回 500（09-13）

**现象**
- 后台「文章管理 → 加入频道」选定频道点「加入」返回 500；同一小说在 3 秒内连点 3 次，每次都 500。
- 后端日志：`DuplicateKeyException` → `Duplicate entry '1-4209' for key 'subscribe_channel_novel.uk_channel_novel'`。

**根因**
- `POST /admin/subscribe-channels/{channelId}/novels` 直接 insert，未校验该小说是否已在该频道；
- `subscribe_channel_novel` 上有 `UNIQUE KEY uk_channel_novel (channel_id, novel_id)`，
  重复加入必然违反唯一键（novel 4209 本就已在频道 1 中）；
- `GlobalExceptionHandler` 只处理 `BusinessException` 与参数校验异常，`DuplicateKeyException`
  落到 Spring 默认错误处理 → HTTP 500，前端只显示「网络异常」。

**修复**
- `AdminSubscribeChannelController.addNovel` 改为**幂等**：先按 (channelId, novelId) 查既有关系，
  存在即原样返回，不再 insert；同时补 novelId 非空校验；
- 新增 `GET /admin/subscribe-channels/novels/{novelId}`（该小说已加入的频道 id 列表），
  后台弹窗据此把已加入的频道显示为「已加入」，并复用既有
  `DELETE /{channelId}/novels/{novelId}` 提供「移出」，从交互上消灭重复提交入口；
- `GlobalExceptionHandler` 增加 `DuplicateKeyException` 兜底，统一返回
  `数据已存在，请勿重复提交`（HTTP 200 + 业务失败码），避免其它唯一键（如频道重名 `uk_name`）
  再次以 500 暴露。

**预防**
- 关联表/映射表写接口一律实现为幂等（先查后写或 upsert），不要依赖前端保证不重复提交；
- 建表带 `UNIQUE KEY` 时，必须同时检查所有写入路径是否会命中重复；
- 唯一键冲突属于可预期业务结果，应在全局异常处理里转成业务提示，而不是 500。

---

## 8. 前端/后端端口直接暴露公网、域名无统一入口（09-15）

**现象**
- 访问必须带端口：`http://<ip>:5173/h5/home`、`http://<ip>:5180/admin/login`、`http://<ip>:8080/api/home`；
- 域名 `xs2026.site` 解析正确但 80/443 无人监听，Swagger 接口文档同样公网可达；
- `deploy.sh` 的校验也只覆盖带端口地址，入口层没有独立健康检查。

**根因**
- `deploy/docker-compose.prod.yml` 一律用 `0.0.0.0:<host_port>:<container_port>` 直接发布容器端口；
- 没有任何统一入口/反代，端口即入口；
- 服务器上也没有宿主机 nginx（80/443 空闲）。

**修复**
- 新增 `mini-novel-gateway`（`nginx:1.27-alpine`，配置 `deploy/gateway/nginx.conf` 只读挂载）：对外只发布 80/443，
  `/` → H5、`/admin/`+`/admin-api/`+`/crawler-api/` → 后台、`/healthz` 供探测；
- `app`/`h5`/`admin-ui` 端口改绑 `127.0.0.1`（对外不再可达，保留了本机排查与部署健康检查能力）；
- `deploy.sh` 增加 `GATEWAY_PORT`/`HTTPS_PORT` 与网关健康检查；
- 路由细节与验收见 [`domain-tls-setup-202609.md`](domain-tls-setup-202609.md)。

**预防**
- **CI 写 `.env` 是固定内容**（`deploy.yml` 的 heredoc），新增 `GATEWAY_PORT`/`HTTPS_PORT`/`TLS_CERT_DIR` 这类变量时必须
  同步改 workflow，否则部署后 `.env` 缺项（虽然 compose 有默认值能跑，但会与实际不一致）；
- 网关配置是只读 bind mount：改完先 `docker exec mini-novel-gateway nginx -t`，再 `nginx -s reload` 或重建容器，
  改宿主机文件不会自动生效；
- 同主机还有其它栈占用端口（`video-frontend:8082`、`video-backend:8081`、`rustdesk:21115-21119`），新增对外端口前先 `ss -lntp`；
- 端口收紧后，Swagger 只能走 SSH 隧道（`ssh -L 8080:127.0.0.1:8080 -p 52527 root@<host>`），不要再把它放回公网。

---

## 9. 大请求体返回 413（H5 容器默认 1m 上限）（09-15）

**现象**
- 经网关上传/提交 5MB 请求体得到 `413 Request Entity Too Large`；同一路径 1MB 也被拒；
- 静态页面与普通接口全部正常，只有大 body 受影响。

**根因**
- nginx 默认 `client_max_body_size 1m`；
- 网关与 `admin-ui` 都显式设了 `320m`，**唯独 `mini-novel-h5` 没设** —— 请求经网关进入 H5 容器后被 H5 拒绝，
  而网关的 `320m` 看起来「已配置」，容易误判为网关没生效。

**定位**
- 两层都是 `nginx/1.27.5`，无法从响应头区分来源，改用逐层对比：
  `docker exec mini-novel-h5 nginx -T | grep client_max_body_size`（无输出 = 未设）
  + 直连 `curl --data-binary @/tmp/body.json http://127.0.0.1:5173/api/...`（同样 413 → 确认是 H5 层）。

**修复**
- `mini-novel-h5/nginx.conf` 补 `client_max_body_size 320m`；
- 线上先热更新（`docker cp` 覆盖容器内配置 + `nginx -s reload`）保证即时生效，下一次镜像重建后由仓库配置固化。

**预防**
- 新增任何上传路径时，**三层都要核对**：网关 → 目标前端 nginx → 后端 multipart 限制；
- 验证用 1MB / 2MB / 5MB 三档矩阵，不要只测一个值（早期一次单点复测出现过假阴性）。

---

## 10. 外网 HTTPS 验收被本机 TLS 栈误报为「服务器故障」（09-15）

**现象**
- 启用 HTTPS 后本机 `curl https://xs2026.site/...` 全部返回 `000`（无响应），但同一时刻：
  `http://` 正常返回 301、服务器上用 `curl --resolve ... https://xs2026.site/...` 返回 200。

**根因**
- 本机 curl / .NET 走 **schannel**，沙箱进程拿不到密钥库，报
  `schannel: AcquireCredentialsHandle failed: SEC_E_NO_CREDENTIALS (0x8009030e)`；
- 这不是服务端问题：`openssl s_client -connect xs2026.site:443 -servername xs2026.site` 握手成功
  （TLSv1.3、`CN=xs2026.site`、`Verify return code: 0 (ok)`）。

**修复 / 绕法**
- 用自带 OpenSSL 的客户端做外网验证：`openssl s_client`（`-showcerts` 可数链）、或 Python `ssl`+`urllib`；
  服务器侧用 `curl --resolve xs2026.site:443:127.0.0.1 https://xs2026.site/...` 走完整证书校验。

**预防**
- 出现「本机全 000、服务端自测正常」时，先用 openssl/Python 两条路径交叉验证，确认是本机 TLS 栈问题再动手，
  不要先去改 nginx；
- 本机 git 只编译了 schannel 后端（`git -c http.sslBackend=openssl` 报 `Unsupported SSL backend 'openssl'`），
  附录里原来「改用 openssl 后端」的建议在本机不成立，见下方附录更正。

---

## 11. 推送 GitHub 失败：本机 DNS 污染 + 沙箱凭据/管道限制（09-15）

**现象**
1. `git fetch --dry-run` / `git push` → `fatal: unable to access ... Failed to connect to github.com:443 after 21129 ms`；
2. 加上 DNS 覆盖后变成 `schannel: AcquireCredentialsHandle failed ...`；
3. 凭据助手报 `sh (17728) ... *** fatal error - couldn't create signal pipe, Win32 error 5`。

**根因**
- `github.com` 的本机解析被污染：返回里混入 `198.51.44.x` / `198.51.45.x`（RFC 5737 测试网段）等地址，
  而 `github.com:22`、`ssh.github.com:443`、`api.github.com:443` 均可达 → 说明只是 443 这个域名的解析/连通问题，
  不是整体断网；
- schannel 需要访问用户密钥库、GCM 凭据助手是 shell 脚本（依赖命名管道），两者都被沙箱拦截。

**修复**
```bash
# 用真实 IP 覆盖 DNS 后推送（20.205.243.166 为 github.com 真实地址之一）
git -c http.curloptResolve=github.com:443:20.205.243.166 push origin main
```
- 沙箱内需一次完整访问权限授权（凭据助手要起 `sh`、schannel 要读密钥库）；
- 认证通道：本机没有 GitHub SSH key（`ssh -T git@github.com` → `Permission denied (publickey)`），只能用 HTTPS + GCM。

**验证**
- 直连真实 IP + SNI：TLS 成功、证书 `CN=github.com`、`GET /randy2014/mini-h5.git/info/refs` 返回 200；
- 推送输出 `ca02c5a..3a05c2f  main -> main`；
- 再用 GitHub API 确认 run #209 `conclusion=success`，VPS `.env` 的 `DEPLOY_SHA=3a05c2f…`。

**预防**
- 推送失败先分清是 **DNS** 还是 **认证**，两者的报错和处理完全不同；
- `git push` 的进度写在 stderr，PowerShell 会渲染成红色错误、退出码也可能显示为 1，
  **成功与否只看 `old..new  branch -> branch` 这一行**，不要被红字误导；
- 需要长期稳定时，给 GitHub 配一把 SSH key（22/443 都通）比绕过 DNS 更省事。

---

## 12. `rsync --delete` 会删掉服务器上「仓库里不存在」的文件（已规避，09-15）

**现象/风险**
- CI 用 `rsync -az --delete` 把仓库同步到 `/opt/mini-h5`，属于**镜像式**同步：服务器上任何仓库里没有的文件都会被删除；
- 本次新增的网关配置、证书若只手工放在服务器上，下一次部署就会被删掉（网关会直接起不来）。

**根因**
- 同步策略本身如此，且 `--delete` 不会区分「手工运维文件」与「仓库废弃文件」。

**处理**
- 先 `commit` + `push` 再走部署（本次所有配置与文档都已入库）；
- 密钥材料放**同步目录之外**：证书在 `/opt/mini-h5-certs`，compose 通过 `TLS_CERT_DIR` 变量挂载，
  git 中永远不出现私钥。

**预防**
- 任何「只改服务器、不改仓库」的改动都要回写仓库，否则下次部署即失效；
- 新增手工维护目录时，同步在 `deploy/.env.prod.example` 里登记变量名并写入文档。

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

# 5) 三个内部入口 + 对外网关（80/443）HTTP 200
curl -s -o /dev/null -w 'api=%{http_code}\n' http://127.0.0.1:8080/api/home
curl -s -o /dev/null -w 'h5=%{http_code}\n' http://127.0.0.1:5173/h5/home
curl -s -o /dev/null -w 'admin=%{http_code}\n' http://127.0.0.1:5180/admin/login
curl -s -o /dev/null -w 'gateway-http=%{http_code}\n' http://127.0.0.1/healthz
# 对外 HTTPS（--resolve 走真实证书校验；证书过期/链不全会在这里暴露）
curl -fsS --resolve xs2026.site:443:127.0.0.1 https://xs2026.site/h5/home  -o /dev/null -w 'https-h5=%{http_code}\n'
curl -fsS --resolve xs2026.site:443:127.0.0.1 https://xs2026.site/admin/login -o /dev/null -w 'https-admin=%{http_code}\n'
# 证书有效期与链完整性（应为 2）
openssl x509 -in /opt/mini-h5-certs/fullchain.pem -noout -enddate
grep -c 'BEGIN CERTIFICATE' /opt/mini-h5-certs/fullchain.pem
# 端口收敛检查：对外只应有 80/443
ss -lntp | grep -E '0\.0\.0\.0:(80|443) '

# 6) 前端代理无 502（重点看 nginx error log 是否有旧 upstream IP）
docker logs mini-novel-h5 2>&1 | grep -E '502|Connection refused' | tail -5
docker logs mini-novel-admin-ui 2>&1 | grep -E '502|Connection refused' | tail -5

# 7) 加入频道幂等（对已存在关系重复调用应返回 code=0，而非 500）
LINK=$(docker exec mini-novel-mysql mysql -uroot -p"$MYSQLPW" -N -e \
  "SELECT CONCAT(channel_id,' ',novel_id) FROM mini_novel.subscribe_channel_novel LIMIT 1")
set -- $LINK
curl -s -X POST "http://127.0.0.1:8080/admin/subscribe-channels/$1/novels" \
  -H 'Content-Type: application/json' -d "{\"novelId\":$2,\"operatorId\":1}"
```

---

## 附：本地/环境注意事项（非生产）

- **本机 curl/.NET 走 schannel，沙箱内报 `SEC_E_NO_CREDENTIALS (0x8009030e)`**，HTTPS 请求全 `000`（详见 §10）：
  这不是服务端问题，改用自带 OpenSSL 的客户端验证 —— `openssl s_client -connect host:443 -servername host`、
  Python `ssl`+`urllib`，或在 VPS 上用 `curl --resolve`。
  **更正**：原先建议的 `git -c http.sslBackend=openssl` 在本机无效（该 git 只编译了 schannel，
  报 `Unsupported SSL backend 'openssl'. Supported SSL backends: schannel`），只能从别的 TLS 栈绕。
- **本机 `github.com:443` 的 DNS 被污染**（返回含 `198.51.44.x` 等测试网段），而 `:22`、`api.github.com:443` 正常；
  推送用 `git -c http.curloptResolve=github.com:443:<真实IP> push origin main`（详见 §11）。
  本机没有 GitHub SSH key，HTTPS 是唯一认证通道。
- 沙箱/CI 差异：本机沙箱禁止子进程管道通信（node:test、esbuild、Mockito 自附加均受影响，
  spawn EPERM），此类测试以 GitHub Actions 结果为准；沙箱内先做 `mvn compile` + Vue SFC 语法校验。
  另外复合命令（`where.exe` + 多段管道）也可能整条 `spawn EPERM`，拆成小命令即可。
- **PowerShell 5.1 的坑**（写脚本/做验证时踩过）：
  - 不支持 `<` 重定向（`</dev/null` 报 “`<` operator is reserved”）→ 用 `'' | openssl ...` 或改走 Python；
  - 不支持 `Set-Content -Encoding utf8NoBOM`（提交信息文件）→ 用
    `[System.IO.File]::WriteAllText($p,$s,(New-Object System.Text.UTF8Encoding($false)))`；
  - **管道会重新编码文本/二进制**，因此 `... | openssl sha256` 算出的哈希不能跨机比对
    （曾据此误判证书与私钥公钥指纹不一致）→ 哈希一律在目标机的 shell 管道内计算。
- 服务器对代理出口 IP（如 198.18.0.1）曾出现短暂 SSH 拒绝（疑似云防护/fail2ban），
  属临时封禁，等待自动解除或由管理员 `fail2ban-client set sshd unbanip <ip>` 处理。
