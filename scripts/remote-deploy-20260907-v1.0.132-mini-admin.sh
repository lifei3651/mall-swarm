#!/usr/bin/env bash
# Admin-only companion deployment; keeps backend, public/team H5 and all configuration intact.
set -Eeuo pipefail
umask 077
APP_ROOT=/opt/lingqimall
RELEASE_DIR=${1:-}
MODE=${2:-}
[[ "$RELEASE_DIR" =~ ^/tmp/lingqimall-mini-admin-132\.[A-Za-z0-9]+$ ]]
[[ "$MODE" == --preflight-only || "$MODE" == --authorize-release ]]
[[ "${LINGQIMALL_RELEASE_AUTHORIZATION:-}" == 1.0.132 ]]
[[ "$(hostname)" == VM-4-6-rockylinux && "$EUID" == 0 ]]
exec 8>/opt/lingqimall/.mini-backend-release.lock
flock -n 8
TARGET=$APP_ROOT/nginx/admin
STAGE=''
ROLLBACK=''
MUTATED=0
protected_hashes() {
  find "$APP_ROOT/config" /etc/lingqimall /etc/systemd/system/lingqimall-distribution.service.d \
    /etc/nginx/conf.d "$APP_ROOT/nginx/shop" "$APP_ROOT/nginx/team" -type f -print0 | sort -z | xargs -0 sha256sum
  sha256sum "$APP_ROOT/app/mall-distribution.jar" "$APP_ROOT/VERSION" /etc/systemd/system/lingqimall-distribution.service /etc/nginx/nginx.conf
}
backup_verify() {
  local output backup_path
  output=$(DB_AUTH_MODE=socket DB_USER=root RETENTION_DAYS=365000 OFFSITE_BACKUP_DIR='' bash "$RELEASE_DIR/production-backup.sh")
  backup_path=$(sed -n 's/^backup completed: //p' <<< "$output")
  [[ "$backup_path" =~ ^/opt/lingqimall/backups/full/20[0-9]{6}_[0-9]{6}$ ]]
  (cd "$backup_path" && sha256sum -c SHA256SUMS >/dev/null && gzip -t database.sql.gz && tar -tzf files-and-config.tar.gz >/dev/null)
  printf '%s\n' "$backup_path"
}
recover() {
  local status=$?
  trap - EXIT
  if [[ "$status" != 0 && "$MUTATED" == 1 && -d "$ROLLBACK/admin" ]]; then
    if [[ -d "$TARGET" ]]; then mv "$TARGET" "$ROLLBACK/failed-admin"; fi
    mv "$ROLLBACK/admin" "$TARGET"
    echo "admin-recovery=previous-admin-restored backup=$ROLLBACK"
  fi
  exit "$status"
}
trap recover EXIT
trap 'echo "admin-release-check-failed line=$LINENO" >&2' ERR
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == 1.0.132 ]]
for svc in lingqimall-distribution nginx mysqld redis; do systemctl is-active --quiet "$svc"; done
curl -fsS http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'
[[ "$(df -Pm "$APP_ROOT" | awk 'NR==2 {print $4}')" -ge 4096 ]]
[[ -z "$(find "$APP_ROOT/backups/full" -mindepth 1 -maxdepth 1 -type d -name '.20??????_??????.tmp' -mtime +1 -print -quit)" ]]
(cd "$RELEASE_DIR" && sha256sum -c SHA256SUMS)
python3 - "$RELEASE_DIR" "$TARGET" <<'PY'
import json, os, sys, tarfile
root, target = sys.argv[1:]
with open(target + '/version.json') as f: old = json.load(f)
assert old['version'] == '1.0.115' and old['gitCommit'] == '9efd8111964765e1db6fcfb920eb5a8af356dbc8'
with tarfile.open(root + '/admin.tar.gz') as archive:
    entries = archive.getmembers()
    for item in entries:
        assert not item.issym() and not item.islnk() and (item.isfile() or item.isdir())
        assert not item.name.startswith('/') and '..' not in item.name.split('/')
        assert not any(x.startswith('.env') for x in item.name.split('/'))
    with archive.extractfile('./version.json') as f: version = json.load(f)
assert version == {'version':'1.0.132','edition':'app-h5-split','application':'admin','gitCommit':'91e5a6d824be2f6e7a3f6f9f5d642b7f9635c957','buildId':'20260907-mini-admin-1.0.132'}
print('admin-candidate-and-previous-version=passed')
PY
BEFORE_FILES=$(protected_hashes)
echo 'admin-preflight=passed previous=1.0.115 target=1.0.132'
[[ "$MODE" != --preflight-only ]] || exit 0
BACKUP_BEFORE=$(backup_verify)
ROLLBACK=$(mktemp -d "$APP_ROOT/backups/mini-admin-132.XXXXXX")
STAGE=$(mktemp -d "$APP_ROOT/nginx/.admin-132-stage.XXXXXX")
tar -xzf "$RELEASE_DIR/admin.tar.gz" -C "$STAGE"
(cd "$STAGE" && sha256sum -c "$RELEASE_DIR/ADMIN_SHA256SUMS" >/dev/null)
# Preserve immutable hashed chunks so an already-open admin page can finish loading.
cp -an "$TARGET/assets/." "$STAGE/assets/"
find "$STAGE" -type d -exec chmod 0755 {} +
find "$STAGE" -type f -exec chmod 0644 {} +
chown -R "$(stat -c '%u:%g' "$TARGET")" "$STAGE"
[[ "$(protected_hashes)" == "$BEFORE_FILES" ]]
MUTATED=1
mv "$TARGET" "$ROLLBACK/admin"
mv "$STAGE" "$TARGET"
(cd "$TARGET" && sha256sum -c "$RELEASE_DIR/ADMIN_SHA256SUMS" >/dev/null)
curl -fsS --max-time 12 https://lingqimall.com/admin/version.json | python3 -c 'import json,sys; v=json.load(sys.stdin); assert v["version"]=="1.0.132" and v["gitCommit"]=="91e5a6d824be2f6e7a3f6f9f5d642b7f9635c957"'
curl -fsS --max-time 12 https://lingqimall.com/admin/ > "$ROLLBACK/new-index.html"
python3 - "$ROLLBACK/new-index.html" <<'PY'
import re, sys, urllib.request
with open(sys.argv[1]) as f: html = f.read()
entries = re.findall(r'(?:src|href)="(/admin/assets/[^" ]+)"', html)
assert entries and '<div id="app">' in html
for entry in entries:
    with urllib.request.urlopen('https://lingqimall.com' + entry, timeout=12) as r: assert r.status == 200
print('admin-html-and-entry-assets=passed')
PY
[[ "$(protected_hashes)" == "$BEFORE_FILES" ]]
BACKUP_AFTER=$(backup_verify)
[[ "$BACKUP_AFTER" != "$BACKUP_BEFORE" ]]
for svc in lingqimall-distribution nginx mysqld redis; do systemctl is-active --quiet "$svc"; done
MUTATED=0
echo "admin-release-success version=1.0.132 before=$BACKUP_BEFORE after=$BACKUP_AFTER rollback=$ROLLBACK backend-h5-config-preserved=yes"
