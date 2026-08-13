#!/usr/bin/env bash
set -Eeuo pipefail

APP_ROOT=/opt/lingqimall
RELEASE_DIR=/tmp/lingqimall-release-v1.0.48
EXPECTED_VERSION=1.0.48
EXPECTED_PREVIOUS_VERSION=1.0.47
EXPECTED_GIT_COMMIT=b0c563a5bc03e8deaf600cafb84a0d0113a8e8fb
EXPECTED_BUILD_ID=20260813-0833-1.0.48
EXPECTED_JAR_SHA256=3c84f549bf70ef9f5cad25e68c988b7b3ef1b96e2b6cedda3b7c8a40db142d11
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
    echo "hotfix failed; restoring 1.0.47 application files" >&2
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

[[ "$RELEASE_DIR" == /tmp/lingqimall-release-v1.0.48 ]]
[[ -s "$RELEASE_DIR/mall-distribution.jar" ]]
[[ -s "$RELEASE_DIR/admin.tar.gz" ]]
[[ -s "$RELEASE_DIR/shop.tar.gz" ]]
[[ -s "$RELEASE_DIR/VERSION" ]]
[[ -s "$RELEASE_DIR/SHA256SUMS" ]]
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
[[ "$(stat -c '%a' /tmp)" == 1777 ]]
mysql_cmd -NBe 'SELECT 1' | grep -qx 1
BEFORE_COUNTS="$(database_counts)"

[[ "$(mysql_cmd -NBe "SELECT COUNT(*) FROM dms_bonus_calculation_task WHERE status IN (0,1)")" == 0 ]]
[[ "$(mysql_cmd -NBe "SELECT COUNT(*) FROM dms_erp_sync_task WHERE status IN (0,1,2)")" == 0 ]]
[[ "$(mysql_cmd -NBe "SELECT COUNT(*) FROM dms_line_change_application WHERE status IN (0,1)")" == 0 ]]
[[ "$(mysql_cmd -NBe "SELECT COUNT(*) FROM dms_shop_order WHERE status=0 AND create_time<=NOW()-INTERVAL 30 MINUTE")" == 0 ]]
[[ "$(mysql_cmd -NBe "SELECT COUNT(*) FROM dms_commission_record r JOIN dms_shop_order o ON o.id=r.order_id WHERE r.status=0 AND r.commission_amount>0 AND o.status=3 AND o.receive_time IS NOT NULL AND o.receive_time<=NOW()-INTERVAL 7 DAY AND NOT EXISTS (SELECT 1 FROM dms_shop_after_sale a WHERE a.order_id=r.order_id AND a.status IN (0,4,5,6))")" == 0 ]]
[[ "$(mysql_cmd -NBe "SELECT COUNT(*) FROM dms_order_balance_allocation a JOIN dms_shop_order o ON o.id=a.order_id WHERE a.status=0 AND a.current_amount>0 AND o.status=3 AND o.receive_time IS NOT NULL AND o.receive_time<=NOW()-INTERVAL 7 DAY AND NOT EXISTS (SELECT 1 FROM dms_shop_after_sale s WHERE s.order_id=a.order_id AND s.status IN (0,4,5,6))")" == 0 ]]

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
  grep -Fx 'etc/nginx/conf.d/lingqimall.conf' "$backup_listing" >/dev/null
)

NEW_ADMIN="$(mktemp -d "$APP_ROOT/nginx/.admin-v1.0.48.XXXXXX")"
NEW_SHOP="$(mktemp -d "$APP_ROOT/nginx/.shop-v1.0.48.XXXXXX")"
tar -xzf "$RELEASE_DIR/admin.tar.gz" -C "$NEW_ADMIN"
tar -xzf "$RELEASE_DIR/shop.tar.gz" -C "$NEW_SHOP"
[[ -s "$NEW_ADMIN/index.html" && -s "$NEW_ADMIN/version.json" ]]
[[ -s "$NEW_SHOP/index.html" && -s "$NEW_SHOP/version.json" ]]
for manifest in "$NEW_ADMIN/version.json" "$NEW_SHOP/version.json"; do
  grep -q '"version": "1.0.48"' "$manifest"
  grep -q "\"gitCommit\": \"$EXPECTED_GIT_COMMIT\"" "$manifest"
  grep -q "\"buildId\": \"$EXPECTED_BUILD_ID\"" "$manifest"
done
chown -R nginx:nginx "$NEW_ADMIN" "$NEW_SHOP"

ROLLBACK_DIR="$(mktemp -d /tmp/lingqimall-rollback-v1.0.48.XXXXXX)"
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
  grep -q '"version": "1.0.48"' <<< "$manifest"
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
curl --http1.1 -fsS --max-time 12 'https://lingqimall.com/api/shop/products?pageNum=1&pageSize=1' >/dev/null
[[ "$(curl --http1.1 -sS -o /dev/null -w '%{http_code}' --max-time 12 'https://lingqimall.com/api/distribution/admin-auth/me')" == 401 ]]

[[ "$(sha256sum "$APP_ROOT/app/mall-distribution.jar" | awk '{print $1}')" == "$EXPECTED_JAR_SHA256" ]]
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "$EXPECTED_VERSION" ]]
[[ "$(database_counts)" == "$BEFORE_COUNTS" ]]
systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysqld
systemctl is-active --quiet redis
redis-cli ping | grep -qx PONG

# 奖金任务每 5 秒运行一次；等待三个周期，确认新进程已拿到 RedisLock，
# 不再出现“锁组件不可用”或连接失败，同时排除启动级错误。
sleep 16
stdout_from=$((STDOUT_SIZE + 1))
stderr_from=$((STDERR_SIZE + 1))
NEW_LOG_OUTPUT="$(mktemp)"
for log_spec in "$APP_ROOT/logs/distribution/stdout.log:$stdout_from" "$APP_ROOT/logs/distribution/stderr.log:$stderr_from"; do
  log_file="${log_spec%%:*}"
  log_from="${log_spec##*:}"
  tail -c "+$log_from" "$log_file" 2>/dev/null >> "$NEW_LOG_OUTPUT"
done
if grep -E '分布式锁组件不可用|分布式锁获取失败|Application run failed|OutOfMemoryError|Access denied for user| ERROR ' "$NEW_LOG_OUTPUT" >/dev/null; then
  echo "hotfix log verification failed" >&2
  exit 1
fi
rm -f "$NEW_LOG_OUTPUT"
NEW_LOG_OUTPUT=""

MUTATED=0
trap - EXIT
rm -rf -- "$RELEASE_DIR" "$ROLLBACK_DIR"
echo "hotfix-success version=$EXPECTED_VERSION backup=$BACKUP_PATH build=$EXPECTED_BUILD_ID admin_entry=$admin_entry shop_entry=$shop_entry core-counts=$BEFORE_COUNTS redis-lock=verified"
