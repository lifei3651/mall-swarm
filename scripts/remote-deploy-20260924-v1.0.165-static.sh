#!/usr/bin/env bash
# Deploy exactly one immutable 1.0.165 static surface from the unified closure candidate.
set -Eeuo pipefail
shopt -s inherit_errexit
umask 077

EXPECTED_VERSION=1.0.165
EXPECTED_BUILD_ID=20260924-closure-1.0.165
EXPECTED_BUILD_METHOD=clean-build-in-release-process
EXPECTED_PREVIOUS_VERSION=1.0.158
EXPECTED_PREVIOUS_COMMIT=fcadf4099861a0a5359b12761cbf232c160635a7
APP_ROOT=/opt/lingqimall
RELEASE_DIR=${1:-}
MODE=${2:-}
SITE=${3:-}
EXPECTED_COMMIT=${4:-}
EXPECTED_SOURCE_TREE=${LINGQIMALL_RELEASE_SOURCE_TREE:-}

fail() { echo "static release aborted: $*" >&2; exit 1; }
[[ "$EUID" == 0 ]] || fail "must run as root"
[[ "$(hostname)" == VM-4-6-rockylinux ]] || fail "unexpected host"
[[ "$MODE" == --preflight-only || "$MODE" == --authorize-release ]] || fail "invalid mode"
[[ "$SITE" == admin || "$SITE" == shop || "$SITE" == team ]] || fail "invalid site"
[[ "$EXPECTED_COMMIT" =~ ^[a-f0-9]{40}$ ]] || fail "invalid source commit"
[[ "$EXPECTED_SOURCE_TREE" =~ ^[a-f0-9]{40}$ ]] || fail "invalid source tree"
[[ "${LINGQIMALL_RELEASE_AUTHORIZATION:-}" == "$EXPECTED_VERSION" ]] || fail "authorization missing"
[[ "$RELEASE_DIR" =~ ^/tmp/lingqimall-closure-165\.[A-Za-z0-9]+$ ]] || fail "invalid candidate directory"

case "$SITE" in
  admin)
    LABEL=admin
    APPLICATION=admin
    BASE=https://lingqimall.com/admin
    PREVIOUS_BUILD_ID=20260923-closure-1.0.158
    ;;
  shop)
    LABEL=public-h5
    APPLICATION=storefront-public
    BASE=https://lingqimall.com
    PREVIOUS_BUILD_ID=20260923-closure-1.0.158
    ;;
  team)
    LABEL=team-h5
    APPLICATION=team-h5
    BASE=https://www.lingqimall.com
    PREVIOUS_BUILD_ID=20260923-closure-1.0.158
    ;;
esac

TARGET=$APP_ROOT/nginx/$SITE
ARCHIVE=$RELEASE_DIR/$SITE.tar.gz
FILE_MANIFEST=$RELEASE_DIR/${SITE^^}_SHA256SUMS
RELEASE_MANIFEST=$RELEASE_DIR/RELEASE_MANIFEST.json
BACKUP_SCRIPT=$RELEASE_DIR/production-backup.sh
RETENTION_VERIFIER=$RELEASE_DIR/verify-artifact-retention.mjs

exec 8>/opt/lingqimall/.mini-backend-release.lock
flock -n 8 || fail "another release is running"
[[ -s "$RETENTION_VERIFIER" ]] || fail "candidate retention verifier is missing"

STAGE=''
ROLLBACK=''
MUTATED=0
EXCHANGED=0

protected_hashes() {
  {
    find "$APP_ROOT/config" /etc/lingqimall /etc/systemd/system/lingqimall-distribution.service.d \
      /etc/nginx/conf.d -type f -print0
    for other in admin shop team; do
      [[ "$other" == "$SITE" ]] || find "$APP_ROOT/nginx/$other" -type f -print0
    done
    printf '%s\0' "$APP_ROOT/app/mall-distribution.jar" "$APP_ROOT/VERSION" \
      /etc/systemd/system/lingqimall-distribution.service /etc/nginx/nginx.conf
  } | sort -z | xargs -0 -r sha256sum
}

