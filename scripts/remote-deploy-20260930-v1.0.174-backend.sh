#!/usr/bin/env bash
# 1.0.174 closure backend release. Exactly one additive 44->45 migration is authorized.
set -Eeuo pipefail
shopt -s inherit_errexit
umask 077

EXPECTED_VERSION=1.0.174
EXPECTED_BUILD_ID=20260930-closure-1.0.174
EXPECTED_BUILD_METHOD=clean-build-in-release-process
EXPECTED_PREVIOUS_VERSION=1.0.173
EXPECTED_PREVIOUS_JAR_SHA=d3782cb36f1c8fd62822f7c7c79c7611ae53bacc150a2d13d5d9a8aac58d1b6e
EXPECTED_MIGRATIONS_BEFORE=44
EXPECTED_MIGRATIONS_AFTER=45
NEW_MIGRATION=V202609301800__direct_referral_immutable_rules.sql
NEW_MIGRATION_SHA=c61638a9bcff44c09a3f549a8412172e0b067662269038dbf67fd48e044e3f03
APP_ROOT=/opt/lingqimall
DB_NAME=mall_distribution
SERVICE=lingqimall-distribution.service
RELEASE_DIR=${1:-}
MODE=${2:-}
EXPECTED_COMMIT=${LINGQIMALL_RELEASE_COMMIT:-}
EXPECTED_SOURCE_TREE=${LINGQIMALL_RELEASE_SOURCE_TREE:-}

fail() { echo "closure-backend-release-aborted: $*" >&2; exit 1; }
[[ "$EUID" == 0 ]] || fail "must run as root"
[[ "$(hostname)" == VM-4-6-rockylinux ]] || fail "unexpected host"
[[ "$MODE" == --preflight-only || "$MODE" == --authorize-release ]] || fail "invalid mode"
[[ "${LINGQIMALL_RELEASE_AUTHORIZATION:-}" == "$EXPECTED_VERSION" ]] || fail "authorization missing"
[[ "$EXPECTED_COMMIT" =~ ^[a-f0-9]{40}$ ]] || fail "invalid commit"
[[ "$EXPECTED_SOURCE_TREE" =~ ^[a-f0-9]{40}$ ]] || fail "invalid source tree"
[[ "$RELEASE_DIR" =~ ^/tmp/lingqimall-closure-174\.[A-Za-z0-9]+$ ]] || fail "invalid release directory"

exec 8>"$APP_ROOT/.mini-backend-release.lock"
flock -n 8 || fail "another backend release is running"

MUTATED=0
ROLLBACK_DIR=''
DEPLOY_START_TIME=''
VERIFY_DB=''
VERIFY_DB_CREATED=0

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

verify_applied_migration_prefix() {
  local database=$1 expected_count=$2 index file base version digest existing
  local files=()
  while IFS= read -r file; do files+=("$file"); done \
    < <(find "$RELEASE_DIR/document/db/migrations" -maxdepth 1 -type f -name 'V*.sql' | LC_ALL=C sort)
  [[ "${#files[@]}" == "$EXPECTED_MIGRATIONS_AFTER" ]] || fail "candidate migration inventory mismatch"
  [[ "$(mysql_db "$database" -NBe 'SELECT CONCAT(COUNT(*),":",SUM(success=1)) FROM dms_schema_migration_history')" == "$expected_count:$expected_count" ]] \
    || fail "database migration count is not the expected successful prefix"
  for ((index=0; index<expected_count; index+=1)); do
    file=${files[$index]}
    base=$(basename "$file")
    version=${base:1:12}
    digest=$(sha256sum "$file" | awk '{print $1}')
    existing=$(mysql_db "$database" -NBe \
      "SELECT CONCAT(script,':',checksum,':',success) FROM dms_schema_migration_history WHERE version='$version' LIMIT 1")
    [[ "$existing" == "$base:$digest:1" ]] || fail "database migration prefix mismatch: $base"
  done
}

apply_module_migrations() {
  local database=$1
  # A verified 44-row prefix means the original migration runner skips 1..44
  # and executes only the fixed 45th SQL. A partial state is never resumed here.
  local state
  state=$(mysql_db "$database" -NBe 'SELECT CONCAT(COUNT(*),":",SUM(success=1)) FROM dms_schema_migration_history')
  [[ "$state" == 44:44 || "$state" == 45:45 ]] || fail "unexpected migration apply state: $state"
  verify_applied_migration_prefix "$database" "${state%%:*}"
  MIGRATION_ROOT_DIR="$RELEASE_DIR" DB_AUTH_MODE=socket DB_HOST=localhost DB_USER=root DB_NAME="$database" \
    bash "$RELEASE_DIR/db-migrate.sh" apply
  verify_applied_migration_prefix "$database" "$EXPECTED_MIGRATIONS_AFTER"
  verify_migrations "$database"
  verify_module_switch_schema "$database"
  verify_direct_referral_schema "$database"
}

