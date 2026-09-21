#!/usr/bin/env bash
set -euo pipefail

closure_root="$(git rev-parse --show-toplevel)"
if [[ "$(basename "$closure_root")" != "mall-swarm-app-h5" ]]; then
  echo "收口回归只允许在 mall-swarm-app-h5 产品基座执行" >&2
  exit 1
fi

cd "$closure_root"

echo "[1/8] 后端全量测试"
./mvnw test

echo "[2/8] 后端生产打包"
./mvnw -DskipTests package

echo "[3/8] 管理后台全量测试"
(
  cd mall-distribution-admin
  npm test -- --run
)

echo "[4/8] 管理后台生产构建"
(
  cd mall-distribution-admin
  npm run build
)

echo "[5/8] 微信小程序全量测试"
(
  cd mall-mini-program
  npm test
)

echo "[6/8] 微信小程序工程检查"
(
  cd mall-mini-program
  npm run check
)

echo "[7/8] 商城 H5 全量测试"
(
  cd mall-shop-web
  npm test
)

echo "[8/8] 商城 H5 三种生产构建"
(
  cd mall-shop-web
  npm run build
)

echo "商城本地收口回归通过。该结果只代表代码、自动测试和本地产物，不代表服务器、微信平台或真机验收完成。"
