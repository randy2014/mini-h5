# 域名与入口配置记录（xs2026.site · 免端口访问 + HTTPS）

> 记录时间：2026-09-15 ｜ 类型：配置记录（现役）
> 持续有效的事实（主机、SSH 端口、容器清单）以 [`../DEPLOYMENT.md`](../DEPLOYMENT.md) 为准，本文件记录本次配置的
> 目的、过程、当时的验收结果与回滚方式。
> 相关文档：[`../DEPLOYMENT.md`](../DEPLOYMENT.md)（TLS Certificate 章节）· [`../deploy/README.md`](../deploy/README.md) · [`ops-handbook.md`](ops-handbook.md)

---

## 一、访问地址（正式）

域名 `xs2026.site` → `64.90.19.6`，对外只开放 **80 / 443**，**所有地址都不带端口**；`http://` 一律 301 跳转到
`https://xs2026.site`（保留原路径与查询串）。

| 用途 | 地址 | 说明 |
|---|---|---|
| H5 阅读端 | `https://xs2026.site/` | 根路径自动跳转到 `/h5/home` |
| H5 首页 | `https://xs2026.site/h5/home` | 用户端主入口 |
| 管理后台 | `https://xs2026.site/admin/login` | 后台登录页（静态资源在 `/admin/assets/`） |
| 后端 API | `https://xs2026.site/api/home` | 由 H5 容器内部反代到 `mini-novel-app:8080` |
| 封面代理 | `https://xs2026.site/api/cover/{id}` | 由 H5 容器内部反代并改写外链 |
| 备选域名 | `https://www.xs2026.site/h5/home` | 证书 SAN 同时覆盖 `www` |
| 网关健康检查 | `https://xs2026.site/healthz` | 返回 `ok`，供部署脚本与监控探测 |
| 接口文档 Swagger | `http://127.0.0.1:8080/swagger-ui.html` | **仅内网**，见下 |

内网入口（只绑 `127.0.0.1`，需 SSH 隧道）：

```bash
# Swagger / 后端直连
ssh -L 8080:127.0.0.1:8080 -p 52527 root@64.90.19.6
# 然后本地浏览器打开 http://localhost:8080/swagger-ui.html
```

```text
127.0.0.1:8080  后端 mini-novel-app（Swagger、接口直连）
127.0.0.1:5173  H5 容器（网关的上游）
127.0.0.1:5180  后台容器（网关的上游）
127.0.0.1:3306  MySQL（同 DEPLOYMENT.md：GUI 工具走 SSH 隧道）
内网     6379  Redis       内网 8090  爬虫服务
```

---

## 二、入口架构

```text
                       ┌────────────────────────────────────────────┐
 浏览器 / 小程序  ──►  │  mini-novel-gateway (nginx:1.27-alpine)    │
   80 → 301 https      │  80 : /healthz + 其余 301 → https://xs2026.site
   443 (TLS 终止)      │  443: TLS1.2/1.3 + HTTP/2 + HSTS            │
                       └───┬──────────────────┬─────────────────────┘
                     "/"   │                  │  "/admin/", "/admin-api/",
                           ▼                  ▼  "/crawler-api/"
              ┌────────────────────┐  ┌──────────────────────────┐
              │ mini-novel-h5 :80  │  │ mini-novel-admin-ui :80  │
              │ 静态站 + SPA 回退    │  │ 静态站 + SPA 回退          │
              │ /api/ /cover/ 反代  │  │ /admin-api/ /crawler-api/ │
              └─────────┬──────────┘  └───────┬──────────┬───────┘
                        ▼                     ▼          ▼
              mini-novel-app:8080   mini-novel-app:8080  mini-novel-crawler-service:8090
```

要点：

1. **端口隐藏**：`app` / `h5` / `admin-ui` 在 `deploy/docker-compose.prod.yml` 中一律绑定 `127.0.0.1`，公网无法直连；
   外部流量只能经网关的 80/443 进入。
2. **HTTPS 终止在网关**：容器内路径 `/etc/nginx/certs/{fullchain.pem,privkey.pem}`，由宿主机 `/opt/mini-h5-certs`
   只读挂载。
