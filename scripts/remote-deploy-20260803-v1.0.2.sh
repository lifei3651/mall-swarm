#!/usr/bin/env bash
set -Eeuo pipefail

APP_ROOT=/opt/lingqimall
RELEASE_DIR=/tmp/lingqimall-release-v1.0.2
ROLLBACK_DIR=/tmp/lingqimall-rollback-v1.0.2

JAR="$RELEASE_DIR/mall-distribution.jar"
ADMIN="$RELEASE_DIR/admin-dist.tar.gz"
SHOP="$RELEASE_DIR/shop-dist.tar.gz"
VERSION_FILE="$RELEASE_DIR/VERSION"

EXPECTED_JAR=647fff20c81895f1aeb01cf76521001b9ccd881d4fd7419bedf6405988e9b12f
EXPECTED_ADMIN=a72bfec6a184015c69071790e27e75f1a668bf72a683bc3e42f66f3997e5b43c
EXPECTED_SHOP=32e71f2175176344a66a94474c83c76d5607c38c197f2f6de2a64201291b8e8c
EXPECTED_ADMIN_INDEX=cdcb6c81267b9785b491389d382a572f5574b6b4b690fd04e5bd9f9794fecc5d
EXPECTED_SHOP_INDEX=a5938d613c379f51ad3a81c6994888567e6ce2e8575dadd9c2adadeee0ae593d
EXPECTED_VERSION=1.0.2

MUTATED=0
HAD_VERSION=0

database_password() {
  awk '/^[[:space:]]+password:/ {print $2; exit}' "$APP_ROOT/config/application.yml"
}

rollback() {
  local code=$?
  if [[ "$code" != 0 && "$MUTATED" == 1 ]]; then
    echo "release failed; restoring application and web files" >&2
    systemctl stop lingqimall-distribution.service || true
    install -m 0644 "$ROLLBACK_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar" || true
    find "$APP_ROOT/nginx/admin" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + || true
    tar -xzf "$ROLLBACK_DIR/admin.tar.gz" -C "$APP_ROOT/nginx/admin" || true
    find "$APP_ROOT/nginx/shop" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + || true
    tar -xzf "$ROLLBACK_DIR/shop.tar.gz" -C "$APP_ROOT/nginx/shop" || true
    if [[ "$HAD_VERSION" == 1 ]]; then
      install -m 0644 "$ROLLBACK_DIR/VERSION" "$APP_ROOT/VERSION" || true
    else
      rm -f "$APP_ROOT/VERSION" || true
    fi
    chown -R www-data:www-data "$APP_ROOT/nginx/admin" "$APP_ROOT/nginx/shop" || true
    systemctl start lingqimall-distribution.service || true
    nginx -t && systemctl reload nginx || true
  fi
  exit "$code"
}
trap rollback EXIT

echo "$EXPECTED_JAR  $JAR" | sha256sum -c -
echo "$EXPECTED_ADMIN  $ADMIN" | sha256sum -c -
echo "$EXPECTED_SHOP  $SHOP" | sha256sum -c -
[[ "$(tr -d '[:space:]' < "$VERSION_FILE")" == "$EXPECTED_VERSION" ]]

systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server
curl -fsS --max-time 5 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'

db_pass=$(database_password)
before_counts=$(MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h127.0.0.1 -umall_user -NBe \
  "SELECT CONCAT(
      (SELECT COUNT(*) FROM dms_shop_member WHERE system_account=0), ':',
      (SELECT COUNT(*) FROM dms_shop_member WHERE system_account=1), ':',
      (SELECT COUNT(*) FROM dms_shop_order), ':',
      (SELECT COUNT(*) FROM dms_commission_record), ':',
      (SELECT COUNT(*) FROM dms_member_asset_flow), ':',
      (SELECT COALESCE(SUM(balance),0) FROM dms_member_asset_account), ':',
      (SELECT COUNT(*) FROM dms_shop_product), ':',
      (SELECT COUNT(*) FROM dms_shop_category), ':',
      (SELECT COUNT(*) FROM dms_admin_user WHERE status=1)
  )" mall_distribution)
unset db_pass
echo "validation-stage=preflight core-counts=$before_counts"

/usr/local/sbin/lingqimall-backup
backup_path=$(readlink -f "$APP_ROOT/backups/full/latest")
echo "validation-stage=full-backup path=$backup_path"

test ! -e "$ROLLBACK_DIR"
install -d -m 0700 "$ROLLBACK_DIR"
cp "$APP_ROOT/app/mall-distribution.jar" "$ROLLBACK_DIR/mall-distribution.jar"
tar -czf "$ROLLBACK_DIR/admin.tar.gz" -C "$APP_ROOT/nginx/admin" .
tar -czf "$ROLLBACK_DIR/shop.tar.gz" -C "$APP_ROOT/nginx/shop" .
if [[ -f "$APP_ROOT/VERSION" ]]; then
  cp "$APP_ROOT/VERSION" "$ROLLBACK_DIR/VERSION"
  HAD_VERSION=1
