#!/usr/bin/env bash
set -euo pipefail

functional_root="$(git rev-parse --show-toplevel)"
if [[ "$(basename "$functional_root")" != "mall-swarm-app-h5" ]]; then
  echo "商城功能回归只允许在 mall-swarm-app-h5 产品基座执行" >&2
  exit 1
fi
cd "$functional_root"

if [[ $# -gt 1 || ( $# -eq 1 && "$1" != "--preflight-only" ) ]]; then
  echo "用法：bash scripts/run-mall-functional-regression.sh [--preflight-only]" >&2
  exit 2
fi

# 日常源码回归不要求干净提交、远端同步或固定版本号；这些是候选封包门禁。
# 此处仍检查三个源码版本一致，避免在工作区测试不同版本的前后端。
node <<'NODE'
const fs = require('node:fs')
const version = fs.readFileSync('VERSION', 'utf8').trim()
const miniPackage = require('./mall-mini-program/package.json')
const miniLock = require('./mall-mini-program/package-lock.json')
if (!version || miniPackage.version !== version || miniLock.version !== version
    || miniLock.packages?.['']?.version !== version) {
  throw new Error('根版本、小程序包版本和锁文件必须一致')
}
NODE

bash scripts/db-migrate.sh plan >/dev/null

unexpected_sensitive="$(git ls-files --cached --others --exclude-standard \
  | grep -E '(^|/)(\.env($|\.)|[^/]+\.(pem|key|p12|pfx)$|project\.private\.config\.json$)' \
  | grep -Ev '^(\.env\.example|mall-shop-web/\.env\.(android|integrated|public|team))$' || true)"
if [[ -n "$unexpected_sensitive" ]]; then
  echo "发现未登记敏感文件名：" >&2
  printf '%s\n' "$unexpected_sensitive" >&2
  exit 1
fi
git diff --check
echo "商城日常功能回归预检通过；不代表冻结候选、服务器、微信或真机通过。"

if [[ "${1:-}" == "--preflight-only" ]]; then
  exit 0
fi

echo "[1/9] 后端全量测试"
./mvnw test

echo "[2/9] 后端生产打包"
./mvnw -DskipTests package

echo "[3/9] 管理后台全量测试"
(cd mall-distribution-admin && npm test -- --run)

echo "[4/9] 管理后台生产构建"
(cd mall-distribution-admin && npm run build)

echo "[5/9] 微信小程序全量测试"
(cd mall-mini-program && npm test)

echo "[6/9] 微信小程序工程检查"
(cd mall-mini-program && npm run check)

echo "[7/9] 商城 H5 全量测试"
(cd mall-shop-web && npm test)

echo "[8/9] 商城 H5 三种生产构建"
(cd mall-shop-web && npm run build)

echo "[9/9] 发布脚本合同与语法回归"
bash -n scripts/production-backup.sh scripts/db-migrate.sh \
  scripts/tests/db-migrate-lock-integration.sh \
  scripts/run-mall-functional-regression.sh
node --test scripts/tests/*.test.mjs

git diff --check
echo "商城日常功能回归通过；冻结候选仍须运行对应版本的发布门禁。"
