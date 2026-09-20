#!/usr/bin/env bash
set -Eeuo pipefail

# 2026-09-20 正式库余额结构补迁移。
# 仅允许在正式主机上执行；先做完整备份和隔离恢复验收，再补正式库。

EXPECTED_HOSTNAME="VM-4-6-rockylinux"
EXPECTED_SERVER_VERSION="1.0.149"
EXPECTED_MIGRATION_VERSION="202609141100"
EXPECTED_MIGRATION_COUNT_BEFORE="38"
EXPECTED_MIGRATION_COUNT_AFTER="39"
EXPECTED_MIGRATION_SHA256="abefb23e96dc5bef44771000b0ce50ef261818137190bee6f978b1ccdeb9dce3"
EXPECTED_AUTHORIZATION="202609141100"
APP_ROOT="/opt/lingqimall"
DB_NAME="mall_distribution"
SERVICE_NAME="lingqimall-distribution.service"
RELEASE_ROOT="${1:-}"
MODE="${2:---preflight-only}"

fail() {
  echo "repair aborted: $*" >&2
  exit 1
}

[[ "$EUID" == 0 ]] || fail "must run as root"
[[ "$(hostname)" == "$EXPECTED_HOSTNAME" ]] || fail "unexpected hostname"
[[ -n "$RELEASE_ROOT" && -d "$RELEASE_ROOT" ]] || fail "release root is missing"
[[ "$MODE" == "--preflight-only" || "$MODE" == "--authorize-repair" ]] || fail "invalid mode"

MIGRATION_FILE="$RELEASE_ROOT/document/db/migrations/V202609141100__balance_holder_withdrawal.sql"
MIGRATION_RUNNER="$RELEASE_ROOT/scripts/db-migrate.sh"
BACKUP_RUNNER="$RELEASE_ROOT/scripts/production-backup.sh"

for required in "$MIGRATION_FILE" "$MIGRATION_RUNNER" "$BACKUP_RUNNER"; do
  [[ -f "$required" ]] || fail "required file is missing: $required"
done

[[ "$(sha256sum "$MIGRATION_FILE" | awk '{print $1}')" == "$EXPECTED_MIGRATION_SHA256" ]] \
  || fail "migration checksum does not match the reviewed file"
[[ "$(cat "$APP_ROOT/VERSION")" == "$EXPECTED_SERVER_VERSION" ]] \
  || fail "server version is not $EXPECTED_SERVER_VERSION"

exec 9>"/var/lock/lingqimall-wallet-schema-repair.lock"
flock -n 9 || fail "another wallet schema repair is running"

mysql_system() {
  mysql --protocol=socket -uroot --batch --skip-column-names "$@"
}

mysql_database() {
  local database="$1"
  shift
  mysql --protocol=socket -uroot "$database" "$@"
}

history_count() {
  mysql_system -e "SELECT COUNT(*) FROM ${DB_NAME}.dms_schema_migration_history WHERE success=1;"
}

failed_history_count() {
  mysql_system -e "SELECT COUNT(*) FROM ${DB_NAME}.dms_schema_migration_history WHERE success<>1;"
}

affected_data_snapshot() {
  local database="$1"
  mysql_system -e "
    SELECT CONCAT('asset|', COUNT(*), '|', COALESCE(SUM(balance),0), '|',
      COALESCE(SUM(frozen_balance),0), '|', COALESCE(SUM(total_in),0), '|', COALESCE(SUM(total_out),0))
      FROM ${database}.dms_member_asset_account;
    SELECT CONCAT('withdraw|', COUNT(*)) FROM ${database}.dms_withdraw_record;
    SELECT CONCAT('active_agent|', COUNT(*)) FROM ${database}.dms_agent WHERE status=1;
  "
}