fi
(cd "$ROLLBACK_DIR" && sha256sum mall-distribution.jar admin.tar.gz shop.tar.gz > SHA256SUMS)

MUTATED=1
systemctl stop lingqimall-distribution.service
install -m 0644 "$JAR" "$APP_ROOT/app/mall-distribution.jar"
install -m 0644 "$VERSION_FILE" "$APP_ROOT/VERSION"
find "$APP_ROOT/nginx/admin" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
tar -xzf "$ADMIN" -C "$APP_ROOT/nginx/admin"
find "$APP_ROOT/nginx/shop" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
tar -xzf "$SHOP" -C "$APP_ROOT/nginx/shop"
chown -R www-data:www-data "$APP_ROOT/nginx/admin" "$APP_ROOT/nginx/shop"

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
echo "validation-stage=backend-healthy"

nginx -t
systemctl reload nginx

echo "$EXPECTED_JAR  $APP_ROOT/app/mall-distribution.jar" | sha256sum -c -
echo "$EXPECTED_ADMIN_INDEX  $APP_ROOT/nginx/admin/index.html" | sha256sum -c -
echo "$EXPECTED_SHOP_INDEX  $APP_ROOT/nginx/shop/index.html" | sha256sum -c -
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "$EXPECTED_VERSION" ]]
grep -R -Fq '让经营更清晰' "$APP_ROOT/nginx/admin/assets"
grep -R -Fq '同时用于商城头部、浏览器标签页、手机网页标题和管理后台登录页标题' "$APP_ROOT/nginx/admin/assets"
! grep -R -Fq '请使用已授权的管理员账号登录' "$APP_ROOT/nginx/admin/assets"
grep -R -Fq '确认清空购物车' "$APP_ROOT/nginx/shop/assets"

db_pass=$(database_password)
post_counts=$(MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h127.0.0.1 -umall_user -NBe \
  "SELECT CONCAT(
      (SELECT COUNT(*) FROM dms_shop_member WHERE system_account=0), ':',
      (SELECT COUNT(*) FROM dms_shop_member WHERE system_account=1), ':',
      (SELECT COUNT(*) FROM dms_shop_order), ':',
      (SELECT COUNT(*) FROM dms_commission_record), ':',
      (SELECT COUNT(*) FROM dms_member_asset_flow), ':',
      (SELECT COALESCE(SUM(balance),0) FROM dms_member_asset_account), ':',
      (SELECT COUNT(*) FROM dms_shop_product), ':',
      (SELECT COUNT(*) FROM dms_shop_category), ':',
      (SELECT COUNT(*) FROM dms_admin_user WHERE status=1)
  )" mall_distribution)
unset db_pass
[[ "$post_counts" == "$before_counts" ]]
echo "validation-stage=database-unchanged core-counts=$post_counts"

captcha_json=$(curl -fsS --max-time 12 'https://lingqimall.com/api/captcha?scene=admin')
key_json=$(curl -fsS --max-time 12 'https://lingqimall.com/api/security/payload-encryption/key')
shop_html=$(curl -fsS --max-time 12 https://lingqimall.com/)
admin_html=$(curl -fsS --max-time 12 https://lingqimall.com/admin/)
grep -q 'data:image/png;base64,' <<< "$captcha_json"
grep -q 'publicKey' <<< "$key_json"
grep -q '<div id="app">' <<< "$shop_html"
grep -q '<div id="app">' <<< "$admin_html"

admin_auth_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' \
  'https://lingqimall.com/api/distribution/admin-auth/me')
wallet_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' \
  'https://lingqimall.com/api/shop/wallet/flows')
actuator_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' \
  'https://lingqimall.com/api/actuator/health')
http_status=$(curl -sS --max-time 12 -o /dev/null -w '%{http_code}' \
  'http://lingqimall.com/')
[[ "$admin_auth_status" == 401 ]]
[[ "$wallet_status" == 401 ]]
[[ "$actuator_status" == 404 ]]
[[ "$http_status" == 301 ]]

security_headers=$(curl -fsSI --max-time 12 https://lingqimall.com/)
admin_headers=$(curl -fsSI --max-time 12 https://lingqimall.com/admin/)
grep -qi '^content-security-policy:' <<< "$security_headers"
grep -qi '^strict-transport-security:' <<< "$security_headers"
grep -qi '^cache-control:.*no-cache' <<< "$admin_headers"
ss -lnt | grep -q '127.0.0.1:3306'
ss -lnt | grep -q '127.0.0.1:6379'

systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server
echo "validation-stage=public-and-security-ok version=$EXPECTED_VERSION backup=$backup_path"

MUTATED=0
trap - EXIT
rm -rf "$ROLLBACK_DIR" "$RELEASE_DIR"
echo "deployment-complete version=$EXPECTED_VERSION backup=$backup_path"
