#!/usr/bin/env bash
set -Eeuo pipefail

APP_ROOT=/opt/lingqimall
RELEASE_DIR=/tmp/lingqimall-release-v1.0.15-frontend
ROLLBACK_DIR=/tmp/lingqimall-rollback-v1.0.15-frontend
BACKUP_PATH=""
MUTATED=0

rollback() {
  local code=$?
  if [[ "$code" != 0 && "$MUTATED" == 1 && -d "$ROLLBACK_DIR/shop" ]]; then
    echo "frontend release failed; restoring previous shop assets" >&2
    rm -rf -- "$APP_ROOT/nginx/shop"
    mv "$ROLLBACK_DIR/shop" "$APP_ROOT/nginx/shop"
    install -m 0644 "$ROLLBACK_DIR/VERSION" "$APP_ROOT/VERSION"
    chown -R www-data:www-data "$APP_ROOT/nginx/shop"
    nginx -t && systemctl reload nginx || true
  fi
  exit "$code"
}
trap rollback EXIT

[[ -s "$RELEASE_DIR/shop.tar.gz" ]]
[[ "$(tr -d '[:space:]' < "$RELEASE_DIR/VERSION")" == "1.0.15" ]]
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "1.0.14" ]]
systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server
curl -fsS --max-time 8 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'

/usr/local/sbin/lingqimall-backup
BACKUP_PATH=$(readlink -f "$APP_ROOT/backups/full/latest")
rm -rf -- "$ROLLBACK_DIR"
install -d -m 0700 "$ROLLBACK_DIR"
mv "$APP_ROOT/nginx/shop" "$ROLLBACK_DIR/shop"
cp "$APP_ROOT/VERSION" "$ROLLBACK_DIR/VERSION"
install -d -m 0755 "$APP_ROOT/nginx/shop"
tar -xzf "$RELEASE_DIR/shop.tar.gz" -C "$APP_ROOT/nginx/shop"
install -m 0644 "$RELEASE_DIR/VERSION" "$APP_ROOT/VERSION"
chown -R www-data:www-data "$APP_ROOT/nginx/shop"
MUTATED=1

nginx -t
systemctl reload nginx
shop_html=$(curl -fsS --max-time 12 https://lingqimall.com/)
grep -q '<div id="app">' <<< "$shop_html"
shop_entry=$(grep -o '/assets/index-[^" ]*\.js' <<< "$shop_html" | head -1)
curl -fsS --max-time 12 "https://lingqimall.com$shop_entry" >/dev/null
grep -R -Fq '网络暂时不可用，请检查网络后重试' "$APP_ROOT/nginx/shop/assets"
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "1.0.15" ]]
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server

MUTATED=0
trap - EXIT
rm -rf -- "$RELEASE_DIR" "$ROLLBACK_DIR"
echo "release-success version=1.0.15 backup=$BACKUP_PATH shop_entry=$shop_entry"
