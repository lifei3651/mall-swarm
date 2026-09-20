#!/usr/bin/env bash
# Backend-only finance audit release. Preserve database, static sites, secrets and runtime configuration.
set -Eeuo pipefail
shopt -s inherit_errexit
umask 077

EXPECTED_VERSION=1.0.145
EXPECTED_PREVIOUS_VERSION=1.0.144
OLD_JAR_SHA=e7fc201b0d48a1d9386f336f37df26fda335e5bcd9f2d289e23cff38511af3b1
NEW_JAR_SHA=e2df05770cac70d97436298f8631e3e4e6803d542bc38323fbf8403e5d54fd55
APP_ROOT=/opt/lingqimall
DB_NAME=mall_distribution
SERVICE=lingqimall-distribution.service
RELEASE_DIR=${1:-}
MODE=${2:-}
[[ "$RELEASE_DIR" =~ ^/tmp/lingqimall-finance-backend-145\.[A-Za-z0-9]+$ ]]
[[ "$MODE" == --preflight-only || "$MODE" == --authorize-release ]]
[[ "${LINGQIMALL_RELEASE_AUTHORIZATION:-}" == "$EXPECTED_VERSION" ]]
[[ "$(hostname)" == VM-4-6-rockylinux && "$EUID" == 0 ]]

exec 8>/opt/lingqimall/.mini-backend-release.lock
flock -n 8
MUTATED=0
ROLLBACK_DIR=''
START_TIME=$(date '+%Y-%m-%d %H:%M:%S')

mysql_db() { mysql --protocol=socket -uroot "$1" "${@:2}"; }
db_snapshot() {
  mysql_db "$DB_NAME" -NBe "SELECT CONCAT(
    (SELECT COUNT(*) FROM dms_shop_member), ':',
    (SELECT COUNT(*) FROM dms_shop_order), ':',
    (SELECT COUNT(*) FROM dms_shop_after_sale), ':',
    (SELECT COUNT(*) FROM dms_commission_record), ':',
    (SELECT COUNT(*) FROM dms_member_asset_flow), ':',
    (SELECT COALESCE(SUM(balance),0) FROM dms_member_asset_account), ':',
    (SELECT COUNT(*) FROM dms_shop_product), ':',
    (SELECT COUNT(*) FROM dms_admin_user));
    SELECT CONCAT(COUNT(*),':',SUM(success=1)) FROM dms_schema_migration_history;"
}
protected_hashes() {
  find "$APP_ROOT/config" /etc/lingqimall /etc/systemd/system/lingqimall-distribution.service.d \
    /etc/nginx/conf.d "$APP_ROOT/nginx/admin" "$APP_ROOT/nginx/shop" "$APP_ROOT/nginx/team" \
    -type f -print0 | sort -z | xargs -0 sha256sum
  sha256sum /etc/systemd/system/lingqimall-distribution.service /etc/nginx/nginx.conf
}
runtime_snapshot() {
  curl -fsS --max-time 12 -H 'X-Shop-Client: wechat-mini-program' -H 'X-Shop-Surface: mini-program' \
    http://127.0.0.1:8086/shop/wechat-mini-program/runtime \
    | python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["code"]==200 and d["data"]["enabled"] is True and d["data"]["phoneAuthorizationEnabled"] is True and d["data"]["shippingInfoEnabled"] is True; print(json.dumps(d["data"],sort_keys=True,separators=(",",":")))'
}
shipping_snapshot() {
  mysql_db "$DB_NAME" -NBe "SELECT CONCAT(id,':',status,':',revision,':',synced_revision,':',attempt_count,':',IFNULL(error_code,'NULL')) FROM dms_wechat_shipping_sync_task ORDER BY id"
}
wait_health() {
  for attempt in $(seq 1 45); do
    curl -fsS --max-time 3 http://127.0.0.1:8086/actuator/health 2>/dev/null | grep -q '"status":"UP"' && return 0
    sleep 2
  done
  return 1
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
    if wait_health && [[ "$(sha256sum "$APP_ROOT/app/mall-distribution.jar" | awk '{print $1}')" == "$OLD_JAR_SHA" ]]; then
      echo "bounded-recovery=previous-backend-restored backup=$ROLLBACK_DIR" >&2
    else
      echo 'bounded-recovery=FAILED; immediate manual intervention required' >&2
    fi
  fi
  exit "$status"
}
trap recover EXIT
trap 'echo "backend-release-check-failed line=$LINENO function=${FUNCNAME[0]:-main}" >&2' ERR

