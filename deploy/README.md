# Production Deployment

> Status: current | Last tidy-up: 2026-09 | Documentation map: [`../docs/README.md`](../docs/README.md)
> Full deployment reference: [`../DEPLOYMENT.md`](../DEPLOYMENT.md).

This project uses GitHub Actions to copy source code to a VPS and run Docker Compose on the server.

Required GitHub repository secrets:

- `VPS_HOST`: server IP or domain
- `VPS_PORT`: SSH port (current production host uses `52527`)
- `VPS_USER`: SSH user, for example `root` or `deploy`
- `VPS_SSH_KEY`: private SSH key used by GitHub Actions
- `VPS_DEPLOY_PATH`: target path on the server, for example `/opt/mini-h5`
- `MYSQL_ROOT_PASSWORD`: production MySQL root password

First server setup:

```bash
mkdir -p /opt/mini-h5
cd /opt/mini-h5
cp deploy/.env.prod.example .env
```

Edit `.env` on the server and set a strong `MYSQL_ROOT_PASSWORD`.

Manual deploy command on the server (the normal path is CI/CD; `deploy.sh` applies `sql/schema.sql`, then builds
serially and runs health checks):

```bash
COMPOSE_PARALLEL_LIMIT=1 docker compose -f deploy/docker-compose.prod.yml --env-file .env up -d --build
```

Public access:

- Everything is served through the single entry point `mini-novel-gateway` on ports `80`/`443` (no port in the URL):
  - H5: `https://xs2026.site/h5/home`
  - Admin UI: `https://xs2026.site/admin/login`
  - API: `https://xs2026.site/api/home`
  - `http://` is 301-redirected to `https://xs2026.site` (same path).

The gateway needs a certificate directory (`TLS_CERT_DIR`, default `/opt/mini-h5-certs`) containing `fullchain.pem`
(leaf + intermediate) and an unencrypted `privkey.pem` (mode 600). Keep it outside the rsync target so deployments never
delete it; see `../DEPLOYMENT.md` → *TLS Certificate*.

Container ports `5173` (H5), `5180` (admin) and `8080` (backend) are bound to `127.0.0.1` and are **not** reachable from
the internet; they exist for the gateway and for deploy health checks. API docs (`/swagger-ui.html`) are therefore
internal-only — reach them through an SSH tunnel.

MySQL, Redis and crawler service are not exposed publicly in the production compose file.
