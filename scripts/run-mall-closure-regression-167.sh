#!/usr/bin/env bash
set -euo pipefail

closure_root="$(git rev-parse --show-toplevel)"
if [[ "$(basename "$closure_root")" != "mall-swarm-app-h5" ]]; then
  echo "收口回归只允许在 mall-swarm-app-h5 产品基座执行" >&2
  exit 1
fi

cd "$closure_root"

EXPECTED_VERSION=1.0.167
EXPECTED_BUILD_ID=20260924-closure-1.0.167
expected_commit="$(git rev-parse HEAD)"
[[ -z "$(git status --porcelain)" ]] || { echo "收口回归要求干净工作区" >&2; exit 1; }
[[ "${RELEASE_GIT_COMMIT:-}" == "$expected_commit" ]] || {
  echo "RELEASE_GIT_COMMIT 必须等于当前不可变提交 $expected_commit" >&2
  exit 1
}
[[ "${RELEASE_BUILD_ID:-}" == "$EXPECTED_BUILD_ID" ]] || {
  echo "RELEASE_BUILD_ID 必须精确等于 $EXPECTED_BUILD_ID" >&2
  exit 1
}
upstream_ref="$(git rev-parse --abbrev-ref '@{upstream}' 2>/dev/null)" \
  || { echo "收口回归要求当前分支配置可读取的 upstream" >&2; exit 1; }
[[ -n "$upstream_ref" ]] || { echo "收口回归要求当前分支配置可读取的 upstream" >&2; exit 1; }
upstream_divergence="$(git rev-list --left-right --count 'HEAD...@{upstream}' 2>/dev/null)" \
  || { echo "无法读取 upstream 同步状态" >&2; exit 1; }
[[ "$upstream_divergence" == $'0\t0' ]] \
  || { echo "收口回归前必须完成远程备份同步" >&2; exit 1; }
node <<'NODE'
const fs = require('fs')
const expectedVersion = '1.0.167'
const version = fs.readFileSync('VERSION', 'utf8').trim()
const pkg = require('./mall-mini-program/package.json')
const lock = require('./mall-mini-program/package-lock.json')
if (version !== expectedVersion || pkg.version !== expectedVersion || lock.version !== expectedVersion
    || lock.packages?.['']?.version !== expectedVersion) {
  throw new Error('根版本、小程序包版本和锁文件必须全部精确等于 1.0.167')
}
NODE
[[ "$(find document/db/migrations -maxdepth 1 -type f -name 'V*.sql' | wc -l | tr -d ' ')" == 41 ]] \
  || { echo "迁移清单不是预期41条" >&2; exit 1; }
[[ "$(find document/db/migrations -maxdepth 1 -type f -name 'V*.sql' -print | LC_ALL=C sort | tail -1 | xargs basename)" \
  == V202609211530__order_item_service_tag_snapshot.sql ]] \
  || { echo "第41条迁移不是订单服务标签快照" >&2; exit 1; }
[[ "$(shasum -a 256 document/db/migrations/V202609211530__order_item_service_tag_snapshot.sql | awk '{print $1}')" \
  == bb5f0dbf8942db2f2c56c28bd1c6c16fa185b1087690a750affd8ac523962526 ]] \
  || { echo "第41条迁移哈希不一致" >&2; exit 1; }
bash scripts/db-migrate.sh plan >/dev/null

echo "[1/10] 后端全量测试"
./mvnw test

echo "[2/10] 后端生产打包"
./mvnw -DskipTests package

echo "[3/10] 管理后台全量测试"
(
  cd mall-distribution-admin
  npm test -- --run
)

echo "[4/10] 管理后台生产构建"
(
  cd mall-distribution-admin
  npm run build
)

echo "[5/10] 微信小程序全量测试"
(
  cd mall-mini-program
  npm test
)

echo "[6/10] 微信小程序工程检查"
(
  cd mall-mini-program
  npm run check
)

echo "[7/10] 商城 H5 全量测试"
(
  cd mall-shop-web
  npm test
)

echo "[8/10] 商城 H5 三种生产构建"
(
  cd mall-shop-web
  npm run build
)

echo "[9/10] 发布脚本与候选构建器语法门禁"
bash -n scripts/production-backup.sh scripts/db-migrate.sh \
  scripts/release-readiness-167.sh \
  scripts/remote-deploy-20260924-v1.0.167-backend.sh \
  scripts/remote-deploy-20260924-v1.0.167-static.sh
node --check scripts/release-lingqi-167.mjs
node --check scripts/prepare-lingqi-mini-release-167.mjs
node --check scripts/upload-lingqi-mini-167.mjs
node --test scripts/tests/*.test.mjs
python3 - <<'PY'
from pathlib import Path

release = Path('scripts/remote-deploy-20260924-v1.0.167-backend.sh').read_text()
assert 'v["paymentEnabled"] is False' in release
assert 'd["data"]["wechatPayEnabled"] is True' in release
PY

echo "[10/10] 版本身份与敏感文件名门禁"
for allowed in .env.example mall-shop-web/.env.android mall-shop-web/.env.integrated \
  mall-shop-web/.env.public mall-shop-web/.env.team; do
  git ls-files --error-unmatch "$allowed" >/dev/null
done
unexpected_sensitive="$(git ls-files | grep -E '(^|/)(\.env($|\.)|[^/]+\.(pem|key|p12|pfx)$|project\.private\.config\.json$)' \
  | grep -Ev '^(\.env\.example|mall-shop-web/\.env\.(android|integrated|public|team))$' || true)"
[[ -z "$unexpected_sensitive" ]] || {
  echo "发现未登记敏感文件名：" >&2
  printf '%s\n' "$unexpected_sensitive" >&2
  exit 1
}
git diff --check

echo "商城本地收口回归通过。该结果只代表代码、自动测试和本地产物，不代表服务器、微信平台或真机验收完成。"