backup_verify() {
  local output backup_path
  output=$(DB_AUTH_MODE=socket DB_USER=root RETENTION_DAYS=365000 OFFSITE_BACKUP_DIR='' bash "$BACKUP_SCRIPT")
  backup_path=$(sed -n 's/^backup completed: //p' <<<"$output")
  [[ "$backup_path" =~ ^/opt/lingqimall/backups/full/20[0-9]{6}_[0-9]{6}$ ]] || fail "invalid backup path"
  (cd "$backup_path" && sha256sum -c SHA256SUMS >/dev/null && gzip -t database.sql.gz && tar -tzf files-and-config.tar.gz >/dev/null)
  printf '%s\n' "$backup_path"
}

verify_candidate_and_tree() {
  local tree=${1:-}
  python3 - "$ARCHIVE" "$FILE_MANIFEST" "$tree" <<'PY'
import hashlib
import pathlib
import re
import sys
import tarfile

archive_path, manifest_path, tree = sys.argv[1:]
line_re = re.compile(r'^([a-f0-9]{64})  \./(.+)$')
expected = {}
with open(manifest_path, encoding='utf-8') as stream:
    for number, raw in enumerate(stream, 1):
        line = raw.rstrip('\n')
        match = line_re.fullmatch(line)
        assert match, f'invalid checksum line {number}'
        digest, relative = match.groups()
        path = pathlib.PurePosixPath(relative)
        assert not path.is_absolute() and '..' not in path.parts
        assert relative not in expected
        assert not any(part.startswith('.env') or part.startswith('._') for part in path.parts)
        expected[relative] = digest
assert expected and 'index.html' in expected and 'version.json' in expected

archive_files = set()
archive_names = set()
with tarfile.open(archive_path, 'r:gz') as archive:
    for member in archive.getmembers():
        name = member.name
        while name.startswith('./'):
            name = name[2:]
        if not name:
            continue
        path = pathlib.PurePosixPath(name)
        assert not path.is_absolute() and '..' not in path.parts
        assert name not in archive_names
        archive_names.add(name)
        assert not member.issym() and not member.islnk()
        assert member.isfile() or member.isdir()
        assert not any(part.startswith('.env') or part.startswith('._') for part in path.parts)
        if member.isfile():
            archive_files.add(name)
    assert archive_files == set(expected), (sorted(archive_files - set(expected)), sorted(set(expected) - archive_files))

if tree:
    root = pathlib.Path(tree)
    actual = set()
    for path in root.rglob('*'):
        relative = path.relative_to(root).as_posix()
        assert not path.is_symlink(), relative
        if path.is_file():
            actual.add(relative)
        else:
            assert path.is_dir(), relative
    assert actual == set(expected), (sorted(actual - set(expected)), sorted(set(expected) - actual))
    for relative, wanted in expected.items():
        digest = hashlib.sha256((root / relative).read_bytes()).hexdigest()
        assert digest == wanted, relative
print('candidate-tree-exact=passed files=' + str(len(expected)))
PY
}

exchange_directories() {
  python3 - "$1" "$2" <<'PY'
import ctypes
import os
import pathlib
import sys

left, right = map(pathlib.Path, sys.argv[1:])
assert left.is_dir() and right.is_dir() and not left.is_symlink() and not right.is_symlink()
libc = ctypes.CDLL(None, use_errno=True)
renameat2 = libc.renameat2
renameat2.argtypes = [ctypes.c_int, ctypes.c_char_p, ctypes.c_int, ctypes.c_char_p, ctypes.c_uint]
renameat2.restype = ctypes.c_int
if renameat2(-100, os.fsencode(left), -100, os.fsencode(right), 2) != 0:
    error = ctypes.get_errno()
    raise OSError(error, os.strerror(error))
PY
}

verify_previous_site() {
  python3 - "$TARGET/version.json" "$BASE" "$APPLICATION" "$PREVIOUS_BUILD_ID" "$EXPECTED_PREVIOUS_COMMIT" <<'PY'
import json
import sys
import time
import urllib.request

target_version, base, application, build_id, commit = sys.argv[1:]
expected = {
    'version':'1.0.158', 'edition':'app-h5-split', 'application':application,
    'gitCommit':commit, 'buildId':build_id,
}
with open(target_version, encoding='utf-8') as stream:
    assert json.load(stream) == expected
with urllib.request.urlopen(base + '/version.json?recovery=' + str(time.time_ns()), timeout=12) as response:
    assert json.load(response) == expected
print('restored-site-version=passed')
PY
}