schema_state() {
  local database="$1"
  mysql_system -e "
    SELECT CONCAT('withdrawable|', COUNT(*), '|', COALESCE(MAX(COLUMN_TYPE),''), '|',
      COALESCE(MAX(IS_NULLABLE),''), '|', COALESCE(MAX(COLUMN_DEFAULT),''))
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA='${database}' AND TABLE_NAME='dms_member_asset_account'
        AND COLUMN_NAME='withdrawable_balance';
    SELECT CONCAT('withdraw_agent_nullable|', COALESCE(MAX(IS_NULLABLE),''))
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA='${database}' AND TABLE_NAME='dms_withdraw_record' AND COLUMN_NAME='agent_id';
    SELECT CONCAT('withdraw_index|', COUNT(DISTINCT INDEX_NAME))
      FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA='${database}' AND TABLE_NAME='dms_withdraw_record'
        AND INDEX_NAME='idx_withdraw_user_time';
  "
}

assert_repaired_schema() {
  local database="$1"
  local column_count column_type column_nullable agent_nullable index_count
  column_count="$(mysql_system -e "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='${database}' AND TABLE_NAME='dms_member_asset_account' AND COLUMN_NAME='withdrawable_balance';")"
  column_type="$(mysql_system -e "SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='${database}' AND TABLE_NAME='dms_member_asset_account' AND COLUMN_NAME='withdrawable_balance';")"
  column_nullable="$(mysql_system -e "SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='${database}' AND TABLE_NAME='dms_member_asset_account' AND COLUMN_NAME='withdrawable_balance';")"
  agent_nullable="$(mysql_system -e "SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='${database}' AND TABLE_NAME='dms_withdraw_record' AND COLUMN_NAME='agent_id';")"
  index_count="$(mysql_system -e "SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='${database}' AND TABLE_NAME='dms_withdraw_record' AND INDEX_NAME='idx_withdraw_user_time';")"
  [[ "$column_count" == 1 ]] || fail "withdrawable_balance is missing from $database"
  [[ "$column_type" == "decimal(14,2)" && "$column_nullable" == "NO" ]] \
    || fail "withdrawable_balance has an unexpected definition in $database"
  [[ "$agent_nullable" == "YES" ]] || fail "withdraw record agent_id is still required in $database"
  [[ "$index_count" == 1 ]] || fail "withdraw user/time index is missing in $database"
}

assert_backfill_rules() {
  local database="$1"
  local active_mismatch inactive_nonzero
  active_mismatch="$(mysql_system -e "
    SELECT COUNT(*) FROM ${database}.dms_member_asset_account a
    JOIN ${database}.dms_agent g ON g.id=a.agent_id
    WHERE g.status=1 AND a.balance>0 AND a.withdrawable_balance<>a.balance;
  ")"
  inactive_nonzero="$(mysql_system -e "
    SELECT COUNT(*) FROM ${database}.dms_member_asset_account a
    LEFT JOIN ${database}.dms_agent g ON g.id=a.agent_id AND g.status=1
    WHERE g.id IS NULL AND a.withdrawable_balance<>0;
  ")"
  [[ "$active_mismatch" == 0 ]] || fail "active-agent balance backfill is incomplete in $database"
  [[ "$inactive_nonzero" == 0 ]] || fail "non-active accounts were made withdrawable in $database"
}

local_migration_count="$(find "$RELEASE_ROOT/document/db/migrations" -maxdepth 1 -type f -name 'V*.sql' | wc -l | tr -d ' ')"
[[ "$local_migration_count" == "$EXPECTED_MIGRATION_COUNT_AFTER" ]] \
  || fail "release migration inventory is not $EXPECTED_MIGRATION_COUNT_AFTER"
[[ "$(failed_history_count)" == 0 ]] || fail "production migration history contains failures"

registered="$(mysql_system -e "SELECT COUNT(*) FROM ${DB_NAME}.dms_schema_migration_history WHERE version='${EXPECTED_MIGRATION_VERSION}' AND success=1;")"
column_before="$(mysql_system -e "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='${DB_NAME}' AND TABLE_NAME='dms_member_asset_account' AND COLUMN_NAME='withdrawable_balance';")"

