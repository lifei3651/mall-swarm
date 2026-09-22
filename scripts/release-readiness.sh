#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT_DIR=$(git -C "$SCRIPT_DIR/.." rev-parse --show-toplevel)
# shellcheck source=production-targets.sh
source "$SCRIPT_DIR/production-targets.sh"

REMOTE_HOST=$LINGQIMALL_PRODUCTION_SSH_HOST
EXPECTED_HOSTNAME=$LINGQIMALL_PRODUCTION_HOSTNAME
IDENTITY_FILE="${LINGQIMALL_SSH_IDENTITY:-$HOME/.ssh/lingqi_server_ed25519}"
CANDIDATE=""
LOCAL_ONLY=0
ALLOW_DIRTY=0

usage() {
  cat <<'EOF'
用法：scripts/release-readiness.sh [选项]
  --candidate <目录或tar.gz>  递归检查正式候选包
  --identity <私钥路径>       指定生产只读检查使用的 SSH 私钥
  --local-only                只执行本地门禁
  --allow-dirty               仅供开发阶段验证脚本；正式发版禁止使用
EOF
}

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --candidate) CANDIDATE=${2:-}; shift 2 ;;
    --identity) IDENTITY_FILE=${2:-}; shift 2 ;;
    --local-only) LOCAL_ONLY=1; shift ;;
    --allow-dirty) ALLOW_DIRTY=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数：$1" >&2; usage >&2; exit 2 ;;
  esac
done

fail() { echo "release-readiness-failed: $*" >&2; exit 1; }
pass() { echo "release-readiness-ok: $*"; }
sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

[[ "$(basename "$ROOT_DIR")" == "mall-swarm-app-h5" ]] || fail "不是唯一产品仓库"
[[ -f "$ROOT_DIR/VERSION" ]] || fail "缺少 VERSION"
if [[ "$ALLOW_DIRTY" != 1 ]]; then
  [[ -z "$(git -C "$ROOT_DIR" status --porcelain)" ]] || fail "工作区不干净"
  if git -C "$ROOT_DIR" rev-parse --abbrev-ref '@{upstream}' >/dev/null 2>&1; then
    [[ "$(git -C "$ROOT_DIR" rev-list --left-right --count 'HEAD...@{upstream}')" == $'0\t0' ]] \
      || fail "本地与远程未同步"
  fi
fi

for non_mall_host in "${LINGQIMALL_FORBIDDEN_DEPLOY_HOSTS[@]}"; do
  while IFS= read -r file; do
    [[ "$file" == "$SCRIPT_DIR/production-targets.sh" ]] && continue
    fail "商城发布脚本仍引用非商城主机：$file"
  done < <(grep -RIl --include='*.sh' -- "$non_mall_host" "$SCRIPT_DIR" || true)
done
pass "唯一仓库、远程同步与非商城主机隔离门禁"

TMP_ROOT=$(mktemp -d "${TMPDIR:-/tmp}/lingqimall-release-readiness.XXXXXX")
cleanup() { rm -rf "$TMP_ROOT"; }
trap cleanup EXIT HUP INT TERM

audit_archive() {
  local archive=$1
  local label=$2
  local listing="$TMP_ROOT/${label}.listing"
  local verbose_listing="$TMP_ROOT/${label}.verbose-listing"
  local errors="$TMP_ROOT/${label}.errors"
  tar -tzf "$archive" >"$listing" 2>"$errors" || fail "$label 无法读取"
  if ! python3 - "$archive" <<'PY'
import sys
import tarfile

with tarfile.open(sys.argv[1], "r:gz") as archive:
    global_headers = getattr(archive, "pax_headers", {})
    if any("xattr" in key.lower() or "com.apple" in key.lower() for key in global_headers):
        raise SystemExit(1)
    for member in archive.getmembers():
        headers = getattr(member, "pax_headers", {})
        if any("xattr" in key.lower() or "com.apple" in key.lower() for key in headers):
            raise SystemExit(1)
PY
  then
    fail "$label 包含扩展属性"
  fi
  if grep -Eq '(^|/)\._|(^|/)__MACOSX(/|$)|(^|/)\.\.(/|$)|^/' "$listing"; then
    fail "$label 包含危险路径或 macOS 隐藏文件"
  fi
  tar -tvzf "$archive" >"$verbose_listing" 2>/dev/null || fail "$label 无法读取详细清单"
  if awk 'substr($0,1,1) == "l" || substr($0,1,1) == "h" { found=1 } END { exit !found }' "$verbose_listing"; then
    fail "$label 包含不允许的链接条目"
  fi
}