verify_recovery_health() {
  local svc
  for svc in lingqimall-distribution nginx mysqld redis; do
    systemctl is-active --quiet "$svc" || return 1
  done
  curl -fsS --max-time 8 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"'
}

recover() {
  local status=$? evidence recovery_failed=0 recovered_hashes=''
  trap - EXIT
  set +e
  if [[ "$status" != 0 && "$MUTATED" == 1 && -n "$ROLLBACK" ]]; then
    evidence=$ROLLBACK/RECOVERY_EVIDENCE.txt
    if ! { : >"$evidence" && printf 'original_status=%s\nsite=%s\nexchanged=%s\ntarget=%s\nstage=%s\n' \
      "$status" "$SITE" "$EXCHANGED" "$TARGET" "$STAGE" >>"$evidence"; }; then
      echo "$LABEL-recovery=FAILED evidence-file-unavailable rollback=$ROLLBACK original-status=$status" >&2
      exit 70
    fi
    if [[ "$EXCHANGED" == 1 && -n "$STAGE" && -d "$STAGE" && -d "$TARGET" ]]; then
      if exchange_directories "$TARGET" "$STAGE" >>"$evidence" 2>&1; then
        printf 'exchange_restore=passed\n' >>"$evidence"
        if mv "$STAGE" "$ROLLBACK/failed-$SITE" >>"$evidence" 2>&1; then
          printf 'failed_candidate_preserved=passed path=%s\n' "$ROLLBACK/failed-$SITE" >>"$evidence"
          STAGE=''
          EXCHANGED=0
        else
          printf 'failed_candidate_preserved=failed\n' >>"$evidence"
          recovery_failed=1
        fi
      else
        printf 'exchange_restore=failed\n' >>"$evidence"
        recovery_failed=1
      fi
    elif [[ -d "$ROLLBACK/$SITE" ]]; then
      if [[ -d "$TARGET" && ! -L "$TARGET" ]]; then
        if mv "$TARGET" "$ROLLBACK/failed-$SITE" >>"$evidence" 2>&1; then
          printf 'failed_candidate_preserved=passed path=%s\n' "$ROLLBACK/failed-$SITE" >>"$evidence"
        else
          printf 'failed_candidate_preserved=failed\n' >>"$evidence"
          recovery_failed=1
        fi
      else
        printf 'failed_candidate_missing_or_unsafe=failed\n' >>"$evidence"
        recovery_failed=1
      fi
      if [[ "$recovery_failed" == 0 ]]; then
        if mv "$ROLLBACK/$SITE" "$TARGET" >>"$evidence" 2>&1; then
          printf 'previous_target_restore=passed\n' >>"$evidence"
        else
          printf 'previous_target_restore=failed\n' >>"$evidence"
          recovery_failed=1
        fi
      fi
    else
      printf 'restore_source=missing\n' >>"$evidence"
      recovery_failed=1
    fi
    if [[ "$recovery_failed" == 0 ]]; then
      if nginx -t >>"$evidence" 2>&1; then
        printf 'nginx_config=passed\n' >>"$evidence"
      else
        printf 'nginx_config=failed\n' >>"$evidence"
        recovery_failed=1
      fi
    fi
    if [[ "$recovery_failed" == 0 ]]; then
      if systemctl reload nginx >>"$evidence" 2>&1; then
        printf 'nginx_reload=passed\n' >>"$evidence"
      else
        printf 'nginx_reload=failed\n' >>"$evidence"
        recovery_failed=1
      fi
    fi
    if [[ "$recovery_failed" == 0 ]]; then
      if verify_previous_site >>"$evidence" 2>&1; then
        printf 'site_version=passed\n' >>"$evidence"
      else
        printf 'site_version=failed\n' >>"$evidence"
        recovery_failed=1
      fi
    fi
    if [[ "$recovery_failed" == 0 ]]; then
      if verify_recovery_health >>"$evidence" 2>&1; then
        printf 'service_health=passed\n' >>"$evidence"
      else
        printf 'service_health=failed\n' >>"$evidence"
        recovery_failed=1
      fi
    fi
    if [[ "$recovery_failed" == 0 ]]; then
      if recovered_hashes=$(protected_hashes 2>>"$evidence") && [[ "$recovered_hashes" == "$BEFORE_FILES" ]]; then
        printf 'protected_files=passed\n' >>"$evidence"
      else
        printf 'protected_files=failed\n' >>"$evidence"
        recovery_failed=1
      fi
    fi
    if [[ "$recovery_failed" == 0 ]]; then
      if printf 'recovery_result=restored\n' >>"$evidence"; then
        echo "$LABEL-recovery=previous-static-restored rollback=$ROLLBACK evidence=$evidence" >&2
      else
        echo "$LABEL-recovery=FAILED evidence-finalize-failed rollback=$ROLLBACK original-status=$status" >&2
        exit 70
      fi
    else
      printf 'recovery_result=failed\n' >>"$evidence" || true
      echo "$LABEL-recovery=FAILED manual-intervention-required rollback=$ROLLBACK evidence=$evidence original-status=$status" >&2
      exit 70
    fi
  fi
  exit "$status"
}
trap recover EXIT
trap 'echo "static-release-check-failed site=$SITE line=$LINENO function=${FUNCNAME[0]:-main}" >&2' ERR

