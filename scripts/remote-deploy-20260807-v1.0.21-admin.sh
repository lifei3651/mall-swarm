#!/usr/bin/env bash
set -Eeuo pipefail

APP_ROOT=/opt/lingqimall
RELEASE_DIR=/tmp/lingqimall-release-v1.0.21-admin
ROLLBACK_DIR=/tmp/lingqimall-rollback-v1.0.21-admin
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
[[ "$(tr -d '[:space:]' < "$RELEASE_DIR/VERSION")" == "1.0.21" ]]
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "1.0.20" ]]

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
sleep 2
admin_html=$(curl -fsS --max-time 12 -H 'Cache-Control: no-cache' -H 'Pragma: no-cache' 'https://lingqimall.com/admin/?release=1.0.21')
grep -q '<div id="app">' <<< "$admin_html"
admin_entry=$(grep -o '/admin/assets/index-[^" ]*\.js' <<< "$admin_html" | head -1)
curl -fsS --max-time 12 -H 'Cache-Control: no-cache' "https://lingqimall.com$admin_entry?release=1.0.21" >/dev/null
grep -R -Fq '颜色微调' "$APP_ROOT/nginx/admin/assets"
grep -R -Fq '首页 Banner' "$APP_ROOT/nginx/admin/assets"
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "1.0.21" ]]
systemctl is-active --quiet nginx
systemctl is-active --quiet mysql
systemctl is-active --quiet redis-server
curl -fsS --max-time 12 -H 'Cache-Control: no-cache' 'https://lingqimall.com/?release=1.0.21' >/dev/null

MUTATED=0
trap - EXIT
rm -rf -- "$RELEASE_DIR" "$ROLLBACK_DIR"
echo "release-success version=1.0.21 backup=$BACKUP_PATH admin_entry=$admin_entry"