verify_module_switch_schema() {
  local database=$1
  [[ "$(mysql_db information_schema -NBe "SELECT COUNT(*) FROM COLUMNS
    WHERE TABLE_SCHEMA='$database' AND TABLE_NAME='dms_tenant'
    AND COLUMN_NAME IN ('coupon_enabled','balance_transactions_enabled','multi_merchant_enabled','invitation_enabled')
    AND DATA_TYPE='tinyint' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT='1'")" == 4 ]] \
    || fail "module switch schema mismatch"
}

module_switch_snapshot() {
  mysql_db "$1" -NBe "SELECT id,coupon_enabled,balance_transactions_enabled,multi_merchant_enabled,invitation_enabled
    FROM dms_tenant ORDER BY id"
}

service_tag_schema_state() {
  local database=$1
  mysql_db information_schema -NBe "SELECT CONCAT(COUNT(*),':',COALESCE(MIN(LOWER(DATA_TYPE)),''))
    FROM COLUMNS WHERE TABLE_SCHEMA='$database' AND TABLE_NAME='dms_shop_order_item' AND COLUMN_NAME='service_tags';"
}

service_tag_snapshot() {
  local database=$1
  # Historical tags are purchase-time snapshots, not the current product value.
  # HEX keeps JSON whitespace/tabs/newlines safe and SQL NULL distinct from JSON null/[]/{}.
  mysql_db "$database" --batch --raw --skip-column-names -e \
    "SELECT CAST(id AS CHAR), IF(service_tags IS NULL, 'SQL_NULL',
      CONCAT('JSON_HEX:', HEX(CAST(service_tags AS CHAR CHARACTER SET utf8mb4))))
      FROM dms_shop_order_item ORDER BY id;"
}

verify_service_tag_snapshot_unchanged() {
  local database=$1 baseline=$2 current
  [[ -f "$baseline" ]] || fail "service tag baseline missing"
  current=$(mktemp /tmp/lingqimall-closure-174-tags.XXXXXX)
  if ! service_tag_snapshot "$database" > "$current"; then
    rm -f "$current"
    fail "service tag snapshot query failed"
  fi
  if ! python3 - "$baseline" "$current" <<'PY'
import pathlib
import re
import sys

def read_snapshot(filename):
    rows = {}
    for line in pathlib.Path(filename).read_text(encoding='ascii').splitlines():
        fields = line.split('\t')
        if len(fields) != 2 or not re.fullmatch(r'[0-9]+', fields[0]):
            raise SystemExit('invalid service tag snapshot row')
        key, value = fields
        if key in rows or not re.fullmatch(r'SQL_NULL|JSON_HEX:(?:[0-9A-F]{2})+', value):
            raise SystemExit('invalid or duplicate service tag snapshot')
        rows[key] = value
    return rows

before = read_snapshot(sys.argv[1])
after = read_snapshot(sys.argv[2])
if any(key not in after or after[key] != value for key, value in before.items()):
    raise SystemExit('historical service tag snapshot changed or disappeared')
# New orders after restart are allowed; every pre-existing row must remain exact.
print('historical-service-tags-preserved=yes rows=' + str(len(before)))
PY
  then
    rm -f "$current"
    fail "historical service tag snapshot verification failed"
  fi
  rm -f "$current"
}

migration_ledger() {
  local database=$1
  mysql_db "$database" -NBe \
    "SELECT CONCAT_WS('|',version,script,checksum,success) FROM dms_schema_migration_history ORDER BY version;"
}

verify_original_migration_ledger() {
  local database=$1 expected=$2 actual
  actual=$(mysql_db "$database" -NBe \
    "SELECT CONCAT_WS('|',version,script,checksum,success) FROM dms_schema_migration_history WHERE version <> '202609301800' ORDER BY version;")
  [[ "$actual" == "$expected" ]] || fail "historical 44 migration records changed"
}

verify_direct_referral_schema_before() {
  local database=$1
  [[ "$(mysql_db information_schema -NBe "SELECT COUNT(*) FROM COLUMNS WHERE TABLE_SCHEMA='$database' AND
    ((TABLE_NAME='dms_commission_rule_version' AND COLUMN_NAME='direct_referral_config')
    OR (TABLE_NAME='dms_commission_clawback' AND COLUMN_NAME='source_clawback_id')
    OR (TABLE_NAME='dms_order_relation_snapshot' AND COLUMN_NAME IN ('first_paid_order_eligible','target_promotion_eligible')))")" == 0 ]] \
    || fail "unregistered direct referral columns require review"
  [[ "$(mysql_db information_schema -NBe "SELECT COUNT(*) FROM TABLES WHERE TABLE_SCHEMA='$database' AND TABLE_NAME='dms_member_first_payment'")" == 0 ]] \
    || fail "unregistered first payment table requires review"
}

