#!/usr/bin/env bash
set -Eeuo pipefail

APP_ROOT=/opt/lingqimall
RELEASE_DIR=/tmp/lingqimall-release-v1.0.47
EXPECTED_VERSION=1.0.47
EXPECTED_PREVIOUS_VERSION=1.0.46
EXPECTED_GIT_COMMIT=69f319e17c4f6bd87cfe5407aac46d93f667e41b
EXPECTED_BUILD_ID=20260813-0811-1.0.47
DB_NAME=mall_distribution
DB_HOST=localhost
ROLLBACK_DIR=""
NEW_ADMIN=""
NEW_SHOP=""
BACKUP_PATH=""
DB_USER=root
BEFORE_COUNTS=""
STDOUT_SIZE=0
STDERR_SIZE=0
MUTATED=0

mysql_cmd() {
  mysql --protocol=socket -u"$DB_USER" "$DB_NAME" "$@"
}

database_counts() {
  mysql_cmd -NBe "SELECT CONCAT(
    (SELECT COUNT(*) FROM dms_shop_member WHERE system_account=0), ':',
    (SELECT COUNT(*) FROM dms_shop_member WHERE system_account=1), ':',
    (SELECT COUNT(*) FROM dms_shop_order), ':',
    (SELECT COUNT(*) FROM dms_commission_record), ':',
    (SELECT COUNT(*) FROM dms_member_asset_flow), ':',
    (SELECT COALESCE(SUM(balance),0) FROM dms_member_asset_account), ':',
    (SELECT COUNT(*) FROM dms_shop_product), ':',
    (SELECT COUNT(*) FROM dms_shop_category), ':',
    (SELECT COUNT(*) FROM dms_admin_user WHERE status=1)
  )"
}

cleanup_staging() {
  [[ -n "$NEW_ADMIN" && -d "$NEW_ADMIN" ]] && rm -rf -- "$NEW_ADMIN"
  [[ -n "$NEW_SHOP" && -d "$NEW_SHOP" ]] && rm -rf -- "$NEW_SHOP"
}

rollback() {
  local code=$?
  if [[ "$code" != 0 && "$MUTATED" == 1 ]]; then
    echo "release failed; restoring 1.0.46 application files" >&2
    systemctl stop lingqimall-distribution.service || true
    [[ -s "$ROLLBACK_DIR/mall-distribution.jar" ]] && install -m 0644 "$ROLLBACK_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar" || true
    if [[ -d "$ROLLBACK_DIR/admin" ]]; then
      rm -rf -- "$APP_ROOT/nginx/admin"
      mv "$ROLLBACK_DIR/admin" "$APP_ROOT/nginx/admin"
    fi
    if [[ -d "$ROLLBACK_DIR/shop" ]]; then
      rm -rf -- "$APP_ROOT/nginx/shop"
      mv "$ROLLBACK_DIR/shop" "$APP_ROOT/nginx/shop"
    fi
    [[ -s "$ROLLBACK_DIR/VERSION" ]] && install -m 0644 "$ROLLBACK_DIR/VERSION" "$APP_ROOT/VERSION" || true
    chown -R nginx:nginx "$APP_ROOT/nginx/admin" "$APP_ROOT/nginx/shop" || true
    systemctl start lingqimall-distribution.service || true
    nginx -t && systemctl reload nginx || true
  fi
  cleanup_staging
  exit "$code"
}
trap rollback EXIT

[[ "$RELEASE_DIR" == /tmp/lingqimall-release-v1.0.47 ]]
[[ -s "$RELEASE_DIR/mall-distribution.jar" ]]
[[ -s "$RELEASE_DIR/admin.tar.gz" ]]
[[ -s "$RELEASE_DIR/shop.tar.gz" ]]
[[ -s "$RELEASE_DIR/VERSION" ]]
[[ -s "$RELEASE_DIR/SHA256SUMS" ]]
[[ -x "$RELEASE_DIR/production-backup.sh" ]]
[[ -x "$RELEASE_DIR/repo/scripts/db-migrate.sh" ]]
[[ "$(tr -d '[:space:]' < "$RELEASE_DIR/VERSION")" == "$EXPECTED_VERSION" ]]
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "$EXPECTED_PREVIOUS_VERSION" ]]
(cd "$RELEASE_DIR" && sha256sum -c SHA256SUMS)

systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysqld
systemctl is-active --quiet redis
curl -fsS --max-time 8 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'
[[ "$(stat -c '%a' /tmp)" == 1777 ]]

mysql_cmd -NBe 'SELECT 1' | grep -qx 1
BEFORE_COUNTS="$(database_counts)"

MIGRATION_ROOT_DIR="$RELEASE_DIR/repo" DB_AUTH_MODE=socket DB_HOST="$DB_HOST" DB_USER="$DB_USER" DB_NAME="$DB_NAME" \
  "$RELEASE_DIR/repo/scripts/db-migrate.sh" status

