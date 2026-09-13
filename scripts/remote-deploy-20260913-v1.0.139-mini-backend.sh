#!/usr/bin/env bash
# Backend + two additive migrations: preserve existing rows, secrets and static sites.
set -Eeuo pipefail
shopt -s inherit_errexit
umask 077
EXPECTED_VERSION=1.0.139
EXPECTED_PREVIOUS_VERSION=1.0.137
OLD_JAR_SHA=80663849f6f43f45180390f75f407f0bcf0fef2a60aa4df848be2fb9e6791c8f
APP_ROOT=/opt/lingqimall
DB_NAME=mall_distribution
SERVICE=lingqimall-distribution.service
RELEASE_DIR=${1:-}
MODE=${2:-}
EXPECTED_COMMIT=${LINGQIMALL_RELEASE_COMMIT:-}
[[ "$EXPECTED_COMMIT" =~ ^[a-f0-9]{40}$ ]]
[[ "$RELEASE_DIR" =~ ^/tmp/lingqimall-mini-backend-139\.[A-Za-z0-9]+$ ]]
[[ "$MODE" == --preflight-only || "$MODE" == --authorize-release ]]
[[ "${LINGQIMALL_RELEASE_AUTHORIZATION:-}" == "$EXPECTED_VERSION" ]]
[[ "$(hostname)" == VM-4-6-rockylinux && "$EUID" == 0 ]]
exec 8>/opt/lingqimall/.mini-backend-release.lock
flock -n 8
MUTATED=0
ROLLBACK_DIR=''
VERIFY_DB=''
START_TIME=$(date '+%Y-%m-%d %H:%M:%S')

