#!/usr/bin/env bash
set -Eeuo pipefail

APP_ROOT=/opt/lingqimall
RELEASE_DIR=/tmp/lingqimall-release-v1.0.62
EXPECTED_VERSION=1.0.62
EXPECTED_PREVIOUS_VERSION=1.0.56
EXPECTED_GIT_COMMIT=403981408a7638c16e8dc483720beba93133a5e9
EXPECTED_BUILD_ID=20260821-1308-1.0.62
EXPECTED_JAR_SHA256=8c7334159755b459016046530f2c9457b21576f97dec0a0244d8bf9e825af0c7
EXPECTED_MIGRATIONS=13
DB_NAME=mall_distribution
ROLLBACK_DIR=""
NEW_ADMIN=""
NEW_SHOP=""
NEW_TEAM=""
NEW_LOG_OUTPUT=""
BACKUP_PATH=""
BEFORE_COUNTS=""
STDOUT_SIZE=0
STDERR_SIZE=0
BACKUP_TOOL_MUTATED=0
APP_MUTATED=0

mysql_cmd() { mysql --protocol=socket -uroot "$DB_NAME" "$@"; }

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
  [[ -n "$NEW_TEAM" && -d "$NEW_TEAM" ]] && rm -rf -- "$NEW_TEAM"
  [[ -n "$NEW_LOG_OUTPUT" && -f "$NEW_LOG_OUTPUT" ]] && rm -f -- "$NEW_LOG_OUTPUT"
}

rollback() {
  local code=$?
  if [[ "$code" != 0 && "$APP_MUTATED" == 1 ]]; then
    echo "1.0.62 release failed; restoring 1.0.56 application files" >&2
    systemctl stop lingqimall-distribution.service || true
    [[ -s "$ROLLBACK_DIR/mall-distribution.jar" ]] && install -m 0644 "$ROLLBACK_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar" || true
    [[ -s "$ROLLBACK_DIR/VERSION" ]] && install -m 0644 "$ROLLBACK_DIR/VERSION" "$APP_ROOT/VERSION" || true
    for site in admin shop team; do
      if [[ -d "$ROLLBACK_DIR/$site" ]]; then
        rm -rf -- "$APP_ROOT/nginx/$site"
        mv "$ROLLBACK_DIR/$site" "$APP_ROOT/nginx/$site"
      fi
    done
    chown -R nginx:nginx "$APP_ROOT/nginx/admin" "$APP_ROOT/nginx/shop" "$APP_ROOT/nginx/team" 2>/dev/null || true
    systemctl start lingqimall-distribution.service || true
    nginx -t && systemctl reload nginx || true
  fi
  if [[ "$code" != 0 && "$BACKUP_TOOL_MUTATED" == 1 && -s "$ROLLBACK_DIR/lingqimall-backup" ]]; then
    install -m 0750 "$ROLLBACK_DIR/lingqimall-backup" /usr/local/sbin/lingqimall-backup || true
  fi
  cleanup_staging
  exit "$code"
}
trap rollback EXIT

[[ "$RELEASE_DIR" == /tmp/lingqimall-release-v1.0.62 ]]
for file in mall-distribution.jar admin.tar.gz shop.tar.gz team.tar.gz VERSION SHA256SUMS production-backup.sh; do
  [[ -s "$RELEASE_DIR/$file" ]]
done
[[ -x "$RELEASE_DIR/production-backup.sh" ]]
[[ "$(tr -d '[:space:]' < "$RELEASE_DIR/VERSION")" == "$EXPECTED_VERSION" ]]
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "$EXPECTED_PREVIOUS_VERSION" ]]
(cd "$RELEASE_DIR" && sha256sum -c SHA256SUMS)
[[ "$(sha256sum "$RELEASE_DIR/mall-distribution.jar" | awk '{print $1}')" == "$EXPECTED_JAR_SHA256" ]]

systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysqld
systemctl is-active --quiet redis
redis-cli ping | grep -qx PONG
curl -fsS --max-time 8 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'
mysql_cmd -NBe 'SELECT 1' | grep -qx 1
BEFORE_COUNTS="$(database_counts)"