install -m 0750 "$RELEASE_DIR/production-backup.sh" /usr/local/sbin/lingqimall-backup
DB_AUTH_MODE=socket DB_USER=root /usr/local/sbin/lingqimall-backup
BACKUP_PATH="$(readlink -f "$APP_ROOT/backups/full/latest")"
[[ "$BACKUP_PATH" =~ ^/opt/lingqimall/backups/full/20[0-9]{6}_[0-9]{6}$ ]]
(
  cd "$BACKUP_PATH"
  sha256sum -c SHA256SUMS
  gzip -t database.sql.gz
  backup_listing="$(mktemp)"
  trap 'rm -f "$backup_listing"' EXIT
  tar -tzf files-and-config.tar.gz > "$backup_listing"
  grep -Fx 'opt/lingqimall/app/mall-distribution.jar' "$backup_listing" >/dev/null
  grep -Fx 'opt/lingqimall/config/application.yml' "$backup_listing" >/dev/null
  grep -Fx 'opt/lingqimall/nginx/admin/index.html' "$backup_listing" >/dev/null
  grep -Fx 'opt/lingqimall/nginx/shop/index.html' "$backup_listing" >/dev/null
  grep -Fx 'etc/systemd/system/lingqimall-distribution.service' "$backup_listing" >/dev/null
  grep -Fx 'etc/nginx/conf.d/lingqimall.conf' "$backup_listing" >/dev/null
)

MIGRATION_ROOT_DIR="$RELEASE_DIR/repo" DB_AUTH_MODE=socket DB_HOST="$DB_HOST" DB_USER="$DB_USER" DB_NAME="$DB_NAME" \
  "$RELEASE_DIR/repo/scripts/db-migrate.sh" apply