if [[ -n "$CANDIDATE" ]]; then
  [[ -e "$CANDIDATE" ]] || fail "候选不存在：$CANDIDATE"
  CANDIDATE_ROOT="$CANDIDATE"
  if [[ -f "$CANDIDATE" ]]; then
    audit_archive "$CANDIDATE" outer
    CANDIDATE_ROOT="$TMP_ROOT/candidate"
    mkdir -p "$CANDIDATE_ROOT"
    tar -xzf "$CANDIDATE" -C "$CANDIDATE_ROOT" 2>"$TMP_ROOT/outer-extract.errors"
    [[ ! -s "$TMP_ROOT/outer-extract.errors" ]] || fail "外层候选解包产生警告"
    if [[ ! -f "$CANDIDATE_ROOT/VERSION" ]]; then
      SINGLE_TOP_LEVEL=$(find "$CANDIDATE_ROOT" -mindepth 1 -maxdepth 1 -type d -print)
      if [[ -n "$SINGLE_TOP_LEVEL" && "$(wc -l <<<"$SINGLE_TOP_LEVEL" | tr -d ' ')" == 1 \
        && -f "$SINGLE_TOP_LEVEL/VERSION" ]]; then
        CANDIDATE_ROOT=$SINGLE_TOP_LEVEL
      fi
    fi
  fi

  for required in mall-distribution.jar admin.tar.gz shop.tar.gz team.tar.gz integrated.tar.gz \
    ADMIN_SHA256SUMS SHOP_SHA256SUMS TEAM_SHA256SUMS INTEGRATED_SHA256SUMS \
    mini-program-source.tar.gz VERSION RELEASE_MANIFEST.json MINI_PROGRAM_MANIFEST.json SHA256SUMS \
    production-backup.sh db-migrate.sh lingqimall.conf lingqimall-security.conf \
    release-backend.sh release-static.sh; do
    [[ -s "$CANDIDATE_ROOT/$required" ]] || fail "候选缺少 $required"
  done
  [[ -d "$CANDIDATE_ROOT/document/db/migrations" ]] || fail "候选缺少数据库迁移集合"
  [[ -x "$CANDIDATE_ROOT/production-backup.sh" ]] || fail "候选备份脚本不可执行"
  [[ -x "$CANDIDATE_ROOT/db-migrate.sh" ]] || fail "候选迁移脚本不可执行"
  [[ -x "$CANDIDATE_ROOT/release-backend.sh" ]] || fail "候选后端发布脚本不可执行"
  [[ -x "$CANDIDATE_ROOT/release-static.sh" ]] || fail "候选静态发布脚本不可执行"
  grep -Fq 'etc/lingqimall' "$CANDIDATE_ROOT/production-backup.sh" \
    || fail "候选备份脚本未覆盖 /etc/lingqimall 客户短信等外部服务配置"
  for inner in admin shop team integrated; do
    audit_archive "$CANDIDATE_ROOT/$inner.tar.gz" "$inner"
    INNER_ROOT="$TMP_ROOT/inner-$inner"
    mkdir -p "$INNER_ROOT"
    tar -xzf "$CANDIDATE_ROOT/$inner.tar.gz" -C "$INNER_ROOT" 2>"$TMP_ROOT/$inner-extract.errors"
    [[ ! -s "$TMP_ROOT/$inner-extract.errors" ]] || fail "$inner 内包解包产生警告"
    if find "$INNER_ROOT" -type f \( -name '*.map' -o -name '.env' -o -name '*.pem' -o -name '*.key' \) -print -quit | grep -q .; then
      fail "$inner 内包含 source map 或敏感配置文件"
    fi
    INNER_SUMS="$CANDIDATE_ROOT/$(printf '%s' "$inner" | tr '[:lower:]' '[:upper:]')_SHA256SUMS"
    python3 - "$INNER_ROOT" "$INNER_SUMS" <<'PY' || fail "$inner 内包逐文件哈希不一致"