fail() { echo "mini-backend-release-failed: $*" >&2; exit 1; }
mysql_db() { mysql --protocol=socket -uroot "$1" "${@:2}"; }
db_snapshot() {
  local database=$1
  mysql_db "$database" -NBe "SELECT CONCAT(
    (SELECT COUNT(*) FROM dms_shop_member), ':',
    (SELECT COUNT(*) FROM dms_shop_order), ':',
    (SELECT COUNT(*) FROM dms_commission_record), ':',
    (SELECT COUNT(*) FROM dms_member_asset_flow), ':',
    (SELECT COALESCE(SUM(balance),0) FROM dms_member_asset_account), ':',
    (SELECT COUNT(*) FROM dms_shop_product), ':',
    (SELECT COUNT(*) FROM dms_shop_category), ':',
    (SELECT COUNT(*) FROM dms_admin_user), ':',
    (SELECT COUNT(*) FROM dms_wechat_mini_program_identity), ':',
    (SELECT COUNT(*) FROM dms_mini_program_subscription_grant), ':',
    (SELECT COUNT(*) FROM dms_wechat_shipping_sync_task));
    "
  # Sensitive configuration is streamed only into a hash, never printed.
  mysqldump --protocol=socket -uroot --no-create-info --skip-comments --skip-add-locks \
    --skip-disable-keys --skip-extended-insert --order-by-primary --no-tablespaces \
    "$database" dms_tenant dms_tenant_display_config dms_message_channel_config \
    dms_message_cost_budget dms_commission_rule_version | sha256sum | awk '{print $1}'
}
migrate_database() {
  local database=$1
  MIGRATION_ROOT_DIR="$ROLLBACK_DIR/migration-root" DB_AUTH_MODE=socket DB_HOST=localhost DB_USER=root DB_NAME="$database" bash "$RELEASE_DIR/db-migrate.sh" apply
}
verify_new_schema() {
  local database=$1
  [[ "$(mysql_db "$database" -NBe 'SELECT CONCAT(COUNT(*),":",SUM(success=1)) FROM dms_schema_migration_history')" == 36:36 ]]
  [[ "$(mysql_db information_schema -NBe "SELECT COUNT(*) FROM tables WHERE table_schema='$database' AND table_name IN ('dms_shop_coupon','dms_shop_coupon_claim')")" == 2 ]]
  [[ "$(mysql_db information_schema -NBe "SELECT COUNT(*) FROM columns WHERE table_schema='$database' AND ((table_name='dms_shop_product_review' AND column_name IN ('merchant_reply_version','platform_reply_version','merchant_reply','merchant_reply_by','merchant_reply_time','platform_reply','platform_reply_by','platform_reply_time')) OR (table_name='dms_shop_order' AND column_name IN ('coupon_claim_id','coupon_title','coupon_refund_rule')) OR (table_name='dms_shop_order_item' AND column_name IN ('coupon_discount_amount','coupon_merchant_amount','coupon_bonus_base_amount')) OR (table_name='dms_shop_after_sale_item' AND column_name IN ('coupon_bonus_refund_amount','coupon_cost_refund_amount')))")" == 16 ]]
  [[ "$(mysql_db "$database" -NBe "SELECT (SELECT COUNT(*) FROM dms_shop_coupon)+(SELECT COUNT(*) FROM dms_shop_coupon_claim)+(SELECT COUNT(*) FROM dms_shop_order WHERE coupon_claim_id IS NOT NULL OR coupon_title IS NOT NULL OR coupon_refund_rule IS NOT NULL)+(SELECT COUNT(*) FROM dms_shop_order_item WHERE coupon_discount_amount IS NOT NULL OR coupon_merchant_amount IS NOT NULL OR coupon_bonus_base_amount IS NOT NULL)+(SELECT COUNT(*) FROM dms_shop_after_sale_item WHERE coupon_bonus_refund_amount IS NOT NULL OR coupon_cost_refund_amount IS NOT NULL)+(SELECT COUNT(*) FROM dms_shop_product_review WHERE merchant_reply_version<>0 OR platform_reply_version<>0 OR merchant_reply IS NOT NULL OR platform_reply IS NOT NULL OR merchant_reply_by IS NOT NULL OR platform_reply_by IS NOT NULL OR merchant_reply_time IS NOT NULL OR platform_reply_time IS NOT NULL)")" == 0 ]]
}
protected_hashes() {
  find /opt/lingqimall/config /etc/lingqimall /etc/systemd/system/lingqimall-distribution.service.d \
    /etc/nginx/conf.d /opt/lingqimall/nginx/admin /opt/lingqimall/nginx/shop /opt/lingqimall/nginx/team \
    -type f -print0 | sort -z | xargs -0 sha256sum
  sha256sum /etc/systemd/system/lingqimall-distribution.service /etc/nginx/nginx.conf
}
verify_disabled_fund_channels() {
  local pid
  pid=$(systemctl show "$SERVICE" -p MainPID --value)
  [[ "$pid" =~ ^[1-9][0-9]*$ ]]
  python3 - "$pid" <<'PY'
import re, sys
with open('/proc/' + sys.argv[1] + '/environ', 'rb') as f:
    entries = f.read().split(b'\0')
for entry in entries:
    key, _, value = entry.partition(b'=')
    normalized = re.sub('[^A-Z0-9]', '', key.decode(errors='replace').upper())
    danger = re.fullmatch(r'(SHOP)?(WECHATPAY|WITHDRAWALPAYOUT(ALIPAY|WECHAT)?|EXTERNALNOTIFICATION(WORKER)?|WECHATMINIPROGRAM(SUBSCRIBEMESSAGE|SHIPPINGINFO))ENABLED', normalized)
    if danger and value.lower() in (b'true', b'1'):
        print('unexpected_runtime_flag=' + key.decode(errors='replace'))
        raise SystemExit(1)
print('payment_payout_notification_environment_gates=unchanged_off')
PY
}
wait_health() {
  for attempt in $(seq 1 45); do
    if curl -fsS --max-time 3 http://127.0.0.1:8086/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then return 0; fi
    sleep 2
  done
  return 1
}
runtime_snapshot() {
  curl -fsS --max-time 12 -H 'X-Shop-Client: wechat-mini-program' -H 'X-Shop-Surface: mini-program' \
    http://127.0.0.1:8086/shop/wechat-mini-program/runtime | python3 -c 'import json,sys; d=json.load(sys.stdin); v=d["data"]; assert d["code"]==200 and v["enabled"] is True and v["phoneAuthorizationEnabled"] is True; print(json.dumps(v,sort_keys=True,separators=(",",":")))'
}
backup_and_verify() {
  local output backup_path
  output=$(DB_AUTH_MODE=socket DB_USER=root RETENTION_DAYS=365000 OFFSITE_BACKUP_DIR='' bash "$RELEASE_DIR/production-backup.sh")
  backup_path=$(sed -n 's/^backup completed: //p' <<< "$output")
  [[ "$backup_path" =~ ^/opt/lingqimall/backups/full/20[0-9]{6}_[0-9]{6}$ ]]
  (cd "$backup_path" && sha256sum -c SHA256SUMS >/dev/null && gzip -t database.sql.gz && tar -tzf files-and-config.tar.gz >/dev/null)
  printf '%s\n' "$backup_path"
}
recover() {
  local status=$?
  trap - EXIT
  if [[ "$status" != 0 && "$MUTATED" == 1 ]]; then
    echo "bounded-recovery=starting backup=$BACKUP_BEFORE" >&2
    set +e
    if systemctl stop "$SERVICE" \
      && install -m 0644 "$ROLLBACK_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar" \
      && install -m 0644 "$ROLLBACK_DIR/VERSION" "$APP_ROOT/VERSION" \
      && systemctl start "$SERVICE" && wait_health \
      && [[ "$(sha256sum "$APP_ROOT/app/mall-distribution.jar" | awk '{print $1}')" == "$OLD_JAR_SHA" ]]; then
      echo 'bounded-recovery=previous-backend-restored'
    else
      echo 'bounded-recovery=FAILED; immediate manual intervention required' >&2
    fi
    echo 'database_restore=not_performed; configuration_and_static_sites=untouched' >&2
  fi
  [[ -z "$VERIFY_DB" ]] || echo "isolated_restore_database_retained=$VERIFY_DB"
  exit "$status"
}
trap recover EXIT
trap 'echo "release-check-failed line=$LINENO function=${FUNCNAME[0]:-main}" >&2' ERR