verify_direct_referral_schema() {
  local database=$1
  [[ "$(mysql_db information_schema -NBe "SELECT COUNT(*) FROM COLUMNS WHERE TABLE_SCHEMA='$database' AND IS_NULLABLE='YES' AND
    ((TABLE_NAME='dms_commission_rule_version' AND COLUMN_NAME='direct_referral_config' AND DATA_TYPE='text')
    OR (TABLE_NAME='dms_commission_clawback' AND COLUMN_NAME='source_clawback_id' AND DATA_TYPE='bigint')
    OR (TABLE_NAME='dms_order_relation_snapshot' AND COLUMN_NAME IN ('owner_agent_id','target_agent_id') AND DATA_TYPE='bigint')
    OR (TABLE_NAME='dms_order_relation_snapshot' AND COLUMN_NAME IN ('first_paid_order_eligible','target_promotion_eligible') AND DATA_TYPE='tinyint')))")" == 6 ]] \
    || fail "direct referral nullable field schema mismatch"
  [[ "$(mysql_db information_schema -NBe "SELECT CONCAT(ENGINE,':',TABLE_TYPE) FROM TABLES WHERE TABLE_SCHEMA='$database' AND TABLE_NAME='dms_member_first_payment'")" == InnoDB:BASE\ TABLE ]] \
    || fail "first payment table engine mismatch"
  [[ "$(mysql_db information_schema -NBe "SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM STATISTICS WHERE TABLE_SCHEMA='$database' AND TABLE_NAME='dms_member_first_payment' AND INDEX_NAME='PRIMARY'")" == tenant_id,user_id ]] \
    || fail "first payment tenant-scoped primary key mismatch"
}

verify_historical_new_fields_empty() {
  local database=$1
  [[ "$(mysql_db "$database" -NBe "SELECT CONCAT(
    (SELECT COUNT(*) FROM dms_commission_rule_version WHERE direct_referral_config IS NOT NULL),':',
    (SELECT COUNT(*) FROM dms_commission_clawback WHERE source_clawback_id IS NOT NULL),':',
    (SELECT COUNT(*) FROM dms_order_relation_snapshot WHERE first_paid_order_eligible IS NOT NULL OR target_promotion_eligible IS NOT NULL))")" == 0:0:0 ]] \
    || fail "migration populated a historical direct referral field"
}

# Hash every existing business column, not just row counts or money totals.
# HEX distinguishes NULL, empty strings, JSON whitespace and arbitrary binary.
# The immutable column inventory intentionally excludes only the migration ledger.
historical_table_digest() {
  local database=$1 table=$2 columns=$3 column expression='' encoded
  [[ "$table" =~ ^[A-Za-z0-9_]+$ ]] || fail "unsafe historical table"
  local column_array=()
  IFS=, read -r -a column_array <<<"$columns"
  [[ "${#column_array[@]}" -gt 0 ]] || fail "empty historical column inventory"
  for column in "${column_array[@]}"; do
    [[ "$column" =~ ^[A-Za-z0-9_]+$ ]] || fail "unsafe historical column"
    encoded="IF(\`$column\` IS NULL,'SQL_NULL',HEX(CAST(\`$column\` AS BINARY)))"
    expression+="${expression:+,}$encoded"
  done
  mysql_db "$database" --batch --raw --skip-column-names \
    -e "SELECT CONCAT_WS('|',$expression) FROM \`$table\`;" \
    | LC_ALL=C sort | sha256sum | awk '{print $1}'
}

