#!/usr/bin/env bash
# Backend + two additive WeChat logistics migrations. Preserve business rows, secrets and all static sites.
set -Eeuo pipefail
shopt -s inherit_errexit
umask 077

EXPECTED_VERSION=1.0.149
EXPECTED_PREVIOUS_VERSION=1.0.146
EXPECTED_OLD_JAR_SHA=014df28fa9123b1c00d51e2a4aa075dd5ea8b56e95ac4ce7f4528c6ca844015e
APP_ROOT=/opt/lingqimall
DB_NAME=mall_distribution
SERVICE=lingqimall-distribution.service
RELEASE_DIR=${1:-}
MODE=${2:-}
EXPECTED_COMMIT=${LINGQIMALL_RELEASE_COMMIT:-}
[[ "$EXPECTED_COMMIT" =~ ^[a-f0-9]{40}$ ]]
[[ "$RELEASE_DIR" =~ ^/tmp/lingqimall-closure-backend-149\.[A-Za-z0-9]+$ ]]
[[ "$MODE" == --preflight-only || "$MODE" == --authorize-release ]]
[[ "${LINGQIMALL_RELEASE_AUTHORIZATION:-}" == "$EXPECTED_VERSION" ]]
[[ "$(hostname)" == VM-4-6-rockylinux && "$EUID" == 0 ]]

exec 8>/opt/lingqimall/.mini-backend-release.lock
flock -n 8
MUTATED=0
ROLLBACK_DIR=''
VERIFY_DB=''
START_TIME=$(date '+%Y-%m-%d %H:%M:%S')

mysql_db() { mysql --protocol=socket -uroot "$1" "${@:2}"; }
wait_health() {
  for attempt in $(seq 1 45); do
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
business_snapshot() {
  local database=$1
  mysql_db "$database" -NBe "SELECT CONCAT(
    (SELECT COUNT(*) FROM dms_shop_member), ':',
    (SELECT COUNT(*) FROM dms_shop_order), ':',
    (SELECT COUNT(*) FROM dms_shop_after_sale), ':',
    (SELECT COUNT(*) FROM dms_commission_record), ':',
    (SELECT COUNT(*) FROM dms_member_asset_flow), ':',
    (SELECT COALESCE(SUM(balance),0) FROM dms_member_asset_account), ':',
    (SELECT COUNT(*) FROM dms_shop_product), ':',
    (SELECT COUNT(*) FROM dms_admin_user));"
}
runtime_snapshot() {
  curl -fsS --max-time 12 -H 'X-Shop-Client: wechat-mini-program' -H 'X-Shop-Surface: mini-program' \
    http://127.0.0.1:8086/shop/wechat-mini-program/runtime \
    | python3 -c 'import json,sys; d=json.load(sys.stdin); v=d["data"]; assert d["code"]==200 and v["enabled"] is True and v["phoneAuthorizationEnabled"] is True and v["shippingInfoEnabled"] is True; print(json.dumps(v,sort_keys=True,separators=(",",":")))'
}
shipping_snapshot() {
  mysql_db "$DB_NAME" -NBe "SELECT CONCAT(id,':',status,':',revision,':',synced_revision,':',attempt_count,':',IFNULL(error_code,'NULL')) FROM dms_wechat_shipping_sync_task ORDER BY id"
}
migrate_database() {
  local database=$1
  MIGRATION_ROOT_DIR="$ROLLBACK_DIR/migration-root" DB_AUTH_MODE=socket DB_HOST=localhost DB_USER=root DB_NAME="$database" \
    bash "$RELEASE_DIR/db-migrate.sh" apply
}
verify_schema() {
  local database=$1
  [[ "$(mysql_db "$database" -NBe 'SELECT CONCAT(COUNT(*),":",SUM(success=1)) FROM dms_schema_migration_history')" == 38:38 ]]
  [[ "$(mysql_db information_schema -NBe "SELECT COUNT(*) FROM tables WHERE table_schema='$database' AND table_name IN ('dms_wechat_logistics_follow_task','dms_wechat_express_order')")" == 2 ]]
  [[ "$(mysql_db "$database" -NBe 'SELECT (SELECT COUNT(*) FROM dms_wechat_logistics_follow_task)+(SELECT COUNT(*) FROM dms_wechat_express_order)')" == 0 ]]
  [[ "$(mysql_db information_schema -NBe "SELECT COUNT(DISTINCT CONCAT(table_name,':',index_name)) FROM statistics WHERE table_schema='$database' AND index_name IN ('uk_wechat_logistics_shipment','idx_wechat_logistics_due','uk_wechat_express_request','uk_wechat_express_order_no','idx_wechat_express_shipment','idx_wechat_express_status')")" == 6 ]]
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
    set +e
    systemctl stop "$SERVICE"
    install -m 0644 "$ROLLBACK_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar"
    install -m 0644 "$ROLLBACK_DIR/VERSION" "$APP_ROOT/VERSION"
    systemctl start "$SERVICE"
    if wait_health && [[ "$(sha256sum "$APP_ROOT/app/mall-distribution.jar" | awk '{print $1}')" == "$EXPECTED_OLD_JAR_SHA" ]]; then
      echo "bounded-recovery=previous-backend-restored backup=$ROLLBACK_DIR additive-tables-retained=yes" >&2
    else
      echo 'bounded-recovery=FAILED; immediate manual intervention required' >&2
    fi
  fi
  [[ -z "$VERIFY_DB" ]] || echo "isolated-restore-database-retained=$VERIFY_DB" >&2
  exit "$status"
}
trap recover EXIT
trap 'echo "backend-release-check-failed line=$LINENO function=${FUNCNAME[0]:-main}" >&2' ERR

