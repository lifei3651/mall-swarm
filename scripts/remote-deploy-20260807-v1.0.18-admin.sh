#!/usr/bin/env bash
set -Eeuo pipefail

APP_ROOT=/opt/lingqimall
RELEASE_DIR=/tmp/lingqimall-release-v1.0.18-admin
ROLLBACK_DIR=/tmp/lingqimall-rollback-v1.0.18-admin
BACKUP_PATH=""
MUTATED=0

rollback() {
  local code=$?
  if [[ "$code" != 0 && "$MUTATED" == 1 ]]; then
    echo "admin release failed; restoring previous admin assets and version" >&2
    rm -rf -- "$APP_ROOT/nginx/admin"
    [[ -d "$ROLLBACK_DIR/admin" ]] && mv "$ROLLBACK_DIR/admin" "$APP_ROOT/nginx/admin"
    [[ -s "$ROLLBACK_DIR/VERSION" ]] && install -m 0644 "$ROLLBACK_DIR/VERSION" "$APP_ROOT/VERSION"
    chown -R www-data:www-data "$APP_ROOT/nginx/admin" || true
    nginx -t && systemctl reload nginx || true
  fi
  exit "$code"
}
trap rollback EXIT

[[ -s "$RELEASE_DIR/admin.tar.gz" ]]
[[ "$(tr -d '[:space:]' < "$RELEASE_DIR/VERSION")" == "1.0.18" ]]
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "1.0.17" ]]

systemctl is-active --quiet lingqimall-distribution.service
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server
curl -fsS --max-time 8 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'

/usr/local/sbin/lingqimall-backup
BACKUP_PATH=$(readlink -f "$APP_ROOT/backups/full/latest")

rm -rf -- "$ROLLBACK_DIR"
install -d -m 0700 "$ROLLBACK_DIR"
mv "$APP_ROOT/nginx/admin" "$ROLLBACK_DIR/admin"
cp "$APP_ROOT/VERSION" "$ROLLBACK_DIR/VERSION"

install -d -m 0755 "$APP_ROOT/nginx/admin"
tar -xzf "$RELEASE_DIR/admin.tar.gz" -C "$APP_ROOT/nginx/admin"
install -m 0644 "$RELEASE_DIR/VERSION" "$APP_ROOT/VERSION"
chown -R www-data:www-data "$APP_ROOT/nginx/admin"
MUTATED=1

nginx -t
systemctl reload nginx
admin_html=$(curl -fsS --max-time 12 https://lingqimall.com/admin/)
grep -q '<div id="app">' <<< "$admin_html"
admin_entry=$(grep -o '/admin/assets/index-[^" ]*\.js' <<< "$admin_html" | head -1)
curl -fsS --max-time 12 "https://lingqimall.com$admin_entry" >/dev/null
grep -R -Fq 'height: 560px' "$APP_ROOT/nginx/admin/assets"
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "1.0.18" ]]
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server
curl -fsS --max-time 12 https://lingqimall.com/ >/dev/null

MUTATED=0
trap - EXIT
rm -rf -- "$RELEASE_DIR" "$ROLLBACK_DIR"
echo "release-success version=1.0.18 backup=$BACKUP_PATH admin_entry=$admin_entry"