capture_historical_business_data() {
  local database=$1 directory=$2 table columns digest
  mkdir -m 0700 "$directory"
  mysql_db information_schema -NBe "SELECT TABLE_NAME FROM TABLES
    WHERE TABLE_SCHEMA='$database' AND TABLE_TYPE='BASE TABLE'
    AND TABLE_NAME <> 'dms_schema_migration_history' ORDER BY TABLE_NAME" > "$directory/tables.before"
  : > "$directory/columns.before.tsv"
  : > "$directory/data.before.sha256"
  while IFS= read -r table; do
    [[ "$table" =~ ^[A-Za-z0-9_]+$ ]] || fail "unsafe business table inventory"
    columns=$(mysql_db information_schema -NBe "SELECT COLUMN_NAME FROM COLUMNS
      WHERE TABLE_SCHEMA='$database' AND TABLE_NAME='$table' ORDER BY ORDINAL_POSITION" | paste -sd, -)
    printf '%s\t%s\n' "$table" "$columns" >> "$directory/columns.before.tsv"
    digest=$(historical_table_digest "$database" "$table" "$columns") || fail "business snapshot query failed"
    [[ "$digest" =~ ^[a-f0-9]{64}$ ]] || fail "invalid business snapshot digest"
    printf '%s  %s\n' "$digest" "$table" >> "$directory/data.before.sha256"
  done < "$directory/tables.before"
  [[ -s "$directory/columns.before.tsv" ]] || fail "empty business data inventory"
}

verify_historical_business_data_unchanged() {
  local database=$1 directory=$2 table columns current inventory digest
  current=$(mktemp /tmp/lingqimall-closure-174-business.XXXXXX)
  inventory=$(mysql_db information_schema -NBe "SELECT TABLE_NAME FROM TABLES
    WHERE TABLE_SCHEMA='$database' AND TABLE_TYPE='BASE TABLE'
    AND TABLE_NAME NOT IN ('dms_schema_migration_history','dms_member_first_payment') ORDER BY TABLE_NAME")
  [[ "$inventory" == "$(sed '/^dms_member_first_payment$/d' "$directory/tables.before")" ]] \
    || { rm -f "$current"; fail "unexpected business table addition or removal"; }
  while IFS=$'\t' read -r table columns; do
    digest=$(historical_table_digest "$database" "$table" "$columns") \
      || { rm -f "$current"; fail "business verification query failed"; }
    [[ "$digest" =~ ^[a-f0-9]{64}$ ]] || { rm -f "$current"; fail "invalid business verification digest"; }
    printf '%s  %s\n' "$digest" "$table" >> "$current"
  done < "$directory/columns.before.tsv"
  if ! cmp -s "$directory/data.before.sha256" "$current"; then
    rm -f "$current"
    fail "pre-existing business data changed"
  fi
  rm -f "$current"
}

first_payment_expected_snapshot() {
  mysql_db "$1" -NBe "SELECT paid.tenant_id,paid.user_id,paid.id
    FROM dms_shop_order paid WHERE paid.pay_time IS NOT NULL AND paid.user_id > 0
    AND NOT EXISTS (SELECT 1 FROM dms_shop_order earlier WHERE earlier.tenant_id=paid.tenant_id
      AND earlier.user_id=paid.user_id AND earlier.pay_time IS NOT NULL
      AND (earlier.pay_time < paid.pay_time OR (earlier.pay_time=paid.pay_time AND earlier.id < paid.id)))
    ORDER BY paid.tenant_id,paid.user_id;"
}

first_payment_full_snapshot() {
  mysql_db "$1" -NBe "SELECT tenant_id,user_id,first_order_id,HEX(CAST(create_time AS BINARY))
    FROM dms_member_first_payment ORDER BY tenant_id,user_id;"
}

verify_first_payment_backfill() {
  local database=$1 expected=$2 mode=${3:-exact} current
  [[ "$mode" == exact || "$mode" == preserved ]] || fail "invalid first payment verification mode"
  current=$(mktemp /tmp/lingqimall-closure-174-first-payment.XXXXXX)
  if ! mysql_db "$database" -NBe "SELECT tenant_id,user_id,first_order_id FROM dms_member_first_payment ORDER BY tenant_id,user_id" > "$current"; then
    rm -f "$current"
    fail "first payment verification query failed"
  fi
  if ! python3 - "$expected" "$current" "$mode" <<'PY'
import pathlib, re, sys
def rows(path):
    result = {}
    for line in pathlib.Path(path).read_text(encoding='ascii').splitlines():
        fields = line.split('\t')
        assert len(fields) == 3 and all(re.fullmatch(r'[0-9]+', value) for value in fields)
        key = tuple(fields[:2])
        assert key not in result
        result[key] = fields[2]
    return result
expected, actual = rows(sys.argv[1]), rows(sys.argv[2])
assert all(actual.get(key) == value for key, value in expected.items()), 'historical first payment changed'
assert sys.argv[3] == 'preserved' or actual == expected, 'unexpected first payment backfill'
print('first-payment-backfill=' + sys.argv[3] + ' rows=' + str(len(expected)))
PY
  then
    rm -f "$current"
    fail "first payment historical backfill verification failed"
  fi
  rm -f "$current"
}

business_snapshot() {
  local database=$1
  mysql_db "$database" -NBe "SELECT CONCAT(
    (SELECT COUNT(*) FROM dms_shop_member), ':',
    (SELECT COUNT(*) FROM dms_shop_order), ':',
    (SELECT COUNT(*) FROM dms_shop_order_item), ':',
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
  body=$(mktemp /tmp/lingqimall-closure-174-unauthorized.XXXXXX)
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
  temporary=$(mktemp "$(dirname "$destination")/.release-174.$(basename "$destination").XXXXXX") || return 1
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
  set +e
  if [[ -n "$VERIFY_DB" && "$VERIFY_DB_CREATED" == 1 ]]; then
    mysql --protocol=socket -uroot -e "DROP DATABASE IF EXISTS \`$VERIFY_DB\`;"
    VERIFY_DB_CREATED=0
    VERIFY_DB=''
  fi
  if [[ "$status" != 0 && "$MUTATED" == 1 ]]; then
    if systemctl stop "$SERVICE" \
      && atomic_install "$ROLLBACK_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar" 0644 \
      && atomic_install "$ROLLBACK_DIR/VERSION" "$APP_ROOT/VERSION" 0644 \
      && systemctl start "$SERVICE" \
      && wait_health \
      && [[ "$(sha256sum "$APP_ROOT/app/mall-distribution.jar" | awk '{print $1}')" == "$EXPECTED_PREVIOUS_JAR_SHA" ]] \
      && [[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "$EXPECTED_PREVIOUS_VERSION" ]]; then
      echo "bounded-recovery=previous-backend-and-version-restored additive-migrations-retained=yes database-not-restored=yes rollback=$ROLLBACK_DIR" >&2
    else
      echo "bounded-recovery=FAILED; immediate manual intervention required" >&2
    fi
  fi
  exit "$status"
}
trap recover EXIT
trap 'echo "closure-backend-release-check-failed line=$LINENO function=${FUNCNAME[0]:-main}" >&2' ERR

for file in mall-distribution.jar VERSION RELEASE_MANIFEST.json SHA256SUMS release-backend.sh \
  db-migrate.sh production-backup.sh verify-artifact-retention.mjs; do
  [[ -s "$RELEASE_DIR/$file" ]] || fail "missing $file"
done
[[ -s "$RELEASE_DIR/document/db/migrations/$NEW_MIGRATION" ]] || fail "new migration is missing"
[[ "$(sha256sum "$RELEASE_DIR/document/db/migrations/$NEW_MIGRATION" | awk '{print $1}')" == "$NEW_MIGRATION_SHA" ]] \
  || fail "new migration checksum mismatch"
[[ -z "$(find "$RELEASE_DIR" -type l -print -quit)" ]] || fail "candidate contains a symbolic link"
(cd "$RELEASE_DIR" && sha256sum -c SHA256SUMS >/dev/null)

CANDIDATE_JAR_SHA=$(python3 - "$RELEASE_DIR" "$EXPECTED_COMMIT" "$EXPECTED_SOURCE_TREE" \
  "$EXPECTED_BUILD_ID" "$EXPECTED_BUILD_METHOD" <<'PY'
import hashlib, json, os, re, sys, zipfile
root, commit, source_tree, build_id, build_method = sys.argv[1:]
with open(os.path.join(root, 'RELEASE_MANIFEST.json')) as stream:
    manifest = json.load(stream)
assert manifest['version'] == '1.0.174'
assert manifest['scope'] == 'mall-closure-candidate'
assert manifest['gitCommit'] == commit
assert re.fullmatch(r'[a-f0-9]{40}', manifest.get('sourceTree', ''))
assert manifest['sourceTree'] == source_tree
assert manifest.get('buildId') == build_id == '20260930-closure-1.0.174'
assert manifest.get('buildMethod') == build_method == 'clean-build-in-release-process'
assert manifest['previousVersion'] == '1.0.173'
assert manifest['previousJarSha256'] == 'd3782cb36f1c8fd62822f7c7c79c7611ae53bacc150a2d13d5d9a8aac58d1b6e'
assert isinstance(manifest.get('generatedAt'), str) and manifest['generatedAt']
migrations = manifest['databaseMigrations']
assert isinstance(migrations, list) and len(migrations) == 45
assert migrations == sorted(migrations)
assert migrations[-1] == 'V202609301800__direct_referral_immutable_rules.sql'
assert all(re.fullmatch(r'V\d{12}__[a-z0-9_]+\.sql', name) for name in migrations)
actual_migrations = sorted(name for name in os.listdir(os.path.join(root, 'document/db/migrations')) if name.endswith('.sql'))
assert actual_migrations == migrations
new_migration = os.path.join(root, 'document/db/migrations', migrations[-1])
with open(new_migration, 'rb') as stream:
    assert hashlib.sha256(stream.read()).hexdigest() == 'c61638a9bcff44c09a3f549a8412172e0b067662269038dbf67fd48e044e3f03'
expected_additions = {
    'V202609261800__tenant_coupon_module_switch.sql': 'c3ffa626c65d08acdd1045b464c0a3f53989df85420f843e96eba6e482f276fb',
    'V202609262000__tenant_balance_and_merchant_mode_switches.sql': '71f738563f3a8ce7c374078c8e6a97c3a038eaf9d73748aadd811eb1298819e6',
    'V202609262130__tenant_invitation_switch.sql': '5e0dca4b5ec3530a1853f6e753aa12395f2f64e9fdc50d80c312abb703a903a2',
    'V202609301800__direct_referral_immutable_rules.sql': 'c61638a9bcff44c09a3f549a8412172e0b067662269038dbf67fd48e044e3f03',
}
assert migrations[41:] == list(expected_additions)
for name, digest in expected_additions.items():
    with open(os.path.join(root, 'document/db/migrations', name), 'rb') as stream:
        assert hashlib.sha256(stream.read()).hexdigest() == digest
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
[[ "$(find "$RELEASE_DIR/document/db/migrations" -maxdepth 1 -type f -name 'V*.sql' | wc -l | tr -d ' ')" == "$EXPECTED_MIGRATIONS_AFTER" ]] \
  || fail "candidate migration inventory mismatch"
MIGRATION_STATE=$(mysql_db "$DB_NAME" -NBe 'SELECT CONCAT(COUNT(*),":",SUM(success=1)) FROM dms_schema_migration_history')
case "$MIGRATION_STATE" in
  44:44) verify_applied_migration_prefix "$DB_NAME" "$EXPECTED_MIGRATIONS_BEFORE"
    verify_module_switch_schema "$DB_NAME"; verify_direct_referral_schema_before "$DB_NAME" ;;
  *) fail "production migration state mismatch (partial/failed migration requires review): $MIGRATION_STATE" ;;
esac
[[ "$(service_tag_schema_state "$DB_NAME")" == 1:json ]] || fail "service_tags column schema mismatch"
BEFORE_MIGRATIONS=$(migration_ledger "$DB_NAME")
[[ "$(mysql_db information_schema -NBe "SELECT COUNT(*) FROM events WHERE event_schema='$DB_NAME'")" == 0 ]] \
  || fail "unexpected database event"
[[ "$(df -Pm "$APP_ROOT" | awk 'NR==2 {print $4}')" -ge 4096 ]] || fail "insufficient free disk space"
command -v node >/dev/null || fail "node runtime is required before release preflight passes"
node -e 'if (Number(process.versions.node.split(".")[0]) < 20) process.exit(1)' \
  || fail "node runtime must be version 20 or newer"
node --check "$RELEASE_DIR/verify-artifact-retention.mjs" >/dev/null \
  || fail "retention verifier cannot run on this host"
verify_four_services
wait_health || fail "backend unhealthy"
BEFORE_FILES=$(protected_hashes)
BEFORE_RUNTIME=$(runtime_snapshot)
BEFORE_PAYMENT=$(payment_snapshot)
probe_public
echo "release-preflight=passed previous=$EXPECTED_PREVIOUS_VERSION target=$EXPECTED_VERSION migrations=$MIGRATION_STATE migration-mode=apply-44-to-45"
[[ "$MODE" == --authorize-release ]] || exit 0

CANDIDATE_ARCHIVE=${LINGQIMALL_CANDIDATE_ARCHIVE:-$RELEASE_DIR/candidate.tar.gz}
RETENTION_RECEIPT=${LINGQIMALL_RETENTION_RECEIPT:-$RELEASE_DIR/artifact-retention.json}
[[ -f "$CANDIDATE_ARCHIVE" ]] || fail "authorized release requires the exact candidate archive"
[[ -f "$RETENTION_RECEIPT" ]] || fail "authorized release requires the durable retention receipt"
command -v node >/dev/null || fail "node is required for durable retention verification"
node "$RELEASE_DIR/verify-artifact-retention.mjs" \
  --candidate "$CANDIDATE_ARCHIVE" --candidate-root "$RELEASE_DIR" \
  --receipt "$RETENTION_RECEIPT" >/dev/null \
  || fail "P0-10 durable retention verification failed"

ROLLBACK_DIR=$(mktemp -d /opt/lingqimall/backups/closure-backend-174.XXXXXX)
install -m 0600 "$APP_ROOT/app/mall-distribution.jar" "$ROLLBACK_DIR/mall-distribution.jar"
install -m 0600 "$APP_ROOT/VERSION" "$ROLLBACK_DIR/VERSION"
BACKUP_BEFORE=$(backup_and_verify "$EXPECTED_PREVIOUS_VERSION")
echo "backup-before=$BACKUP_BEFORE"

VERIFY_DB=m174v_$(date +%Y%m%d%H%M%S)
[[ "$VERIFY_DB" =~ ^m174v_[0-9]{14}$ ]] || fail "invalid verification database"
DB_CHARSET=$(mysql_db information_schema -NBe \
  "SELECT CONCAT(default_character_set_name,' ',default_collation_name) FROM schemata WHERE schema_name='$DB_NAME'")
mysql --protocol=socket -uroot -e \
  "CREATE DATABASE \`$VERIFY_DB\` CHARACTER SET ${DB_CHARSET%% *} COLLATE ${DB_CHARSET##* };"
VERIFY_DB_CREATED=1
gzip -dc "$BACKUP_BEFORE/database.sql.gz" | mysql --protocol=socket -uroot "$VERIFY_DB"
ISOLATED_BEFORE=$(business_snapshot "$VERIFY_DB")
service_tag_snapshot "$VERIFY_DB" > "$ROLLBACK_DIR/isolated-order-tags.before.tsv"
capture_historical_business_data "$VERIFY_DB" "$ROLLBACK_DIR/isolated-business"
first_payment_expected_snapshot "$VERIFY_DB" > "$ROLLBACK_DIR/isolated-first-payment.expected.tsv"
ISOLATED_SWITCHES=$(module_switch_snapshot "$VERIFY_DB")
verify_applied_migration_prefix "$VERIFY_DB" "${MIGRATION_STATE%%:*}"
verify_direct_referral_schema_before "$VERIFY_DB"
apply_module_migrations "$VERIFY_DB"
verify_original_migration_ledger "$VERIFY_DB" "$BEFORE_MIGRATIONS"
verify_historical_business_data_unchanged "$VERIFY_DB" "$ROLLBACK_DIR/isolated-business"
verify_historical_new_fields_empty "$VERIFY_DB"
verify_first_payment_backfill "$VERIFY_DB" "$ROLLBACK_DIR/isolated-first-payment.expected.tsv" exact
[[ "$(module_switch_snapshot "$VERIFY_DB")" == "$ISOLATED_SWITCHES" ]] \
  || fail "isolated migration first run changed module switches"
verify_service_tag_snapshot_unchanged "$VERIFY_DB" "$ROLLBACK_DIR/isolated-order-tags.before.tsv"
ISOLATED_FIRST_PAYMENTS=$(first_payment_full_snapshot "$VERIFY_DB")
apply_module_migrations "$VERIFY_DB"
verify_original_migration_ledger "$VERIFY_DB" "$BEFORE_MIGRATIONS"
verify_historical_business_data_unchanged "$VERIFY_DB" "$ROLLBACK_DIR/isolated-business"
verify_historical_new_fields_empty "$VERIFY_DB"
verify_first_payment_backfill "$VERIFY_DB" "$ROLLBACK_DIR/isolated-first-payment.expected.tsv" exact
[[ "$(first_payment_full_snapshot "$VERIFY_DB")" == "$ISOLATED_FIRST_PAYMENTS" ]] \
  || fail "isolated migration rerun changed first payment markers"
[[ "$(module_switch_snapshot "$VERIFY_DB")" == "$ISOLATED_SWITCHES" ]] \
  || fail "isolated migration rerun changed module switches"
[[ "$(service_tag_schema_state "$VERIFY_DB")" == 1:json ]] || fail "isolated service_tags schema verification failed"
verify_service_tag_snapshot_unchanged "$VERIFY_DB" "$ROLLBACK_DIR/isolated-order-tags.before.tsv"
[[ "$(business_snapshot "$VERIFY_DB")" == "$ISOLATED_BEFORE" ]] \
  || fail "isolated verification changed business state"
mysql --protocol=socket -uroot -e "DROP DATABASE \`$VERIFY_DB\`;"
VERIFY_DB_CREATED=0
VERIFY_DB=''

MUTATED=1
DEPLOY_START_TIME=$(date '+%Y-%m-%d %H:%M:%S')
systemctl stop "$SERVICE"
if systemctl is-active --quiet "$SERVICE"; then fail "backend did not stop"; fi
STOPPED_DATABASE=$(business_snapshot "$DB_NAME")
service_tag_snapshot "$DB_NAME" > "$ROLLBACK_DIR/production-order-tags.before.tsv"
[[ "$(migration_ledger "$DB_NAME")" == "$BEFORE_MIGRATIONS" ]] || fail "migration ledger changed before replacement"
# Only the fixed 45th additive SQL may run after the full isolated rehearsal.
# Existing business data and opt-ins remain exact; first payment rows are a
# precise, independently recomputed historical-paid-order increment.
STOPPED_SWITCHES=$(module_switch_snapshot "$DB_NAME")
capture_historical_business_data "$DB_NAME" "$ROLLBACK_DIR/production-business"
first_payment_expected_snapshot "$DB_NAME" > "$ROLLBACK_DIR/production-first-payment.expected.tsv"
verify_applied_migration_prefix "$DB_NAME" "$EXPECTED_MIGRATIONS_BEFORE"
verify_direct_referral_schema_before "$DB_NAME"
apply_module_migrations "$DB_NAME"
verify_original_migration_ledger "$DB_NAME" "$BEFORE_MIGRATIONS"
verify_historical_business_data_unchanged "$DB_NAME" "$ROLLBACK_DIR/production-business"
verify_historical_new_fields_empty "$DB_NAME"
verify_first_payment_backfill "$DB_NAME" "$ROLLBACK_DIR/production-first-payment.expected.tsv" exact
verify_module_switch_schema "$DB_NAME"
[[ "$(module_switch_snapshot "$DB_NAME")" == "$STOPPED_SWITCHES" ]] || fail "verification changed module switches"
[[ "$(service_tag_schema_state "$DB_NAME")" == 1:json ]] || fail "production service_tags schema verification failed"
verify_service_tag_snapshot_unchanged "$DB_NAME" "$ROLLBACK_DIR/production-order-tags.before.tsv"
[[ "$(business_snapshot "$DB_NAME")" == "$STOPPED_DATABASE" ]] \
  || fail "production additive migration changed pre-existing business state"
AFTER_MIGRATIONS=$(migration_ledger "$DB_NAME")
atomic_install "$RELEASE_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar" 0644 \
  || fail "candidate JAR replacement failed"
atomic_install "$RELEASE_DIR/VERSION" "$APP_ROOT/VERSION" 0644 \
  || fail "candidate VERSION replacement failed"
[[ "$(business_snapshot "$DB_NAME")" == "$STOPPED_DATABASE" ]] || fail "database changed during file replacement"
verify_historical_business_data_unchanged "$DB_NAME" "$ROLLBACK_DIR/production-business"
verify_first_payment_backfill "$DB_NAME" "$ROLLBACK_DIR/production-first-payment.expected.tsv" exact
[[ "$(migration_ledger "$DB_NAME")" == "$AFTER_MIGRATIONS" ]] || fail "migration ledger changed during file replacement"
systemctl start "$SERVICE"
wait_health || fail "new backend unhealthy"

[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "$EXPECTED_VERSION" ]] || fail "new VERSION not active"
[[ "$(sha256sum "$APP_ROOT/app/mall-distribution.jar" | awk '{print $1}')" == "$CANDIDATE_JAR_SHA" ]] \
  || fail "new JAR not active"
