#!/usr/bin/env bash
set -Eeuo pipefail

APP_ROOT=/opt/lingqimall
RELEASE_DIR=/tmp/lingqimall-release-v1.0.53
EXPECTED_VERSION=1.0.53
EXPECTED_PREVIOUS_VERSION=1.0.50
EXPECTED_GIT_COMMIT=58d5c9046be6404151c23b59c3a89952e7e57d23
EXPECTED_BUILD_ID=20260813-1540-1.0.53
EXPECTED_JAR_SHA256=fe341fd4b4c33362afc56012aaaad4a49d351be5c227e16d03662ca66403f817
EXPECTED_MIGRATION_SHA256=d46b5fc6c46caefbd0f789dbd1bd0944c300119d5ce2320dff27e33aa8273668
DB_NAME=mall_distribution
ROLLBACK_DIR=""
NEW_ADMIN=""
NEW_SHOP=""
NEW_LOG_OUTPUT=""
BACKUP_PATH=""
BEFORE_COUNTS=""
STDOUT_SIZE=0
STDERR_SIZE=0
MUTATED=0

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
  [[ -n "$NEW_LOG_OUTPUT" && -f "$NEW_LOG_OUTPUT" ]] && rm -f -- "$NEW_LOG_OUTPUT"
}

rollback() {
  local code=$?
  if [[ "$code" != 0 && "$MUTATED" == 1 ]]; then
    echo "1.0.53 release failed; restoring 1.0.50 application files" >&2
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

[[ "$RELEASE_DIR" == /tmp/lingqimall-release-v1.0.53 ]]
for file in mall-distribution.jar admin.tar.gz shop.tar.gz VERSION SHA256SUMS; do
  [[ -s "$RELEASE_DIR/$file" ]]
done
[[ -x "$RELEASE_DIR/repo/scripts/db-migrate.sh" ]]
[[ -s "$RELEASE_DIR/repo/document/db/migrations/V202608131430__durable_idempotency.sql" ]]
[[ "$(tr -d '[:space:]' < "$RELEASE_DIR/VERSION")" == "$EXPECTED_VERSION" ]]
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "$EXPECTED_PREVIOUS_VERSION" ]]
(cd "$RELEASE_DIR" && sha256sum -c SHA256SUMS)
[[ "$(sha256sum "$RELEASE_DIR/mall-distribution.jar" | awk '{print $1}')" == "$EXPECTED_JAR_SHA256" ]]
[[ "$(sha256sum "$RELEASE_DIR/repo/document/db/migrations/V202608131430__durable_idempotency.sql" | awk '{print $1}')" == "$EXPECTED_MIGRATION_SHA256" ]]

systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysqld
systemctl is-active --quiet redis
redis-cli ping | grep -qx PONG
curl -fsS --max-time 8 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'
mysql_cmd -NBe 'SELECT 1' | grep -qx 1
BEFORE_COUNTS="$(database_counts)"

[[ "$(mysql_cmd -NBe "SELECT COUNT(*) FROM dms_bonus_calculation_task WHERE status IN (0,1)")" == 0 ]]
[[ "$(mysql_cmd -NBe "SELECT COUNT(*) FROM dms_erp_sync_task WHERE status IN (0,1,2)")" == 0 ]]
[[ "$(mysql_cmd -NBe "SELECT COUNT(*) FROM dms_line_change_application WHERE status IN (0,1)")" == 0 ]]
[[ "$(mysql_cmd -NBe "SELECT COUNT(*) FROM dms_shop_order WHERE status=0 AND create_time<=NOW()-INTERVAL 30 MINUTE")" == 0 ]]

MIGRATION_ROOT_DIR="$RELEASE_DIR/repo" DB_AUTH_MODE=socket DB_HOST=localhost DB_USER=root DB_NAME="$DB_NAME" \
  "$RELEASE_DIR/repo/scripts/db-migrate.sh" status
history_count="$(mysql_cmd -NBe "SELECT COUNT(*) FROM dms_schema_migration_history WHERE success=1")"
durable_table_count="$(mysql_cmd -NBe "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='dms_idempotency_record'")"
if [[ "$history_count:$durable_table_count" == 5:1 ]]; then
  # 允许同一发布的前一次程序切换被验收门回滚后重试；迁移必须与本包完全一致。
  [[ "$(mysql_cmd -NBe "SELECT COUNT(*) FROM dms_schema_migration_history WHERE version='202608131430' AND checksum='$EXPECTED_MIGRATION_SHA256' AND success=1")" == 1 ]]
elif [[ "$history_count:$durable_table_count" != 4:0 ]]; then
  echo "unexpected migration state: $history_count:$durable_table_count" >&2
  exit 1
fi

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

MIGRATION_ROOT_DIR="$RELEASE_DIR/repo" DB_AUTH_MODE=socket DB_HOST=localhost DB_USER=root DB_NAME="$DB_NAME" \
  "$RELEASE_DIR/repo/scripts/db-migrate.sh" apply
[[ "$(mysql_cmd -NBe "SELECT COUNT(*) FROM dms_schema_migration_history WHERE version='202608131430' AND checksum='$EXPECTED_MIGRATION_SHA256' AND success=1")" == 1 ]]
[[ "$(mysql_cmd -NBe "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='dms_idempotency_record'")" == 1 ]]
[[ "$(mysql_cmd -NBe "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='dms_idempotency_record' AND index_name='idx_idempotency_status_time'")" == 2 ]]
[[ "$(database_counts)" == "$BEFORE_COUNTS" ]]

