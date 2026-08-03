#!/usr/bin/env bash
# 灵启商城 1.0.3 -> 1.0.4 前端发布（从本地源码重建的发布包，后端与数据库不变）
set -Eeuo pipefail

APP_ROOT=/opt/lingqimall
RELEASE_DIR=/tmp/lingqimall-release-v1.0.4
STAGE_DIR=/tmp/lingqimall-stage-v1.0.4
ROLLBACK_DIR=/tmp/lingqimall-rollback-v1.0.4

ADMIN_PACKAGE="$RELEASE_DIR/admin-dist.tar.gz"
SHOP_PACKAGE="$RELEASE_DIR/shop-dist.tar.gz"
VERSION_FILE="$RELEASE_DIR/VERSION"

EXPECTED_ADMIN_PACKAGE=d3f4f4194b458fa6950d7765df44e46d6151d9cc176e13a1022c745922955176
EXPECTED_SHOP_PACKAGE=f1fc2b65795ccdcce36f2ec8a5733daf42836b110f034ff20ab250e5df1f243f
EXPECTED_VERSION_FILE=aea5adbef574785273c4f39c1b7687cf801993e0cb6bf6781a41689e4edbb20c
EXPECTED_ADMIN_INDEX=6a29f48fd3248448e1ad618601a3fc4cd1d51f5e06fceaf253eeee0737b20716
EXPECTED_SHOP_INDEX=10a1cf3fdba00f97f06bd7aa92a849a26b7d91a595c863e9af64f33a78b7c5f7
EXPECTED_OLD_VERSION=1.0.3
EXPECTED_NEW_VERSION=1.0.4

MUTATED=0

database_password() {
  awk '/^[[:space:]]+password:/ {print $2; exit}' "$APP_ROOT/config/application.yml"
}

database_counts() {
  local db_pass
  db_pass=$(database_password)
  MYSQL_PWD="$db_pass" mysql --ssl-mode=REQUIRED -h127.0.0.1 -umall_user -NBe \
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
    )" mall_distribution
}

rollback() {
  local code=$?
  if [[ "$code" != 0 && "$MUTATED" == 1 ]]; then
    echo "release failed; restoring web files and version marker" >&2
    find "$APP_ROOT/nginx/admin" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + || true
    tar -xzf "$ROLLBACK_DIR/admin.tar.gz" -C "$APP_ROOT/nginx/admin" || true
    find "$APP_ROOT/nginx/shop" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + || true
    tar -xzf "$ROLLBACK_DIR/shop.tar.gz" -C "$APP_ROOT/nginx/shop" || true
    install -m 0644 "$ROLLBACK_DIR/VERSION" "$APP_ROOT/VERSION" || true
    chown -R www-data:www-data "$APP_ROOT/nginx/admin" "$APP_ROOT/nginx/shop" || true
    nginx -t && systemctl reload nginx || true
  fi
  exit "$code"
}
trap rollback EXIT

echo "$EXPECTED_ADMIN_PACKAGE  $ADMIN_PACKAGE" | sha256sum -c -
echo "$EXPECTED_SHOP_PACKAGE  $SHOP_PACKAGE" | sha256sum -c -
echo "$EXPECTED_VERSION_FILE  $VERSION_FILE" | sha256sum -c -
[[ "$(tr -d '[:space:]' < "$VERSION_FILE")" == "$EXPECTED_NEW_VERSION" ]]
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "$EXPECTED_OLD_VERSION" ]]

systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server
curl -fsS --max-time 5 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'

before_counts=$(database_counts)
echo "validation-stage=preflight version=$EXPECTED_OLD_VERSION core-counts=$before_counts"

/usr/local/sbin/lingqimall-backup
backup_path=$(readlink -f "$APP_ROOT/backups/full/latest")
echo "validation-stage=full-backup path=$backup_path"

test ! -e "$STAGE_DIR"
test ! -e "$ROLLBACK_DIR"
install -d -m 0700 "$STAGE_DIR/admin" "$STAGE_DIR/shop" "$ROLLBACK_DIR"
tar -xzf "$ADMIN_PACKAGE" -C "$STAGE_DIR/admin"
tar -xzf "$SHOP_PACKAGE" -C "$STAGE_DIR/shop"
echo "$EXPECTED_ADMIN_INDEX  $STAGE_DIR/admin/index.html" | sha256sum -c -
echo "$EXPECTED_SHOP_INDEX  $STAGE_DIR/shop/index.html" | sha256sum -c -

