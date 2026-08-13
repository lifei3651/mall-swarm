#!/usr/bin/env bash
set -Eeuo pipefail

APP_ROOT=/opt/lingqimall
RELEASE_DIR=/tmp/lingqimall-release-v1.0.50
EXPECTED_VERSION=1.0.50
EXPECTED_PREVIOUS_VERSION=1.0.49
EXPECTED_GIT_COMMIT=bdc6324f42c0de2ef178511e817d8c87122c7e50
EXPECTED_BUILD_ID=20260813-0950-1.0.50
DB_NAME=mall_distribution
ROLLBACK_DIR=""
NEW_ADMIN=""
NEW_SHOP=""
BACKUP_PATH=""
BEFORE_COUNTS=""
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
}

rollback() {
  local code=$?
  if [[ "$code" != 0 && "$MUTATED" == 1 ]]; then
    echo "frontend release failed; restoring 1.0.49 static files" >&2
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
    nginx -t && systemctl reload nginx || true
  fi
  cleanup_staging
  exit "$code"
}
trap rollback EXIT

[[ "$RELEASE_DIR" == /tmp/lingqimall-release-v1.0.50 ]]
[[ -s "$RELEASE_DIR/admin.tar.gz" ]]
[[ -s "$RELEASE_DIR/shop.tar.gz" ]]
[[ -s "$RELEASE_DIR/VERSION" ]]
[[ -s "$RELEASE_DIR/SHA256SUMS" ]]
[[ "$(tr -d '[:space:]' < "$RELEASE_DIR/VERSION")" == "$EXPECTED_VERSION" ]]
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "$EXPECTED_PREVIOUS_VERSION" ]]
(cd "$RELEASE_DIR" && sha256sum -c SHA256SUMS)

systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysqld
systemctl is-active --quiet redis
redis-cli ping | grep -qx PONG
curl -fsS --max-time 8 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'
mysql_cmd -NBe 'SELECT 1' | grep -qx 1
BEFORE_COUNTS="$(database_counts)"

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

NEW_ADMIN="$(mktemp -d "$APP_ROOT/nginx/.admin-v1.0.50.XXXXXX")"
NEW_SHOP="$(mktemp -d "$APP_ROOT/nginx/.shop-v1.0.50.XXXXXX")"
tar -xzf "$RELEASE_DIR/admin.tar.gz" -C "$NEW_ADMIN"
tar -xzf "$RELEASE_DIR/shop.tar.gz" -C "$NEW_SHOP"
[[ -s "$NEW_ADMIN/index.html" && -s "$NEW_ADMIN/version.json" ]]
[[ -s "$NEW_SHOP/index.html" && -s "$NEW_SHOP/version.json" ]]
for manifest in "$NEW_ADMIN/version.json" "$NEW_SHOP/version.json"; do
  grep -q '"version": "1.0.50"' "$manifest"
  grep -q "\"gitCommit\": \"$EXPECTED_GIT_COMMIT\"" "$manifest"
  grep -q "\"buildId\": \"$EXPECTED_BUILD_ID\"" "$manifest"
done
grep -R -Fq '顺丰速运' "$NEW_ADMIN/assets"
if grep -R -Fq '具体截止时间以订单状态和商城规则为准' "$NEW_SHOP/assets"; then
  echo "storefront still contains the removed after-sale notice" >&2
  exit 1
fi
chown -R nginx:nginx "$NEW_ADMIN" "$NEW_SHOP"

ROLLBACK_DIR="$(mktemp -d /tmp/lingqimall-rollback-v1.0.50.XXXXXX)"
install -m 0600 "$APP_ROOT/VERSION" "$ROLLBACK_DIR/VERSION"
MUTATED=1
mv "$APP_ROOT/nginx/admin" "$ROLLBACK_DIR/admin"
mv "$APP_ROOT/nginx/shop" "$ROLLBACK_DIR/shop"
mv "$NEW_ADMIN" "$APP_ROOT/nginx/admin"
NEW_ADMIN=""
mv "$NEW_SHOP" "$APP_ROOT/nginx/shop"
NEW_SHOP=""
install -m 0644 "$RELEASE_DIR/VERSION" "$APP_ROOT/VERSION"

nginx -t
systemctl reload nginx

shop_manifest="$(curl --http1.1 -fsS --max-time 12 -H 'Cache-Control: no-cache' "https://lingqimall.com/version.json?release=$EXPECTED_VERSION")"
admin_manifest="$(curl --http1.1 -fsS --max-time 12 -H 'Cache-Control: no-cache' "https://lingqimall.com/admin/version.json?release=$EXPECTED_VERSION")"
for manifest in "$shop_manifest" "$admin_manifest"; do
  grep -q '"version": "1.0.50"' <<< "$manifest"
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

[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "$EXPECTED_VERSION" ]]
[[ "$(database_counts)" == "$BEFORE_COUNTS" ]]
systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysqld
systemctl is-active --quiet redis
redis-cli ping | grep -qx PONG
curl -fsS --max-time 8 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'

MUTATED=0
trap - EXIT
rm -rf -- "$RELEASE_DIR" "$ROLLBACK_DIR"
echo "frontend-release-success version=$EXPECTED_VERSION backup=$BACKUP_PATH build=$EXPECTED_BUILD_ID admin_entry=$admin_entry shop_entry=$shop_entry core-counts=$BEFORE_COUNTS"