verify_four_services
verify_migrations "$DB_NAME"
verify_direct_referral_schema "$DB_NAME"
verify_first_payment_backfill "$DB_NAME" "$ROLLBACK_DIR/production-first-payment.expected.tsv" preserved
[[ "$(migration_ledger "$DB_NAME")" == "$AFTER_MIGRATIONS" ]] || fail "migration ledger changed after restart"
[[ "$(service_tag_schema_state "$DB_NAME")" == 1:json ]] || fail "service_tags schema changed after restart"
verify_service_tag_snapshot_unchanged "$DB_NAME" "$ROLLBACK_DIR/production-order-tags.before.tsv"
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
[[ "$(migration_ledger "$DB_NAME")" == "$AFTER_MIGRATIONS" ]] || fail "migration ledger changed after backup"
verify_first_payment_backfill "$DB_NAME" "$ROLLBACK_DIR/production-first-payment.expected.tsv" preserved
[[ "$(protected_hashes)" == "$BEFORE_FILES" ]] || fail "protected files changed after backup"
verify_service_tag_snapshot_unchanged "$DB_NAME" "$ROLLBACK_DIR/production-order-tags.before.tsv"
verify_four_services
wait_health || fail "backend unhealthy after post-release backup"
install -m 0600 "$RELEASE_DIR/RELEASE_MANIFEST.json" "$ROLLBACK_DIR/RELEASE_MANIFEST.json"
MUTATED=0
trap - EXIT
echo "release-success version=$EXPECTED_VERSION jar=$CANDIDATE_JAR_SHA backup-before=$BACKUP_BEFORE backup-after=$BACKUP_AFTER rollback=$ROLLBACK_DIR migrations=45:45 migration-mode=apply-44-to-45 historical-business-data-preserved=yes first-payment-historical-backfill-verified=yes configuration-and-static-sites-preserved=yes"