for file in mall-distribution.jar VERSION RELEASE_MANIFEST.json SHA256SUMS release.sh production-backup.sh db-migrate.sh \
  V202609201430__wechat_logistics_follow_tasks.sql V202609201500__wechat_express_orders.sql; do [[ -s "$RELEASE_DIR/$file" ]]; done
(cd "$RELEASE_DIR" && sha256sum -c SHA256SUMS)
python3 - "$RELEASE_DIR" <<'PY'
import hashlib, json, os, sys, zipfile
root = sys.argv[1]
with open(root + '/RELEASE_MANIFEST.json') as stream: manifest = json.load(stream)
assert manifest['version'] == '1.0.149' and manifest['scope'] == 'closure-backend-with-logistics-migrations'
assert manifest['gitCommit'] == os.environ['LINGQIMALL_RELEASE_COMMIT']
assert manifest['previousVersion'] == '1.0.146'
assert manifest['previousJarSha256'] == '014df28fa9123b1c00d51e2a4aa075dd5ea8b56e95ac4ce7f4528c6ca844015e'
assert manifest['databaseMigrations'] == ['V202609201430__wechat_logistics_follow_tasks.sql','V202609201500__wechat_express_orders.sql']
with open(root + '/mall-distribution.jar','rb') as stream: assert hashlib.sha256(stream.read()).hexdigest() == manifest['jarSha256']
with zipfile.ZipFile(root + '/mall-distribution.jar') as archive:
    names = set(archive.namelist())
    for name in [
      'BOOT-INF/classes/com/macro/mall/distribution/service/WeChatLogisticsFollowService.class',
      'BOOT-INF/classes/com/macro/mall/distribution/service/WeChatExpressDeliveryService.class',
      'BOOT-INF/classes/com/macro/mall/distribution/service/WeChatExpressCancellationStateService.class',
      'BOOT-INF/classes/com/macro/mall/distribution/controller/ShopController.class',
    ]: assert name in names
expected = {'mall-distribution.jar','VERSION','RELEASE_MANIFEST.json','SHA256SUMS','release.sh','production-backup.sh','db-migrate.sh',*manifest['databaseMigrations']}
assert set(os.listdir(root)) == expected
print('backend-candidate-identity=passed')
PY
[[ "$(tr -d '[:space:]' < "$RELEASE_DIR/VERSION")" == "$EXPECTED_VERSION" ]]
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "$EXPECTED_PREVIOUS_VERSION" ]]
[[ "$(sha256sum "$APP_ROOT/app/mall-distribution.jar" | awk '{print $1}')" == "$EXPECTED_OLD_JAR_SHA" ]]
[[ "$(df -Pm "$APP_ROOT" | awk 'NR==2 {print $4}')" -ge 4096 ]]
for svc in "$SERVICE" nginx mysqld redis; do systemctl is-active --quiet "$svc"; done
redis-cli ping | grep -qx PONG
wait_health
[[ "$(mysql_db "$DB_NAME" -NBe 'SELECT CONCAT(COUNT(*),":",SUM(success=1)) FROM dms_schema_migration_history')" == 36:36 ]]
[[ "$(mysql_db information_schema -NBe "SELECT COUNT(*) FROM tables WHERE table_schema='$DB_NAME' AND table_name IN ('dms_wechat_logistics_follow_task','dms_wechat_express_order')")" == 0 ]]
BEFORE_MIGRATIONS=$(mysql_db "$DB_NAME" -NBe 'SELECT version,checksum,success FROM dms_schema_migration_history ORDER BY version')
BEFORE_BUSINESS=$(business_snapshot "$DB_NAME")
BEFORE_FILES=$(protected_hashes)
BEFORE_RUNTIME=$(runtime_snapshot)
BEFORE_SHIPPING=$(shipping_snapshot)
echo "release-preflight=passed previous=$EXPECTED_PREVIOUS_VERSION target=$EXPECTED_VERSION migrations=36:36"
[[ "$MODE" != --preflight-only ]] || exit 0

