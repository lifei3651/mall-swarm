#!/usr/bin/env bash
# 1.0.154 closure backend release. No schema migration is allowed in this release.
set -Eeuo pipefail
shopt -s inherit_errexit
umask 077

EXPECTED_VERSION=1.0.154
EXPECTED_PREVIOUS_VERSION=1.0.153
EXPECTED_PREVIOUS_JAR_SHA=bb2ef6a1c3c8fe908f088b16ac86fbce193334645e83c343adbd4392d2b93009
EXPECTED_MIGRATIONS=40
EXPECTED_LAST_MIGRATION=V202609202100__system_fund_accounts.sql
APP_ROOT=/opt/lingqimall
DB_NAME=mall_distribution
SERVICE=lingqimall-distribution.service
RELEASE_DIR=${1:-}
MODE=${2:-}
EXPECTED_COMMIT=${LINGQIMALL_RELEASE_COMMIT:-}

fail() { echo "closure-backend-release-aborted: $*" >&2; exit 1; }
[[ "$EUID" == 0 ]] || fail "must run as root"
[[ "$(hostname)" == VM-4-6-rockylinux ]] || fail "unexpected host"
[[ "$MODE" == --preflight-only || "$MODE" == --authorize-release ]] || fail "invalid mode"
[[ "${LINGQIMALL_RELEASE_AUTHORIZATION:-}" == "$EXPECTED_VERSION" ]] || fail "authorization missing"
[[ "$EXPECTED_COMMIT" =~ ^[a-f0-9]{40}$ ]] || fail "invalid commit"
[[ "$RELEASE_DIR" =~ ^/tmp/lingqimall-closure-154\.[A-Za-z0-9]+$ ]] || fail "invalid release directory"

exec 8>"$APP_ROOT/.mini-backend-release.lock"
flock -n 8 || fail "another backend release is running"

MUTATED=0
ROLLBACK_DIR=''
DEPLOY_START_TIME=''

mysql_db() {
  local database=$1
  shift
  mysql --protocol=socket -uroot "$database" "$@"
}

wait_health() {
  for _ in $(seq 1 45); do
    curl -fsS --max-time 3 http://127.0.0.1:8086/actuator/health 2>/dev/null \
      | grep -q '"status":"UP"' && return 0
    sleep 2
  done
  return 1
}

verify_four_services() {
  local svc
  for svc in "$SERVICE" nginx mysqld redis; do
    systemctl is-active --quiet "$svc" || fail "$svc inactive"
  done
  redis-cli ping | grep -qx PONG || fail "redis unavailable"
}

verify_migrations() {
  local database=$1
  MIGRATION_ROOT_DIR="$RELEASE_DIR" DB_AUTH_MODE=socket DB_HOST=localhost DB_USER=root DB_NAME="$database" \
    bash "$RELEASE_DIR/db-migrate.sh" verify >/dev/null
}

migration_ledger() {
  local database=$1
  mysql_db "$database" -NBe \
    "SELECT CONCAT_WS('|',version,script,checksum,success) FROM dms_schema_migration_history ORDER BY version;"
}

business_snapshot() {
  local database=$1
  mysql_db "$database" -NBe "SELECT CONCAT(
    (SELECT COUNT(*) FROM dms_shop_member), ':',
    (SELECT COUNT(*) FROM dms_shop_order), ':',
    (SELECT COUNT(*) FROM dms_shop_after_sale), ':',
    (SELECT COUNT(*) FROM dms_commission_record), ':',
    (SELECT COUNT(*) FROM dms_member_asset_account), ':',
    (SELECT COUNT(*) FROM dms_member_asset_flow), ':',
    (SELECT COALESCE(SUM(balance),0) FROM dms_member_asset_account), ':',
    (SELECT COALESCE(SUM(frozen_balance),0) FROM dms_member_asset_account), ':',
    (SELECT COALESCE(SUM(withdrawable_balance),0) FROM dms_member_asset_account), ':',
    (SELECT COUNT(*) FROM dms_order_balance_allocation), ':',
    (SELECT COALESCE(SUM(current_amount),0) FROM dms_order_balance_allocation), ':',
    (SELECT COALESCE(SUM(settled_amount),0) FROM dms_order_balance_allocation), ':',
    (SELECT COUNT(*) FROM dms_wechat_shipping_sync_task), ':',
    (SELECT COUNT(*) FROM dms_wechat_logistics_follow_task), ':',
    (SELECT COUNT(*) FROM dms_wechat_express_order));"
}

