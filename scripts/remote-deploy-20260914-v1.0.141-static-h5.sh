#!/usr/bin/env bash
# One static H5 target at a time; preserve backend, configuration, database and other sites.
set -Eeuo pipefail
umask 077
APP_ROOT=/opt/lingqimall
RELEASE_DIR=${1:-}
MODE=${2:-}
EXPECTED_COMMIT=${3:-}
SITE=${LINGQIMALL_STATIC_SITE:-}
[[ "$SITE" == shop || "$SITE" == team ]]
if [[ "$SITE" == shop ]]; then
  LABEL=public-h5; APPLICATION=storefront-public; BASE=https://lingqimall.com; OTHER=$APP_ROOT/nginx/team
else
  LABEL=team-h5; APPLICATION=team-h5; BASE=https://www.lingqimall.com; OTHER=$APP_ROOT/nginx/shop
fi
TARGET=$APP_ROOT/nginx/$SITE
[[ "$RELEASE_DIR" =~ ^/tmp/lingqimall-${LABEL}-141\.[A-Za-z0-9]+$ ]]
[[ "$EXPECTED_COMMIT" =~ ^[a-f0-9]{40}$ ]]
[[ "$MODE" == --preflight-only || "$MODE" == --authorize-release ]]
[[ "${LINGQIMALL_RELEASE_AUTHORIZATION:-}" == 1.0.141 ]]
[[ "$(hostname)" == VM-4-6-rockylinux && "$EUID" == 0 ]]
exec 8>/opt/lingqimall/.mini-backend-release.lock
flock -n 8
STAGE=''; ROLLBACK=''; MUTATED=0
protected_hashes() {
  find "$APP_ROOT/config" /etc/lingqimall /etc/systemd/system/lingqimall-distribution.service.d \
    /etc/nginx/conf.d "$APP_ROOT/nginx/admin" "$OTHER" -type f -print0 | sort -z | xargs -0 sha256sum
  sha256sum "$APP_ROOT/app/mall-distribution.jar" "$APP_ROOT/VERSION" /etc/systemd/system/lingqimall-distribution.service /etc/nginx/nginx.conf
}
backup_verify() {
  local output backup_path
  output=$(DB_AUTH_MODE=socket DB_USER=root RETENTION_DAYS=365000 OFFSITE_BACKUP_DIR='' bash "$RELEASE_DIR/production-backup.sh")
  backup_path=$(sed -n 's/^backup completed: //p' <<< "$output")
  [[ "$backup_path" =~ ^/opt/lingqimall/backups/full/20[0-9]{6}_[0-9]{6}$ ]]
  (cd "$backup_path" && sha256sum -c SHA256SUMS >/dev/null && gzip -t database.sql.gz)
  python3 - "$backup_path" "$TARGET" "$SITE" <<'PY'
import sys, tarfile, pathlib
with tarfile.open(sys.argv[1] + '/files-and-config.tar.gz') as archive:
    for filename in ['index.html', 'version.json']:
        assert archive.extractfile('opt/lingqimall/nginx/' + sys.argv[3] + '/' + filename).read() == pathlib.Path(sys.argv[2], filename).read_bytes()
PY
  printf '%s\n' "$backup_path"
}
recover() {
  local status=$?
  trap - EXIT
  if [[ "$status" != 0 && "$MUTATED" == 1 && -d "$ROLLBACK/$SITE" ]]; then
    [[ ! -d "$TARGET" ]] || mv "$TARGET" "$ROLLBACK/failed-$SITE"
    mv "$ROLLBACK/$SITE" "$TARGET"
    echo "$LABEL-recovery=previous-static-restored backup=$ROLLBACK" >&2
  fi
  exit "$status"
}
trap recover EXIT
trap 'echo "static-h5-release-check-failed site=$SITE line=$LINENO" >&2' ERR
[[ -d "$TARGET" && ! -L "$TARGET" ]]
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == 1.0.141 ]]
for svc in lingqimall-distribution nginx mysqld redis; do systemctl is-active --quiet "$svc"; done
curl -fsS --max-time 8 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'
[[ "$(df -Pm "$APP_ROOT" | awk 'NR==2 {print $4}')" -ge 4096 ]]
(cd "$RELEASE_DIR" && sha256sum -c SHA256SUMS)
python3 - "$RELEASE_DIR" "$TARGET" "$EXPECTED_COMMIT" "$SITE" "$APPLICATION" "$LABEL" <<'PY'
import json, sys, tarfile
root, target, commit, site, application, label = sys.argv[1:]
with open(target + '/version.json') as f: old = json.load(f)
assert old['version'] == '1.0.140' and old['gitCommit'] == '78a22078994acf6c7c84524ae66f57f15ee4e2a3'
with tarfile.open(root + '/' + site + '.tar.gz') as archive:
    names = set()
    for item in archive.getmembers():
        assert not item.issym() and not item.islnk() and (item.isfile() or item.isdir())
        assert not item.name.startswith('/') and '..' not in item.name.split('/')
        assert not any(x.startswith('.env') or x.startswith('._') for x in item.name.split('/'))
        assert item.name not in names
        names.add(item.name)
    with archive.extractfile('./version.json') as f: version = json.load(f)