for file in "$ARCHIVE" "$FILE_MANIFEST" "$RELEASE_MANIFEST" "$RELEASE_DIR/SHA256SUMS" "$BACKUP_SCRIPT"; do
  [[ -f "$file" && ! -L "$file" && -s "$file" ]] || fail "missing or unsafe candidate file: $file"
done
[[ -d "$TARGET" && ! -L "$TARGET" ]] || fail "target is missing or unsafe"

python3 - "$RELEASE_DIR/SHA256SUMS" "$SITE" <<'PY'
import pathlib, re, sys
site = sys.argv[2]
seen = set()
pattern = re.compile(r'^[a-f0-9]{64}  (.+)$')
for number, raw in enumerate(open(sys.argv[1], encoding='utf-8'), 1):
    match = pattern.fullmatch(raw.rstrip('\n'))
    assert match, f'invalid outer checksum line {number}'
    name = match.group(1)
    path = pathlib.PurePosixPath(name)
    assert not path.is_absolute() and '..' not in path.parts and name not in seen
    seen.add(name)
required = {
    'RELEASE_MANIFEST.json', 'production-backup.sh', site + '.tar.gz',
    site.upper() + '_SHA256SUMS',
}
assert required <= seen, sorted(required - seen)
PY
(cd "$RELEASE_DIR" && sha256sum -c SHA256SUMS >/dev/null)

mapfile -t IDENTITY < <(python3 - "$RELEASE_MANIFEST" "$EXPECTED_COMMIT" "$EXPECTED_SOURCE_TREE" \
  "$EXPECTED_BUILD_ID" "$EXPECTED_BUILD_METHOD" "$SITE" "$ARCHIVE" "$FILE_MANIFEST" <<'PY'
import hashlib, json, re, sys
manifest_path, commit, source_tree, build_id, build_method, site, archive, checksums = sys.argv[1:]
with open(manifest_path, encoding='utf-8') as stream:
    manifest = json.load(stream)
assert manifest['version'] == '1.0.165'
assert manifest['scope'] == 'mall-closure-candidate'
assert manifest['gitCommit'] == commit
assert re.fullmatch(r'[a-f0-9]{40}', manifest.get('sourceTree', ''))
assert manifest['sourceTree'] == source_tree
assert manifest.get('buildId') == build_id == '20260924-closure-1.0.165'
assert manifest.get('buildMethod') == build_method == 'clean-build-in-release-process'
assert re.fullmatch(r'[a-f0-9]{64}', manifest['jarSha256'])
assert manifest['previousVersion'] == '1.0.158'
assert manifest['previousJarSha256'] == 'cbd249426c1dfcdad4de7dbf65f9780486b47a9807e35ae3eb27fb8252f1ae1f'
assert manifest['previousStaticVersion'] == '1.0.158'
assert manifest['previousStaticCommit'] == 'fcadf4099861a0a5359b12761cbf232c160635a7'
artifacts = manifest.get('artifacts')
assert isinstance(artifacts, dict)
artifact = artifacts.get(site)
assert isinstance(artifact, dict)
assert artifact['archive'] == site + '.tar.gz'
assert artifact['checksums'] == site.upper() + '_SHA256SUMS'
assert artifact['sha256'] == hashlib.sha256(open(archive, 'rb').read()).hexdigest()
assert artifact['checksumsSha256'] == hashlib.sha256(open(checksums, 'rb').read()).hexdigest()
assert artifact['fileCount'] == sum(1 for _ in open(checksums, encoding='utf-8'))
print(manifest['jarSha256'])
PY
)
[[ "${#IDENTITY[@]}" == 1 ]] || fail "invalid release identity"
EXPECTED_BACKEND_JAR_SHA=${IDENTITY[0]}