import hashlib
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1])
sums_file = pathlib.Path(sys.argv[2])
sums = {}
for number, line in enumerate(sums_file.read_text().splitlines(), 1):
    if not line:
        continue
    match = re.fullmatch(r"([0-9a-f]{64})  \./([^\0]+)", line)
    if not match or match.group(2).startswith("/") or ".." in pathlib.PurePosixPath(match.group(2)).parts:
        raise SystemExit(f"invalid checksum line {number}")
    if match.group(2) in sums:
        raise SystemExit(f"duplicate checksum path {match.group(2)}")
    sums[match.group(2)] = match.group(1)
actual = {}
for file in root.rglob("*"):
    if file.is_file():
        actual[file.relative_to(root).as_posix()] = hashlib.sha256(file.read_bytes()).hexdigest()
if sums != actual:
    raise SystemExit("checksum inventory differs from extracted archive")
PY
  done

  audit_archive "$CANDIDATE_ROOT/mini-program-source.tar.gz" mini-program-source
  python3 - "$CANDIDATE_ROOT" "$ROOT_DIR" <<'PY' || fail "统一候选清单、小程序源码或逐文件哈希门禁失败"
import hashlib
import io
import json
import pathlib
import re
import subprocess
import sys
import tarfile

candidate = pathlib.Path(sys.argv[1]).resolve()
root = pathlib.Path(sys.argv[2]).resolve()
expected_appid = "wxd26e0a4e41df392b"
expected_api = "https://lingqimall.com/api"
expected_plugins = {"logisticsPlugin": {"provider": "wx9ad912bf20548d92", "version": "2.1.12"}}
expected_version = "1.0.155"
expected_scope = "mall-closure-candidate"
expected_build_id = "20260922-closure-1.0.155"
expected_build_method = "clean-build-in-release-process"
expected_previous_backend_version = "1.0.154"
expected_previous_backend_jar = "786aec477acb1deaf71c058bfbd55e9d57a9953c31bafa859ae2c9b43695dd96"
expected_previous_static_version = "1.0.154"
expected_previous_static_commit = "a8f86f2ed3123c21082e7404d372812e11dae790"
expected_migration_count = 41
expected_last_migration = "V202609211530__order_item_service_tag_snapshot.sql"
expected_last_migration_sha = "bb5f0dbf8942db2f2c56c28bd1c6c16fa185b1087690a750affd8ac523962526"

def fail(message):
    raise SystemExit(message)

def sha_bytes(value):
    return hashlib.sha256(value).hexdigest()

def sha_file(file):
    return sha_bytes(file.read_bytes())

def load_json(file):
    try:
        return json.loads(file.read_text())
    except Exception as error:
        fail(f"invalid JSON {file.name}: {error}")

def normalized(name):
    name = name.replace("\\", "/")
    while name.startswith("./"):
        name = name[2:]
    parts = pathlib.PurePosixPath(name).parts
    if not name or name.startswith("/") or ".." in parts:
        fail(f"unsafe path: {name}")
    return name

def aggregate(files):
    ledger = "".join(f"{files[name]}  {name}\n" for name in sorted(files))
    return sha_bytes(ledger.encode())

release = load_json(candidate / "RELEASE_MANIFEST.json")
mini = load_json(candidate / "MINI_PROGRAM_MANIFEST.json")
version = (candidate / "VERSION").read_text().strip()
commit = release.get("gitCommit")
if not re.fullmatch(r"[0-9a-f]{40}", commit or ""):
    fail("release manifest does not bind an immutable commit")
if version != expected_version or release.get("version") != version:
    fail("VERSION and release manifest disagree")
if release.get("scope") != expected_scope or release.get("buildId") != expected_build_id \
        or release.get("buildMethod") != expected_build_method:
    fail("release candidate scope, buildId, or buildMethod is incorrect")
source_tree = release.get("sourceTree")
if not re.fullmatch(r"[0-9a-f]{40}", source_tree or ""):
    fail("release manifest does not bind a source tree")
if release.get("previousVersion") != expected_previous_backend_version \
        or release.get("previousJarSha256") != expected_previous_backend_jar \
        or release.get("previousStaticVersion") != expected_previous_static_version \
        or release.get("previousStaticCommit") != expected_previous_static_commit:
    fail("release candidate production baseline is incorrect")
