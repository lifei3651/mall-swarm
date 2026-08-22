#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DEPLOY_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
ROOT_DIR=$(CDPATH= cd -- "$DEPLOY_DIR/../.." && pwd)
HTML_DIR="$DEPLOY_DIR/html"
STAGING=$(mktemp -d "${TMPDIR:-/tmp}/mall-private-build.XXXXXX")
trap 'rm -rf "$STAGING"' EXIT HUP INT TERM

[ -x "$ROOT_DIR/mvnw" ] || { echo "缺少 Maven Wrapper" >&2; exit 1; }
command -v npm >/dev/null 2>&1 || { echo "缺少 Node.js/npm" >&2; exit 1; }

(cd "$ROOT_DIR" && ./mvnw clean package -Ddocker.skip=true)
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
version=$(tr -d '\n' < "$ROOT_DIR/VERSION")
grep -q "\"version\"[[:space:]]*:[[:space:]]*\"$version\"" "$STAGING/html/public/version.json" \
  || { echo "商城构建版本与根 VERSION 不一致" >&2; exit 1; }
grep -q "\"version\"[[:space:]]*:[[:space:]]*\"$version\"" "$STAGING/html/team/version.json" \
  || { echo "团队H5构建版本与根 VERSION 不一致" >&2; exit 1; }
grep -q "\"version\"[[:space:]]*:[[:space:]]*\"$version\"" "$STAGING/html/integrated/version.json" \
  || { echo "一体化H5构建版本与根 VERSION 不一致" >&2; exit 1; }
grep -q "\"version\"[[:space:]]*:[[:space:]]*\"$version\"" "$STAGING/html/admin/version.json" \
  || { echo "后台构建版本与根 VERSION 不一致" >&2; exit 1; }

# html 是被 .gitignore 排除的构建产物；只替换该明确目录，不触碰客户配置、证书或数据卷。
rm -rf "$HTML_DIR"
mv "$STAGING/html" "$HTML_DIR"
echo "公开商城、团队H5、一体化H5、后台和后端生产构建完成，版本：$version"
