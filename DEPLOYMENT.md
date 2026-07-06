# Deployment

## Deployment Principle

Application changes must go through GitHub Actions. The normal production path is:

```text
local code change -> git commit -> git push main -> GitHub Actions -> VPS deploy script -> Docker Compose restart/build
```

Do not use the VPS as the normal place to build application code manually. Manual VPS commands are only for operations such as logs, database checks, disk cleanup, scheduler toggles, and emergency recovery.

## Production Host

- Domain: `xs2026.site`

- Host: `43.161.222.78`
- SSH port: `2222`
- Deploy path: `/opt/mini-h5`
- H5: `http://43.161.222.78:5173/h5/home`
- Admin: `http://43.161.222.78:5180/`
- API: `http://43.161.222.78:8080`

Secrets and passwords must stay in GitHub Secrets or local environment files. Do not commit them.

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
- `VPS_PORT`: `2222`.
- `VPS_USER`: usually `root`.
- `VPS_SSH_KEY`: private key content for deployment.
- `VPS_DEPLOY_PATH`: `/opt/mini-h5`.
- `MYSQL_ROOT_PASSWORD`: production MySQL root password.

## What the Workflow Does

1. Checks out the repository.
2. Verifies deploy secrets.
3. Prepares SSH key and `known_hosts`.
4. Ensures deploy directory exists.
5. Uses `rsync --delete` to sync repository files to the VPS.
6. Writes production `.env` on the VPS.
7. Runs `deploy/deploy.sh`.

The workflow excludes `.git`, `.github`, `.env`, `target`, `node_modules`, and frontend `dist` artifacts from rsync.

## Production Compose

Production compose file:

```text
deploy/docker-compose.prod.yml
```

Services:

- `mini-novel-mysql`: MySQL 8.4, host-bound to `127.0.0.1:${MYSQL_HOST_PORT:-3306}`.
- `mini-novel-redis`: Redis 7.4.
- `mini-novel-app`: Spring Boot backend, published on `${APP_PORT:-8080}`.
- `mini-novel-h5`: H5 frontend, published on `${H5_PORT:-5173}`.
- `mini-novel-crawler-service`: crawler runtime, internal port `8090`, not publicly published.
- `mini-novel-admin-ui`: admin frontend, published on `${ADMIN_PORT:-5180}`.

## Deploy Script

Script:

```text
deploy/deploy.sh
```

It performs:

1. Start MySQL and Redis.
2. Wait for MySQL health.
3. Apply known SQL migrations.
4. Build and start application services.
5. Verify:
   - `http://127.0.0.1:${APP_PORT}/api/home`
   - `http://127.0.0.1:${H5_PORT}/h5/home`
   - `http://127.0.0.1:${ADMIN_PORT}/admin/login`

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
SSH host: 43.161.222.78
SSH port: 2222
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

The largest temporary growth usually comes from crawler raw tables and MySQL binlog.

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

Raw staging cleanup is acceptable at the current stage:

```sql
SET SESSION sql_log_bin = 0;
TRUNCATE TABLE mini_novel_crawler.crawl_content_raw;
TRUNCATE TABLE mini_novel_crawler.crawl_chapter_raw;
TRUNCATE TABLE mini_novel_crawler.crawl_book_raw;
TRUNCATE TABLE mini_novel_crawler.crawl_merge_item;
SET SESSION sql_log_bin = 1;
```

If binlog grows too much and replication is not used:

```sql
SHOW BINARY LOGS;
PURGE BINARY LOGS TO 'binlog.current_file_name';
SET PERSIST binlog_expire_logs_seconds = 86400;
```
