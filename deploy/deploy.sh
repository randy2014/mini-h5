#!/bin/sh
set -eu

COMPOSE_FILE="${COMPOSE_FILE:-deploy/docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-.env}"

if [ ! -f "$ENV_FILE" ]; then
  echo "Missing env file: $ENV_FILE" >&2
  exit 1
fi

set -a
. "./$ENV_FILE"
set +a

APP_PORT="${APP_PORT:-8080}"
H5_PORT="${H5_PORT:-5173}"
ADMIN_PORT="${ADMIN_PORT:-5180}"

compose() {
  docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"
}

wait_for_mysql() {
  echo "Waiting for MySQL container health..."
  i=0
  while [ "$i" -lt 60 ]; do
    status="$(docker inspect --format='{{.State.Health.Status}}' mini-novel-mysql 2>/dev/null || true)"
    if [ "$status" = "healthy" ]; then
      echo "MySQL is healthy."
      return 0
    fi
    i=$((i + 1))
    sleep 2
  done
  echo "MySQL did not become healthy in time." >&2
  docker logs --tail 80 mini-novel-mysql || true
  exit 1
}

run_migration() {
  file="$1"
  if [ -f "$file" ]; then
    echo "Applying migration: $file"
    docker exec -i mini-novel-mysql mysql \
      --default-character-set=utf8mb4 \
      -uroot \
      -p"$MYSQL_ROOT_PASSWORD" < "$file"
  fi
}

wait_for_http() {
  name="$1"
  url="$2"
  echo "Checking $name: $url"
  i=0
  while [ "$i" -lt 30 ]; do
    if curl -fsS "$url" >/dev/null 2>&1; then
      echo "$name is reachable."
      return 0
    fi
    i=$((i + 1))
    sleep 2
  done
  echo "$name health check failed: $url" >&2
  return 1
}

echo "Starting infrastructure services..."
compose up -d mini-novel-mysql mini-novel-redis
wait_for_mysql

run_migration "sql/migrations/20260629_crawl_task_scope.sql"
run_migration "sql/migrations/20260629_rule_config_chain.sql"
run_migration "sql/migrations/20260701_shuqi_public_seed.sql"
run_migration "sql/migrations/20260701_shuqi_store_rank_sources.sql"
run_migration "sql/migrations/20260701_23qb_category_sources.sql"
run_migration "sql/migrations/20260702_23qb_only_crawler_source.sql"
run_migration "sql/migrations/20260712_crawler_authorized_book.sql"

echo "Building and starting application services..."
compose up -d --build
compose ps

wait_for_http "Backend API" "http://127.0.0.1:${APP_PORT}/api/home"
wait_for_http "H5" "http://127.0.0.1:${H5_PORT}/h5/home"
wait_for_http "Admin UI" "http://127.0.0.1:${ADMIN_PORT}/admin/login"

echo "Deployment completed."