tar -czf "$ROLLBACK_DIR/admin.tar.gz" -C "$APP_ROOT/nginx/admin" .
tar -czf "$ROLLBACK_DIR/shop.tar.gz" -C "$APP_ROOT/nginx/shop" .
cp "$APP_ROOT/VERSION" "$ROLLBACK_DIR/VERSION"
(cd "$ROLLBACK_DIR" && sha256sum admin.tar.gz shop.tar.gz VERSION > SHA256SUMS)

old_shop_entry=$(sed -n 's/.*src="\([^"]*\/assets\/index-[^"]*\.js\)".*/\1/p' "$APP_ROOT/nginx/shop/index.html" | head -n1)

MUTATED=1

# 先合并带哈希的静态资源，最后替换入口文件。旧分块继续保留，确保发布时已打开的页面仍能完成跳转。
find "$STAGE_DIR/admin" -mindepth 1 -maxdepth 1 ! -name index.html -exec cp -a -- {} "$APP_ROOT/nginx/admin/" \;
find "$STAGE_DIR/shop" -mindepth 1 -maxdepth 1 ! -name index.html -exec cp -a -- {} "$APP_ROOT/nginx/shop/" \;
install -m 0644 "$STAGE_DIR/admin/index.html" "$APP_ROOT/nginx/admin/index.html"
install -m 0644 "$STAGE_DIR/shop/index.html" "$APP_ROOT/nginx/shop/index.html"
install -m 0644 "$VERSION_FILE" "$APP_ROOT/VERSION"
chown -R www-data:www-data "$APP_ROOT/nginx/admin" "$APP_ROOT/nginx/shop"

nginx -t
systemctl reload nginx

echo "$EXPECTED_ADMIN_INDEX  $APP_ROOT/nginx/admin/index.html" | sha256sum -c -
echo "$EXPECTED_SHOP_INDEX  $APP_ROOT/nginx/shop/index.html" | sha256sum -c -
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "$EXPECTED_NEW_VERSION" ]]

grep -R -Fq 'lingqi_mall_cart_v2' "$APP_ROOT/nginx/shop/assets"
grep -R -Fq '页面资源已更新，正在为您重新加载' "$APP_ROOT/nginx/shop/assets"
grep -R -Fq '灵启商城智慧经营驾驶舱' "$APP_ROOT/nginx/admin/assets"
grep -R -Fq '直属邀请会员' "$APP_ROOT/nginx/admin/assets"

if [[ -n "$old_shop_entry" ]]; then
  test -f "$APP_ROOT/nginx/shop$old_shop_entry"
fi

post_counts=$(database_counts)
[[ "$post_counts" == "$before_counts" ]]
echo "validation-stage=database-unchanged core-counts=$post_counts"

shop_html=$(curl -fsS --max-time 12 https://lingqimall.com/)
admin_html=$(curl -fsS --max-time 12 https://lingqimall.com/admin/)
grep -q '<div id="app">' <<< "$shop_html"
grep -q '<div id="app">' <<< "$admin_html"

new_shop_entry=$(sed -n 's/.*src="\([^"]*\/assets\/index-[^"]*\.js\)".*/\1/p' "$APP_ROOT/nginx/shop/index.html" | head -n1)
new_admin_entry=$(sed -n 's/.*src="\([^"]*\/assets\/index-[^"]*\.js\)".*/\1/p' "$APP_ROOT/nginx/admin/index.html" | head -n1)
curl -fsS --max-time 12 -o /dev/null "https://lingqimall.com$new_shop_entry"
curl -fsS --max-time 12 -o /dev/null "https://lingqimall.com$new_admin_entry"

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
grep -qi '^cache-control:.*no-cache' <<< "$security_headers"
grep -qi '^cache-control:.*no-cache' <<< "$admin_headers"

systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server
ss -lnt | grep -q '127.0.0.1:3306'
ss -lnt | grep -q '127.0.0.1:6379'

MUTATED=0
trap - EXIT
rm -rf -- "$STAGE_DIR" "$ROLLBACK_DIR" "$RELEASE_DIR"
echo "release-success version=$EXPECTED_NEW_VERSION backup=$backup_path core-counts=$post_counts"
