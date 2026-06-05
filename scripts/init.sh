#!/usr/bin/env bash
# ZestFlow 数据库初始化 — executor/collector DDL（Admin 库自行 CREATE DATABASE + Flyway）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_BIN="${MYSQL_BIN:-mysql}"

read_password() {
  local yml="$ROOT/zestflow-admin/src/main/resources/application-local.yml"
  if [[ ! -f "$yml" ]]; then
    echo "Missing $yml" >&2
    exit 1
  fi
  grep -E '^\s*password:\s*' "$yml" | head -1 | sed -E "s/^\s*password:\s*['\"]?([^'\"]*)['\"]?\s*$/\1/"
}

export MYSQL_PWD
MYSQL_PWD="$(read_password)"

echo 'Dropping databases for clean DDL ...'
"$MYSQL_BIN" -h "$MYSQL_HOST" -u "$MYSQL_USER" --default-character-set=utf8mb4 -e \
  "DROP DATABASE IF EXISTS \`zestflow_admin\`; DROP DATABASE IF EXISTS \`zestflow_app_bussiness\`; DROP DATABASE IF EXISTS \`zestflow_app_log\`;"

for f in \
  "$ROOT/zestflow-executor/src/main/resources/db/init.sql" \
  "$ROOT/zestflow-collector/collector-jdbc/src/main/resources/db/init.sql"
do
  echo "Applying $f ..."
  "$MYSQL_BIN" -h "$MYSQL_HOST" -u "$MYSQL_USER" --default-character-set=utf8mb4 -e "source ${f//\\//}"
done

unset MYSQL_PWD
echo 'Done: executor/collector DDL (Admin: create DB yourself, then start Admin for Flyway)'
