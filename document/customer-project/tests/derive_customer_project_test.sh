#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT_DIR=$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)
DERIVE_SCRIPT="$ROOT_DIR/document/customer-project/scripts/derive-customer-project.sh"
TEST_ROOT=$(mktemp -d "${TMPDIR:-/tmp}/customer-project-derive-test.XXXXXX")
cleanup() {
  rm -rf "$TEST_ROOT"
}
trap cleanup EXIT HUP INT TERM

DESTINATION="$TEST_ROOT/customer_demo"
SOURCE_COMMIT=$(git -C "$ROOT_DIR" rev-parse HEAD)
SOURCE_VERSION=$(git -C "$ROOT_DIR" show "${SOURCE_COMMIT}:VERSION" | tr -d '\r\n')

"$DERIVE_SCRIPT" \
  --customer-code customer_demo \
  --customer-name 客户演示项目 \
  --destination "$DESTINATION" \
  --source-ref "$SOURCE_COMMIT" >/dev/null

[ -d "$DESTINATION/.git" ]
[ "$(git -C "$DESTINATION" branch --show-current)" = "main" ]
[ -z "$(git -C "$DESTINATION" status --porcelain)" ]
[ "$(git -C "$DESTINATION" rev-list --count HEAD)" = "1" ]
[ -z "$(git -C "$DESTINATION" remote)" ]
[ ! -e "$DESTINATION/.deploy" ]
[ ! -e "$DESTINATION/scripts/production-targets.sh" ]
[ ! -e "$DESTINATION/document/RELEASE_REPORT.md" ]
[ ! -e "$DESTINATION/document/DAILY_LOG.md" ]
[ ! -e "$DESTINATION/document/audits" ]
[ -f "$DESTINATION/scripts/vite-version-manifest.mjs" ]
[ -f "$DESTINATION/scripts/nginx/lingqimall.conf" ]
[ -f "$DESTINATION/CUSTOMER_PROJECT.md" ]
[ -f "$DESTINATION/CUSTOMER_PROJECT_ORIGIN.json" ]
grep -q 'customer_demo' "$DESTINATION/AGENTS.md"
grep -q 'CUSTOMER_BONUS_DISABLED' "$DESTINATION/CUSTOMER_PROJECT.md"
grep -q 'replace-with-customer-domain.invalid' "$DESTINATION/mall-shop-web/.env.android"
grep -Fq 'notifyUrl: ${ALIPAY_NOTIFY_URL:}' "$DESTINATION/mall-distribution/src/main/resources/application.yml"
grep -q "form-action 'self' https://openapi.alipay.com" "$DESTINATION/scripts/nginx/lingqimall.conf"
! grep -q 'lingqimall\.com' "$DESTINATION/scripts/nginx/lingqimall.conf"
! grep -q 'git@github.com:lifei3651/mall-swarm.git' "$DESTINATION/AGENTS.md"
! find "$DESTINATION/scripts" -maxdepth 1 -type f -name 'remote-*' -print -quit | grep -q .
! find "$DESTINATION" -type f \( -name '.env' -o -name '*.pem' -o -name '*.key' \) -print -quit | grep -q .

python3 - "$DESTINATION/CUSTOMER_PROJECT_ORIGIN.json" "$SOURCE_COMMIT" "$SOURCE_VERSION" <<'PY'
import json
import pathlib
import sys

manifest = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
assert manifest["customerCode"] == "customer_demo"
assert manifest["customerName"] == "客户演示项目"
assert manifest["baseCommit"] == sys.argv[2]
assert manifest["baseVersion"] == sys.argv[3]
assert manifest["gitRemoteConfigured"] is False
assert manifest["productionTargetConfigured"] is False
assert manifest["defaultBonusPolicy"] == "CUSTOMER_BONUS_DISABLED"
PY

if "$DERIVE_SCRIPT" --customer-code customer_demo --customer-name 客户演示项目 --destination "$DESTINATION" >/dev/null 2>&1; then
  echo "派生工具不应覆盖已存在目录" >&2
  exit 1
fi
if "$DERIVE_SCRIPT" --customer-code A --customer-name 客户演示项目 --destination "$TEST_ROOT/invalid" >/dev/null 2>&1; then
  echo "派生工具不应接受无效项目标识" >&2
  exit 1
fi
if "$DERIVE_SCRIPT" --customer-code customer_demo --customer-name 客户演示项目 --destination "$TEST_ROOT/invalid-ref" --source-ref missing-ref >/dev/null 2>&1; then
  echo "派生工具不应接受无效来源提交" >&2
  exit 1
fi
if "$DERIVE_SCRIPT" --customer-code customer_demo --customer-name 客户演示项目 --destination "$ROOT_DIR/target/forbidden-customer-project" >/dev/null 2>&1; then
  echo "派生工具不应在商城基座内部创建客户项目" >&2
  exit 1
fi

echo "customer project derivation tests passed"
