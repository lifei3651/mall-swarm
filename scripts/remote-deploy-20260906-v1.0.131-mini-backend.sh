#!/usr/bin/env bash
# Backend-only: preserve all existing secrets/configuration, static sites and customer data.
set -Eeuo pipefail
shopt -s inherit_errexit
umask 077
EXPECTED_VERSION=1.0.131
EXPECTED_PREVIOUS_VERSION=1.0.126
OLD_JAR_SHA=9756cb0147fe0e4bd1c9f1a6dff603e93b912eb0d629a0e562aa4bbeb2eeac6c
APP_ROOT=/opt/lingqimall
DB_NAME=mall_distribution
SERVICE=lingqimall-distribution.service
RELEASE_DIR=${1:-}
MODE=${2:-}
[[ "$RELEASE_DIR" =~ ^/tmp/lingqimall-mini-backend-131\.[A-Za-z0-9]+$ ]]
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
    SELECT CONCAT(COUNT(*),':',SUM(success=1)) FROM dms_schema_migration_history;"
  # Sensitive configuration is streamed only into a hash, never printed.
  mysqldump --protocol=socket -uroot --no-create-info --skip-comments --skip-add-locks \
    --skip-disable-keys --skip-extended-insert --order-by-primary --no-tablespaces \
    "$database" dms_tenant dms_tenant_display_config dms_message_channel_config \
    dms_message_cost_budget dms_commission_rule_version | sha256sum | awk '{print $1}'
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

for file in mall-distribution.jar VERSION RELEASE_MANIFEST.json SHA256SUMS release.sh production-backup.sh; do
  [[ -s "$RELEASE_DIR/$file" ]]
done
(cd "$RELEASE_DIR" && sha256sum -c SHA256SUMS)
python3 - "$RELEASE_DIR" <<'PY'
import hashlib, json, os, re, sys, zipfile
root = sys.argv[1]
with open(root + '/RELEASE_MANIFEST.json') as f:
    m = json.load(f)
assert m['version'] == '1.0.131' and m['scope'] == 'backend-only'
assert m['gitCommit'] == '596aac592369d75a5897e3d2d83a7b88417d1965'
assert m['buildId'] == '20260906-mini-backend-1.0.131'
with open(root + '/mall-distribution.jar', 'rb') as f:
    assert hashlib.sha256(f.read()).hexdigest() == m['jarSha256']
with zipfile.ZipFile(root + '/mall-distribution.jar') as z:
    assert 'BOOT-INF/classes/com/macro/mall/distribution/service/WeChatMiniProgramMemberService.class' in z.namelist()
assert sorted(os.listdir(root)) == sorted(['mall-distribution.jar', 'VERSION', 'RELEASE_MANIFEST.json', 'SHA256SUMS', 'release.sh', 'production-backup.sh'])
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
[[ "$(mysql_db information_schema -NBe "SELECT COUNT(*) FROM events WHERE event_schema='$DB_NAME'")" == 0 ]]
[[ "$(mysql_db "$DB_NAME" -NBe "SELECT (SELECT COUNT(*) FROM dms_bonus_calculation_task WHERE status IN (0,1))+(SELECT COUNT(*) FROM dms_erp_sync_task WHERE status IN (0,1,2))+(SELECT COUNT(*) FROM dms_line_change_application WHERE status IN (0,1))+(SELECT COUNT(*) FROM dms_shop_order WHERE status=0 AND create_time<=NOW()-INTERVAL 30 MINUTE)")" == 0 ]]
BEFORE_DB=$(db_snapshot "$DB_NAME")
BEFORE_FILES=$(protected_hashes)
BEFORE_RUNTIME=$(runtime_snapshot)
echo "release-preflight=passed previous=$EXPECTED_PREVIOUS_VERSION target=$EXPECTED_VERSION scope=backend-only"
if [[ "$MODE" == --preflight-only ]]; then exit 0; fi

ROLLBACK_DIR=$(mktemp -d /opt/lingqimall/backups/mini-backend-131.XXXXXX)
install -m 0600 "$APP_ROOT/app/mall-distribution.jar" "$ROLLBACK_DIR/mall-distribution.jar"
install -m 0600 "$APP_ROOT/VERSION" "$ROLLBACK_DIR/VERSION"
BACKUP_BEFORE=$(backup_and_verify)
echo "backup-before=$BACKUP_BEFORE"
VERIFY_DB=mall_distribution_release_verify_131_$(date +%Y%m%d%H%M%S)
[[ "$VERIFY_DB" =~ ^mall_distribution_release_verify_131_[0-9]{14}$ ]]
[[ "$(mysql_db information_schema -NBe "SELECT COUNT(*) FROM schemata WHERE schema_name='$VERIFY_DB'")" == 0 ]]
DB_CHARSET=$(mysql_db information_schema -NBe "SELECT CONCAT(default_character_set_name,' ',default_collation_name) FROM schemata WHERE schema_name='$DB_NAME'")
[[ "$DB_CHARSET" =~ ^[A-Za-z0-9_]+\ [A-Za-z0-9_]+$ ]]
mysql --protocol=socket -uroot -e "CREATE DATABASE \`$VERIFY_DB\` CHARACTER SET ${DB_CHARSET%% *} COLLATE ${DB_CHARSET##* };"
gzip -dc "$BACKUP_BEFORE/database.sql.gz" | mysql --protocol=socket -uroot "$VERIFY_DB"
[[ "$(db_snapshot "$VERIFY_DB")" == "$BEFORE_DB" ]]
echo "backup-isolated-restore=passed database=$VERIFY_DB"
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
install -m 0644 "$RELEASE_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar"
install -m 0644 "$RELEASE_DIR/VERSION" "$APP_ROOT/VERSION"
systemctl start "$SERVICE"
wait_health
verify_disabled_fund_channels
[[ "$(runtime_snapshot)" == "$BEFORE_RUNTIME" ]]
for url in https://lingqimall.com/api/shop/home https://www.lingqimall.com/api/shop/home; do
  curl -fsS --max-time 12 "$url" | python3 -c 'import json,sys; assert json.load(sys.stdin)["code"]==200'
done
for path in /shop/media/member-avatar/1/avatar.png /shop/orders /shop/messages/unread /shop/wechat-mini-program/subscriptions /shop/wechat-mini-program/member-capabilities; do
  [[ "$(curl -sS -o /dev/null -w '%{http_code}' --max-time 12 "https://lingqimall.com/api$path")" == 401 ]]
done
[[ "$(db_snapshot "$DB_NAME")" == "$BEFORE_DB" ]]
[[ "$(protected_hashes)" == "$BEFORE_FILES" ]]
[[ "$(sha256sum "$APP_ROOT/app/mall-distribution.jar" | awk '{print $1}')" == "$(sha256sum "$RELEASE_DIR/mall-distribution.jar" | awk '{print $1}')" ]]
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
MUTATED=0
echo "release-success version=$EXPECTED_VERSION backup-before=$BACKUP_BEFORE backup-after=$BACKUP_AFTER rollback=$ROLLBACK_DIR migrations=34:34 no-data-migrations=yes configuration-and-static-sites-preserved=yes payment-payout-unchanged=yes"