NEW_ADMIN="$(mktemp -d "$APP_ROOT/nginx/.admin-v1.0.53.XXXXXX")"
NEW_SHOP="$(mktemp -d "$APP_ROOT/nginx/.shop-v1.0.53.XXXXXX")"
tar -xzf "$RELEASE_DIR/admin.tar.gz" -C "$NEW_ADMIN"
tar -xzf "$RELEASE_DIR/shop.tar.gz" -C "$NEW_SHOP"
for manifest in "$NEW_ADMIN/version.json" "$NEW_SHOP/version.json"; do
  grep -q '"version": "1.0.53"' "$manifest"
  grep -q "\"gitCommit\": \"$EXPECTED_GIT_COMMIT\"" "$manifest"
  grep -q "\"buildId\": \"$EXPECTED_BUILD_ID\"" "$manifest"
done
if find "$NEW_ADMIN" "$NEW_SHOP" -type f -name '*.map' -print -quit | grep -q .; then
  echo "source map found in release assets" >&2
  exit 1
fi
grep -R -Fq '秒杀活动' "$NEW_ADMIN/assets"
grep -R -Fq '复购商城' "$NEW_SHOP/assets"
chown -R nginx:nginx "$NEW_ADMIN" "$NEW_SHOP"

ROLLBACK_DIR="$(mktemp -d /tmp/lingqimall-rollback-v1.0.53.XXXXXX)"
install -m 0600 "$APP_ROOT/app/mall-distribution.jar" "$ROLLBACK_DIR/mall-distribution.jar"
install -m 0600 "$APP_ROOT/VERSION" "$ROLLBACK_DIR/VERSION"

MUTATED=1
systemctl stop lingqimall-distribution.service
STDOUT_SIZE="$(stat -c '%s' "$APP_ROOT/logs/distribution/stdout.log" 2>/dev/null || echo 0)"
STDERR_SIZE="$(stat -c '%s' "$APP_ROOT/logs/distribution/stderr.log" 2>/dev/null || echo 0)"
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
  if curl -fsS --max-time 5 http://127.0.0.1:8086/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
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
for manifest in "$shop_manifest" "$admin_manifest"; do
  grep -q '"version": "1.0.53"' <<< "$manifest"
  grep -q "\"gitCommit\": \"$EXPECTED_GIT_COMMIT\"" <<< "$manifest"
  grep -q "\"buildId\": \"$EXPECTED_BUILD_ID\"" <<< "$manifest"
done

admin_html="$(curl --http1.1 -fsS --max-time 12 -H 'Cache-Control: no-cache' "https://lingqimall.com/admin/?release=$EXPECTED_VERSION")"
shop_html="$(curl --http1.1 -fsS --max-time 12 -H 'Cache-Control: no-cache' "https://lingqimall.com/?release=$EXPECTED_VERSION")"
grep -q '<div id="app">' <<< "$admin_html"
grep -q '<div id="app">' <<< "$shop_html"
admin_entry="$(grep -o '/admin/assets/index-[^" ]*\.js' <<< "$admin_html" | head -1)"
shop_entry="$(grep -o '/assets/index-[^" ]*\.js' <<< "$shop_html" | head -1)"
[[ -n "$admin_entry" && -n "$shop_entry" ]]
curl --http1.1 -fsS --max-time 12 "https://lingqimall.com${admin_entry}?release=$EXPECTED_VERSION" >/dev/null
curl --http1.1 -fsS --max-time 12 "https://lingqimall.com${shop_entry}?release=$EXPECTED_VERSION" >/dev/null
curl --http1.1 -fsS --max-time 12 'https://lingqimall.com/api/shop/home' >/dev/null
product_response="$(curl --http1.1 -fsS --max-time 12 'https://lingqimall.com/api/shop/products?pageNum=1&pageSize=1')"
for forbidden in costPrice costAmount bvValue safetyStock shippingAddress shippingAddressId \
  deliveryAddress deliveryProvince deliveryCity deliveryDistrict returnAddressId \
  freightTemplateId repurchasePrice repurchasePv repurchaseEnabled repurchaseConfig; do
  if grep -q "\"$forbidden\"" <<< "$product_response"; then
    echo "public product response leaked $forbidden" >&2
    exit 1
  fi
done
[[ "$(curl --http1.1 -sS -o /dev/null -w '%{http_code}' --max-time 12 'https://lingqimall.com/api/distribution/admin-auth/me')" == 401 ]]
[[ "$(curl --http1.1 -sS -o /dev/null -w '%{http_code}' --max-time 12 'https://lingqimall.com/api/actuator/health')" == 404 ]]

[[ "$(sha256sum "$APP_ROOT/app/mall-distribution.jar" | awk '{print $1}')" == "$EXPECTED_JAR_SHA256" ]]
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "$EXPECTED_VERSION" ]]
[[ "$(database_counts)" == "$BEFORE_COUNTS" ]]
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
  echo "1.0.53 startup log verification failed" >&2
  exit 1
fi
rm -f "$NEW_LOG_OUTPUT"
NEW_LOG_OUTPUT=""

MUTATED=0
trap - EXIT
rm -rf -- "$RELEASE_DIR" "$ROLLBACK_DIR"
echo "release-success version=$EXPECTED_VERSION backup=$BACKUP_PATH build=$EXPECTED_BUILD_ID admin_entry=$admin_entry shop_entry=$shop_entry core-counts=$BEFORE_COUNTS migration=202608131430"