ROLLBACK_DIR=$(mktemp -d /opt/lingqimall/backups/closure-backend-149.XXXXXX)
install -d -m 0700 "$ROLLBACK_DIR/migration-root/document/db/migrations"
install -m 0600 "$RELEASE_DIR"/V202609201{430__wechat_logistics_follow_tasks,500__wechat_express_orders}.sql "$ROLLBACK_DIR/migration-root/document/db/migrations/"
install -m 0600 "$APP_ROOT/app/mall-distribution.jar" "$ROLLBACK_DIR/mall-distribution.jar"
install -m 0600 "$APP_ROOT/VERSION" "$ROLLBACK_DIR/VERSION"
BACKUP_BEFORE=$(backup_and_verify)
VERIFY_DB=mall_distribution_release_verify_149_$(date +%Y%m%d%H%M%S)
[[ "$VERIFY_DB" =~ ^mall_distribution_release_verify_149_[0-9]{14}$ ]]
DB_CHARSET=$(mysql_db information_schema -NBe "SELECT CONCAT(default_character_set_name,' ',default_collation_name) FROM schemata WHERE schema_name='$DB_NAME'")
mysql --protocol=socket -uroot -e "CREATE DATABASE \`$VERIFY_DB\` CHARACTER SET ${DB_CHARSET%% *} COLLATE ${DB_CHARSET##* };"
gzip -dc "$BACKUP_BEFORE/database.sql.gz" | mysql --protocol=socket -uroot "$VERIFY_DB"
[[ "$(business_snapshot "$VERIFY_DB")" == "$BEFORE_BUSINESS" ]]
migrate_database "$VERIFY_DB"
verify_schema "$VERIFY_DB"
migrate_database "$VERIFY_DB"
[[ "$(mysql_db "$VERIFY_DB" -NBe "SELECT version,checksum,success FROM dms_schema_migration_history WHERE version NOT IN ('202609201430','202609201500') ORDER BY version")" == "$BEFORE_MIGRATIONS" ]]
[[ "$(business_snapshot "$VERIFY_DB")" == "$BEFORE_BUSINESS" ]]
mysql --protocol=socket -uroot -e "DROP DATABASE \`$VERIFY_DB\`;"
VERIFY_DB=''

MUTATED=1
systemctl stop "$SERVICE"
migrate_database "$DB_NAME"
verify_schema "$DB_NAME"
[[ "$(mysql_db "$DB_NAME" -NBe "SELECT version,checksum,success FROM dms_schema_migration_history WHERE version NOT IN ('202609201430','202609201500') ORDER BY version")" == "$BEFORE_MIGRATIONS" ]]
[[ "$(business_snapshot "$DB_NAME")" == "$BEFORE_BUSINESS" ]]
install -m 0644 "$RELEASE_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar"
install -m 0644 "$RELEASE_DIR/VERSION" "$APP_ROOT/VERSION"
systemctl start "$SERVICE"
wait_health
[[ "$(runtime_snapshot)" == "$BEFORE_RUNTIME" ]]
[[ "$(shipping_snapshot)" == "$BEFORE_SHIPPING" ]]
[[ "$(protected_hashes)" == "$BEFORE_FILES" ]]
for url in https://lingqimall.com/api/shop/home https://www.lingqimall.com/api/shop/home; do curl -fsS --max-time 12 "$url" | python3 -c 'import json,sys; assert json.load(sys.stdin)["code"]==200'; done
for spec in \
  'GET /shop/admin/wechat-express/options' \
  'POST /shop/admin/orders/1/wechat-express' \
  'DELETE /shop/admin/orders/1/wechat-express/1' \
  'POST /shop/orders/1/wechat-logistics-token?shipmentId=1'; do
  method=${spec%% *}; path=${spec#* }
  [[ "$(curl -sS -o /dev/null -w '%{http_code}' --max-time 12 -X "$method" "https://lingqimall.com/api$path" -H 'X-Shop-Client: wechat-mini-program' -H 'X-Shop-Surface: mini-program')" == 401 ]]
done
sleep 8
journalctl -u "$SERVICE" --since "$START_TIME" --no-pager > "$ROLLBACK_DIR/new-journal.log"
! grep -Eq 'Application run failed|OutOfMemoryError|UnsatisfiedDependencyException|BeanCreationException' "$ROLLBACK_DIR/new-journal.log"
BACKUP_AFTER=$(backup_and_verify)
[[ "$BACKUP_AFTER" != "$BACKUP_BEFORE" ]]
for svc in "$SERVICE" nginx mysqld redis; do systemctl is-active --quiet "$svc"; done
install -m 0600 "$RELEASE_DIR/RELEASE_MANIFEST.json" "$ROLLBACK_DIR/RELEASE_MANIFEST.json"
MUTATED=0
echo "release-success version=$EXPECTED_VERSION backup-before=$BACKUP_BEFORE backup-after=$BACKUP_AFTER rollback=$ROLLBACK_DIR migrations=38:38 logistics-tables-empty=yes static-config-preserved=yes"
