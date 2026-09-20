#!/usr/bin/env bash
set -Eeuo pipefail
shopt -s inherit_errexit
umask 077

EXPECTED_VERSION=1.0.152
EXPECTED_PREVIOUS_VERSION=1.0.149
EXPECTED_PREVIOUS_JAR_SHA=fbbea526aef6ed69144ccf64b14642240cb187d4595de9bbf8cd22bebaffa2d1
EXPECTED_MIGRATIONS_BEFORE=39
EXPECTED_MIGRATIONS_AFTER=40
NEW_MIGRATION=V202609202100__system_fund_accounts.sql
APP_ROOT=/opt/lingqimall
DB_NAME=mall_distribution
SERVICE=lingqimall-distribution.service
RELEASE_DIR=${1:-}
MODE=${2:-}
EXPECTED_COMMIT=${LINGQIMALL_RELEASE_COMMIT:-}

fail() { echo "release aborted: $*" >&2; exit 1; }
[[ "$EUID" == 0 ]] || fail "must run as root"
[[ "$(hostname)" == VM-4-6-rockylinux ]] || fail "unexpected host"
[[ "$MODE" == --preflight-only || "$MODE" == --authorize-release ]] || fail "invalid mode"
[[ "${LINGQIMALL_RELEASE_AUTHORIZATION:-}" == "$EXPECTED_VERSION" ]] || fail "authorization missing"
[[ "$EXPECTED_COMMIT" =~ ^[a-f0-9]{40}$ ]] || fail "invalid commit"
[[ "$RELEASE_DIR" =~ ^/tmp/lingqimall-financial-hotfix-152\.[A-Za-z0-9]+$ ]] || fail "invalid release directory"

exec 8>/opt/lingqimall/.mini-backend-release.lock
flock -n 8 || fail "another backend release is running"

mysql_db() { local database=$1; shift; mysql --protocol=socket -uroot "$database" "$@"; }
wait_health() {
  for _ in $(seq 1 45); do
    curl -fsS --max-time 3 http://127.0.0.1:8086/actuator/health 2>/dev/null | grep -q '"status":"UP"' && return 0
    sleep 2
  done
  return 1
}
protected_hashes() {
  find "$APP_ROOT/config" /etc/lingqimall /etc/systemd/system/lingqimall-distribution.service.d \
    /etc/nginx/conf.d "$APP_ROOT/nginx/admin" "$APP_ROOT/nginx/shop" "$APP_ROOT/nginx/team" \
    -type f -print0 | sort -z | xargs -0 sha256sum
  sha256sum /etc/systemd/system/lingqimall-distribution.service /etc/nginx/nginx.conf
}
customer_snapshot() {
  local database=$1
  mysql_db "$database" -NBe "SELECT CONCAT(
    (SELECT COUNT(*) FROM dms_shop_member WHERE user_id NOT IN (-900000000000000001,-900000000000000005)), ':',
    (SELECT COUNT(*) FROM dms_agent WHERE user_id NOT IN (-900000000000000001,-900000000000000005)), ':',
    (SELECT COUNT(*) FROM dms_agent_account WHERE user_id NOT IN (-900000000000000001,-900000000000000005)), ':',
    (SELECT COUNT(*) FROM dms_member_asset_account WHERE user_id NOT IN (-900000000000000001,-900000000000000005)), ':',
    (SELECT COALESCE(SUM(balance),0) FROM dms_member_asset_account WHERE user_id NOT IN (-900000000000000001,-900000000000000005)), ':',
    (SELECT COALESCE(SUM(withdrawable_balance),0) FROM dms_member_asset_account WHERE user_id NOT IN (-900000000000000001,-900000000000000005)), ':',
    (SELECT COUNT(*) FROM dms_shop_order), ':',
    (SELECT COUNT(*) FROM dms_order_balance_allocation), ':',
    (SELECT COALESCE(SUM(current_amount),0) FROM dms_order_balance_allocation), ':',
    (SELECT COUNT(*) FROM dms_finance_risk_rule));"
}
system_account_state() {
  local database=$1
  mysql_db "$database" -NBe "SELECT CONCAT(
    (SELECT COUNT(*) FROM dms_shop_member WHERE user_id IN (-900000000000000001,-900000000000000005) AND system_account=1 AND status=0 AND team_opt_in=0), ':',
    (SELECT COUNT(*) FROM dms_agent WHERE user_id IN (-900000000000000001,-900000000000000005) AND status=2 AND source_type=3), ':',
    (SELECT COUNT(*) FROM dms_agent_account WHERE user_id IN (-900000000000000001,-900000000000000005)), ':',
    (SELECT COUNT(*) FROM dms_member_asset_account WHERE user_id IN (-900000000000000001,-900000000000000005) AND asset_code='CASH_BONUS'), ':',
    (SELECT COALESCE(SUM(balance+frozen_balance+withdrawable_balance+total_in+total_out),0) FROM dms_member_asset_account WHERE user_id IN (-900000000000000001,-900000000000000005)), ':',
    (SELECT COALESCE(SUM(available_balance+total_commission+settled_commission+unsettled_commission+frozen_commission+withdrawn_amount),0) FROM dms_agent_account WHERE user_id IN (-900000000000000001,-900000000000000005)));"
}
migrate_database() {
  local database=$1
  MIGRATION_ROOT_DIR="$RELEASE_DIR" DB_AUTH_MODE=socket DB_HOST=localhost DB_USER=root DB_NAME="$database" \
    bash "$RELEASE_DIR/scripts/db-migrate.sh" apply
}
verify_migrations() {
  local database=$1
  MIGRATION_ROOT_DIR="$RELEASE_DIR" DB_AUTH_MODE=socket DB_HOST=localhost DB_USER=root DB_NAME="$database" \
    bash "$RELEASE_DIR/scripts/db-migrate.sh" verify >/dev/null
}
backup_and_verify() {
  local output path
  output=$(DB_AUTH_MODE=socket DB_USER=root RETENTION_DAYS=365000 OFFSITE_BACKUP_DIR='' \
    bash "$RELEASE_DIR/scripts/production-backup.sh")
  path=$(sed -n 's/^backup completed: //p' <<<"$output")
  [[ "$path" =~ ^/opt/lingqimall/backups/full/20[0-9]{6}_[0-9]{6}$ ]] || fail "backup path invalid"
  (cd "$path" && sha256sum -c SHA256SUMS >/dev/null && gzip -t database.sql.gz && tar -tzf files-and-config.tar.gz >/dev/null)
  printf '%s\n' "$path"
}

