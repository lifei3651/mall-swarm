#!/usr/bin/env bash
set -eo pipefail

ROOT_DIR="${MIGRATION_ROOT_DIR:-$(git rev-parse --show-toplevel)}"
MIGRATION_DIR="${ROOT_DIR}/document/db/migrations"
COMMAND="${1:-plan}"

mapfile_compat() {
  while IFS= read -r line; do MIGRATIONS+=("$line"); done
}

MIGRATIONS=()
mapfile_compat < <(find "$MIGRATION_DIR" -maxdepth 1 -type f -name 'V*.sql' -print | LC_ALL=C sort)

validate_names() {
  local file base version
  for file in "${MIGRATIONS[@]}"; do
    base="$(basename "$file")"
    if [[ ! "$base" =~ ^V([0-9]{12})__([a-z0-9_]+)\.sql$ ]]; then
      echo "迁移文件名不合法：$base" >&2
      echo "请使用 VYYYYMMDDHHMM__lowercase_description.sql" >&2
      exit 2
    fi
    version="${BASH_REMATCH[1]}"
    if [[ -n "${LAST_VERSION:-}" && "$version" == "$LAST_VERSION" ]]; then
      echo "迁移版本号重复：$version" >&2
      exit 2
    fi
    LAST_VERSION="$version"
  done
}

checksum() {
  if command -v shasum >/dev/null 2>&1; then shasum -a 256 "$1" | awk '{print $1}'; else sha256sum "$1" | awk '{print $1}'; fi
}

validate_names