3. **上游自动重解析**：网关用 `resolver 127.0.0.11 valid=10s` + 变量 `proxy_pass`，容器重建换 IP 后 10 秒内自愈，
   不会复现 2026-09-05 那次「后端重建 → 前端 502」；`h5` / `admin-ui` 自身仍是启动时解析，所以 `deploy.sh`
   保留了对它们的 restart。
4. **跳转固定到主域名**：HTTP 跳转目标写死 `https://xs2026.site`，用 IP 访问也会落到主域名，避免 `https://<ip>`
   触发证书告警。

---

## 三、本次变更清单

### 3.1 仓库改动

| 文件 | 作用 |
|---|---|
| `deploy/gateway/nginx.conf` | **新增**：统一入口网关配置（80 跳转 + 443 TLS 业务路由 + `/healthz`） |
| `deploy/docker-compose.prod.yml` | 新增 `mini-novel-gateway` 服务（发布 80/443、挂载配置与证书）；`app`/`h5`/`admin-ui` 端口改绑 `127.0.0.1` |
| `deploy/deploy.sh` | 新增 `GATEWAY_PORT`/`HTTPS_PORT`；健康检查加入 `http://127.0.0.1:80/healthz` 与 HTTPS（`curl --resolve`，证书过期/链不全会让部署失败） |
| `deploy/.env.prod.example` | 新增 `GATEWAY_PORT` / `HTTPS_PORT` / `TLS_CERT_DIR` |
| `.github/workflows/deploy.yml` | 部署时写入的 `.env` 同步加入 `GATEWAY_PORT` / `HTTPS_PORT` / `TLS_CERT_DIR` |
| `mini-novel-h5/nginx.conf` | 补 `client_max_body_size 320m`（容器默认 1m，会导致大请求 413） |
| `DEPLOYMENT.md` | 新增 **TLS Certificate** 章节；生产入口、compose 服务清单、部署校验项更新为 80/443 |
| `README.md`、`deploy/README.md`、`PROJECT_SUMMARY.md`、`PROJECT_CONTEXT.md`、`docs/ops-handbook.md` | 访问地址统一为 https，端口与拓扑表更新 |
| `docs/domain-tls-setup-202609.md` | 本文件 |

### 3.2 服务器侧操作（按顺序）

1. 备份原配置到 `/root/mini-h5-backup/`（`docker-compose.prod.yml`、`deploy.sh`、`.env`）。
2. 新建网关容器：`docker compose up -d --no-build mini-novel-gateway`。
3. 重建 `mini-novel-app` → 等健康检查通过 → 重建 `mini-novel-h5`、`mini-novel-admin-ui`（改绑 `127.0.0.1`），
   顺序保证前端 nginx 重新解析到后端新 IP。
4. 热更新 H5 容器请求体上限（无需重建镜像）：
   `docker cp` 覆盖 `/etc/nginx/conf.d/default.conf` 后 `docker exec mini-novel-h5 nginx -s reload`。
5. 建目录 `/opt/mini-h5-certs`（700），上传 `fullchain.pem`（644）与 `privkey.pem`（600），哈希比对一致。
6. 网关加 443 与证书挂载后重建：`docker compose up -d --no-build mini-novel-gateway`。
7. 外网验收（见第五节）。

> 提醒：CI 走 `rsync --delete`，**改动必须先 commit + push**，否则仓库里不存在的服务器文件会在下次部署被删除。

---

## 四、HTTPS 证书

### 4.1 证书信息

| 项 | 值 |
|---|---|
| 颁发者 | DigiCert Inc — `Encryption Everywhere DV TLS CA - G2` |
| 主题 | `CN=xs2026.site` |
| SAN | `xs2026.site`、`www.xs2026.site` |
| 类型 | DV（域名验证）单域名 + www |
| 密钥 | RSA 2048 |
| 序列号 | `0B944C9EBBB14174AA993389992AB5B9` |
| 有效期 | **2026-09-15 00:00:00 GMT → 2026-12-14 23:59:59 GMT**（约 90 天） |
| 链 | 叶子 → `Encryption Everywhere DV TLS CA - G2` → 根 `DigiCert Global Root G2` |
| fullchain.pem SHA256 | `4c86efaff9b551dd245a51f1376159365b0987b21c6e5131f845ef128d8bd108` |
| 公钥 SHA256（证书↔私钥匹配依据） | `d683d7e7fa085cc2157209a0ab2aef0e359519993b2a7d90dba8a16d060ed116`（DER/SPKI；证书侧与私钥侧一致即为匹配） |