for file in mall-distribution.jar VERSION RELEASE_MANIFEST.json SHA256SUMS release.sh production-backup.sh; do [[ -s "$RELEASE_DIR/$file" ]]; done
(cd "$RELEASE_DIR" && sha256sum -c SHA256SUMS)
python3 - "$RELEASE_DIR" "$NEW_JAR_SHA" <<'PY'
import hashlib, json, os, sys, zipfile
root, expected_sha = sys.argv[1:]
with open(root + '/RELEASE_MANIFEST.json') as f: manifest = json.load(f)
assert manifest['version'] == '1.0.145' and manifest['scope'] == 'finance-backend-only'
assert manifest['productionBaseline'] == 'b22b0f0f2155b68ea25aa99ce06ecf820507dc18'
assert manifest['databaseMigrations'] == [] and manifest['jarSha256'] == expected_sha
with open(root + '/mall-distribution.jar', 'rb') as f: assert hashlib.sha256(f.read()).hexdigest() == expected_sha
with zipfile.ZipFile(root + '/mall-distribution.jar') as archive:
    names = set(archive.namelist())
    assert 'BOOT-INF/classes/com/macro/mall/distribution/service/impl/DistributionAuditServiceImpl.class' in names
    assert 'BOOT-INF/classes/mapper/DmsShopOrderMapper.xml' in names
    assert 'BOOT-INF/classes/mapper/DmsCommissionRecordMapper.xml' in names
assert sorted(os.listdir(root)) == sorted(['mall-distribution.jar','VERSION','RELEASE_MANIFEST.json','SHA256SUMS','release.sh','production-backup.sh'])
print('candidate-identity-and-finance-classes=passed')
PY
[[ "$(tr -d '[:space:]' < "$RELEASE_DIR/VERSION")" == "$EXPECTED_VERSION" ]]
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "$EXPECTED_PREVIOUS_VERSION" ]]
[[ "$(sha256sum "$APP_ROOT/app/mall-distribution.jar" | awk '{print $1}')" == "$OLD_JAR_SHA" ]]
[[ "$(df -Pm "$APP_ROOT" | awk 'NR==2 {print $4}')" -ge 4096 ]]
for svc in "$SERVICE" nginx mysqld redis; do systemctl is-active --quiet "$svc"; done
redis-cli ping | grep -qx PONG
wait_health
[[ "$(mysql_db "$DB_NAME" -NBe 'SELECT CONCAT(COUNT(*),":",SUM(success=1)) FROM dms_schema_migration_history')" == 36:36 ]]
BEFORE_DB=$(db_snapshot)
BEFORE_FILES=$(protected_hashes)
BEFORE_RUNTIME=$(runtime_snapshot)
BEFORE_SHIPPING=$(shipping_snapshot)
[[ "$BEFORE_SHIPPING" == '1:SUCCESS:2:2:1:NULL' ]]
echo "release-preflight=passed previous=$EXPECTED_PREVIOUS_VERSION target=$EXPECTED_VERSION migrations=36:36"
[[ "$MODE" != --preflight-only ]] || exit 0

ROLLBACK_DIR=$(mktemp -d /opt/lingqimall/backups/finance-backend-145.XXXXXX)
install -m 0600 "$APP_ROOT/app/mall-distribution.jar" "$ROLLBACK_DIR/mall-distribution.jar"
install -m 0600 "$APP_ROOT/VERSION" "$ROLLBACK_DIR/VERSION"
BACKUP_BEFORE=$(backup_and_verify)
MUTATED=1
systemctl stop "$SERVICE"
install -m 0644 "$RELEASE_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar"
install -m 0644 "$RELEASE_DIR/VERSION" "$APP_ROOT/VERSION"
systemctl start "$SERVICE"
wait_health
[[ "$(sha256sum "$APP_ROOT/app/mall-distribution.jar" | awk '{print $1}')" == "$NEW_JAR_SHA" ]]
[[ "$(runtime_snapshot)" == "$BEFORE_RUNTIME" ]]
[[ "$(shipping_snapshot)" == "$BEFORE_SHIPPING" ]]
[[ "$(db_snapshot)" == "$BEFORE_DB" ]]
[[ "$(protected_hashes)" == "$BEFORE_FILES" ]]
for url in https://lingqimall.com/api/shop/home https://www.lingqimall.com/api/shop/home; do curl -fsS --max-time 12 "$url" | python3 -c 'import json,sys; assert json.load(sys.stdin)["code"]==200'; done
for path in /distribution/audit/orders /distribution/audit/bonus-sources /shop/orders; do
  [[ "$(curl -sS -o /dev/null -w '%{http_code}' --max-time 12 "https://lingqimall.com/api$path")" == 401 ]]
done
sleep 8
journalctl -u "$SERVICE" --since "$START_TIME" --no-pager > "$ROLLBACK_DIR/new-journal.log"
! grep -Eq 'Application run failed|OutOfMemoryError|UnsatisfiedDependencyException|BeanCreationException' "$ROLLBACK_DIR/new-journal.log"
BACKUP_AFTER=$(backup_and_verify)
[[ "$BACKUP_AFTER" != "$BACKUP_BEFORE" ]]
for svc in "$SERVICE" nginx mysqld redis; do systemctl is-active --quiet "$svc"; done
install -m 0600 "$RELEASE_DIR/RELEASE_MANIFEST.json" "$ROLLBACK_DIR/RELEASE_MANIFEST.json"
MUTATED=0
echo "release-success version=$EXPECTED_VERSION backup-before=$BACKUP_BEFORE backup-after=$BACKUP_AFTER rollback=$ROLLBACK_DIR migrations=36:36 database-static-config-preserved=yes"