protected_hashes() {
  find "$APP_ROOT/config" /etc/lingqimall /etc/systemd/system/lingqimall-distribution.service.d \
    /etc/nginx/conf.d "$APP_ROOT/nginx/admin" "$APP_ROOT/nginx/shop" "$APP_ROOT/nginx/team" \
    -type f -print0 | sort -z | xargs -0 sha256sum
  sha256sum /etc/systemd/system/lingqimall-distribution.service /etc/nginx/nginx.conf
}

runtime_snapshot() {
  # The runtime endpoint deliberately does not certify payment readiness; the
  # independent /shop/pay/config snapshot below is the payment source of truth.
  curl -fsS --max-time 12 \
    -H 'X-Shop-Client: wechat-mini-program' -H 'X-Shop-Surface: mini-program' \
    http://127.0.0.1:8086/shop/wechat-mini-program/runtime \
    | python3 -c 'import json,sys; d=json.load(sys.stdin); v=d["data"]; assert d["code"]==200 and v["enabled"] is True and v["phoneAuthorizationEnabled"] is True and v["shippingInfoEnabled"] is True and v["paymentEnabled"] is False; print(json.dumps(v,sort_keys=True,separators=(",",":")))'
}

payment_snapshot() {
  curl -fsS --max-time 12 http://127.0.0.1:8086/shop/pay/config \
    | python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["code"]==200 and d["data"]["wechatPayEnabled"] is True; print(json.dumps(d["data"],sort_keys=True,separators=(",",":")))'
}

probe_public() {
  local url
  for url in https://lingqimall.com/api/shop/home https://www.lingqimall.com/api/shop/home; do
    curl --http1.1 -fsS --max-time 12 "$url" \
      | python3 -c 'import json,sys; assert json.load(sys.stdin)["code"]==200'
  done
}

