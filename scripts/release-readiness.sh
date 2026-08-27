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
    VERSION RELEASE_MANIFEST.json SHA256SUMS production-backup.sh db-migrate.sh lingqimall.conf \
    lingqimall-security.conf release.sh; do
    [[ -s "$CANDIDATE_ROOT/$required" ]] || fail "候选缺少 $required"
  done
  [[ -x "$CANDIDATE_ROOT/production-backup.sh" ]] || fail "候选备份脚本不可执行"
  [[ -x "$CANDIDATE_ROOT/db-migrate.sh" ]] || fail "候选迁移脚本不可执行"
  [[ -x "$CANDIDATE_ROOT/release.sh" ]] || fail "候选发布脚本不可执行"
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
  done
  if command -v sha256sum >/dev/null 2>&1; then
    (cd "$CANDIDATE_ROOT" && sha256sum -c SHA256SUMS >/dev/null) || fail "候选 SHA256SUMS 不一致"
  else
    (cd "$CANDIDATE_ROOT" && shasum -a 256 -c SHA256SUMS >/dev/null) || fail "候选 SHA256SUMS 不一致"
  fi
  [[ "$(grep -Fc 'SET SESSION group_concat_max_len=16777216;' "$CANDIDATE_ROOT/release.sh")" -ge 2 ]] \
    || fail "候选缺少完整装修哈希防截断门禁"
  grep -Fq -- '--preflight-only' "$CANDIDATE_ROOT/release.sh" \
    || fail "候选缺少只读预检模式"
  [[ "$(sha256_file "$ROOT_DIR/scripts/nginx/lingqimall.conf")" == "$(sha256_file "$CANDIDATE_ROOT/lingqimall.conf")" ]] \
    || fail "候选 Nginx 配置与仓库模板不一致"
  if find "$CANDIDATE_ROOT" -type f \( -name '*.map' -o -name '.env' -o -name '*.pem' -o -name '*.key' \) -print -quit | grep -q .; then
    fail "候选含 source map 或敏感配置文件"
  fi
  pass "外层候选、四个内包、哈希、数据库哈希门禁与 Nginx 模板"
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