for file in mall-distribution.jar VERSION RELEASE_MANIFEST.json SHA256SUMS release.sh production-backup.sh db-migrate.sh V202609121900__product_review_replies.sql V202609122000__shop_coupons.sql; do
  [[ -s "$RELEASE_DIR/$file" ]]
done
(cd "$RELEASE_DIR" && sha256sum -c SHA256SUMS)
python3 - "$RELEASE_DIR" <<'PY'
import hashlib, json, os, re, sys, zipfile
root = sys.argv[1]
with open(root + '/RELEASE_MANIFEST.json') as f:
    m = json.load(f)
assert m['version'] == '1.0.139' and m['scope'] == 'backend-only'
assert re.fullmatch(r'[a-f0-9]{40}', os.environ.get('LINGQIMALL_RELEASE_COMMIT', ''))
assert m['gitCommit'] == os.environ['LINGQIMALL_RELEASE_COMMIT']
assert m['buildId'] == '20260913-mini-backend-1.0.139'
with open(root + '/mall-distribution.jar', 'rb') as f:
    assert hashlib.sha256(f.read()).hexdigest() == m['jarSha256']
with zipfile.ZipFile(root + '/mall-distribution.jar') as z:
    assert 'BOOT-INF/classes/com/macro/mall/distribution/controller/MiniProgramAccountAuthController.class' in z.namelist()
