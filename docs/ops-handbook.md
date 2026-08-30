# mini-h5 项目运维处理汇总手册

> 整理时间：2026-08-31 | 覆盖范围：2026-08-24 ~ 2026-08-31 部署与故障处理全过程

---

## 一、项目与部署架构

| 项目 | 内容 |
|------|------|
| 仓库 | https://github.com/randy2014/mini-h5 |
| 技术栈 | Java 17 + Spring Boot 3（多模块 Maven）+ Vue 3 前端，Docker Compose 部署 |
| 服务器 | 64.90.19.6（Ubuntu 24.04，美国），SSH 端口 52527（root） |
| 部署路径 | /opt/mini-h5 |
| 部署方式 | GitHub Actions：push main → 构建测试 → rsync 同步 → 服务器执行 deploy.sh |
| 数据库 | MySQL 8.4（容器 mini-novel-mysql），密码 ******** |
| 缓存 | Redis 7.4（容器 mini-novel-redis） |

### 服务清单（6 个容器）

| 服务 | 容器名 | 端口 | 说明 |
|------|--------|------|------|
| MySQL | mini-novel-mysql | 127.0.0.1:3306（内网） | 数据库，仅本机可连 |
| Redis | mini-novel-redis | 内网 6379 | 缓存 |
| 后端 API | mini-novel-app | **8080** | Spring Boot 主服务 |
| H5 前端 | mini-novel-h5 | **5173** | 用户端 H5 |
| 管理后台 | mini-novel-admin-ui | **5180** | 后台管理（/admin/login） |
| 爬虫服务 | mini-novel-crawler-service | 内网 8090 | 数据采集/清洗 |

### 访问入口

- 管理后台：http://64.90.19.6:5180/admin/login
- H5 前端：http://64.90.19.6:5173/h5/home
- 后端 API：http://64.90.19.6:8080/api/home
- Swagger：http://64.90.19.6:8080/swagger-ui.html

---

## 二、两天处理时间线

### 08-30（第一天）：部署上线 + 基础设施打通

| # | 事项 | 结果 |
|---|------|------|
| 1 | 克隆仓库、分析项目结构（Java 多模块 + Vue） | ✅ |
| 2 | 服务器环境检查（Docker 29.7.2 / Compose v5.5.0） | ✅ |
| 3 | 打包上传代码（231K，排除 .git/node_modules/target） | ✅ |
| 4 | 修复 Windows CRLF 行尾符导致部署脚本失败 | ✅ |
| 5 | 端口绑定 127.0.0.1 → 0.0.0.0（外部可访问） | ✅ |
| 6 | 6 容器全部启动，三入口验证 HTTP 200 | ✅ |
| 7 | MySQL 认证插件修复（2059 错误 → mysql_native_password） | ✅ |
| 8 | 数据迁移受阻：旧服务器 43.161.222.78 密码认证失败 | ⚠️ 未完成 |
| 9 | 后台白屏排查（vite base=/admin/ 与部署路径不匹配） | 定位根因 |
| 10 | **GitHub CI/CD 链路打通**（secrets 更新到新服务器 + 专用 SSH 部署密钥） | ✅ |
| 11 | 管理后台白屏修复（Dockerfile/nginx.conf 子路径部署） | ✅ 已部署 |
| 12 | Maven 源修复（阿里云 → Maven Central，海外服务器 SSL 握手问题） | ✅ 已部署 |
| 13 | nginx 端口重定向修复（port_in_redirect/absolute_redirect off） | ✅ 已部署 |
| 14 | **磁盘 100% 满紧急清理**（爬虫 5 天写满 29G） | ✅ 100% → 36% |
| 15 | 登录页样式排查（实为浏览器缓存，服务端正常） | ✅ |
| 16 | 登录后 API 超时 → 诊断确认服务器僵死 | 需重启 |

### 08-31（第二天）：服务器恢复 + 全面加固