证书包来源：CA 控制台下载的 `xs2026.site.crt`（= 完整链，含中间证书）、`xs2026.site.key`（未加密 PKCS#1）、
`xs2026.site.pem`（与 `.crt` 内容相同）。三者已核对：`.crt` 与 `.pem` 哈希一致，`.key` 与证书的公钥指纹一致。

在 VPS 上复算匹配关系（两侧输出必须相同；注意要用 shell 管道，PowerShell 管道会改变字节）：

```bash
echo -n "cert: "; openssl x509 -in /opt/mini-h5-certs/fullchain.pem -noout -pubkey | openssl pkey -pubin -outform DER | openssl sha256 | awk '{print $2}'
echo -n "key : "; openssl pkey -in /opt/mini-h5-certs/privkey.pem -pubout -outform DER | openssl sha256 | awk '{print $2}'
```

### 4.2 服务器上的落地位置

```text
/opt/mini-h5-certs/                700 root:root   ← 在 rsync 同步目录之外，部署不会覆盖
├── fullchain.pem                  644 root:root   站点证书 + 中间证书
└── privkey.pem                    600 root:root   未加密私钥
```

容器内挂载点：`/etc/nginx/certs`（只读）。

> 为什么放同步目录之外：CI 用 `rsync --delete` 同步仓库到 `/opt/mini-h5`，任何仓库里不存在的文件都会被删；
> 证书属于密钥材料，本就不该进仓库。

### 4.3 续期步骤（2026-12-14 前务必执行）

```bash
# 1) 覆盖两个文件（保持 PEM 文本、顺序为 叶子 → 中间证书；私钥必须无 passphrase）
scp xs2026.site.crt root@64.90.19.6:/opt/mini-h5-certs/fullchain.pem
scp xs2026.site.key root@64.90.19.6:/opt/mini-h5-certs/privkey.pem
ssh root@64.90.19.6 'chmod 644 /opt/mini-h5-certs/fullchain.pem && chmod 600 /opt/mini-h5-certs/privkey.pem'

# 2) 让网关加载新证书
cd /opt/mini-h5 && docker compose -f deploy/docker-compose.prod.yml --env-file .env restart mini-novel-gateway

# 3) 验证（不带 -k，真实校验证书链与域名）
curl -fsS --resolve xs2026.site:443:127.0.0.1 https://xs2026.site/h5/home -o /dev/null -w '%{http_code}\n'
curl -fsS --resolve xs2026.site:443:127.0.0.1 https://xs2026.site/admin/login -o /dev/null -w '%{http_code}\n'

# 4) 确认链完整（应为 2）与到期时间
grep -c 'BEGIN CERTIFICATE' /opt/mini-h5-certs/fullchain.pem
docker run --rm -v /opt/mini-h5-certs:/c:ro nginx:1.27-alpine true  # 也可用宿主机 openssl x509 -enddate
```

---

## 五、验收结果（2026-09-15 外网实测）

| 检查项 | 结果 |
|---|---|
| `https://xs2026.site/` | 200 `text/html` |
| `https://xs2026.site/h5/home` | 200 `text/html` |
| `https://xs2026.site/admin/login` | 200 `text/html` |
| `https://xs2026.site/api/home` | 200 `application/json` |
| `https://xs2026.site/api/cover/1` | 200 `image/svg+xml` |
| `https://xs2026.site/healthz` | 200 `text/plain` |
| `https://www.xs2026.site/h5/home` | 200（连续 5 次全通过） |
| 静态资源 | `/assets/index-C0iVFCtl.js`、`/assets/index-Clnp5EtU.css`、`/admin/assets/index-AJDS5vgD.js`、`/admin/assets/index-h3S0zpCk.css` 全部 200 |
| 登录 POST 透传 | `POST /api/auth/login` 正常到达后端并返回业务 JSON |
| 大请求体 | 5MB POST 通过（修复前被 H5 容器 1m 上限返回 413） |
| HTTP 跳转 | `/`、`/admin/login`、`/h5/home?from=probe`、`www`、`http://64.90.19.6/...` 均 301 → `https://xs2026.site/<原路径>` |
| 证书校验 | 严格校验通过（`ssl_verify_result=0`，Python/OpenSSL 链校验通过，`Verify return code: 0 (ok)`） |
| 协议 | TLSv1.3（`TLS_AES_256_GCM_SHA384`），网关支持 HTTP/2 |
| 下发证书链 | 2 张（叶子 + 中间证书），避免安卓/微信 WebView 报「证书链不完整」 |
| 安全头 | `Strict-Transport-Security: max-age=31536000`（未开 includeSubDomains / preload） |
| 稳定性 | 连续 12 次 HTTPS 请求 12/12 成功；服务器日志对应请求均为 200 |
| 端口收敛 | 公网仅 `0.0.0.0:80`、`0.0.0.0:443`；`5173/5180/8080` 只在 `127.0.0.1` 监听；外网 `5173/5180/8080` 连接全部失败 |

