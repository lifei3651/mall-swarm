#!/usr/bin/env bash
set -Eeuo pipefail

APP_ROOT=/opt/lingqimall
RELEASE_DIR=/tmp/lingqimall-release-20260802-1208-admin-shop-ui
ROLLBACK_DIR=/tmp/lingqimall-rollback-20260802-1208-admin-shop-ui

ADMIN="$RELEASE_DIR/admin-dist.tar.gz"
SHOP="$RELEASE_DIR/shop-dist.tar.gz"
NGINX_SITE="$RELEASE_DIR/lingqimall.conf"

EXPECTED_ADMIN=2f5d89254a7abeede9b5916a246a8c35ab17bf59d430dc6f29e2453b67b0ce84
EXPECTED_SHOP=bec4c705e3616e174d69264a820a8281c9d6656f2f50c8b568e4cf41f8d1c145
EXPECTED_NGINX=6001c17572384bc8bddb77c6f975f1d11d86c2414cfafec7540e4a1af4ae1b60
EXPECTED_ADMIN_INDEX=26f8cb9f59732cbe7edefd10bf7e41af2bc889212fa09c41183bb6f67741e4cb
EXPECTED_SHOP_INDEX=591c6f4d2f8ca1094916ca3024eec0d3aa9af8108823b6c454ae106798d7b6ae

MUTATED=0

rollback() {
  code=$?
  if [[ "$code" != 0 && "$MUTATED" == 1 ]]; then
    echo "deployment failed; restoring previous web files and nginx config" >&2
    find "$APP_ROOT/nginx/admin" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + || true
    tar -xzf "$ROLLBACK_DIR/admin.tar.gz" -C "$APP_ROOT/nginx/admin" || true
    find "$APP_ROOT/nginx/shop" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + || true
    tar -xzf "$ROLLBACK_DIR/shop.tar.gz" -C "$APP_ROOT/nginx/shop" || true
    install -m 0644 "$ROLLBACK_DIR/lingqimall.conf" /etc/nginx/sites-enabled/lingqimall.conf || true
    chown -R www-data:www-data "$APP_ROOT/nginx/admin" "$APP_ROOT/nginx/shop" || true
    nginx -t && systemctl reload nginx || true
  fi
  exit "$code"
}
trap rollback EXIT

echo "$EXPECTED_ADMIN  $ADMIN" | sha256sum -c -
echo "$EXPECTED_SHOP  $SHOP" | sha256sum -c -
echo "$EXPECTED_NGINX  $NGINX_SITE" | sha256sum -c -

systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server
curl -fsS --max-time 5 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'

/usr/local/sbin/lingqimall-backup
test ! -e "$ROLLBACK_DIR"
install -d -m 0700 "$ROLLBACK_DIR"
tar -czf "$ROLLBACK_DIR/admin.tar.gz" -C "$APP_ROOT/nginx/admin" .
tar -czf "$ROLLBACK_DIR/shop.tar.gz" -C "$APP_ROOT/nginx/shop" .
cp /etc/nginx/sites-enabled/lingqimall.conf "$ROLLBACK_DIR/lingqimall.conf"

MUTATED=1

# 不先删除旧哈希资源：已打开的页面在短期内仍能加载旧分块，避免点击菜单无反应。
tar -xzf "$ADMIN" -C "$APP_ROOT/nginx/admin"
tar -xzf "$SHOP" -C "$APP_ROOT/nginx/shop"
chown -R www-data:www-data "$APP_ROOT/nginx/admin" "$APP_ROOT/nginx/shop"

install -m 0644 "$NGINX_SITE" /etc/nginx/sites-enabled/lingqimall.conf
nginx -t
systemctl reload nginx

echo "$EXPECTED_ADMIN_INDEX  $APP_ROOT/nginx/admin/index.html" | sha256sum -c -
echo "$EXPECTED_SHOP_INDEX  $APP_ROOT/nginx/shop/index.html" | sha256sum -c -

admin_html=$(curl -fsS --max-time 12 https://lingqimall.com/admin/)
shop_html=$(curl -fsS --max-time 12 https://lingqimall.com/)
grep -q '<div id="app"></div>' <<< "$admin_html"
grep -q '<div id="app"></div>' <<< "$shop_html"

grep -R -Fq '价格、库存与规格' "$APP_ROOT/nginx/admin/assets"
grep -R -Fq 'admin-session-expired' "$APP_ROOT/nginx/admin/assets"
grep -R -Fq 'Failed to fetch dynamically imported module' "$APP_ROOT/nginx/admin/assets"
grep -R -Fq '返回上一页' "$APP_ROOT/nginx/shop/assets"

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

admin_headers=$(curl -fsSI --max-time 12 https://lingqimall.com/admin/)
site_headers=$(curl -fsSI --max-time 12 https://lingqimall.com/)
grep -qi '^cache-control:.*no-cache' <<< "$admin_headers"
grep -qi '^content-security-policy:' <<< "$admin_headers"
grep -qi '^strict-transport-security:' <<< "$admin_headers"
grep -qi '^content-security-policy:' <<< "$site_headers"
grep -qi '^strict-transport-security:' <<< "$site_headers"

systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server

# 当前版本稳定后只清理七天前的旧哈希资源，保留近期旧页面的兼容窗口。
find "$APP_ROOT/nginx/admin/assets" -type f -mtime +7 -delete
find "$APP_ROOT/nginx/shop/assets" -type f -mtime +7 -delete

MUTATED=0
trap - EXIT
rm -rf "$ROLLBACK_DIR" "$RELEASE_DIR"
echo "deployment-complete admin-auth=$admin_auth_status wallet=$wallet_status actuator=$actuator_status redirect=$http_status"