if [[ "$COMMAND" == "plan" ]]; then
  if ((${#MIGRATIONS[@]} == 0)); then
    echo "暂无待登记迁移。旧 document/sql 文件不会被自动执行。"
  else
    for file in "${MIGRATIONS[@]}"; do echo "$(basename "$file")  $(checksum "$file")"; done
  fi
  exit 0
fi

if [[ "$COMMAND" != "status" && "$COMMAND" != "apply" ]]; then
  echo "用法：scripts/db-migrate.sh plan|status|apply" >&2
  exit 2
fi

DB_AUTH_MODE="${DB_AUTH_MODE:-password}"
DB_HOST="${DB_HOST:-localhost}"
DB_USER="${DB_USER:-}"
DB_PASSWORD="${DB_PASSWORD:-}"

for name in DB_HOST DB_USER DB_NAME; do
  if [[ -z "${!name:-}" ]]; then echo "缺少环境变量：$name" >&2; exit 2; fi
done
if [[ "$DB_AUTH_MODE" != password && "$DB_AUTH_MODE" != socket ]]; then echo "DB_AUTH_MODE 仅支持 password 或 socket" >&2; exit 2; fi
if [[ "$DB_AUTH_MODE" == password && -z "$DB_PASSWORD" ]]; then echo "缺少环境变量：DB_PASSWORD" >&2; exit 2; fi
if [[ ! "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]]; then echo "DB_NAME 格式不合法" >&2; exit 2; fi
if [[ "$DB_AUTH_MODE" == password && ( "$DB_PASSWORD" == *$'\n'* || "$DB_PASSWORD" == *$'\r'* ) ]]; then echo "DB_PASSWORD 不能包含换行符" >&2; exit 2; fi
DB_PORT="${DB_PORT:-3306}"

CLIENT_FILE=""
LOCK_KEY="$(printf '%s_%s' "$DB_HOST" "$DB_NAME" | tr -cd 'A-Za-z0-9_.-')"
LOCK_DIR="${TMPDIR:-/tmp}/mall-db-migrate-${LOCK_KEY}.lock"
cleanup() {
  if [[ "${DB_LOCK_HELD:-0}" == 1 ]]; then mysql_cmd --batch --skip-column-names -e "SELECT RELEASE_LOCK('mall_schema:${DB_NAME}');" >/dev/null 2>&1 || true; fi
  [[ -n "$CLIENT_FILE" ]] && rm -f "$CLIENT_FILE"
  if [[ "${LOCK_HELD:-0}" == 1 ]]; then rmdir "$LOCK_DIR" 2>/dev/null || true; fi
}
trap cleanup EXIT
escape_option_value() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  printf '"%s"' "$value"
}
if [[ "$DB_AUTH_MODE" == password ]]; then
  CLIENT_FILE="$(mktemp)"
  chmod 600 "$CLIENT_FILE"
  printf '[client]\nhost=%s\nport=%s\nuser=%s\npassword=%s\nget-server-public-key\ndefault-character-set=utf8mb4\n' \
    "$(escape_option_value "$DB_HOST")" "$DB_PORT" "$(escape_option_value "$DB_USER")" \
    "$(escape_option_value "$DB_PASSWORD")" > "$CLIENT_FILE"
fi

mysql_cmd() {
  if [[ "$DB_AUTH_MODE" == socket ]]; then
    mysql --protocol=socket -u"$DB_USER" "$DB_NAME" "$@"
  else
    mysql --defaults-extra-file="$CLIENT_FILE" "$DB_NAME" "$@"
  fi
}

if [[ "$COMMAND" == "status" ]]; then
  table_exists="$(mysql_cmd --batch --skip-column-names -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}' AND table_name='dms_schema_migration_history';")"
  if [[ "$table_exists" != "1" ]]; then echo "迁移记录尚未初始化。"; exit 0; fi
  mysql_cmd --batch --skip-column-names -e 'SELECT version,script,checksum,success,installed_at FROM dms_schema_migration_history ORDER BY version;'
  exit 0
fi

mysql_cmd <<'SQL'
CREATE TABLE IF NOT EXISTS dms_schema_migration_history (
  version VARCHAR(32) PRIMARY KEY,
  script VARCHAR(255) NOT NULL,
  checksum CHAR(64) NOT NULL,
  success TINYINT NOT NULL,
  execution_time_ms BIGINT NOT NULL DEFAULT 0,
  installed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
SQL

if ! mkdir "$LOCK_DIR" 2>/dev/null; then echo "已有迁移任务正在执行：$LOCK_DIR" >&2; exit 3; fi
LOCK_HELD=1
db_lock="$(mysql_cmd --batch --skip-column-names -e "SELECT GET_LOCK('mall_schema:${DB_NAME}', 0);")"
if [[ "$db_lock" != "1" ]]; then echo "另一台主机正在对该数据库执行迁移" >&2; exit 3; fi
DB_LOCK_HELD=1

for file in "${MIGRATIONS[@]}"; do
  base="$(basename "$file")"
  version="${base:1:12}"
  hash="$(checksum "$file")"
  existing="$(mysql_cmd --batch --skip-column-names -e "SELECT CONCAT(checksum,':',success) FROM dms_schema_migration_history WHERE version='${version}' LIMIT 1;")"
  if [[ -n "$existing" ]]; then
    if [[ "$existing" == "${hash}:1" ]]; then echo "已跳过 $base"; continue; fi
    echo "迁移记录冲突或曾失败，停止执行：$base（禁止修改已登记迁移）" >&2
    exit 4
  fi
  started="$(date +%s)"
  echo "正在执行 $base"
  if mysql_cmd < "$file"; then
    elapsed="$(( ($(date +%s) - started) * 1000 ))"
    mysql_cmd -e "INSERT INTO dms_schema_migration_history(version,script,checksum,success,execution_time_ms) VALUES('${version}','${base}','${hash}',1,${elapsed});"
  else
    mysql_cmd -e "INSERT INTO dms_schema_migration_history(version,script,checksum,success,execution_time_ms) VALUES('${version}','${base}','${hash}',0,0);" || true
    echo "迁移失败：$base。数据库 DDL 可能已部分生效，必须人工核对后再处理。" >&2
    exit 5
  fi
done
echo "数据库迁移执行完成。"
