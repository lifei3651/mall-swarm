#!/usr/bin/env bash
# team static only; preserve other sites, backend, configuration and database.
set -Eeuo pipefail
umask 077
APP_ROOT=/opt/lingqimall
RELEASE_DIR=${1:-}
MODE=${2:-}
EXPECTED_COMMIT=${3:-}
[[ "$RELEASE_DIR" =~ ^/tmp/lingqimall-team-h5-136\.[A-Za-z0-9]+$ ]]
[[ "$EXPECTED_COMMIT" =~ ^[a-f0-9]{40}$ ]]
[[ "$MODE" == --preflight-only || "$MODE" == --authorize-release ]]
[[ "${LINGQIMALL_RELEASE_AUTHORIZATION:-}" == 1.0.136 ]]
[[ "$(hostname)" == VM-4-6-rockylinux && "$EUID" == 0 ]]
exec 8>/opt/lingqimall/.mini-backend-release.lock
flock -n 8
TARGET=$APP_ROOT/nginx/team
STAGE=''
ROLLBACK=''
MUTATED=0
protected_hashes() {
  find "$APP_ROOT/config" /etc/lingqimall /etc/systemd/system/lingqimall-distribution.service.d \
    /etc/nginx/conf.d "$APP_ROOT/nginx/admin" "$APP_ROOT/nginx/shop" -type f -print0 | sort -z | xargs -0 sha256sum
  sha256sum "$APP_ROOT/app/mall-distribution.jar" "$APP_ROOT/VERSION" /etc/systemd/system/lingqimall-distribution.service /etc/nginx/nginx.conf
}
backup_verify() {
  local output backup_path
  output=$(DB_AUTH_MODE=socket DB_USER=root RETENTION_DAYS=365000 OFFSITE_BACKUP_DIR='' bash "$RELEASE_DIR/production-backup.sh")
  backup_path=$(sed -n 's/^backup completed: //p' <<< "$output")
  [[ "$backup_path" =~ ^/opt/lingqimall/backups/full/20[0-9]{6}_[0-9]{6}$ ]]
  (cd "$backup_path" && sha256sum -c SHA256SUMS >/dev/null && gzip -t database.sql.gz)
  python3 - "$backup_path" "$TARGET" <<'PY'
import sys, tarfile, pathlib
with tarfile.open(sys.argv[1] + '/files-and-config.tar.gz') as archive:
    for filename in ['index.html', 'version.json']:
        assert archive.extractfile('opt/lingqimall/nginx/team/' + filename).read() == pathlib.Path(sys.argv[2], filename).read_bytes()
PY
  printf '%s\n' "$backup_path"
}
recover() {
  local status=$?
  trap - EXIT
  if [[ "$status" != 0 && "$MUTATED" == 1 && -d "$ROLLBACK/team" ]]; then
    if [[ -d "$TARGET" ]]; then mv "$TARGET" "$ROLLBACK/failed-team"; fi
    mv "$ROLLBACK/team" "$TARGET"
    echo "team-h5-recovery=previous-static-restored backup=$ROLLBACK"
  fi
  exit "$status"
}
trap recover EXIT
trap 'echo "team-h5-release-check-failed line=$LINENO" >&2' ERR
[[ -d "$TARGET" && ! -L "$TARGET" ]]
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == 1.0.136 ]]
for svc in lingqimall-distribution nginx mysqld redis; do systemctl is-active --quiet "$svc"; done
curl -fsS --max-time 8 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'
[[ "$(df -Pm "$APP_ROOT" | awk 'NR==2 {print $4}')" -ge 4096 ]]
[[ -z "$(find "$APP_ROOT/backups/full" -mindepth 1 -maxdepth 1 -type d -name '.20??????_??????.tmp' -mtime +1 -print -quit)" ]]
(cd "$RELEASE_DIR" && sha256sum -c SHA256SUMS)
python3 - "$RELEASE_DIR" "$TARGET" "$EXPECTED_COMMIT" <<'PY'
import json, sys, tarfile
root, target, commit = sys.argv[1:]
with open(target + '/version.json') as f: old = json.load(f)
assert old['version'] == '1.0.115' and old['gitCommit'] == '9efd8111964765e1db6fcfb920eb5a8af356dbc8'
with tarfile.open(root + '/team.tar.gz') as archive:
    names = set()
    for item in archive.getmembers():
        assert not item.issym() and not item.islnk() and (item.isfile() or item.isdir())
        assert not item.name.startswith('/') and '..' not in item.name.split('/')
        assert not any(x.startswith('.env') or x.startswith('._') for x in item.name.split('/'))
        assert item.name not in names
        names.add(item.name)
    with archive.extractfile('./version.json') as f: version = json.load(f)
assert version == {'version':'1.0.136','edition':'app-h5-split','application':'team-h5','gitCommit':commit,'buildId':'20260909-team-h5-1.0.136'}
print('team-h5-candidate-and-previous-version=passed')
PY
BEFORE_FILES=$(protected_hashes)
echo 'team-h5-preflight=passed previous=1.0.115 target=1.0.136 backend=1.0.136'
[[ "$MODE" != --preflight-only ]] || exit 0
BACKUP_BEFORE=$(backup_verify)
ROLLBACK=$(mktemp -d "$APP_ROOT/backups/team-h5-136.XXXXXX")
STAGE=$(mktemp -d "$APP_ROOT/nginx/.team-136-stage.XXXXXX")
tar -xzf "$RELEASE_DIR/team.tar.gz" -C "$STAGE"
(cd "$STAGE" && sha256sum -c "$RELEASE_DIR/TEAM_SHA256SUMS" >/dev/null)
# Retain old immutable chunks for customers with an already-open page.
cp -an "$TARGET/assets/." "$STAGE/assets/"
find "$STAGE" -type d -exec chmod 0755 {} +
find "$STAGE" -type f -exec chmod 0644 {} +
chown -R "$(stat -c '%u:%g' "$TARGET")" "$STAGE"
[[ "$(protected_hashes)" == "$BEFORE_FILES" ]]
MUTATED=1
mv "$TARGET" "$ROLLBACK/team"
mv "$STAGE" "$TARGET"
(cd "$TARGET" && sha256sum -c "$RELEASE_DIR/TEAM_SHA256SUMS" >/dev/null)
python3 - "$EXPECTED_COMMIT" <<'PY'
import json, re, sys, urllib.request
base = 'https://www.lingqimall.com'
with urllib.request.urlopen(base + '/version.json?release=1.0.136', timeout=12) as response: version = json.load(response)
assert version['version'] == '1.0.136' and version['gitCommit'] == sys.argv[1]
with urllib.request.urlopen(base + '/?release=1.0.136', timeout=12) as response: html = response.read().decode()
entries = re.findall(r'(?:src|href)="(/assets/[^" ]+)"', html)
assert entries and '<div id="app">' in html
for entry in entries:
    with urllib.request.urlopen(base + entry, timeout=12) as response: assert response.status == 200
print('team-h5-version-html-entry-assets=passed')
PY
[[ "$(protected_hashes)" == "$BEFORE_FILES" ]]
BACKUP_AFTER=$(backup_verify)
[[ "$BACKUP_AFTER" != "$BACKUP_BEFORE" ]]
for svc in lingqimall-distribution nginx mysqld redis; do systemctl is-active --quiet "$svc"; done
MUTATED=0
echo "team-h5-release-success version=1.0.136 source=$EXPECTED_COMMIT before=$BACKUP_BEFORE after=$BACKUP_AFTER rollback=$ROLLBACK backend-other-sites-config-preserved=yes"