verify_unauthorized_contract() {
  local body status
  body=$(mktemp /tmp/lingqimall-closure-154-unauthorized.XXXXXX)
  status=$(curl --http1.1 -sS --max-time 12 -o "$body" -w '%{http_code}' \
    https://lingqimall.com/api/shop/orders)
  [[ "$status" == 401 ]] || { rm -f "$body"; fail "protected route HTTP status is not 401"; }
  if ! python3 - "$body" <<'PY'
import json, sys
with open(sys.argv[1]) as stream:
    payload = json.load(stream)
assert payload.get('code') == 401
assert not payload.get('data')
PY
  then
    rm -f "$body"
    fail "protected route response body is not the 401 contract"
  fi
  rm -f "$body"
}

backup_and_verify() {
  local expected_version=$1 output path archived_version
  output=$(DB_AUTH_MODE=socket DB_USER=root RETENTION_DAYS=365000 OFFSITE_BACKUP_DIR='' \
    bash "$RELEASE_DIR/production-backup.sh")
  path=$(sed -n 's/^backup completed: //p' <<<"$output")
  [[ "$path" =~ ^/opt/lingqimall/backups/full/20[0-9]{6}_[0-9]{6}$ ]] \
    || fail "backup path invalid"
  (cd "$path" && sha256sum -c SHA256SUMS >/dev/null && gzip -t database.sql.gz \
    && tar -tzf files-and-config.tar.gz >/dev/null)
  tar -tzf "$path/files-and-config.tar.gz" | grep -Fx "${APP_ROOT#/}/VERSION" >/dev/null \
    || fail "backup does not contain VERSION"
  archived_version=$(tar -xOf "$path/files-and-config.tar.gz" "${APP_ROOT#/}/VERSION" | tr -d '[:space:]')
  [[ "$archived_version" == "$expected_version" ]] || fail "backup VERSION mismatch"
  printf '%s\n' "$path"
}

atomic_install() {
  local source=$1 destination=$2 mode=$3 temporary
  [[ -f "$source" && -f "$destination" ]] || return 1
  temporary=$(mktemp "$(dirname "$destination")/.release-154.$(basename "$destination").XXXXXX") || return 1
  if ! install -m "$mode" "$source" "$temporary" \
    || ! chown --reference="$destination" "$temporary" \
    || ! mv -f "$temporary" "$destination"; then
    rm -f "$temporary"
    return 1
  fi
}

recover() {
  local status=$?
  trap - EXIT
  if [[ "$status" != 0 && "$MUTATED" == 1 ]]; then
    set +e
    if systemctl stop "$SERVICE" \
      && atomic_install "$ROLLBACK_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar" 0644 \
      && atomic_install "$ROLLBACK_DIR/VERSION" "$APP_ROOT/VERSION" 0644 \
      && systemctl start "$SERVICE" \
      && wait_health \
      && [[ "$(sha256sum "$APP_ROOT/app/mall-distribution.jar" | awk '{print $1}')" == "$EXPECTED_PREVIOUS_JAR_SHA" ]] \
      && [[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "$EXPECTED_PREVIOUS_VERSION" ]]; then
      echo "bounded-recovery=previous-backend-and-version-restored rollback=$ROLLBACK_DIR" >&2
    else
      echo "bounded-recovery=FAILED; immediate manual intervention required" >&2
    fi
  fi
  exit "$status"
}
trap recover EXIT
trap 'echo "closure-backend-release-check-failed line=$LINENO function=${FUNCNAME[0]:-main}" >&2' ERR

for file in mall-distribution.jar VERSION RELEASE_MANIFEST.json SHA256SUMS release-backend.sh \
  db-migrate.sh production-backup.sh; do
  [[ -s "$RELEASE_DIR/$file" ]] || fail "missing $file"
done
[[ -s "$RELEASE_DIR/document/db/migrations/$EXPECTED_LAST_MIGRATION" ]] \
  || fail "last migration is missing"
[[ -z "$(find "$RELEASE_DIR" -type l -print -quit)" ]] || fail "candidate contains a symbolic link"
(cd "$RELEASE_DIR" && sha256sum -c SHA256SUMS >/dev/null)

CANDIDATE_JAR_SHA=$(python3 - "$RELEASE_DIR" "$EXPECTED_COMMIT" <<'PY'
import hashlib, json, os, re, sys, zipfile
root, commit = sys.argv[1:]
with open(os.path.join(root, 'RELEASE_MANIFEST.json')) as stream:
    manifest = json.load(stream)
assert manifest['version'] == '1.0.154'
assert manifest['scope'] == 'mall-closure-candidate'
assert manifest['gitCommit'] == commit
assert manifest['previousVersion'] == '1.0.153'
assert manifest['previousJarSha256'] == 'bb2ef6a1c3c8fe908f088b16ac86fbce193334645e83c343adbd4392d2b93009'
assert isinstance(manifest.get('buildId'), str) and manifest['buildId']
assert isinstance(manifest.get('generatedAt'), str) and manifest['generatedAt']
migrations = manifest['databaseMigrations']
assert isinstance(migrations, list) and len(migrations) == 40
assert migrations == sorted(migrations)
assert migrations[-1] == 'V202609202100__system_fund_accounts.sql'
assert all(re.fullmatch(r'V\d{12}__[a-z0-9_]+\.sql', name) for name in migrations)
actual_migrations = sorted(name for name in os.listdir(os.path.join(root, 'document/db/migrations')) if name.endswith('.sql'))
assert actual_migrations == migrations
jar = os.path.join(root, 'mall-distribution.jar')
with open(jar, 'rb') as stream:
    jar_sha = hashlib.sha256(stream.read()).hexdigest()
assert jar_sha == manifest['jarSha256']
assert jar_sha != manifest['previousJarSha256']
with zipfile.ZipFile(jar) as archive:
    names = set(archive.namelist())
    assert 'BOOT-INF/classes/com/macro/mall/distribution/exception/GlobalExceptionHandler.class' in names
    mapper = archive.read('BOOT-INF/classes/mapper/DmsFinanceRiskRuleMapper.xml').decode()
    assert 'INSERT IGNORE INTO dms_finance_risk_rule' in mapper
print(jar_sha)
PY
)
[[ "$CANDIDATE_JAR_SHA" =~ ^[a-f0-9]{64}$ ]] || fail "candidate JAR identity invalid"
[[ "$(tr -d '[:space:]' < "$RELEASE_DIR/VERSION")" == "$EXPECTED_VERSION" ]] \
  || fail "candidate version mismatch"
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "$EXPECTED_PREVIOUS_VERSION" ]] \
  || fail "server version mismatch"
[[ "$(sha256sum "$APP_ROOT/app/mall-distribution.jar" | awk '{print $1}')" == "$EXPECTED_PREVIOUS_JAR_SHA" ]] \
  || fail "server JAR mismatch"
[[ "$(find "$RELEASE_DIR/document/db/migrations" -maxdepth 1 -type f -name 'V*.sql' | wc -l | tr -d ' ')" == "$EXPECTED_MIGRATIONS" ]] \
  || fail "candidate migration inventory mismatch"
[[ "$(mysql_db "$DB_NAME" -NBe 'SELECT CONCAT(COUNT(*),":",SUM(success=1)) FROM dms_schema_migration_history')" == 40:40 ]] \
  || fail "production migration state mismatch"
verify_migrations "$DB_NAME"
BEFORE_MIGRATIONS=$(migration_ledger "$DB_NAME")
[[ "$(mysql_db information_schema -NBe "SELECT COUNT(*) FROM events WHERE event_schema='$DB_NAME'")" == 0 ]] \
  || fail "unexpected database event"