python3 - "$TARGET/version.json" "$ARCHIVE" "$EXPECTED_COMMIT" "$APPLICATION" "$EXPECTED_BUILD_ID" "$PREVIOUS_BUILD_ID" "$EXPECTED_PREVIOUS_COMMIT" <<'PY'
import json, sys, tarfile
old_path, archive_path, commit, application, build_id, previous_build_id, previous_commit = sys.argv[1:]
with open(old_path, encoding='utf-8') as stream:
    old = json.load(stream)
assert old == {
    'version':'1.0.158', 'edition':'app-h5-split', 'application':application,
    'gitCommit':previous_commit, 'buildId':previous_build_id,
}
with tarfile.open(archive_path, 'r:gz') as archive:
    member = next(item for item in archive.getmembers() if item.name.lstrip('./') == 'version.json')
    with archive.extractfile(member) as stream:
        candidate = json.load(stream)
assert candidate == {
    'version':'1.0.165', 'edition':'app-h5-split', 'application':application,
    'gitCommit':commit, 'buildId':build_id,
}
print('static-old-and-candidate-identity=passed')
PY
verify_candidate_and_tree ''

[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "$EXPECTED_VERSION" ]] || fail "backend version is not 1.0.165"
[[ "$(sha256sum "$APP_ROOT/app/mall-distribution.jar" | awk '{print $1}')" == "$EXPECTED_BACKEND_JAR_SHA" ]] || fail "backend JAR does not match candidate manifest"
for svc in lingqimall-distribution nginx mysqld redis; do
  systemctl is-active --quiet "$svc" || fail "$svc inactive"
done
nginx -t >/dev/null
curl -fsS --max-time 8 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"' || fail "backend unhealthy"
[[ "$(df -Pm "$APP_ROOT" | awk 'NR==2 {print $4}')" -ge 4096 ]] || fail "insufficient free space"

BEFORE_FILES=$(protected_hashes)
echo "$LABEL-preflight=passed previous=$EXPECTED_PREVIOUS_VERSION target=$EXPECTED_VERSION backend=$EXPECTED_VERSION backend-jar=$EXPECTED_BACKEND_JAR_SHA"
[[ "$MODE" == --authorize-release ]] || exit 0

CANDIDATE_ARCHIVE=${LINGQIMALL_CANDIDATE_ARCHIVE:-$RELEASE_DIR/candidate.tar.gz}
RETENTION_RECEIPT=${LINGQIMALL_RETENTION_RECEIPT:-$RELEASE_DIR/artifact-retention.json}
[[ -f "$CANDIDATE_ARCHIVE" ]] || fail "authorized release requires the exact candidate archive"
[[ -f "$RETENTION_RECEIPT" ]] || fail "authorized release requires the durable retention receipt"
command -v node >/dev/null || fail "node is required for durable retention verification"
node "$RETENTION_VERIFIER" \
  --candidate "$CANDIDATE_ARCHIVE" --candidate-root "$RELEASE_DIR" \
  --receipt "$RETENTION_RECEIPT" >/dev/null \
  || fail "P0-10 durable retention verification failed"