[[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_schema_migration_history WHERE success=1')" == "$EXPECTED_MIGRATIONS" ]]
[[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_schema_migration_history WHERE success<>1')" == 0 ]]
[[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_bonus_calculation_task WHERE status IN (0,1)')" == 0 ]]
[[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_erp_sync_task WHERE status IN (0,1,2)')" == 0 ]]
[[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_line_change_application WHERE status IN (0,1)')" == 0 ]]
[[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_shop_order WHERE status=0 AND create_time<=NOW()-INTERVAL 30 MINUTE')" == 0 ]]

ROLLBACK_DIR="$(mktemp -d /tmp/lingqimall-rollback-v1.0.62.XXXXXX)"
install -m 0600 /usr/local/sbin/lingqimall-backup "$ROLLBACK_DIR/lingqimall-backup"
install -m 0600 "$APP_ROOT/app/mall-distribution.jar" "$ROLLBACK_DIR/mall-distribution.jar"
install -m 0600 "$APP_ROOT/VERSION" "$ROLLBACK_DIR/VERSION"
install -m 0750 "$RELEASE_DIR/production-backup.sh" /usr/local/sbin/lingqimall-backup
BACKUP_TOOL_MUTATED=1

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
  grep -Fx 'opt/lingqimall/nginx/team/index.html' "$backup_listing" >/dev/null
  grep -Fx 'etc/systemd/system/lingqimall-distribution.service' "$backup_listing" >/dev/null
  grep -Fx 'etc/nginx/conf.d/lingqimall.conf' "$backup_listing" >/dev/null
)
[[ "$(database_counts)" == "$BEFORE_COUNTS" ]]

NEW_ADMIN="$(mktemp -d "$APP_ROOT/nginx/.admin-v1.0.62.XXXXXX")"
NEW_SHOP="$(mktemp -d "$APP_ROOT/nginx/.shop-v1.0.62.XXXXXX")"
NEW_TEAM="$(mktemp -d "$APP_ROOT/nginx/.team-v1.0.62.XXXXXX")"
tar -xzf "$RELEASE_DIR/admin.tar.gz" -C "$NEW_ADMIN"
tar -xzf "$RELEASE_DIR/shop.tar.gz" -C "$NEW_SHOP"
tar -xzf "$RELEASE_DIR/team.tar.gz" -C "$NEW_TEAM"
for manifest_spec in \
  "$NEW_ADMIN/version.json:admin" \
  "$NEW_SHOP/version.json:storefront-public" \
  "$NEW_TEAM/version.json:team-h5"; do
  manifest="${manifest_spec%%:*}"
  application="${manifest_spec##*:}"
  grep -q '"version": "1.0.62"' "$manifest"
  grep -q "\"gitCommit\": \"$EXPECTED_GIT_COMMIT\"" "$manifest"
  grep -q "\"buildId\": \"$EXPECTED_BUILD_ID\"" "$manifest"
  grep -q "\"application\": \"$application\"" "$manifest"
done
if find "$NEW_ADMIN" "$NEW_SHOP" "$NEW_TEAM" -type f -name '*.map' -print -quit | grep -q .; then
  echo "source map found in release assets" >&2
  exit 1
fi
chown -R nginx:nginx "$NEW_ADMIN" "$NEW_SHOP" "$NEW_TEAM"

systemctl stop lingqimall-distribution.service
STDOUT_SIZE="$(stat -c '%s' "$APP_ROOT/logs/distribution/stdout.log" 2>/dev/null || echo 0)"
STDERR_SIZE="$(stat -c '%s' "$APP_ROOT/logs/distribution/stderr.log" 2>/dev/null || echo 0)"
APP_MUTATED=1
mv "$APP_ROOT/nginx/admin" "$ROLLBACK_DIR/admin"
mv "$APP_ROOT/nginx/shop" "$ROLLBACK_DIR/shop"
mv "$APP_ROOT/nginx/team" "$ROLLBACK_DIR/team"
mv "$NEW_ADMIN" "$APP_ROOT/nginx/admin"; NEW_ADMIN=""
mv "$NEW_SHOP" "$APP_ROOT/nginx/shop"; NEW_SHOP=""
mv "$NEW_TEAM" "$APP_ROOT/nginx/team"; NEW_TEAM=""
install -m 0644 "$RELEASE_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar"
install -m 0644 "$RELEASE_DIR/VERSION" "$APP_ROOT/VERSION"
systemctl start lingqimall-distribution.service

healthy=0
for _ in $(seq 1 60); do
  if curl -fsS --max-time 5 http://127.0.0.1:8086/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
    healthy=1
    break
  fi
  sleep 2
done
[[ "$healthy" == 1 ]]
nginx -t
systemctl reload nginx

public_manifest="$(curl --http1.1 -fsS --max-time 12 -H 'Cache-Control: no-cache' "https://lingqimall.com/version.json?release=$EXPECTED_BUILD_ID")"
team_manifest="$(curl --http1.1 -fsS --max-time 12 -H 'Cache-Control: no-cache' "https://www.lingqimall.com/version.json?release=$EXPECTED_BUILD_ID")"
admin_manifest="$(curl --http1.1 -fsS --max-time 12 -H 'Cache-Control: no-cache' "https://lingqimall.com/admin/version.json?release=$EXPECTED_BUILD_ID")"
for manifest_spec in "$public_manifest|storefront-public" "$team_manifest|team-h5" "$admin_manifest|admin"; do
  manifest="${manifest_spec%|*}"
  application="${manifest_spec##*|}"
  grep -q '"version": "1.0.62"' <<< "$manifest"
  grep -q "\"gitCommit\": \"$EXPECTED_GIT_COMMIT\"" <<< "$manifest"
  grep -q "\"buildId\": \"$EXPECTED_BUILD_ID\"" <<< "$manifest"
  grep -q "\"application\": \"$application\"" <<< "$manifest"
done

admin_html="$(curl --http1.1 -fsS --max-time 12 -H 'Cache-Control: no-cache' "https://lingqimall.com/admin/?release=$EXPECTED_BUILD_ID")"
public_html="$(curl --http1.1 -fsS --max-time 12 -H 'Cache-Control: no-cache' "https://lingqimall.com/?release=$EXPECTED_BUILD_ID")"
team_html="$(curl --http1.1 -fsS --max-time 12 -H 'Cache-Control: no-cache' "https://www.lingqimall.com/?release=$EXPECTED_BUILD_ID")"
for html in "$admin_html" "$public_html" "$team_html"; do grep -q '<div id="app">' <<< "$html"; done
admin_entry="$(grep -o '/admin/assets/index-[^" ]*\.js' <<< "$admin_html" | head -1)"
public_entry="$(grep -o '/assets/index-[^" ]*\.js' <<< "$public_html" | head -1)"
team_entry="$(grep -o '/assets/index-[^" ]*\.js' <<< "$team_html" | head -1)"
[[ -n "$admin_entry" && -n "$public_entry" && -n "$team_entry" ]]
curl --http1.1 -fsS --max-time 12 "https://lingqimall.com${admin_entry}?release=$EXPECTED_BUILD_ID" >/dev/null
curl --http1.1 -fsS --max-time 12 "https://lingqimall.com${public_entry}?release=$EXPECTED_BUILD_ID" >/dev/null
curl --http1.1 -fsS --max-time 12 "https://www.lingqimall.com${team_entry}?release=$EXPECTED_BUILD_ID" >/dev/null
curl --http1.1 -fsS --max-time 12 'https://lingqimall.com/api/shop/home' >/dev/null
curl --http1.1 -fsS --max-time 12 'https://www.lingqimall.com/api/shop/home' >/dev/null
product_response="$(curl --http1.1 -fsS --max-time 12 'https://lingqimall.com/api/shop/products?pageNum=1&pageSize=1')"
for forbidden in costPrice costAmount bvValue safetyStock shippingAddress shippingAddressId \
  deliveryAddress deliveryProvince deliveryCity deliveryDistrict returnAddressId \
  freightTemplateId repurchasePrice repurchasePv repurchaseEnabled repurchaseConfig settlementPrice merchantId; do
  if grep -q "\"$forbidden\"" <<< "$product_response"; then
    echo "public product response leaked $forbidden" >&2
    exit 1
  fi
done
for host in lingqimall.com www.lingqimall.com; do
  [[ "$(curl --http1.1 -sS -o /dev/null -w '%{http_code}' --max-time 12 "https://$host/api/distribution/admin-auth/me")" == 401 ]]
  [[ "$(curl --http1.1 -sS -o /dev/null -w '%{http_code}' --max-time 12 "https://$host/api/actuator/health")" == 404 ]]
done

[[ "$(sha256sum "$APP_ROOT/app/mall-distribution.jar" | awk '{print $1}')" == "$EXPECTED_JAR_SHA256" ]]
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "$EXPECTED_VERSION" ]]
[[ "$(database_counts)" == "$BEFORE_COUNTS" ]]
[[ "$(mysql_cmd -NBe 'SELECT COUNT(*) FROM dms_schema_migration_history WHERE success=1')" == "$EXPECTED_MIGRATIONS" ]]
systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysqld
systemctl is-active --quiet redis
redis-cli ping | grep -qx PONG

sleep 16
stdout_from=$((STDOUT_SIZE + 1))
stderr_from=$((STDERR_SIZE + 1))
NEW_LOG_OUTPUT="$(mktemp)"
for log_spec in "$APP_ROOT/logs/distribution/stdout.log:$stdout_from" "$APP_ROOT/logs/distribution/stderr.log:$stderr_from"; do
  log_file="${log_spec%%:*}"
  log_from="${log_spec##*:}"
  tail -c "+$log_from" "$log_file" 2>/dev/null >> "$NEW_LOG_OUTPUT"
done
if grep -E 'Application run failed|OutOfMemoryError|Access denied for user|UnsatisfiedDependencyException|BeanCreationException| ERROR ' "$NEW_LOG_OUTPUT" >/dev/null; then
  echo "1.0.62 startup log verification failed" >&2
  exit 1
fi
rm -f "$NEW_LOG_OUTPUT"; NEW_LOG_OUTPUT=""

APP_MUTATED=0
BACKUP_TOOL_MUTATED=0
trap - EXIT
rm -rf -- "$RELEASE_DIR" "$ROLLBACK_DIR"
echo "release-success version=$EXPECTED_VERSION backup=$BACKUP_PATH build=$EXPECTED_BUILD_ID public_entry=$public_entry team_entry=$team_entry admin_entry=$admin_entry core-counts=$BEFORE_COUNTS migrations=$EXPECTED_MIGRATIONS jar=$EXPECTED_JAR_SHA256"