assert version == {'version':'1.0.141','edition':'app-h5-split','application':application,'gitCommit':commit,'buildId':'20260914-' + label + '-1.0.141'}
print(label + '-candidate-and-previous-version=passed')
PY
BEFORE_FILES=$(protected_hashes)
echo "$LABEL-preflight=passed previous=1.0.140 target=1.0.141 backend=1.0.141"
[[ "$MODE" != --preflight-only ]] || exit 0
BACKUP_BEFORE=$(backup_verify)
ROLLBACK=$(mktemp -d "$APP_ROOT/backups/$LABEL-141.XXXXXX")
STAGE=$(mktemp -d "$APP_ROOT/nginx/.$SITE-141-stage.XXXXXX")
tar -xzf "$RELEASE_DIR/$SITE.tar.gz" -C "$STAGE"
(cd "$STAGE" && sha256sum -c "$RELEASE_DIR/${SITE^^}_SHA256SUMS" >/dev/null)
cp -an "$TARGET/assets/." "$STAGE/assets/"
find "$STAGE" -type d -exec chmod 0755 {} +
find "$STAGE" -type f -exec chmod 0644 {} +
chown -R "$(stat -c '%u:%g' "$TARGET")" "$STAGE"
[[ "$(protected_hashes)" == "$BEFORE_FILES" ]]
MUTATED=1
mv "$TARGET" "$ROLLBACK/$SITE"
mv "$STAGE" "$TARGET"
(cd "$TARGET" && sha256sum -c "$RELEASE_DIR/${SITE^^}_SHA256SUMS" >/dev/null)
python3 - "$EXPECTED_COMMIT" "$BASE" <<'PY'
import json, re, sys, urllib.request
commit, base = sys.argv[1:]
with urllib.request.urlopen(base + '/version.json?release=1.0.141', timeout=12) as response: version = json.load(response)
assert version['version'] == '1.0.141' and version['gitCommit'] == commit
with urllib.request.urlopen(base + '/?release=1.0.141', timeout=12) as response: html = response.read().decode()
entries = re.findall(r'(?:src|href)="(/assets/[^" ]+)"', html)
assert entries and '<div id="app">' in html
for entry in entries:
    with urllib.request.urlopen(base + entry, timeout=12) as response: assert response.status == 200
print('version-html-entry-assets=passed')
PY
[[ "$(protected_hashes)" == "$BEFORE_FILES" ]]
BACKUP_AFTER=$(backup_verify)
[[ "$BACKUP_AFTER" != "$BACKUP_BEFORE" ]]
for svc in lingqimall-distribution nginx mysqld redis; do systemctl is-active --quiet "$svc"; done
MUTATED=0
echo "$LABEL-release-success version=1.0.141 source=$EXPECTED_COMMIT before=$BACKUP_BEFORE after=$BACKUP_AFTER rollback=$ROLLBACK backend-other-sites-config-preserved=yes"