[[ "$(mysql_cmd -NBe "SELECT COUNT(*) FROM dms_schema_migration_history WHERE success=1 AND ((version='202608121635' AND checksum='cbf83f544d30257158ad7f764cbf0ce6ec56f26bdf4a54cb55773b3d46b94541') OR (version='202608121715' AND checksum='79b248dbf1c1ecb0ff1ec8bf307ea76f5e6df08e8001e4fe434690b35816a037'))")" == 2 ]]
[[ "$(mysql_cmd -NBe "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DB_NAME' AND ((TABLE_NAME='dms_tenant' AND COLUMN_NAME IN ('flash_sale_enabled','flash_sale_bonus_mode','repurchase_mall_enabled','repurchase_eligibility_mode','repurchase_bonus_mode')) OR (TABLE_NAME='dms_shop_product' AND COLUMN_NAME IN ('normal_sale_enabled','repurchase_sale_enabled','repurchase_price','repurchase_pv','repurchase_purchase_limit')) OR (TABLE_NAME='dms_shop_sku' AND COLUMN_NAME IN ('repurchase_price','repurchase_pv')) OR (TABLE_NAME='dms_shop_order' AND COLUMN_NAME IN ('business_type','business_source_id','service_remark'))) ")" == 15 ]]
[[ "$(mysql_cmd -NBe "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME IN ('dms_flash_sale_activity','dms_flash_sale_reservation')")" == 2 ]]
[[ "$(mysql_cmd -NBe "SELECT CONCAT(COALESCE(SUM(flash_sale_enabled),0),':',COALESCE(SUM(repurchase_mall_enabled),0)) FROM dms_tenant")" == 0:0 ]]
[[ "$(database_counts)" == "$BEFORE_COUNTS" ]]

NEW_ADMIN="$(mktemp -d "$APP_ROOT/nginx/.admin-v1.0.47.XXXXXX")"
NEW_SHOP="$(mktemp -d "$APP_ROOT/nginx/.shop-v1.0.47.XXXXXX")"
tar -xzf "$RELEASE_DIR/admin.tar.gz" -C "$NEW_ADMIN"
tar -xzf "$RELEASE_DIR/shop.tar.gz" -C "$NEW_SHOP"
[[ -s "$NEW_ADMIN/index.html" && -s "$NEW_ADMIN/version.json" ]]
[[ -s "$NEW_SHOP/index.html" && -s "$NEW_SHOP/version.json" ]]
grep -q '"version": "1.0.47"' "$NEW_ADMIN/version.json"
grep -q "\"gitCommit\": \"$EXPECTED_GIT_COMMIT\"" "$NEW_ADMIN/version.json"
grep -q "\"buildId\": \"$EXPECTED_BUILD_ID\"" "$NEW_ADMIN/version.json"
grep -q '"version": "1.0.47"' "$NEW_SHOP/version.json"
grep -q "\"gitCommit\": \"$EXPECTED_GIT_COMMIT\"" "$NEW_SHOP/version.json"
grep -q "\"buildId\": \"$EXPECTED_BUILD_ID\"" "$NEW_SHOP/version.json"
chown -R nginx:nginx "$NEW_ADMIN" "$NEW_SHOP"

ROLLBACK_DIR="$(mktemp -d /tmp/lingqimall-rollback-v1.0.47.XXXXXX)"
install -m 0600 "$APP_ROOT/app/mall-distribution.jar" "$ROLLBACK_DIR/mall-distribution.jar"
install -m 0600 "$APP_ROOT/VERSION" "$ROLLBACK_DIR/VERSION"
STDOUT_SIZE="$(stat -c '%s' "$APP_ROOT/logs/distribution/stdout.log" 2>/dev/null || echo 0)"
STDERR_SIZE="$(stat -c '%s' "$APP_ROOT/logs/distribution/stderr.log" 2>/dev/null || echo 0)"

MUTATED=1
systemctl stop lingqimall-distribution.service
mv "$APP_ROOT/nginx/admin" "$ROLLBACK_DIR/admin"
mv "$APP_ROOT/nginx/shop" "$ROLLBACK_DIR/shop"
mv "$NEW_ADMIN" "$APP_ROOT/nginx/admin"
NEW_ADMIN=""
mv "$NEW_SHOP" "$APP_ROOT/nginx/shop"
NEW_SHOP=""
install -m 0644 "$RELEASE_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar"
install -m 0644 "$RELEASE_DIR/VERSION" "$APP_ROOT/VERSION"
systemctl start lingqimall-distribution.service

healthy=0
for _ in $(seq 1 60); do
  if curl -fsS --max-time 5 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'; then
    healthy=1
    break
  fi
  sleep 2
done
[[ "$healthy" == 1 ]]
nginx -t
systemctl reload nginx

shop_manifest="$(curl --http1.1 -fsS --max-time 12 -H 'Cache-Control: no-cache' "https://lingqimall.com/version.json?release=$EXPECTED_VERSION")"
admin_manifest="$(curl --http1.1 -fsS --max-time 12 -H 'Cache-Control: no-cache' "https://lingqimall.com/admin/version.json?release=$EXPECTED_VERSION")"
grep -q '"version": "1.0.47"' <<< "$shop_manifest"
grep -q "\"gitCommit\": \"$EXPECTED_GIT_COMMIT\"" <<< "$shop_manifest"
grep -q "\"buildId\": \"$EXPECTED_BUILD_ID\"" <<< "$shop_manifest"
grep -q '"version": "1.0.47"' <<< "$admin_manifest"
grep -q "\"gitCommit\": \"$EXPECTED_GIT_COMMIT\"" <<< "$admin_manifest"
grep -q "\"buildId\": \"$EXPECTED_BUILD_ID\"" <<< "$admin_manifest"

admin_html="$(curl --http1.1 -fsS --max-time 12 -H 'Cache-Control: no-cache' "https://lingqimall.com/admin/?release=$EXPECTED_VERSION")"
shop_html="$(curl --http1.1 -fsS --max-time 12 -H 'Cache-Control: no-cache' "https://lingqimall.com/?release=$EXPECTED_VERSION")"
grep -q '<div id="app">' <<< "$admin_html"
grep -q '<div id="app">' <<< "$shop_html"
admin_entry="$(grep -o '/admin/assets/index-[^" ]*\.js' <<< "$admin_html" | head -1)"
shop_entry="$(grep -o '/assets/index-[^" ]*\.js' <<< "$shop_html" | head -1)"
[[ -n "$admin_entry" && -n "$shop_entry" ]]
curl --http1.1 -fsS --max-time 12 -H 'Cache-Control: no-cache' "https://lingqimall.com${admin_entry}?release=$EXPECTED_VERSION" >/dev/null
curl --http1.1 -fsS --max-time 12 -H 'Cache-Control: no-cache' "https://lingqimall.com${shop_entry}?release=$EXPECTED_VERSION" >/dev/null
curl --http1.1 -fsS --max-time 12 'https://lingqimall.com/api/shop/home' >/dev/null
curl --http1.1 -fsS --max-time 12 'https://lingqimall.com/api/shop/products?pageNum=1&pageSize=1' >/dev/null
grep -R -Fq '实际轨迹以承运商查询为准' "$APP_ROOT/nginx/shop/assets"
grep -R -Fq '客服备注' "$APP_ROOT/nginx/admin/assets"

[[ "$(sha256sum "$APP_ROOT/app/mall-distribution.jar" | awk '{print $1}')" == a5ce58871fe13096cf2639042d68b9ccadf4750129477322150d88836ff25936 ]]
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "$EXPECTED_VERSION" ]]
[[ "$(database_counts)" == "$BEFORE_COUNTS" ]]
systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysqld
systemctl is-active --quiet redis

stdout_from=$((STDOUT_SIZE + 1))
stderr_from=$((STDERR_SIZE + 1))
! tail -c "+$stdout_from" "$APP_ROOT/logs/distribution/stdout.log" 2>/dev/null | grep -Eq 'Application run failed|OutOfMemoryError|Access denied for user|BUILD FAILURE'
! tail -c "+$stderr_from" "$APP_ROOT/logs/distribution/stderr.log" 2>/dev/null | grep -Eq 'Application run failed|OutOfMemoryError|Access denied for user|BUILD FAILURE'

MUTATED=0
trap - EXIT
rm -rf -- "$RELEASE_DIR" "$ROLLBACK_DIR"
echo "release-success version=$EXPECTED_VERSION backup=$BACKUP_PATH build=$EXPECTED_BUILD_ID admin_entry=$admin_entry shop_entry=$shop_entry core-counts=$BEFORE_COUNTS"