artifacts = release.get("artifacts") or {}
for label in ("admin", "shop", "team", "integrated"):
    artifact = artifacts.get(label) or {}
    archive_name = f"{label}.tar.gz"
    checksums_name = f"{label.upper()}_SHA256SUMS"
    if artifact.get("archive") != archive_name or artifact.get("checksums") != checksums_name:
        fail(f"static artifact binding mismatch: {label}")
    if artifact.get("sha256") != sha_file(candidate / archive_name):
        fail(f"static archive checksum mismatch: {label}")
    if artifact.get("checksumsSha256") != sha_file(candidate / checksums_name):
        fail(f"static checksum manifest mismatch: {label}")
    checksum_count = sum(1 for line in (candidate / checksums_name).read_text().splitlines() if line)
    if artifact.get("fileCount") != checksum_count or checksum_count <= 0:
        fail(f"static artifact fileCount invalid: {label}")
if mini.get("schemaVersion") != 1 or mini.get("version") != version or mini.get("gitCommit") != commit:
    fail("mini-program and release manifest identity disagree")
if mini.get("sourceArchive") != "mini-program-source.tar.gz" or mini.get("sourceRoot") != "mall-mini-program":
    fail("unexpected mini-program source binding")
if mini.get("appid") != expected_appid or mini.get("api") != expected_api or mini.get("urlCheck") is not True:
    fail("mini-program production identity is incorrect")
if mini.get("plugins") != expected_plugins:
    fail("mini-program plugin inventory is incorrect")

source_archive = candidate / "mini-program-source.tar.gz"
source_sha = sha_file(source_archive)
if mini.get("sourceArchiveSha256") != source_sha:
    fail("mini-program source archive checksum mismatch")
mini_bytes = (candidate / "MINI_PROGRAM_MANIFEST.json").read_bytes()
release_mini = release.get("miniProgram") or {}
expected_release_mini = {
    "sourceArchive": "mini-program-source.tar.gz",
    "sourceArchiveSha256": source_sha,
    "manifest": "MINI_PROGRAM_MANIFEST.json",
    "manifestSha256": sha_bytes(mini_bytes),
    "fileCount": mini.get("fileCount"),
}
for key, expected in expected_release_mini.items():
    if release_mini.get(key) != expected:
        fail(f"release miniProgram binding mismatch: {key}")

manifest_files = mini.get("files")
if not isinstance(manifest_files, dict) or mini.get("fileCount") != len(manifest_files):
    fail("mini-program file manifest count mismatch")
for name, checksum in manifest_files.items():
    if normalized(name) != name or not name.startswith("mall-mini-program/"):
        fail(f"mini-program manifest path is invalid: {name}")
    if not re.fullmatch(r"[0-9a-f]{64}", checksum or ""):
        fail(f"mini-program manifest checksum is invalid: {name}")
if mini.get("aggregateSha256") != aggregate(manifest_files):
    fail("mini-program manifest aggregate checksum mismatch")

def tar_files_from_handle(handle):
    files = {}
    for member in handle.getmembers():
        name = normalized(member.name)
        if member.isdir():
            continue
        if not member.isfile():
            fail(f"mini-program archive contains a link or special file: {name}")
        if not name.startswith("mall-mini-program/"):
            fail(f"mini-program archive contains an extra root: {name}")
        if name in files:
            fail(f"mini-program archive contains duplicate path: {name}")
        stream = handle.extractfile(member)
        if stream is None:
            fail(f"unable to read mini-program archive entry: {name}")
        files[name] = sha_bytes(stream.read())
    return files

with tarfile.open(source_archive, "r:gz") as handle:
    source_files = tar_files_from_handle(handle)
if source_files != manifest_files:
    fail("mini-program source archive and file manifest differ")

try:
    resolved = subprocess.check_output(["git", "-C", str(root), "rev-parse", f"{commit}^{{commit}}"], text=True).strip()
    git_version = subprocess.check_output(["git", "-C", str(root), "show", f"{commit}:VERSION"], text=True).strip()
    git_archive = subprocess.check_output(["git", "-C", str(root), "archive", "--format=tar", commit, "mall-mini-program"])
except subprocess.CalledProcessError as error:
    fail(f"candidate commit is unavailable: {error}")
if resolved != commit or git_version != version:
    fail("candidate commit or root VERSION mismatch")
resolved_tree = subprocess.check_output(["git", "-C", str(root), "rev-parse", f"{commit}^{{tree}}"], text=True).strip()
if resolved_tree != source_tree:
    fail("candidate sourceTree does not match gitCommit")
