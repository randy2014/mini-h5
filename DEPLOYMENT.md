# Deployment

> Status: current | Last tidy-up: 2026-09 | Documentation map: [`docs/README.md`](docs/README.md)
> This file is the authority for host, port and deployment facts. Incidents: [`docs/incident-log-202609.md`](docs/incident-log-202609.md).

## Deployment Principle

Application changes must go through GitHub Actions. The normal production path is:

```text
local code change -> git commit -> git push main -> GitHub Actions -> VPS deploy script -> Docker Compose restart/build
```

Do not use the VPS as the normal place to build application code manually. Manual VPS commands are only for operations such as logs, database checks, disk cleanup, scheduler toggles, and emergency recovery.

## Production Host

- Domain: `xs2026.site`

- Host: `64.90.19.6`
- SSH port: `52527`
- Deploy path: `/opt/mini-h5`
- Public entry (only ports `80`/`443`, no port in the URL): `mini-novel-gateway`
  - H5: `https://xs2026.site/h5/home` (root `/` redirects there)
  - Admin: `https://xs2026.site/admin/login`
  - API: `https://xs2026.site/api/home`
  - Health: `https://xs2026.site/healthz`
  - `http://` requests are answered with `301` to `https://xs2026.site` (same path preserved)
- Internal only (bound to `127.0.0.1`, reachable through an SSH tunnel):
  - Backend API: `http://127.0.0.1:8080/api/home`
  - API docs: `http://127.0.0.1:8080/swagger-ui.html` (`ssh -L 8080:127.0.0.1:8080 -p 52527 root@64.90.19.6`)
  - H5 container: `http://127.0.0.1:5173` · Admin container: `http://127.0.0.1:5180`

The earlier host `43.161.222.78:2222` is the old server and is no longer used. Production memory was upgraded to
7.9G after the 2026-09-01 OOM incident.

Secrets and passwords must stay in GitHub Secrets or local environment files. Do not commit them.

## TLS Certificate

TLS is terminated by `mini-novel-gateway`. The certificate lives on the VPS at `/opt/mini-h5-certs/`, which is
**outside** the rsync target so `rsync --delete` can never remove it, and it is never committed to the repository:

```text
/opt/mini-h5-certs/fullchain.pem   644  站点证书 + 中间证书（链必须完整）
/opt/mini-h5-certs/privkey.pem     600  未加密私钥（带 passphrase 的私钥 nginx 无法启动）
```

- Current certificate: DigiCert *Encryption Everywhere DV*, `CN=xs2026.site`, SAN = `xs2026.site`, `www.xs2026.site`,
  RSA 2048, **valid 2026-09-15 → 2026-12-14** (~90 days). Renew before it expires.
- Renewal: overwrite the two files (keep them PEM, keep the chain order leaf → intermediate) and run
  `cd /opt/mini-h5 && docker compose -f deploy/docker-compose.prod.yml --env-file .env restart mini-novel-gateway`.
- Verify from the VPS (no `-k`, so the chain and hostname are really validated):

  ```bash
  curl -fsS --resolve xs2026.site:443:127.0.0.1 https://xs2026.site/h5/home -o /dev/null -w '%{http_code}\n'
  curl -fsS --resolve xs2026.site:443:127.0.0.1 https://xs2026.site/admin/login -o /dev/null -w '%{http_code}\n'
  ```

- `deploy.sh` performs the same HTTPS checks after every deployment, so an expired or incomplete chain fails the
  deploy instead of silently serving a broken site.
- Never publish the private key: do not paste it into chat, tickets, or the repository. If it leaks, reissue the
  certificate immediately.

## GitHub Actions Secrets

The workflow `.github/workflows/deploy.yml` expects these repository secrets:

```text
VPS_HOST
VPS_PORT
VPS_USER
VPS_SSH_KEY
VPS_DEPLOY_PATH
MYSQL_ROOT_PASSWORD
```

Expected values shape:

- `VPS_HOST`: VPS public IP.
- `VPS_PORT`: `52527` (current host).
- `VPS_USER`: usually `root`.
- `VPS_SSH_KEY`: private key content for deployment.
- `VPS_DEPLOY_PATH`: `/opt/mini-h5`.
- `MYSQL_ROOT_PASSWORD`: production MySQL root password.