assert m['databaseMigrations'] == ['V202609121900__product_review_replies.sql', 'V202609122000__shop_coupons.sql']
assert sorted(os.listdir(root)) == sorted(['mall-distribution.jar', 'VERSION', 'RELEASE_MANIFEST.json', 'SHA256SUMS', 'release.sh', 'production-backup.sh', 'db-migrate.sh'] + m['databaseMigrations'])
print('candidate_identity_and_backend_class=passed')
PY
[[ "$(tr -d '[:space:]' < "$RELEASE_DIR/VERSION")" == "$EXPECTED_VERSION" ]]
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "$EXPECTED_PREVIOUS_VERSION" ]]
[[ "$(sha256sum "$APP_ROOT/app/mall-distribution.jar" | awk '{print $1}')" == "$OLD_JAR_SHA" ]]
[[ "$(stat -c '%a %U:%G' /etc/lingqimall/wechat-mini-program.env)" == '600 root:root' ]]
[[ "$(df -Pm "$APP_ROOT" | awk 'NR==2 {print $4}')" -ge 4096 ]]
[[ -z "$(find "$APP_ROOT/backups/full" -mindepth 1 -maxdepth 1 -type d -name '.20??????_??????.tmp' -mtime +1 -print -quit)" ]]
for svc in "$SERVICE" nginx mysqld redis; do systemctl is-active --quiet "$svc"; done
redis-cli ping | grep -qx PONG
wait_health
verify_disabled_fund_channels
[[ "$(mysql_db "$DB_NAME" -NBe 'SELECT CONCAT(COUNT(*),":",SUM(success=1)) FROM dms_schema_migration_history')" == 34:34 ]]
[[ "$(mysql_db information_schema -NBe "SELECT COUNT(*) FROM tables WHERE table_schema='$DB_NAME' AND table_name IN ('dms_shop_coupon','dms_shop_coupon_claim')")" == 0 ]]
BEFORE_MIGRATIONS=$(mysql_db "$DB_NAME" -NBe 'SELECT version,checksum,success FROM dms_schema_migration_history ORDER BY version')
[[ "$(mysql_db information_schema -NBe "SELECT COUNT(*) FROM events WHERE event_schema='$DB_NAME'")" == 0 ]]
[[ "$(mysql_db "$DB_NAME" -NBe "SELECT (SELECT COUNT(*) FROM dms_bonus_calculation_task WHERE status IN (0,1))+(SELECT COUNT(*) FROM dms_erp_sync_task WHERE status IN (0,1,2))+(SELECT COUNT(*) FROM dms_line_change_application WHERE status IN (0,1))+(SELECT COUNT(*) FROM dms_shop_order WHERE status=0 AND create_time<=NOW()-INTERVAL 30 MINUTE)")" == 0 ]]
BEFORE_DB=$(db_snapshot "$DB_NAME")
BEFORE_FILES=$(protected_hashes)
BEFORE_RUNTIME=$(runtime_snapshot)
echo "release-preflight=passed previous=$EXPECTED_PREVIOUS_VERSION target=$EXPECTED_VERSION scope=backend-only"
if [[ "$MODE" == --preflight-only ]]; then exit 0; fi