with tarfile.open(fileobj=io.BytesIO(git_archive), mode="r:") as handle:
    git_files = tar_files_from_handle(handle)
if source_files != git_files:
    fail("mini-program source archive does not equal the immutable git tree")

def source_json(name):
    data = subprocess.check_output(["git", "-C", str(root), "show", f"{commit}:mall-mini-program/{name}"])
    return json.loads(data)

package = source_json("package.json")
lock = source_json("package-lock.json")
project = source_json("project.config.json")
app = source_json("app.json")
runtime = subprocess.check_output(["git", "-C", str(root), "show", f"{commit}:mall-mini-program/config/runtime.js"], text=True)
if package.get("version") != version or lock.get("version") != version or (lock.get("packages") or {}).get("", {}).get("version") != version:
    fail("root, mini package, and lock versions disagree")
if (project.get("setting") or {}).get("urlCheck") is not True:
    fail("mini-program source disables legal-domain validation")
if f"API_BASE_URL: '{expected_api}'" not in runtime:
    fail("mini-program source points at the wrong API")
if app.get("plugins") != expected_plugins:
    fail("mini-program source plugin inventory is incorrect")

sums_file = candidate / "SHA256SUMS"
sums = {}
for line_number, line in enumerate(sums_file.read_text().splitlines(), 1):
    if not line:
        continue
    match = re.fullmatch(r"([0-9a-f]{64})  ([^\0]+)", line)
    if not match:
        fail(f"invalid SHA256SUMS line {line_number}")
    name = normalized(match.group(2))
    if name in sums:
        fail(f"duplicate SHA256SUMS entry: {name}")
    sums[name] = match.group(1)
actual = {}
for file in candidate.rglob("*"):
    if not file.is_file():
        continue
    relative = file.relative_to(candidate).as_posix()
    if relative != "SHA256SUMS":
        actual[relative] = sha_file(file)
if sums != actual:
    fail("SHA256SUMS does not cover every candidate file exactly once")

repo_migrations = sorted(file.name for file in (root / "document/db/migrations").glob("V*.sql"))
candidate_migrations = sorted(file.name for file in (candidate / "document/db/migrations").glob("V*.sql"))
if len(repo_migrations) != expected_migration_count or repo_migrations[-1] != expected_last_migration:
    fail("repository migration inventory is not the fixed 1.0.155 set")
if repo_migrations != candidate_migrations:
    fail("candidate migration inventory differs from the repository")
for name in repo_migrations:
    if sha_file(root / "document/db/migrations" / name) != sha_file(candidate / "document/db/migrations" / name):
        fail(f"candidate migration differs from repository: {name}")
if sha_file(candidate / "document/db/migrations" / expected_last_migration) != expected_last_migration_sha:
    fail("1.0.155 service-tag migration checksum mismatch")

fixed_files = {
    "mall-distribution.jar", "admin.tar.gz", "shop.tar.gz", "team.tar.gz", "integrated.tar.gz",
    "ADMIN_SHA256SUMS", "SHOP_SHA256SUMS", "TEAM_SHA256SUMS", "INTEGRATED_SHA256SUMS",
    "mini-program-source.tar.gz", "VERSION", "RELEASE_MANIFEST.json", "MINI_PROGRAM_MANIFEST.json",
    "SHA256SUMS", "production-backup.sh", "db-migrate.sh", "lingqimall.conf",
    "lingqimall-security.conf", "release-backend.sh", "release-static.sh",
}
expected_inventory = fixed_files | {f"document/db/migrations/{name}" for name in repo_migrations}
candidate_inventory = {file.relative_to(candidate).as_posix() for file in candidate.rglob("*") if file.is_file()}
if candidate_inventory != expected_inventory:
    missing = sorted(expected_inventory - candidate_inventory)
    extra = sorted(candidate_inventory - expected_inventory)
    fail(f"candidate inventory mismatch; missing={missing}, extra={extra}")