---

## 六、回滚

```bash
# 备份（本次操作前）
/root/mini-h5-backup/docker-compose.prod.yml.bak-20260915222955
/root/mini-h5-backup/deploy.sh.bak
/root/mini-h5-backup/.env.bak
```

- **只回滚端口/网关**：把备份的 `docker-compose.prod.yml` 拷回 `deploy/`，执行
  `docker compose -f deploy/docker-compose.prod.yml --env-file .env up -d --no-build`，即恢复成
  `5173/5180/8080` 直接对外、无网关容器；此时 80/443 不再监听。
- **停掉网关但保留新端口绑定**：`docker compose stop mini-novel-gateway`（前端会因只绑 `127.0.0.1` 而无法从公网访问，
  属预期）。
- **证书回滚**：`/opt/mini-h5-certs/` 直接覆盖旧文件 + `restart mini-novel-gateway`。

## 七、常见问题排查

| 现象 | 原因与处理 |
|---|---|
| `502 Bad Gateway` | 上游容器被重建后 IP 变化。网关会在 10 秒内自愈；`h5`/`admin-ui` 内部 nginx 需 `docker compose restart mini-novel-h5 mini-novel-admin-ui`（`deploy.sh` 已自动执行） |
| 浏览器提示「证书链不完整」/ 部分 App 打不开 | `fullchain.pem` 缺中间证书，`grep -c 'BEGIN CERTIFICATE'` 应为 2 |
| 上传大文件返回 `413` | 三处限制都要够大：网关（320m）、`admin-ui`（320m）、`h5`（320m，本次补上）。改完 `nginx -s reload` 或重建容器 |
| 改了 `deploy/gateway/nginx.conf` 不生效 | 该文件是只读 bind mount：`docker exec mini-novel-gateway nginx -t && docker exec mini-novel-gateway nginx -s reload`，或重建网关容器 |
| HTTP 访问没有跳转 / 跳转后域名不对 | 跳转写死 `https://xs2026.site`；若域名更换需同步修改 `deploy/gateway/nginx.conf` 的 `server_name` 与 301 目标 |
| 用 IP 打开有证书告警 | 请用域名访问；HTTP 访问 IP 也会被 301 到主域名 |
| 网关起不来 | `docker logs mini-novel-gateway`；多为证书文件缺失/权限不足/私钥带 passphrase。可先语法预检：<br>`docker run --rm -v /opt/mini-h5/deploy/gateway/nginx.conf:/etc/nginx/conf.d/default.conf:ro -v /opt/mini-h5-certs:/etc/nginx/certs:ro nginx:1.27-alpine nginx -t` |

## 八、遗留与后续

1. **证书 2026-12-14 到期**：建议提前一周续期并按 §4.3 操作；`deploy.sh` 的 HTTPS 检查会在链/证书异常时让部署失败。
2. **私钥安全**：私钥只存在于 `/opt/mini-h5-certs/privkey.pem`（600）与本地证书包中；证书包的压缩包口令曾在聊天中传递过，
   建议删除本地 `xs2026.site_*.zip`、不复用该口令。私钥泄露需立即重签。
3. **未做**：CDN/WAF、OCSP Stapling、`includeSubDomains` 版 HSTS、IPv6 对外（当前仅 IPv4）、Swagger 的公网暴露
   （仍保持内网）。
4. **其余项目**：同主机上的 `video-frontend:8082`、`video-backend:8081`、`rustdesk:21115-21119` 等其它栈未纳入本次网关，
   仍按原方式暴露。