for file in mall-distribution.jar VERSION RELEASE_MANIFEST.json SHA256SUMS \
  scripts/db-migrate.sh scripts/production-backup.sh document/db/migrations/$NEW_MIGRATION; do
  [[ -s "$RELEASE_DIR/$file" ]] || fail "missing $file"
done
(cd "$RELEASE_DIR" && sha256sum -c SHA256SUMS >/dev/null)
python3 - "$RELEASE_DIR" "$EXPECTED_COMMIT" <<'PY'
import json, os, sys, zipfile
root, commit = sys.argv[1:]
with open(root + '/RELEASE_MANIFEST.json') as stream:
    manifest = json.load(stream)
assert manifest['version'] == '1.0.152'
assert manifest['scope'] == 'financial-accounts-risk-sse-hotfix'
assert manifest['gitCommit'] == commit
assert manifest['previousVersion'] == '1.0.149'
assert manifest['previousJarSha256'] == 'fbbea526aef6ed69144ccf64b14642240cb187d4595de9bbf8cd22bebaffa2d1'
assert len(manifest['databaseMigrations']) == 40
assert manifest['databaseMigrations'][-1] == 'V202609202100__system_fund_accounts.sql'
with zipfile.ZipFile(root + '/mall-distribution.jar') as archive:
    names = set(archive.namelist())
    assert 'BOOT-INF/classes/com/macro/mall/distribution/exception/GlobalExceptionHandler.class' in names
    mapper = archive.read('BOOT-INF/classes/mapper/DmsFinanceRiskRuleMapper.xml').decode()
    assert 'INSERT IGNORE INTO dms_finance_risk_rule' in mapper
