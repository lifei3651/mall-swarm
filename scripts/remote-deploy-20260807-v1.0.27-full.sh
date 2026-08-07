#!/usr/bin/env bash
set -Eeuo pipefail

APP_ROOT=/opt/lingqimall
RELEASE_DIR=/tmp/lingqimall-release-v1.0.27-full
ROLLBACK_DIR=/tmp/lingqimall-rollback-v1.0.27-full
BACKUP_PATH=""
MUTATED=0

rollback() {
  local code=$?
  if [[ "$code" != 0 && "$MUTATED" == 1 ]]; then
    echo "full release failed; restoring previous backend, admin, shop and version" >&2
    systemctl stop lingqimall-distribution.service || true
    [[ -s "$ROLLBACK_DIR/mall-distribution.jar" ]] && install -m 0644 "$ROLLBACK_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar" || true
    [[ -d "$ROLLBACK_DIR/admin" ]] && { rm -rf -- "$APP_ROOT/nginx/admin"; mv "$ROLLBACK_DIR/admin" "$APP_ROOT/nginx/admin"; } || true
    [[ -d "$ROLLBACK_DIR/shop" ]] && { rm -rf -- "$APP_ROOT/nginx/shop"; mv "$ROLLBACK_DIR/shop" "$APP_ROOT/nginx/shop"; } || true
    [[ -s "$ROLLBACK_DIR/VERSION" ]] && install -m 0644 "$ROLLBACK_DIR/VERSION" "$APP_ROOT/VERSION" || true
    chown -R www-data:www-data "$APP_ROOT/nginx/admin" "$APP_ROOT/nginx/shop" || true
    systemctl start lingqimall-distribution.service || true
    nginx -t && systemctl reload nginx || true
  fi
  exit "$code"
}
trap rollback EXIT

[[ -s "$RELEASE_DIR/mall-distribution.jar" ]]
[[ -s "$RELEASE_DIR/admin.tar.gz" ]]
[[ -s "$RELEASE_DIR/shop.tar.gz" ]]
[[ "$(tr -d '[:space:]' < "$RELEASE_DIR/VERSION")" == "1.0.27" ]]
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "1.0.26" ]]

systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server
curl -fsS --max-time 8 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'

/usr/local/sbin/lingqimall-backup
BACKUP_PATH=$(readlink -f "$APP_ROOT/backups/full/latest")

rm -rf -- "$ROLLBACK_DIR"
install -d -m 0700 "$ROLLBACK_DIR"
cp "$APP_ROOT/app/mall-distribution.jar" "$ROLLBACK_DIR/mall-distribution.jar"
mv "$APP_ROOT/nginx/admin" "$ROLLBACK_DIR/admin"
mv "$APP_ROOT/nginx/shop" "$ROLLBACK_DIR/shop"
cp "$APP_ROOT/VERSION" "$ROLLBACK_DIR/VERSION"
MUTATED=1

systemctl stop lingqimall-distribution.service
install -m 0644 "$RELEASE_DIR/mall-distribution.jar" "$APP_ROOT/app/mall-distribution.jar"
install -d -m 0755 "$APP_ROOT/nginx/admin" "$APP_ROOT/nginx/shop"
tar -xzf "$RELEASE_DIR/admin.tar.gz" -C "$APP_ROOT/nginx/admin"
tar -xzf "$RELEASE_DIR/shop.tar.gz" -C "$APP_ROOT/nginx/shop"
install -m 0644 "$RELEASE_DIR/VERSION" "$APP_ROOT/VERSION"
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
nginx -t
systemctl reload nginx
admin_html=$(curl -fsS --max-time 12 -H 'Cache-Control: no-cache' 'https://lingqimall.com/admin/?release=1.0.27')
shop_html=$(curl -fsS --max-time 12 -H 'Cache-Control: no-cache' 'https://lingqimall.com/?release=1.0.27')
grep -q '<div id="app">' <<< "$admin_html"
grep -q '<div id="app">' <<< "$shop_html"
admin_entry=$(grep -o '/admin/assets/index-[^" ]*\.js' <<< "$admin_html" | head -1)
shop_entry=$(grep -o '/assets/index-[^" ]*\.js' <<< "$shop_html" | head -1)
curl -fsS --max-time 12 -H 'Cache-Control: no-cache' "https://lingqimall.com$admin_entry?release=1.0.27" >/dev/null
curl -fsS --max-time 12 -H 'Cache-Control: no-cache' "https://lingqimall.com$shop_entry?release=1.0.27" >/dev/null
grep -R -Fq '后台退款' "$APP_ROOT/nginx/admin/assets"
grep -R -Fq '下单后7天内可申请售后' "$APP_ROOT/nginx/shop/assets"
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "1.0.27" ]]
systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server

MUTATED=0
trap - EXIT
rm -rf -- "$RELEASE_DIR" "$ROLLBACK_DIR"
echo "release-success version=1.0.27 backup=$BACKUP_PATH admin_entry=$admin_entry shop_entry=$shop_entry"