if [[ "$registered" == 1 && "$column_before" == 1 ]]; then
  assert_repaired_schema "$DB_NAME"
  echo "wallet schema is already repaired"
  exit 0
fi

[[ "$registered" == 0 ]] || fail "migration history and schema disagree"
[[ "$column_before" == 0 ]] || fail "withdrawable_balance exists without migration history"
[[ "$(history_count)" == "$EXPECTED_MIGRATION_COUNT_BEFORE" ]] \
  || fail "production migration count is not $EXPECTED_MIGRATION_COUNT_BEFORE"
[[ "$(systemctl is-active "$SERVICE_NAME")" == active ]] || fail "backend service is not active"
mysql_system -e 'SELECT 1;' >/dev/null
redis-cli ping | grep -qx PONG || fail "redis is unavailable"
curl -fsS --max-time 8 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"' \
  || fail "backend health check failed"

echo "preflight passed"
echo "production history: $(history_count)/$local_migration_count"
schema_state "$DB_NAME"

if [[ "$MODE" == "--preflight-only" ]]; then
  exit 0
fi

[[ "${LINGQIMALL_SCHEMA_REPAIR_AUTHORIZATION:-}" == "$EXPECTED_AUTHORIZATION" ]] \
  || fail "explicit repair authorization is missing"

service_was_stopped=0
verify_database=""
cleanup() {
  local exit_code=$?
  if [[ -n "$verify_database" ]]; then
    mysql_system -e "DROP DATABASE IF EXISTS ${verify_database};" >/dev/null 2>&1 || true
  fi
  if [[ "$service_was_stopped" == 1 ]]; then
    systemctl start "$SERVICE_NAME" >/dev/null 2>&1 || true
  fi
  exit "$exit_code"
}
trap cleanup EXIT

protected_hash_before="$(
  {
    sha256sum "$APP_ROOT/VERSION" "$APP_ROOT/app/mall-distribution.jar"
    find "$APP_ROOT/config" -type f -print0 | LC_ALL=C sort -z | xargs -0 sha256sum
  } | sha256sum | awk '{print $1}'
)"
data_before="$(affected_data_snapshot "$DB_NAME")"

backup_output="$(APP_ROOT="$APP_ROOT" DB_NAME="$DB_NAME" DB_AUTH_MODE=socket DB_USER=root bash "$BACKUP_RUNNER")"
echo "$backup_output"
backup_before="$(awk '/^backup completed:/ {print $3}' <<<"$backup_output")"
[[ -d "$backup_before" ]] || fail "pre-repair backup path was not returned"
(cd "$backup_before" && sha256sum -c SHA256SUMS >/dev/null) || fail "pre-repair backup checksum failed"
gzip -t "$backup_before/database.sql.gz" || fail "pre-repair database backup is invalid"

verify_database="mall_distribution_wallet_verify_$(date +%Y%m%d%H%M%S)"
[[ "$verify_database" =~ ^[A-Za-z0-9_]+$ ]] || fail "invalid verification database name"
mysql_system -e "CREATE DATABASE ${verify_database} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
gzip -dc "$backup_before/database.sql.gz" | mysql_database "$verify_database"
[[ "$(affected_data_snapshot "$verify_database")" == "$data_before" ]] \
  || fail "isolated restore data does not match production snapshot"

MIGRATION_ROOT_DIR="$RELEASE_ROOT" DB_AUTH_MODE=socket DB_USER=root DB_NAME="$verify_database" \
  bash "$MIGRATION_RUNNER" apply
assert_repaired_schema "$verify_database"
assert_backfill_rules "$verify_database"
[[ "$(affected_data_snapshot "$verify_database")" == "$data_before" ]] \
  || fail "migration changed pre-existing financial fields in isolated restore"