PY

  for marker in 'business_snapshot' 'protected_hashes' 'verify_unauthorized_contract' \
    'NEW_MIGRATION=V202609211530__order_item_service_tag_snapshot.sql' \
    'additive-service-tag-migration-retained=yes'; do
    grep -Fq -- "$marker" "$CANDIDATE_ROOT/release-backend.sh" \
      || fail "候选后端发布脚本缺少收口保护：$marker"
  done
  CANDIDATE_VERSION=$(tr -d '[:space:]' < "$CANDIDATE_ROOT/VERSION")
  grep -Fxq "EXPECTED_VERSION=$CANDIDATE_VERSION" "$CANDIDATE_ROOT/release-backend.sh" \
    || fail "候选后端发布脚本版本与统一候选不一致"
  grep -Fxq "EXPECTED_VERSION=$CANDIDATE_VERSION" "$CANDIDATE_ROOT/release-static.sh" \
    || fail "候选静态发布脚本版本与统一候选不一致"
  bash -n "$CANDIDATE_ROOT/release-backend.sh" || fail "候选后端发布脚本语法错误"
  bash -n "$CANDIDATE_ROOT/release-static.sh" || fail "候选静态发布脚本语法错误"
  grep -Fq -- '--preflight-only' "$CANDIDATE_ROOT/release-backend.sh" \
    || fail "候选后端发布脚本缺少只读预检模式"
  grep -Fq -- '--preflight-only' "$CANDIDATE_ROOT/release-static.sh" \
    || fail "候选静态发布脚本缺少只读预检模式"
  [[ "$(sha256_file "$ROOT_DIR/scripts/nginx/lingqimall.conf")" == "$(sha256_file "$CANDIDATE_ROOT/lingqimall.conf")" ]] \
    || fail "候选 Nginx 配置与仓库模板不一致"
  [[ "$(sha256_file "$ROOT_DIR/scripts/nginx/lingqimall-security.conf")" == "$(sha256_file "$CANDIDATE_ROOT/lingqimall-security.conf")" ]] \
    || fail "候选 Nginx 安全配置与仓库模板不一致"
  if find "$CANDIDATE_ROOT" -type f \( -name '*.map' -o -name '.env' -o -name '*.pem' -o -name '*.key' \) -print -quit | grep -q .; then
    fail "候选含 source map 或敏感配置文件"
  fi
  pass "统一候选、四个静态内包、小程序不可变源码、逐文件哈希、迁移集合与 Nginx 模板"
fi

if [[ "$LOCAL_ONLY" == 1 ]]; then
  pass "本地预检完成"
  exit 0
fi

[[ "$REMOTE_HOST" == "$LINGQIMALL_PRODUCTION_SSH_HOST" ]] || fail "生产 SSH 目标被改写"
[[ -f "$IDENTITY_FILE" ]] || fail "缺少生产 SSH 私钥"
SSH=(ssh -i "$IDENTITY_FILE" -o BatchMode=yes -o ConnectTimeout=10 -o StrictHostKeyChecking=yes -o LogLevel=ERROR "root@$REMOTE_HOST")
REMOTE_SNAPSHOT=$("${SSH[@]}" 'hostname; systemctl is-active nginx; systemctl is-active mysqld; systemctl is-active redis; systemctl is-active lingqimall-distribution; curl -fsS --max-time 5 http://127.0.0.1:8086/actuator/health') \
  || fail "无法读取正式主机状态"
[[ "$(sed -n '1p' <<<"$REMOTE_SNAPSHOT")" == "$EXPECTED_HOSTNAME" ]] || fail "正式主机身份不符"
[[ "$(grep -cx 'active' <<<"$REMOTE_SNAPSHOT")" == 4 ]] || fail "正式主机四项服务未全部 active"
grep -Fq '"status":"UP"' <<<"$REMOTE_SNAPSHOT" || fail "正式后端健康检查未通过"

REMOTE_NGINX_HASH=$("${SSH[@]}" "sha256sum '$LINGQIMALL_PRODUCTION_NGINX_CONFIG'" | awk '{print $1}') \
  || fail "无法读取生产 Nginx 配置哈希"
LOCAL_NGINX_HASH=$(sha256_file "$ROOT_DIR/scripts/nginx/lingqimall.conf")
[[ "$REMOTE_NGINX_HASH" == "$LOCAL_NGINX_HASH" ]] || fail "仓库 Nginx 模板与生产配置漂移"

PUBLIC_VERSION=$(curl --http1.1 -fsS --max-time 12 -H 'Cache-Control: no-cache' "https://lingqimall.com/version.json?readiness=$(date +%s)") \
  || fail "公网版本清单不可用"
grep -Fq '"edition": "app-h5-split"' <<<"$PUBLIC_VERSION" || fail "公网不是拆分版"
pass "正式主机身份、四项服务、健康、公网清单与 Nginx 模板一致"