## What the Workflow Does

1. Checks out the repository.
2. Runs crawler service tests (`ContentReviewControllerTest`).
3. Builds the admin UI (`npm install`, `npm test`, `npm run build`).
4. Verifies deploy secrets; deployment is skipped when they are missing.
5. Prepares SSH key and `known_hosts`.
6. Ensures deploy directory exists.
7. Uses `rsync --delete` to sync repository files to the VPS.
8. Writes production `.env` on the VPS.
9. Runs `deploy/deploy.sh` and records the deployed commit as `DEPLOY_SHA` in `.env`.

The workflow excludes `.git`, `.github`, `.env`, `target`, `node_modules`, and frontend `dist` artifacts from rsync.

## Production Compose

Production compose file:

```text
deploy/docker-compose.prod.yml
```

Services:

- `mini-novel-mysql`: MySQL 8.4, host-bound to `127.0.0.1:${MYSQL_HOST_PORT:-3306}`, started with
  `--mysql-native-password=ON --disable-log-bin`.
- `mini-novel-redis`: Redis 7.4.
- `mini-novel-app`: Spring Boot backend, bound to `127.0.0.1:${APP_PORT:-8080}` (internal only), volumes
  `mini-novel-cover-cache:/var/cache/covers` and `mini-novel-media:/data/media`.
- `mini-novel-h5`: H5 frontend, bound to `127.0.0.1:${H5_PORT:-5173}` (internal only).
- `mini-novel-crawler-service`: crawler runtime, internal port `8090`, not publicly published, gated by
  `CRAWLER_SCHEDULE_ENABLED`.
- `mini-novel-admin-ui`: admin frontend, bound to `127.0.0.1:${ADMIN_PORT:-5180}` (internal only).
- `mini-novel-gateway`: `nginx:1.27-alpine` entry point published on `0.0.0.0:${GATEWAY_PORT:-80}` and
  `0.0.0.0:${HTTPS_PORT:-443}`; config is `deploy/gateway/nginx.conf` (read-only) plus the certificate directory
  `TLS_CERT_DIR` (default `/opt/mini-h5-certs`) mounted read-only at `/etc/nginx/certs`. Port `80` only answers `/healthz`
  and 301-redirects everything else to `https://xs2026.site`; port `443` serves H5 on `/`, and `/admin/`, `/admin-api/`,
  `/crawler-api/` on the admin UI. It uses Docker DNS (`resolver 127.0.0.11`) with variable `proxy_pass`, so it
  re-resolves upstream IPs after container recreation instead of caching a stale IP.

## Deploy Script

Script:

```text
deploy/deploy.sh
```

It performs:

1. Start MySQL and Redis.
2. Wait for MySQL health.
3. Apply the single schema script `sql/schema.sql`. It is idempotent and safe to re-run on every deploy;
   it creates both databases, brings every table to the current shape, seeds required configuration and
   re-applies the necessary idempotent data corrections. A failure aborts the deploy (the script runs under
   `set -e`).
4. Build and start application services **serially** (`COMPOSE_PARALLEL_LIMIT=1 compose up -d --build`) to avoid
   concurrent multi-image builds exhausting VPS memory.
5. Restart `mini-novel-h5` and `mini-novel-admin-ui` so nginx re-resolves the backend container's new IP
   (otherwise the frontends return 502 after a backend rebuild). The gateway does not need a restart: its
   `resolver` + variable `proxy_pass` re-resolves upstreams on its own.
6. Verify:
   - `http://127.0.0.1:${APP_PORT}/api/home`
   - `http://127.0.0.1:${H5_PORT}/h5/home`
   - `http://127.0.0.1:${ADMIN_PORT}/admin/login`
   - `http://127.0.0.1:${GATEWAY_PORT}/healthz`
   - `https://xs2026.site/h5/home` and `https://xs2026.site/admin/login` (with `--resolve`, certificate validated)

## Schema Changes

`sql/schema.sql` is the only database script:

```text
§1 databases + cleanup of dropped tables   §2 current table structures
§3 seeded configuration + idempotent data corrections
§4 change area for new statements          §5 read-only structure self-check
```

To change the schema:

1. Add the statement to §4, guarded like the examples in that section
   (`information_schema` check → `PREPARE` → `EXECUTE` → `DEALLOCATE PREPARE`).
2. Keep §2's `CREATE TABLE` definition in sync, so fresh databases are created correctly.
3. Run the file twice against a test database and confirm the second run reports no error and changes nothing.

Do **not** add numbered migration files again: 31 files under `sql/migrations/` were merged into this single
script in 2026-09 precisely to remove the "which migration ran last?" problem, and `deploy.sh` re-runs the whole
script on every deployment.

## Manual Runtime Checks

From the VPS:

```bash
cd /opt/mini-h5
docker compose -f deploy/docker-compose.prod.yml --env-file .env ps
docker logs --tail 100 mini-novel-app
docker logs --tail 100 mini-novel-crawler-service
docker logs --tail 100 mini-novel-mysql
```

Check deployed SHA:

```bash
cat /opt/mini-h5/.env | grep DEPLOY_SHA
```

Check disk:

```bash
df -h /
docker system df
docker exec mini-novel-mysql sh -lc 'du -h -d 1 /var/lib/mysql | sort -h'
```

## MySQL Access

MySQL is not exposed publicly. Use an SSH tunnel:

```text
SSH host: 64.90.19.6
SSH port: 52527
SSH user: root
DB host through tunnel: 127.0.0.1
DB port through tunnel: 3306
DB user: root
```

For Navicat, configure the SSH tab first, then set the MySQL host to `127.0.0.1`.

## Crawler Manual Trigger

The crawler service is internal only. Trigger it from inside the VPS/container:

```bash
docker exec mini-novel-crawler-service sh -lc \
  'curl -s -X POST http://127.0.0.1:8090/crawler/config/schedules/13/run-now'
```

Check task status:

```sql
SELECT id,status,trigger_type,total_count,success_count,fail_count,started_at,finished_at,updated_at
FROM mini_novel_crawler.crawl_task_v2
ORDER BY id DESC
LIMIT 10;
```

## Emergency Disk Cleanup

The largest temporary growth comes from crawler raw tables, Docker build cache, and (since 2026-09) the media
volume `mini-novel-media`. MySQL binlog no longer grows because binary logging is disabled.

Check where the space is:

```bash
df -h /
docker system df
docker exec mini-novel-mysql sh -lc 'du -h -d 1 /var/lib/mysql | sort -h'
docker exec mini-novel-app sh -lc 'du -h -d 2 /data/media | sort -h'
```

Before cleanup, confirm no active crawler tasks:

```sql
SELECT status, COUNT(*)
FROM mini_novel_crawler.crawl_task_v2
WHERE status IN ('PENDING','RUNNING')
GROUP BY status;

SELECT status, COUNT(*)
FROM mini_novel_crawler.crawl_merge_task
WHERE status IN ('PENDING','MERGING')
GROUP BY status;
```

Raw staging cleanup is acceptable at the current stage (binary logging is off, so `sql_log_bin` is not needed):

```sql
TRUNCATE TABLE mini_novel_crawler.crawl_content_raw;
TRUNCATE TABLE mini_novel_crawler.crawl_chapter_raw;
TRUNCATE TABLE mini_novel_crawler.crawl_book_raw;
TRUNCATE TABLE mini_novel_crawler.crawl_merge_item;
```

Docker build cache grows by roughly 2G per deploy; prune it after deploying:

```bash
docker builder prune -f
```

Media files are removed together with their posts (draft delete / asset removal), and orphan assets, tmp files and
stuck PROCESSING tasks are reclaimed daily by `MediaCleanupJob` (cron `0 30 3 * * ?`). If the media volume still
grows unexpectedly, check for orphan files and large videos before deleting anything by hand.

MySQL binary logging is permanently disabled (`--disable-log-bin` in the prod compose file); do not re-enable it
unless replication is introduced, and there is no binlog retention to tune any more.