| # | 事项 | 结果 |
|---|------|------|
| 1 | 用户云控制台强制重启，服务器恢复（磁盘 46%） | ✅ |
| 2 | 紧急停止爬虫容器止损 | ✅ |
| 3 | 爬虫调度防复发修复：CRAWLER_SCHEDULE_ENABLED 开关（代码+compose） | ✅ 已部署 |
| 4 | 内容审核 502 修复（爬虫容器重启后代理恢复） | ✅ |
| 5 | 用户确认定时任务不停 → 调度开关改回 true，按计划每晚执行 | ✅ 已部署 |
| 6 | 垃圾数据清理第一轮：构建缓存 2.14G + 历史任务/审核日志（保留 30 天） | ✅ |
| 7 | 正式库体检：悬挂映射清理 2335 条（chapter_source_mapping 等） | ✅ |
| 8 | 后台采集管理两个历史数据清空（任务/清洗入库表） | ✅ |
| 9 | 资源占用全面盘点 | ✅ |
| 10 | **MySQL binlog 永久关闭**（--disable-log-bin）+ 旧日志删除 1.86G | ✅ 已部署 |
| 11 | 构建缓存再清理 2.05G | ✅ |

---

## 三、关键问题与解决方案（详细）

### 1. 管理后台白屏

- **现象**：http://64.90.19.6:5180/ 白屏，JS/CSS 404
- **根因**：admin-ui 的 vite 配置 `base: '/admin/'`，页面引用 `/admin/assets/...`，但 Dockerfile 把构建产物放在 nginx 根路径 → 资源全部 404 回退成 HTML
- **修复**（GitHub 提交）：
  - `mini-novel-admin-ui/Dockerfile`：dist 拷贝到 `/usr/share/nginx/html/admin`
  - `mini-novel-admin-ui/nginx.conf`：根路径 302 → `/admin/`，SPA 回退指向 admin，重定向保留外部端口
- **验证**：/admin/login → 200，JS 资源 995KB 正常加载

### 2. 磁盘写满导致服务器僵死（两次）

- **现象**：登录后台后 API 超时 → SSH/HTTP 全部无响应 → 必须云控制台强制重启
- **根因**：爬虫容器持续运行，每 60 秒轮询调度持续抓取写库（5 天写满 29G），binlog 增长 5G+，暂存表 1.4G
- **处理**：
  1. 停爬虫容器 + 清 binlog + 清暂存表（100% → 36%）
  2. 重启后再次被爬虫拖垮 → 云控制台强制重启
  3. **根治**：爬虫调度总开关 `CRAWLER_SCHEDULE_ENABLED`（compose 控制），改为按 crawl_schedule 定时（每天 02:00/04:00）执行
- **教训**：定时采集频率必须可控，任务积压要及时清理

### 3. 内容审核 502

- **现象**：后台-内容审核列表 502
- **根因**：审核接口走 crawler-api（nginx → 爬虫容器 8090），爬虫容器被停导致代理失败
- **修复**：CI/CD 部署重建爬虫容器（调度关闭状态），接口恢复 401（需登录态属正常）

### 4. MySQL 认证插件问题（Navicat 2059）

- **现象**：`Authentication plugin 'caching_sha2_password' cannot be loaded`
- **修复**：`ALTER USER root IDENTIFIED WITH mysql_native_password`（三个 host 全部修改）

### 5. Maven 构建失败

- **现象**：CI/CD 部署时服务器端 Docker 构建失败，SSL 握手中断
- **根因**：Dockerfile 强制走阿里云 Maven 镜像，美国服务器访问不稳定
- **修复**：移除阿里云镜像配置，改用 Maven 中央仓库

### 6. MySQL binlog 关闭

- **修改**：compose 中 MySQL command 增加 `--disable-log-bin`（commit 9e7727a8）
- **验证**：log_bin=OFF，旧 binlog 文件（1.86G）已删除
- **影响**：MySQL 不再产生 binlog，彻底消除日志占盘风险（单机无主从，无影响）

---

## 四、当前系统状态（2026-08-31 07:33）

