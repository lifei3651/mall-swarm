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

if [[ "$COMMAND" != "status" && "$COMMAND" != "verify" && "$COMMAND" != "apply" ]]; then
  echo "用法：scripts/db-migrate.sh plan|status|verify|apply" >&2
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
MIGRATION_STEP_TIMEOUT_SECONDS="${MIGRATION_STEP_TIMEOUT_SECONDS:-3600}"
if [[ ! "$MIGRATION_STEP_TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ ]]; then
  echo "MIGRATION_STEP_TIMEOUT_SECONDS 必须为正整数秒数" >&2
  exit 2
fi

CLIENT_FILE=""
LOCK_KEY="$(printf '%s_%s' "$DB_HOST" "$DB_NAME" | tr -cd 'A-Za-z0-9_.-')"
LOCK_NAME="mall_schema:${DB_NAME}"
if ((${#LOCK_NAME} > 64)); then
  if command -v shasum >/dev/null 2>&1; then
    lock_hash="$(printf '%s' "$DB_NAME" | shasum -a 256 | awk '{print substr($1,1,52)}')"
  else
    lock_hash="$(printf '%s' "$DB_NAME" | sha256sum | awk '{print substr($1,1,52)}')"
  fi
  LOCK_NAME="mall_schema:${lock_hash}"
fi
LOCK_STATE_DIR=""
LOCK_HOLDER_PID=""
LOCK_STDIN_OPEN=0
cleanup() {
  if [[ "$LOCK_STDIN_OPEN" == 1 ]]; then exec 3>&-; fi
  if [[ -n "$LOCK_HOLDER_PID" ]]; then
    kill "$LOCK_HOLDER_PID" 2>/dev/null || true
    wait "$LOCK_HOLDER_PID" 2>/dev/null || true
  fi
  [[ -n "$CLIENT_FILE" ]] && rm -f "$CLIENT_FILE"
  if [[ -n "$LOCK_STATE_DIR" ]]; then
    rm -f "$LOCK_STATE_DIR/lock.stdin" "$LOCK_STATE_DIR/lock.stdout" "$LOCK_STATE_DIR/lock.stderr"
    rmdir "$LOCK_STATE_DIR" 2>/dev/null || true
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
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

mysql_lock_holder() {
  if [[ "$DB_AUTH_MODE" == socket ]]; then
    exec mysql --protocol=socket -u"$DB_USER" "$DB_NAME" "$@"
  else
    exec mysql --defaults-extra-file="$CLIENT_FILE" "$DB_NAME" "$@"
  fi
}

if [[ "$COMMAND" == "status" ]]; then
  table_exists="$(mysql_cmd --batch --skip-column-names -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}' AND table_name='dms_schema_migration_history';")"
  if [[ "$table_exists" != "1" ]]; then echo "迁移记录尚未初始化。"; exit 0; fi
  mysql_cmd --batch --skip-column-names -e 'SELECT version,script,checksum,success,installed_at FROM dms_schema_migration_history ORDER BY version;'
  exit 0
fi

if [[ "$COMMAND" == "verify" ]]; then
  table_exists="$(mysql_cmd --batch --skip-column-names -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}' AND table_name='dms_schema_migration_history';")"
  [[ "$table_exists" == "1" ]] || { echo "迁移记录尚未初始化。" >&2; exit 6; }
  history_count="$(mysql_cmd --batch --skip-column-names -e 'SELECT COUNT(*) FROM dms_schema_migration_history;')"
  success_count="$(mysql_cmd --batch --skip-column-names -e 'SELECT COUNT(*) FROM dms_schema_migration_history WHERE success=1;')"
  [[ "$history_count" == "${#MIGRATIONS[@]}" && "$success_count" == "${#MIGRATIONS[@]}" ]] || {
    echo "迁移清单数量不一致：本地=${#MIGRATIONS[@]}，数据库记录=${history_count}，成功=${success_count}" >&2
    exit 6
  }
  for file in "${MIGRATIONS[@]}"; do
    base="$(basename "$file")"
    version="${base:1:12}"
    hash="$(checksum "$file")"
    existing="$(mysql_cmd --batch --skip-column-names -e "SELECT CONCAT(script,':',checksum,':',success) FROM dms_schema_migration_history WHERE version='${version}' LIMIT 1;")"
    [[ "$existing" == "${base}:${hash}:1" ]] || {
      echo "迁移缺失、名称不符、校验和冲突或执行失败：$base" >&2
      exit 6
    }
  done
  echo "迁移清单一致：本地=${#MIGRATIONS[@]}，数据库成功=${success_count}。"
  exit 0
fi

assert_db_lock_held() {
  local owner
  if ! kill -0 "$LOCK_HOLDER_PID" 2>/dev/null; then
    echo "迁移锁连接已断开，停止执行；数据库 DDL 可能已部分生效。" >&2
    exit 7
  fi
  if ! owner="$(mysql_cmd --batch --skip-column-names -e "SELECT IS_USED_LOCK('${LOCK_NAME}');")" || [[ "$owner" != "$LOCK_OWNER_ID" ]]; then
    echo "迁移锁已丢失，停止执行；数据库 DDL 可能已部分生效。" >&2
    exit 7
  fi
}

# All migration SQL and its history writes use the connection holding GET_LOCK.
# MySQL must not reconnect after an error: a new session would silently lose it.
LOCK_STATE_DIR="$(mktemp -d "${TMPDIR:-/tmp}/mall-db-migrate-${LOCK_KEY}.XXXXXX")"
mkfifo "$LOCK_STATE_DIR/lock.stdin"
mysql_lock_holder --skip-reconnect --unbuffered --batch --skip-column-names < "$LOCK_STATE_DIR/lock.stdin" \
  > "$LOCK_STATE_DIR/lock.stdout" 2> "$LOCK_STATE_DIR/lock.stderr" &
LOCK_HOLDER_PID=$!
exec 3> "$LOCK_STATE_DIR/lock.stdin"
LOCK_STDIN_OPEN=1
printf "SELECT CONCAT('MIGRATE_LOCK:', GET_LOCK('%s', 0), ':', CONNECTION_ID());\n" "$LOCK_NAME" >&3
lock_line=""
LOCK_OWNER_ID=""
for ((attempt=0; attempt<100; attempt++)); do
  lock_line="$(grep -m1 '^MIGRATE_LOCK:' "$LOCK_STATE_DIR/lock.stdout" || true)"
  if [[ -n "$lock_line" ]]; then break; fi
  if ! kill -0 "$LOCK_HOLDER_PID" 2>/dev/null; then break; fi
  sleep 0.1
done
IFS=: read -r _ db_lock LOCK_OWNER_ID <<< "$lock_line"
if [[ "$db_lock" != 1 || ! "$LOCK_OWNER_ID" =~ ^[0-9]+$ ]]; then
  if [[ "$db_lock" == 0 ]]; then
    echo "另一台主机正在对该数据库执行迁移" >&2
  else
    echo "无法建立持久迁移锁连接，停止执行。" >&2
  fi
  exit 3
fi
assert_db_lock_held

ACK_PREFIX="MIGRATE_ACK_$(basename "$LOCK_STATE_DIR" | tr -cd 'A-Za-z0-9_')"
ACK_SEQUENCE=0
holder_running() {
  local state
  kill -0 "$LOCK_HOLDER_PID" 2>/dev/null || return 1
  if [[ -r "/proc/${LOCK_HOLDER_PID}/stat" ]]; then
    state="$(awk '{print $3}' "/proc/${LOCK_HOLDER_PID}/stat")"
  elif command -v ps >/dev/null 2>&1; then
    state="$(ps -o stat= -p "$LOCK_HOLDER_PID" 2>/dev/null | tr -d '[:space:]')"
  else
    # The bounded ACK timeout still prevents an infinite wait on a minimal OS.
    return 0
  fi
  [[ -n "$state" && "$state" != Z* && "$state" != X* ]]
}
wait_for_ack() {
  local marker="$1" timeout="$2" deadline=$((SECONDS + $2))
  while true; do
    if grep -Fqx "$marker" "$LOCK_STATE_DIR/lock.stdout"; then return 0; fi
    if ! holder_running; then return 1; fi
    if ((SECONDS >= deadline)); then
      echo "等待迁移数据库会话响应超时（${timeout} 秒）。" >&2
      return 1
    fi
    sleep 0.1
  done
}
holder_query() {
  local marker
  ACK_SEQUENCE=$((ACK_SEQUENCE + 1))
  marker="${ACK_PREFIX}_${ACK_SEQUENCE}"
  if ! printf '%s\n' "$1" "SELECT '${marker}';" >&3 || ! wait_for_ack "$marker" 300; then
    tail -n 8 "$LOCK_STATE_DIR/lock.stderr" >&2 || true
    echo "持锁数据库会话已中断，停止执行；数据库 DDL 可能已部分生效。" >&2
    exit 7
  fi
  assert_db_lock_held
}
holder_migration() {
  local marker
  ACK_SEQUENCE=$((ACK_SEQUENCE + 1))
  marker="${ACK_PREFIX}_${ACK_SEQUENCE}"
  if ! cat "$1" >&3 || ! printf '\n%s\n' "SELECT '${marker}';" >&3; then return 1; fi
  wait_for_ack "$marker" "$MIGRATION_STEP_TIMEOUT_SECONDS"
}

holder_query 'CREATE TABLE IF NOT EXISTS dms_schema_migration_history (
  version VARCHAR(32) PRIMARY KEY,
  script VARCHAR(255) NOT NULL,
  checksum CHAR(64) NOT NULL,
  success TINYINT NOT NULL,
  execution_time_ms BIGINT NOT NULL DEFAULT 0,
  installed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;'

for file in "${MIGRATIONS[@]}"; do
  assert_db_lock_held
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
  holder_query "INSERT INTO dms_schema_migration_history(version,script,checksum,success,execution_time_ms) VALUES('${version}','${base}','${hash}',0,0);"
  if holder_migration "$file"; then
    assert_db_lock_held
    elapsed="$(( ($(date +%s) - started) * 1000 ))"
    holder_query "UPDATE dms_schema_migration_history SET success=1, execution_time_ms=${elapsed} WHERE version='${version}' AND success=0;"
  else
    tail -n 8 "$LOCK_STATE_DIR/lock.stderr" >&2 || true
    echo "迁移失败：$base。数据库 DDL 可能已部分生效，必须人工核对后再处理。" >&2
    exit 5
  fi
done
assert_db_lock_held
echo "数据库迁移执行完成。"
