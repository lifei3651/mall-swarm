#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DEPLOY_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
ROOT_DIR=$(CDPATH= cd -- "$DEPLOY_DIR/../.." && pwd)
HTML_DIR="$DEPLOY_DIR/html"
STAGING=$(mktemp -d "${TMPDIR:-/tmp}/mall-private-build.XXXXXX")
CANDIDATE_DIR="$ROOT_DIR/target/release-candidates"
CANDIDATE_STAGING="$STAGING/release-candidates"
restore_candidates() {
  if [ -d "$CANDIDATE_STAGING" ]; then
    mkdir -p "$ROOT_DIR/target"
    [ ! -e "$CANDIDATE_DIR" ] || { echo "候选目录恢复目标已存在，停止覆盖" >&2; exit 1; }
    mv "$CANDIDATE_STAGING" "$CANDIDATE_DIR"
  fi
}
cleanup() {
  restore_candidates
  rm -rf "$STAGING"
}
trap cleanup EXIT HUP INT TERM

[ -x "$ROOT_DIR/mvnw" ] || { echo "缺少 Maven Wrapper" >&2; exit 1; }
command -v npm >/dev/null 2>&1 || { echo "缺少 Node.js/npm" >&2; exit 1; }
version=$(tr -d '\n' < "$ROOT_DIR/VERSION")
release_git_commit=${RELEASE_GIT_COMMIT:-$(git -C "$ROOT_DIR" rev-parse HEAD)}
release_build_id=${RELEASE_BUILD_ID:-$(date +%Y%m%d-%H%M)-${version}}
if [ -n "$(git -C "$ROOT_DIR" status --porcelain)" ] && [ -z "${RELEASE_BUILD_ID:-}" ]; then
  release_build_id="${release_build_id}-dirty"
fi
export RELEASE_GIT_COMMIT="$release_git_commit"
export RELEASE_BUILD_ID="$release_build_id"

# Maven 根模块 clean 会删除 target；正式候选属于冻结交付物，构建期间先移出再原样恢复。
if [ -d "$CANDIDATE_DIR" ]; then
  mv "$CANDIDATE_DIR" "$CANDIDATE_STAGING"
fi

(cd "$ROOT_DIR" && ./mvnw clean package -Ddocker.skip=true)
restore_candidates
(cd "$ROOT_DIR/mall-shop-web" && npm ci && npm test -- --run && npm run build)
(cd "$ROOT_DIR/mall-distribution-admin" && npm ci && npm test -- --run && npm run build)

mkdir -p "$STAGING/html/public" "$STAGING/html/team" "$STAGING/html/integrated" "$STAGING/html/admin"
cp -R "$ROOT_DIR/mall-shop-web/dist/." "$STAGING/html/public/"
cp -R "$ROOT_DIR/mall-shop-web/dist-team/." "$STAGING/html/team/"
cp -R "$ROOT_DIR/mall-shop-web/dist-integrated/." "$STAGING/html/integrated/"
cp -R "$ROOT_DIR/mall-distribution-admin/dist/." "$STAGING/html/admin/"

if find "$STAGING/html" -type f -name '*.map' -print -quit | grep -q .; then
  echo "生产构建包含 source map，已停止交付" >&2
  exit 1
fi
grep -q "\"version\"[[:space:]]*:[[:space:]]*\"$version\"" "$STAGING/html/public/version.json" \
  || { echo "商城构建版本与根 VERSION 不一致" >&2; exit 1; }
grep -q "\"version\"[[:space:]]*:[[:space:]]*\"$version\"" "$STAGING/html/team/version.json" \
  || { echo "团队H5构建版本与根 VERSION 不一致" >&2; exit 1; }
grep -q "\"version\"[[:space:]]*:[[:space:]]*\"$version\"" "$STAGING/html/integrated/version.json" \
  || { echo "一体化H5构建版本与根 VERSION 不一致" >&2; exit 1; }
grep -q "\"version\"[[:space:]]*:[[:space:]]*\"$version\"" "$STAGING/html/admin/version.json" \
  || { echo "后台构建版本与根 VERSION 不一致" >&2; exit 1; }
for manifest in "$STAGING"/html/*/version.json; do
  grep -q "\"gitCommit\"[[:space:]]*:[[:space:]]*\"$release_git_commit\"" "$manifest" \
    || { echo "构建清单缺少 Git 身份：$manifest" >&2; exit 1; }
  grep -q "\"buildId\"[[:space:]]*:[[:space:]]*\"$release_build_id\"" "$manifest" \
    || { echo "构建清单缺少构建批次：$manifest" >&2; exit 1; }
done

# html 是被 .gitignore 排除的构建产物；只替换该明确目录，不触碰客户配置、证书或数据卷。
rm -rf "$HTML_DIR"
mv "$STAGING/html" "$HTML_DIR"
echo "公开商城、团队H5、一体化H5、后台和后端生产构建完成，版本：${version}，批次：${release_build_id}"