PY
[[ "$(tr -d '[:space:]' < "$RELEASE_DIR/VERSION")" == "$EXPECTED_VERSION" ]] || fail "candidate version mismatch"
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "$EXPECTED_PREVIOUS_VERSION" ]] || fail "server version mismatch"
[[ "$(sha256sum "$APP_ROOT/app/mall-distribution.jar" | awk '{print $1}')" == "$EXPECTED_PREVIOUS_JAR_SHA" ]] || fail "server jar mismatch"
[[ "$(find "$RELEASE_DIR/document/db/migrations" -maxdepth 1 -type f -name 'V*.sql' | wc -l | tr -d ' ')" == "$EXPECTED_MIGRATIONS_AFTER" ]] || fail "candidate migration inventory mismatch"
MIGRATION_STATE=$(mysql_db "$DB_NAME" -NBe 'SELECT CONCAT(COUNT(*),":",SUM(success=1)) FROM dms_schema_migration_history')
[[ "$MIGRATION_STATE" == "$EXPECTED_MIGRATIONS_BEFORE:$EXPECTED_MIGRATIONS_BEFORE" || "$MIGRATION_STATE" == "$EXPECTED_MIGRATIONS_AFTER:$EXPECTED_MIGRATIONS_AFTER" ]] || fail "production migration state mismatch"
[[ "$(mysql_db "$DB_NAME" -NBe "SELECT COUNT(*) FROM dms_shop_member WHERE (login_account='SYSTEM_REMAINDER' AND user_id<>-900000000000000001) OR (login_account='SYSTEM_PRODUCT_COST' AND user_id<>-900000000000000005) OR (phone='SYS-REMAINDER-0001' AND user_id<>-900000000000000001) OR (phone='SYS-COST-0005' AND user_id<>-900000000000000005)")" == 0 ]] || fail "reserved member identity conflict"
[[ "$(mysql_db "$DB_NAME" -NBe "SELECT COUNT(*) FROM dms_agent WHERE (agent_code='SYS_REMAINDER' AND user_id<>-900000000000000001) OR (agent_code='SYS_PRODUCT_COST' AND user_id<>-900000000000000005) OR (invite_code='SYSREM01' AND user_id<>-900000000000000001) OR (invite_code='SYSCOST1' AND user_id<>-900000000000000005)")" == 0 ]] || fail "reserved agent identity conflict"
TARGET_ORDER_STATE=$(mysql_db "$DB_NAME" -NBe "SELECT CONCAT(status,':',pay_amount,':',(SELECT COUNT(*) FROM dms_order_balance_allocation a WHERE a.order_id=o.id)) FROM dms_shop_order o WHERE order_no='L0FYBGR54XLRLS'")
if [[ "$MIGRATION_STATE" == "$EXPECTED_MIGRATIONS_BEFORE:$EXPECTED_MIGRATIONS_BEFORE" ]]; then
  [[ "$(system_account_state "$DB_NAME")" == 0:0:0:0:0.00:0.00 ]] || fail "pre-migration system account state changed"
  [[ "$TARGET_ORDER_STATE" == 2:0.01:0 ]] || fail "pre-migration target order state changed"
else
  [[ "$(system_account_state "$DB_NAME")" == 2:2:2:2:0.00:0.00 ]] || fail "resumed system account state changed"
  [[ "$TARGET_ORDER_STATE" == 2:0.01:2 ]] || fail "resumed target order state changed"
  [[ "$(mysql_db "$DB_NAME" -NBe "SELECT CONCAT(COUNT(*),':',COUNT(DISTINCT allocation_type),':',COALESCE(SUM(current_amount),0),':',COALESCE(SUM(settled_amount),0),':',COALESCE(SUM(status),0)) FROM dms_order_balance_allocation WHERE order_id=(SELECT id FROM dms_shop_order WHERE order_no='L0FYBGR54XLRLS' LIMIT 1)")" == 2:2:0.01:0.00:2 ]] || fail "resumed target allocations changed"
fi
for svc in "$SERVICE" nginx mysqld redis; do systemctl is-active --quiet "$svc" || fail "$svc inactive"; done
redis-cli ping | grep -qx PONG || fail "redis unavailable"
wait_health || fail "backend unhealthy"
echo "release-preflight=passed previous=$EXPECTED_PREVIOUS_VERSION target=$EXPECTED_VERSION migrations=$MIGRATION_STATE"
[[ "$MODE" == --authorize-release ]] || exit 0

ROLLBACK_DIR=$(mktemp -d /opt/lingqimall/backups/financial-hotfix-152.XXXXXX)
VERIFY_DB=''
MUTATED=0
SERVICE_STOPPED=0
cleanup() {
  local status=$?
  trap - EXIT
  set +e
  [[ -z "$VERIFY_DB" ]] || mysql --protocol=socket -uroot -e "DROP DATABASE IF EXISTS \`$VERIFY_DB\`;"
  if [[ "$status" != 0 && "$MUTATED" == 1 ]]; then
    systemctl stop "$SERVICE"
    install -m 0644 "$ROLLBACK_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar"
    install -m 0644 "$ROLLBACK_DIR/VERSION" "$APP_ROOT/VERSION"
    systemctl start "$SERVICE"
    wait_health
    echo "bounded-recovery=previous-backend-restored additive-account-migration-retained=yes" >&2
  elif [[ "$SERVICE_STOPPED" == 1 ]]; then
    systemctl start "$SERVICE"
  fi
  exit "$status"
}
trap cleanup EXIT
trap 'echo "backend-release-check-failed line=$LINENO function=${FUNCNAME[0]:-main}" >&2' ERR

install -m 0600 "$APP_ROOT/app/mall-distribution.jar" "$ROLLBACK_DIR/mall-distribution.jar"
install -m 0600 "$APP_ROOT/VERSION" "$ROLLBACK_DIR/VERSION"
BEFORE_FILES=$(protected_hashes)
BEFORE_CUSTOMERS=$(customer_snapshot "$DB_NAME")
BACKUP_BEFORE=$(backup_and_verify)