ROLLBACK_DIR=$(mktemp -d /opt/lingqimall/backups/mini-backend-139.XXXXXX)
install -d -m 0700 "$ROLLBACK_DIR/migration-root/document/db/migrations"
install -m 0600 "$RELEASE_DIR/V202609121900__product_review_replies.sql" "$RELEASE_DIR/V202609122000__shop_coupons.sql" "$ROLLBACK_DIR/migration-root/document/db/migrations/"
install -m 0600 "$APP_ROOT/app/mall-distribution.jar" "$ROLLBACK_DIR/mall-distribution.jar"
install -m 0600 "$APP_ROOT/VERSION" "$ROLLBACK_DIR/VERSION"
BACKUP_BEFORE=$(backup_and_verify)
echo "backup-before=$BACKUP_BEFORE"
VERIFY_DB=mall_distribution_release_verify_139_$(date +%Y%m%d%H%M%S)
[[ "$VERIFY_DB" =~ ^mall_distribution_release_verify_139_[0-9]{14}$ ]]
[[ "$(mysql_db information_schema -NBe "SELECT COUNT(*) FROM schemata WHERE schema_name='$VERIFY_DB'")" == 0 ]]
DB_CHARSET=$(mysql_db information_schema -NBe "SELECT CONCAT(default_character_set_name,' ',default_collation_name) FROM schemata WHERE schema_name='$DB_NAME'")
[[ "$DB_CHARSET" =~ ^[A-Za-z0-9_]+\ [A-Za-z0-9_]+$ ]]
mysql --protocol=socket -uroot -e "CREATE DATABASE \`$VERIFY_DB\` CHARACTER SET ${DB_CHARSET%% *} COLLATE ${DB_CHARSET##* };"
gzip -dc "$BACKUP_BEFORE/database.sql.gz" | mysql --protocol=socket -uroot "$VERIFY_DB"
[[ "$(db_snapshot "$VERIFY_DB")" == "$BEFORE_DB" ]]
echo "backup-isolated-restore=passed database=$VERIFY_DB"
migrate_database "$VERIFY_DB"
verify_new_schema "$VERIFY_DB"
[[ "$(db_snapshot "$VERIFY_DB")" == "$BEFORE_DB" ]]
migrate_database "$VERIFY_DB"
verify_new_schema "$VERIFY_DB"
[[ "$(mysql_db "$VERIFY_DB" -NBe "SELECT version,checksum,success FROM dms_schema_migration_history WHERE version NOT IN ('202609121900','202609122000') ORDER BY version")" == "$BEFORE_MIGRATIONS" ]]
echo 'isolated-additive-migration-first-and-repeat=passed business-data-preserved=yes'
mysql --protocol=socket -uroot -e "DROP DATABASE \`$VERIFY_DB\`;"
VERIFY_DB=''
[[ "$(db_snapshot "$DB_NAME")" == "$BEFORE_DB" ]]
[[ "$(protected_hashes)" == "$BEFORE_FILES" ]]
[[ "$(runtime_snapshot)" == "$BEFORE_RUNTIME" ]]
[[ "$(sha256sum "$APP_ROOT/app/mall-distribution.jar" | awk '{print $1}')" == "$OLD_JAR_SHA" ]]

STDOUT_OFFSET=$(stat -c %s "$APP_ROOT/logs/distribution/stdout.log")
STDERR_OFFSET=$(stat -c %s "$APP_ROOT/logs/distribution/stderr.log")
MUTATED=1
systemctl stop "$SERVICE"
migrate_database "$DB_NAME"
verify_new_schema "$DB_NAME"
[[ "$(db_snapshot "$DB_NAME")" == "$BEFORE_DB" ]]
[[ "$(mysql_db "$DB_NAME" -NBe "SELECT version,checksum,success FROM dms_schema_migration_history WHERE version NOT IN ('202609121900','202609122000') ORDER BY version")" == "$BEFORE_MIGRATIONS" ]]
install -m 0644 "$RELEASE_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar"
install -m 0644 "$RELEASE_DIR/VERSION" "$APP_ROOT/VERSION"
systemctl start "$SERVICE"
wait_health
verify_disabled_fund_channels
[[ "$(runtime_snapshot)" == "$BEFORE_RUNTIME" ]]
for url in https://lingqimall.com/api/shop/home https://www.lingqimall.com/api/shop/home; do
  curl -fsS --max-time 12 "$url" | python3 -c 'import json,sys; assert json.load(sys.stdin)["code"]==200'