# 再跑一次，验证迁移总账和脚本可以安全重试。
MIGRATION_ROOT_DIR="$RELEASE_ROOT" DB_AUTH_MODE=socket DB_USER=root DB_NAME="$verify_database" \
  bash "$MIGRATION_RUNNER" apply
[[ "$(mysql_system -e "SELECT COUNT(*) FROM ${verify_database}.dms_schema_migration_history WHERE success=1;")" == "$EXPECTED_MIGRATION_COUNT_AFTER" ]] \
  || fail "isolated migration history is incomplete"
[[ "$(mysql_system -e "SELECT COUNT(*) FROM ${verify_database}.dms_schema_migration_history WHERE success<>1;")" == 0 ]] \
  || fail "isolated migration history contains failures"
mysql_system -e "DROP DATABASE ${verify_database};"
verify_database=""

log_cursor="$(journalctl -u "$SERVICE_NAME" --show-cursor -n 0 --no-pager | awk -F': ' '/-- cursor:/{print $2}')"
systemctl stop "$SERVICE_NAME"
service_was_stopped=1

MIGRATION_ROOT_DIR="$RELEASE_ROOT" DB_AUTH_MODE=socket DB_USER=root DB_NAME="$DB_NAME" \
  bash "$MIGRATION_RUNNER" apply

assert_repaired_schema "$DB_NAME"
assert_backfill_rules "$DB_NAME"
[[ "$(history_count)" == "$EXPECTED_MIGRATION_COUNT_AFTER" ]] || fail "production migration history is incomplete"
[[ "$(failed_history_count)" == 0 ]] || fail "production migration history contains failures"
[[ "$(affected_data_snapshot "$DB_NAME")" == "$data_before" ]] \
  || fail "migration changed pre-existing production financial fields"

# 直接执行余额 Mapper 所需的列查询，确认不会再触发 Unknown column。
mysql_system -e "
  SELECT id, agent_id, user_id, asset_code, asset_name, balance, withdrawable_balance,
         frozen_balance, total_in, total_out, create_time, update_time
  FROM ${DB_NAME}.dms_member_asset_account LIMIT 1;
" >/dev/null

systemctl start "$SERVICE_NAME"
service_was_stopped=0
for _ in $(seq 1 30); do
  if curl -fsS --max-time 5 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'; then
    break
  fi
  sleep 1
done
[[ "$(systemctl is-active "$SERVICE_NAME")" == active ]] || fail "backend did not restart"
curl -fsS --max-time 8 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"' \
  || fail "backend health check failed after repair"

protected_hash_after="$(
  {
    sha256sum "$APP_ROOT/VERSION" "$APP_ROOT/app/mall-distribution.jar"
    find "$APP_ROOT/config" -type f -print0 | LC_ALL=C sort -z | xargs -0 sha256sum
  } | sha256sum | awk '{print $1}'
)"
[[ "$protected_hash_after" == "$protected_hash_before" ]] || fail "application files changed during schema repair"

if [[ -n "$log_cursor" ]]; then
  if journalctl -u "$SERVICE_NAME" --after-cursor "$log_cursor" --no-pager \
      | grep -E 'Unknown column|BadSqlGrammarException|SQLSyntaxErrorException|Application run failed'; then
    fail "backend logs contain a schema or startup error after repair"
  fi
fi

backup_output="$(APP_ROOT="$APP_ROOT" DB_NAME="$DB_NAME" DB_AUTH_MODE=socket DB_USER=root bash "$BACKUP_RUNNER")"
echo "$backup_output"
backup_after="$(awk '/^backup completed:/ {print $3}' <<<"$backup_output")"
[[ -d "$backup_after" ]] || fail "post-repair backup path was not returned"
(cd "$backup_after" && sha256sum -c SHA256SUMS >/dev/null) || fail "post-repair backup checksum failed"

echo "wallet schema repair completed"
echo "backup_before=$backup_before"
echo "backup_after=$backup_after"
echo "migration_history=$(history_count)/$local_migration_count"
schema_state "$DB_NAME"