| 指标 | 状态 |
|------|------|
| 磁盘 | **35%**（9.9G/29G，可用 20G） |
| 内存 | 3.8G，可用 1.1G，正常 |
| CPU | 负载 0.08，健康 |
| 容器 | 6 个 mini-h5 全部 Up，MySQL/Redis healthy |
| 后台/API/H5 | 全部 HTTP 200 |
| binlog | OFF（不再增长） |
| 定时任务 | 每天 02:00（审核源）+ 04:00（23qb 采集）自动执行 |
| 正式数据 | novel 3735 本、chapter 73643 章 |

---

## 五、常用操作手册

### 1. 常规部署（代码变更上线）
```bash
# 修改代码 → 提交推送 GitHub main → 流水线自动部署
git add -A && git commit -m "描述" && git push origin main
# 注意：本机 github.com:443 可能被阻断，走 API 推送（见下）
```

### 2. 通过 API 推送（本机 github.com 不通时）
```python
# 用 GitHub REST API 提交文件（需 PAT）
# 流程：GET ref → GET commit → POST blobs → POST trees → POST commits → PATCH ref
```

### 3. 服务器紧急排查
```bash
# SSH 连接（本机无 sshpass，用 paramiko）
ssh -p 52527 root@64.90.19.6   # 密码 ********

# 磁盘检查
df -h /
docker system df   # 镜像/缓存/卷占用

# 清理构建缓存（每次部署后执行）
docker builder prune -f

# 容器状态
docker ps -a
docker logs --tail 100 mini-novel-app

# MySQL 操作（容器内）
docker exec -it mini-novel-mysql mysql -uroot -p******** mini_novel
```

### 4. 爬虫调度控制
```yaml
# deploy/docker-compose.prod.yml 中 crawler-service 环境变量：
CRAWLER_SCHEDULE_ENABLED: "true"   # true=自动调度 / false=暂停
# 修改后推送 GitHub 触发部署生效
```
- 调度配置在数据库 `mini_novel_crawler.crawl_schedule` 表（时间、源、启用状态）
- 采集源配置在 `crawl_source` 表（source_code、source_type、enabled）

### 5. 数据库信息
- 业务库：`mini_novel`（novel/chapter/category/app_user 等）
- 爬虫库：`mini_novel_crawler`（crawl_source/schedule/task_v2/merge_task 等）
- 连接方式：Navicat SSH 隧道（主机 64.90.19.6:52527 root）+ 常规 127.0.0.1:3306 root/********

---

## 六、遗留事项与风险

| 事项 | 说明 | 状态 |
|------|------|------|
| 桌面 mini_novel.sql（147M）导入 | 旧服务器备份（app_user/category/chapter 三表），导入会替换现有 chapter | ⏳ 待用户确认 |
| 重复章节数据 | 5 本小说（novel 132/130/256/50/47）225 条重复章节，内容版本不同 | ⏳ 待确认保留策略 |
| GitHub PAT 清理 | 临时生成的 PAT 已多次使用，建议用完删除 | ⚠️ 待办 |
| 爬虫数据持续增长 | 每天定时抓取仍会新增数据，暂存表需关注 | 低风险（binlog 已关） |
| 构建缓存 | 每次 CI/CD 部署新增 ~2G | 低风险（部署后清理） |

---

## 七、重要经验教训

1. **磁盘是最大的风险源**：爬虫持续写库 + binlog 增长是两次服务器僵死的根因，已通过调度开关 + 关闭 binlog 根治
2. **一切修复走 CI/CD**：用户明确要求不允许直接改服务器容器/文件，GitHub 提交 → 流水线部署是唯一变更途径（紧急运维除外）
3. **本机网络特殊性**：github.com:443 间歇阻断，api.github.com 可用，git 推送失败时用 REST API 兜底
4. **部署后必查**：磁盘、容器状态、三入口 HTTP、构建缓存清理
5. **数据库操作先备份**：所有删除操作前先 mysqldump 到 /opt/backup/