done
for path in /shop/media/member-avatar/1/avatar.png /shop/orders /shop/messages/unread /shop/wechat-mini-program/subscriptions /shop/wechat-mini-program/member-capabilities /shop/wechat-mini-program/bonus-summary; do
  [[ "$(curl -sS -o /dev/null -w '%{http_code}' --max-time 12 "https://lingqimall.com/api$path")" == 401 ]]
done
[[ "$(db_snapshot "$DB_NAME")" == "$BEFORE_DB" ]]
[[ "$(protected_hashes)" == "$BEFORE_FILES" ]]
[[ "$(sha256sum "$APP_ROOT/app/mall-distribution.jar" | awk '{print $1}')" == "$(sha256sum "$RELEASE_DIR/mall-distribution.jar" | awk '{print $1}')" ]]
# Empty payloads are rejected before authentication: no SMS, account creation or login.
for route in account-login account-register; do
  response_file="$ROLLBACK_DIR/check-$route.json"
  status=$(curl -sS --max-time 12 -o "$response_file" -w '%{http_code}' \
    -H 'Content-Type: application/json' -H 'X-Shop-Client: wechat-mini-program' \
    -H 'X-Shop-Surface: mini-program' --data '{}' \
    "https://lingqimall.com/api/shop/wechat-mini-program/auth/$route")
  [[ "$status" == 400 ]]
  python3 - "$response_file" <<'PY'
import json, sys
with open(sys.argv[1]) as f:
    data = json.load(f)
assert data.get('code') != 200 and not data.get('data')
print('native_account_route_invalid_payload_rejected=yes')
PY
done
sleep 10
journalctl -u "$SERVICE" --since "$START_TIME" --no-pager > "$ROLLBACK_DIR/new-journal.log"
if grep -Eq 'Application run failed|OutOfMemoryError|UnsatisfiedDependencyException|BeanCreationException' "$ROLLBACK_DIR/new-journal.log"; then fail 'startup failure'; fi
for log_spec in "stdout.log:$STDOUT_OFFSET" "stderr.log:$STDERR_OFFSET"; do
  log_name=${log_spec%%:*}
  log_offset=${log_spec##*:}
  [[ "$(stat -c %s "$APP_ROOT/logs/distribution/$log_name")" -ge "$log_offset" ]]
  tail -c +"$((log_offset + 1))" "$APP_ROOT/logs/distribution/$log_name" > "$ROLLBACK_DIR/new-$log_name"
  if grep -Eq 'Application run failed|OutOfMemoryError|UnsatisfiedDependencyException|BeanCreationException| ERROR ' "$ROLLBACK_DIR/new-$log_name"; then fail 'application log failure'; fi
done
BACKUP_AFTER=$(backup_and_verify)
[[ "$BACKUP_AFTER" != "$BACKUP_BEFORE" ]]
tar -tzf "$BACKUP_AFTER/files-and-config.tar.gz" > "$ROLLBACK_DIR/post-backup-files.txt"
grep -Fx 'etc/lingqimall/wechat-mini-program.env' "$ROLLBACK_DIR/post-backup-files.txt" >/dev/null
[[ "$(db_snapshot "$DB_NAME")" == "$BEFORE_DB" ]]
[[ "$(protected_hashes)" == "$BEFORE_FILES" ]]
[[ "$(runtime_snapshot)" == "$BEFORE_RUNTIME" ]]
for svc in "$SERVICE" nginx mysqld redis; do systemctl is-active --quiet "$svc"; done
install -m 0600 "$RELEASE_DIR/RELEASE_MANIFEST.json" "$ROLLBACK_DIR/RELEASE_MANIFEST.json"
verify_new_schema "$DB_NAME"
MUTATED=0
echo "release-success version=$EXPECTED_VERSION backup-before=$BACKUP_BEFORE backup-after=$BACKUP_AFTER rollback=$ROLLBACK_DIR migrations=36:36 additive-migrations=2 configuration-and-static-sites-preserved=yes payment-payout-unchanged=yes"
