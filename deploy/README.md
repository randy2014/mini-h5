# Production Deployment

This project uses GitHub Actions to copy source code to a VPS and run Docker Compose on the server.

Required GitHub repository secrets:

- `VPS_HOST`: server IP or domain
- `VPS_PORT`: SSH port, usually `22`
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

Manual deploy command on the server:

```bash
docker compose -f deploy/docker-compose.prod.yml --env-file .env up -d --build
```

Public ports:

- H5: `5173`
- Admin UI: `5180`
- Backend API: `8080`

MySQL, Redis and crawler service are not exposed publicly in the production compose file.
