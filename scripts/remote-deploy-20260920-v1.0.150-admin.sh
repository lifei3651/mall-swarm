#!/usr/bin/env bash
# Deploy the admin order-action clarity hotfix while preserving backend, database, configuration and both storefronts.
set -Eeuo pipefail
umask 077

APP_ROOT=/opt/lingqimall
RELEASE_DIR=${1:-}
MODE=${2:-}
EXPECTED_COMMIT=${3:-}
TARGET=$APP_ROOT/nginx/admin
[[ "$RELEASE_DIR" =~ ^/tmp/lingqimall-admin-150\.[A-Za-z0-9]+$ ]]
[[ "$EXPECTED_COMMIT" =~ ^[a-f0-9]{40}$ ]]
[[ "$MODE" == --preflight-only || "$MODE" == --authorize-release ]]
[[ "${LINGQIMALL_RELEASE_AUTHORIZATION:-}" == 1.0.150 ]]
[[ "$(hostname)" == VM-4-6-rockylinux && "$EUID" == 0 ]]
exec 8>/opt/lingqimall/.mini-backend-release.lock
flock -n 8
STAGE=''; ROLLBACK=''; MUTATED=0

protected_hashes() {
  find "$APP_ROOT/config" /etc/lingqimall /etc/systemd/system/lingqimall-distribution.service.d \
    /etc/nginx/conf.d "$APP_ROOT/nginx/shop" "$APP_ROOT/nginx/team" -type f -print0 | sort -z | xargs -0 sha256sum
  sha256sum "$APP_ROOT/app/mall-distribution.jar" "$APP_ROOT/VERSION" \
    /etc/systemd/system/lingqimall-distribution.service /etc/nginx/nginx.conf
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
    [[ ! -d "$TARGET" ]] || mv "$TARGET" "$ROLLBACK/failed-admin"
    mv "$ROLLBACK/admin" "$TARGET"
    echo "admin-recovery=previous-static-restored backup=$ROLLBACK" >&2
  fi
  exit "$status"
}
trap recover EXIT
trap 'echo "admin-release-check-failed line=$LINENO" >&2' ERR

[[ -d "$TARGET" && ! -L "$TARGET" ]]
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == 1.0.149 ]]
for svc in lingqimall-distribution nginx mysqld redis; do systemctl is-active --quiet "$svc"; done
curl -fsS --max-time 8 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'
[[ "$(df -Pm "$APP_ROOT" | awk 'NR==2 {print $4}')" -ge 4096 ]]
(cd "$RELEASE_DIR" && sha256sum -c SHA256SUMS)
python3 - "$RELEASE_DIR" "$TARGET" "$EXPECTED_COMMIT" <<'PY'
import json, sys, tarfile
root, target, commit = sys.argv[1:]
with open(target + '/version.json') as stream:
    old = json.load(stream)
assert old == {'version':'1.0.149','edition':'app-h5-split','application':'admin','gitCommit':'c1457f48bbfa64c1879e6986a659ef2ff8173bf9','buildId':'20260920-admin-1.0.149'}
with tarfile.open(root + '/admin.tar.gz') as archive:
    names = set()
    for item in archive.getmembers():
        assert not item.issym() and not item.islnk() and (item.isfile() or item.isdir())
        assert not item.name.startswith('/') and '..' not in item.name.split('/')
        assert not any(part.startswith('.env') or part.startswith('._') for part in item.name.split('/'))
        assert item.name not in names
        names.add(item.name)
    with archive.extractfile('./version.json') as stream:
        version = json.load(stream)
assert version == {'version':'1.0.150','edition':'app-h5-split','application':'admin','gitCommit':commit,'buildId':'20260920-admin-1.0.150'}
print('admin-candidate-and-previous-version=passed')
PY
BEFORE_FILES=$(protected_hashes)
echo 'admin-preflight=passed previous=1.0.149 target=1.0.150 backend=1.0.149'
[[ "$MODE" != --preflight-only ]] || exit 0

BACKUP_BEFORE=$(backup_verify)
ROLLBACK=$(mktemp -d "$APP_ROOT/backups/admin-150.XXXXXX")
STAGE=$(mktemp -d "$APP_ROOT/nginx/.admin-150-stage.XXXXXX")
tar -xzf "$RELEASE_DIR/admin.tar.gz" -C "$STAGE"
(cd "$STAGE" && sha256sum -c "$RELEASE_DIR/ADMIN_SHA256SUMS" >/dev/null)
cp -an "$TARGET/assets/." "$STAGE/assets/" 2>/dev/null || true
find "$STAGE" -type d -exec chmod 0755 {} +
find "$STAGE" -type f -exec chmod 0644 {} +
chown -R "$(stat -c '%u:%g' "$TARGET")" "$STAGE"
[[ "$(protected_hashes)" == "$BEFORE_FILES" ]]
MUTATED=1
mv "$TARGET" "$ROLLBACK/admin"
mv "$STAGE" "$TARGET"
(cd "$TARGET" && sha256sum -c "$RELEASE_DIR/ADMIN_SHA256SUMS" >/dev/null)
python3 - "$EXPECTED_COMMIT" <<'PY'
import json, re, sys, urllib.request
base = 'https://lingqimall.com/admin'
with urllib.request.urlopen(base + '/version.json?release=1.0.150', timeout=12) as response:
    version = json.load(response)
assert version == {'version':'1.0.150','edition':'app-h5-split','application':'admin','gitCommit':sys.argv[1],'buildId':'20260920-admin-1.0.150'}
with urllib.request.urlopen(base + '/?release=1.0.150', timeout=12) as response:
    html = response.read().decode()
entries = re.findall(r'(?:src|href)="(/admin/assets/[^" ]+)"', html)
assert entries and '<div id="app">' in html
for entry in entries:
    with urllib.request.urlopen('https://lingqimall.com' + entry, timeout=12) as response:
        assert response.status == 200
print('admin-version-html-entry-assets=passed')
PY
grep -R -Fq '订单已关闭，无需再次取消' "$TARGET/assets"
grep -R -Fq '已发货，请通过售后处理' "$TARGET/assets"
grep -R -Fq '联合支付单号' "$TARGET/assets"
[[ "$(protected_hashes)" == "$BEFORE_FILES" ]]
BACKUP_AFTER=$(backup_verify)
[[ "$BACKUP_AFTER" != "$BACKUP_BEFORE" ]]
for svc in lingqimall-distribution nginx mysqld redis; do systemctl is-active --quiet "$svc"; done
curl -fsS --max-time 8 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'
MUTATED=0
echo "admin-release-success version=1.0.150 source=$EXPECTED_COMMIT before=$BACKUP_BEFORE after=$BACKUP_AFTER rollback=$ROLLBACK backend-other-sites-config-preserved=yes"