[[ "$(df -Pm "$APP_ROOT" | awk 'NR==2 {print $4}')" -ge 4096 ]] || fail "insufficient free disk space"
verify_four_services
wait_health || fail "backend unhealthy"
BEFORE_FILES=$(protected_hashes)
BEFORE_RUNTIME=$(runtime_snapshot)
BEFORE_PAYMENT=$(payment_snapshot)
probe_public
echo "release-preflight=passed previous=$EXPECTED_PREVIOUS_VERSION target=$EXPECTED_VERSION migrations=40:40 no-new-migration=yes"
[[ "$MODE" == --authorize-release ]] || exit 0

ROLLBACK_DIR=$(mktemp -d /opt/lingqimall/backups/closure-backend-154.XXXXXX)
install -m 0600 "$APP_ROOT/app/mall-distribution.jar" "$ROLLBACK_DIR/mall-distribution.jar"
install -m 0600 "$APP_ROOT/VERSION" "$ROLLBACK_DIR/VERSION"
BACKUP_BEFORE=$(backup_and_verify "$EXPECTED_PREVIOUS_VERSION")
echo "backup-before=$BACKUP_BEFORE"

MUTATED=1
DEPLOY_START_TIME=$(date '+%Y-%m-%d %H:%M:%S')
systemctl stop "$SERVICE"
if systemctl is-active --quiet "$SERVICE"; then fail "backend did not stop"; fi
STOPPED_DATABASE=$(business_snapshot "$DB_NAME")
[[ "$(migration_ledger "$DB_NAME")" == "$BEFORE_MIGRATIONS" ]] || fail "migration ledger changed before replacement"
atomic_install "$RELEASE_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar" 0644 \
  || fail "candidate JAR replacement failed"
atomic_install "$RELEASE_DIR/VERSION" "$APP_ROOT/VERSION" 0644 \
  || fail "candidate VERSION replacement failed"
[[ "$(business_snapshot "$DB_NAME")" == "$STOPPED_DATABASE" ]] || fail "database changed during file replacement"
[[ "$(migration_ledger "$DB_NAME")" == "$BEFORE_MIGRATIONS" ]] || fail "migration ledger changed during file replacement"
systemctl start "$SERVICE"
wait_health || fail "new backend unhealthy"

[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "$EXPECTED_VERSION" ]] || fail "new VERSION not active"
[[ "$(sha256sum "$APP_ROOT/app/mall-distribution.jar" | awk '{print $1}')" == "$CANDIDATE_JAR_SHA" ]] \
  || fail "new JAR not active"
verify_four_services
verify_migrations "$DB_NAME"
[[ "$(migration_ledger "$DB_NAME")" == "$BEFORE_MIGRATIONS" ]] || fail "migration ledger changed after restart"
[[ "$(protected_hashes)" == "$BEFORE_FILES" ]] || fail "configuration or static files changed"
[[ "$(runtime_snapshot)" == "$BEFORE_RUNTIME" ]] || fail "mini-program runtime configuration changed"
[[ "$(payment_snapshot)" == "$BEFORE_PAYMENT" ]] || fail "payment runtime configuration changed"
probe_public
verify_unauthorized_contract

sleep 8
journalctl -u "$SERVICE" --since "$DEPLOY_START_TIME" --no-pager > "$ROLLBACK_DIR/new-journal.log"
! grep -Eq 'Application run failed|OutOfMemoryError|UnsatisfiedDependencyException|BeanCreationException|Duplicate entry.*uk_rule_code|HttpMessageNotWritableException.*text/event-stream|资金归集目标账户不存在' "$ROLLBACK_DIR/new-journal.log" \
  || fail "critical error detected after restart"

BACKUP_AFTER=$(backup_and_verify "$EXPECTED_VERSION")
[[ "$BACKUP_AFTER" != "$BACKUP_BEFORE" ]] || fail "post-release backup missing"
[[ "$(migration_ledger "$DB_NAME")" == "$BEFORE_MIGRATIONS" ]] || fail "migration ledger changed after backup"
[[ "$(protected_hashes)" == "$BEFORE_FILES" ]] || fail "protected files changed after backup"
verify_four_services
wait_health || fail "backend unhealthy after post-release backup"
install -m 0600 "$RELEASE_DIR/RELEASE_MANIFEST.json" "$ROLLBACK_DIR/RELEASE_MANIFEST.json"
MUTATED=0
trap - EXIT
echo "release-success version=$EXPECTED_VERSION jar=$CANDIDATE_JAR_SHA backup-before=$BACKUP_BEFORE backup-after=$BACKUP_AFTER rollback=$ROLLBACK_DIR migrations=40:40 no-new-migration=yes configuration-and-static-sites-preserved=yes"