BACKUP_BEFORE=$(backup_verify)
ROLLBACK=$(mktemp -d "$APP_ROOT/backups/$LABEL-165.XXXXXX")
STAGE=$(mktemp -d "$APP_ROOT/nginx/.$SITE-165-stage.XXXXXX")
tar --no-same-owner --no-same-permissions -xzf "$ARCHIVE" -C "$STAGE"
verify_candidate_and_tree "$STAGE"
find "$STAGE" -type d -exec chmod 0755 {} +
find "$STAGE" -type f -exec chmod 0644 {} +
chown -R "$(stat -c '%u:%g' "$TARGET")" "$STAGE"
[[ "$(protected_hashes)" == "$BEFORE_FILES" ]] || fail "protected files changed before switch"

exchange_directories "$TARGET" "$STAGE"
MUTATED=1
EXCHANGED=1
mv "$STAGE" "$ROLLBACK/$SITE"
STAGE=''
EXCHANGED=0
verify_candidate_and_tree "$TARGET"
nginx -t >/dev/null
systemctl reload nginx

python3 - "$EXPECTED_COMMIT" "$BASE" "$APPLICATION" "$EXPECTED_BUILD_ID" "$TARGET" <<'PY'
import hashlib
import json
import pathlib
import re
import sys
import urllib.request

commit, base, application, build_id, target = sys.argv[1:]
query = '?release=1.0.165-' + commit[:12]
with urllib.request.urlopen(base + '/version.json' + query, timeout=12) as response:
    version = json.load(response)
assert version == {
    'version':'1.0.165', 'edition':'app-h5-split', 'application':application,
    'gitCommit':commit, 'buildId':build_id,
}
with urllib.request.urlopen(base + '/' + query, timeout=12) as response:
    html_bytes = response.read()
html = html_bytes.decode('utf-8')
assert hashlib.sha256(html_bytes).hexdigest() == hashlib.sha256((pathlib.Path(target) / 'index.html').read_bytes()).hexdigest()
assert '<div id="app">' in html
prefix = '/admin/assets/' if application == 'admin' else '/assets/'
entries = sorted(set(re.findall(r'(?:src|href)=["\'](' + re.escape(prefix) + r'[^"\' ?]+)', html)))
assert entries and any(entry.endswith('.js') for entry in entries)
origin = 'https://lingqimall.com' if application == 'admin' else base
for entry in entries:
    relative = entry.removeprefix('/admin/' if application == 'admin' else '/')
    local = pathlib.Path(target) / relative
    assert local.is_file()
    with urllib.request.urlopen(origin + entry + query, timeout=12) as response:
        remote = response.read()
    assert hashlib.sha256(remote).hexdigest() == hashlib.sha256(local.read_bytes()).hexdigest(), entry
print('public-version-html-entry-assets=passed entries=' + str(len(entries)))
PY

verify_candidate_and_tree "$TARGET"
[[ "$(protected_hashes)" == "$BEFORE_FILES" ]] || fail "backend, configuration, or another site changed"
for svc in lingqimall-distribution nginx mysqld redis; do
  systemctl is-active --quiet "$svc" || fail "$svc inactive after switch"
done
curl -fsS --max-time 8 http://127.0.0.1:8086/actuator/health | grep -q '"status":"UP"' || fail "backend unhealthy after switch"
[[ "$(tr -d '[:space:]' < "$APP_ROOT/VERSION")" == "$EXPECTED_VERSION" ]] || fail "backend version changed"
[[ "$(sha256sum "$APP_ROOT/app/mall-distribution.jar" | awk '{print $1}')" == "$EXPECTED_BACKEND_JAR_SHA" ]] || fail "backend JAR changed"

BACKUP_AFTER=$(backup_verify)
[[ "$BACKUP_AFTER" != "$BACKUP_BEFORE" ]] || fail "post-release backup missing"
[[ "$(protected_hashes)" == "$BEFORE_FILES" ]] || fail "protected files changed during final backup"
MUTATED=0
trap - EXIT
echo "$LABEL-release-success version=$EXPECTED_VERSION source=$EXPECTED_COMMIT build=$EXPECTED_BUILD_ID before=$BACKUP_BEFORE after=$BACKUP_AFTER rollback=$ROLLBACK exact-tree=yes backend-other-sites-config-preserved=yes"