VERIFY_DB=m152v_$(date +%Y%m%d%H%M%S)
[[ "$VERIFY_DB" =~ ^m152v_[0-9]{14}$ ]] || fail "invalid verification database"
DB_CHARSET=$(mysql_db information_schema -NBe "SELECT CONCAT(default_character_set_name,' ',default_collation_name) FROM schemata WHERE schema_name='$DB_NAME'")
mysql --protocol=socket -uroot -e "CREATE DATABASE \`$VERIFY_DB\` CHARACTER SET ${DB_CHARSET%% *} COLLATE ${DB_CHARSET##* };"
gzip -dc "$BACKUP_BEFORE/database.sql.gz" | mysql --protocol=socket -uroot "$VERIFY_DB"
[[ "$(customer_snapshot "$VERIFY_DB")" == "$BEFORE_CUSTOMERS" ]] || fail "isolated restore snapshot mismatch"
migrate_database "$VERIFY_DB"
verify_migrations "$VERIFY_DB"
[[ "$(system_account_state "$VERIFY_DB")" == 2:2:2:2:0.00:0.00 ]] || fail "isolated system account verification failed"
[[ "$(customer_snapshot "$VERIFY_DB")" == "$BEFORE_CUSTOMERS" ]] || fail "isolated migration changed customer data"
migrate_database "$VERIFY_DB"
verify_migrations "$VERIFY_DB"
[[ "$(customer_snapshot "$VERIFY_DB")" == "$BEFORE_CUSTOMERS" ]] || fail "isolated migration is not idempotent"
mysql --protocol=socket -uroot -e "DROP DATABASE \`$VERIFY_DB\`;"
VERIFY_DB=''

MUTATED=1
systemctl stop "$SERVICE"
SERVICE_STOPPED=1
migrate_database "$DB_NAME"
verify_migrations "$DB_NAME"
[[ "$(system_account_state "$DB_NAME")" == 2:2:2:2:0.00:0.00 ]] || fail "production system account verification failed"
[[ "$(customer_snapshot "$DB_NAME")" == "$BEFORE_CUSTOMERS" ]] || fail "production migration changed customer data"
install -m 0644 "$RELEASE_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar"
install -m 0644 "$RELEASE_DIR/VERSION" "$APP_ROOT/VERSION"
systemctl start "$SERVICE"
SERVICE_STOPPED=0
wait_health || fail "new backend unhealthy"

for _ in $(seq 1 45); do
  allocation_state=$(mysql_db "$DB_NAME" -NBe "SELECT CONCAT(COUNT(*),':',COUNT(DISTINCT allocation_type),':',COALESCE(SUM(current_amount),0),':',COALESCE(SUM(settled_amount),0),':',COALESCE(SUM(status),0)) FROM dms_order_balance_allocation WHERE order_id=(SELECT id FROM dms_shop_order WHERE order_no='L0FYBGR54XLRLS' LIMIT 1)")
  [[ "$allocation_state" == 2:2:0.01:0.00:2 ]] && break
  sleep 2
done
[[ "${allocation_state:-}" == 2:2:0.01:0.00:2 ]] || fail "target order allocation was not backfilled"
[[ "$(mysql_db "$DB_NAME" -NBe "SELECT COUNT(*) FROM dms_member_asset_flow WHERE user_id IN (-900000000000000001,-900000000000000005)")" == 0 ]] || fail "system balance was credited before settlement"
[[ "$(system_account_state "$DB_NAME")" == 2:2:2:2:0.00:0.00 ]] || fail "system balances changed unexpectedly"
[[ "$(protected_hashes)" == "$BEFORE_FILES" ]] || fail "protected files changed"
for url in https://lingqimall.com/api/shop/home https://www.lingqimall.com/api/shop/home; do
  curl -fsS --max-time 12 "$url" | python3 -c 'import json,sys; assert json.load(sys.stdin)["code"]==200'
done
sleep 5
journalctl -u "$SERVICE" --since "5 minutes ago" --no-pager > "$ROLLBACK_DIR/new-journal.log"
! grep -Eq 'Application run failed|OutOfMemoryError|UnsatisfiedDependencyException|BeanCreationException|Duplicate entry.*uk_rule_code|HttpMessageNotWritableException.*text/event-stream|资金归集目标账户不存在' "$ROLLBACK_DIR/new-journal.log"
BACKUP_AFTER=$(backup_and_verify)
[[ "$BACKUP_AFTER" != "$BACKUP_BEFORE" ]] || fail "post-release backup missing"
install -m 0600 "$RELEASE_DIR/RELEASE_MANIFEST.json" "$ROLLBACK_DIR/RELEASE_MANIFEST.json"
MUTATED=0
trap - EXIT
echo "release-success version=$EXPECTED_VERSION backup-before=$BACKUP_BEFORE backup-after=$BACKUP_AFTER rollback=$ROLLBACK_DIR migrations=40:40 allocations=2:2:0.01:0.00:2"
