#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT_DIR=$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)
TEST_ROOT=$(mktemp -d "${TMPDIR:-/tmp}/customer-project-upgrade-test.XXXXXX")
cleanup() {
  rm -rf "$TEST_ROOT"
}
trap cleanup EXIT HUP INT TERM

BASE_REPO="$TEST_ROOT/base"
git clone -q --no-hardlinks "$ROOT_DIR" "$BASE_REPO"
git -C "$BASE_REPO" config user.name 'Customer Upgrade Test'
git -C "$BASE_REPO" config user.email 'noreply@local.invalid'
cp "$ROOT_DIR/document/customer-project/scripts/upgrade-customer-project.sh" \
  "$BASE_REPO/document/customer-project/scripts/upgrade-customer-project.sh"
chmod +x "$BASE_REPO/document/customer-project/scripts/upgrade-customer-project.sh"
git -C "$BASE_REPO" add document/customer-project/scripts/upgrade-customer-project.sh
git -C "$BASE_REPO" commit -q -m '加入客户项目升级工具'
OLD_COMMIT=$(git -C "$BASE_REPO" rev-parse HEAD)

CUSTOMER_PROJECT="$TEST_ROOT/customer"
"$BASE_REPO/document/customer-project/scripts/derive-customer-project.sh" \
  --customer-code customer_upgrade \
  --customer-name 客户升级测试 \
  --destination "$CUSTOMER_PROJECT" \
  --source-ref "$OLD_COMMIT" >/dev/null

BASE_VERSION=$(cat "$BASE_REPO/VERSION")
printf '%s-dirty\n' "$BASE_VERSION" >"$BASE_REPO/VERSION"
if "$BASE_REPO/document/customer-project/scripts/upgrade-customer-project.sh" \
  --base-repo "$BASE_REPO" --project "$CUSTOMER_PROJECT" --target-ref "$OLD_COMMIT" >/dev/null 2>&1; then
  echo "商城基座工作区不干净时升级工具必须停止" >&2
  exit 1
fi
printf '%s\n' "$BASE_VERSION" >"$BASE_REPO/VERSION"

BASE_REMOTE=$(git -C "$BASE_REPO" remote get-url origin)
git -C "$CUSTOMER_PROJECT" remote add origin "file://$BASE_REMOTE"
if "$BASE_REPO/document/customer-project/scripts/upgrade-customer-project.sh" \
  --base-repo "$BASE_REPO" --project "$CUSTOMER_PROJECT" --target-ref "$OLD_COMMIT" >/dev/null 2>&1; then
  echo "客户项目指向商城基座的等价远程地址时升级工具必须停止" >&2
  exit 1
fi
git -C "$CUSTOMER_PROJECT" remote remove origin

if "$BASE_REPO/document/customer-project/scripts/upgrade-customer-project.sh" \
  --base-repo "$BASE_REPO" --project "$CUSTOMER_PROJECT" --target-ref "$OLD_COMMIT" \
  --report "$CUSTOMER_PROJECT/preview.md" >/dev/null 2>&1; then
  echo "只读预检报告不能写入客户项目" >&2
  exit 1
fi
[ ! -e "$CUSTOMER_PROJECT/preview.md" ]

printf '\n客户专属版权补充。\n' >>"$CUSTOMER_PROJECT/COPYRIGHT.md"
git -C "$CUSTOMER_PROJECT" add COPYRIGHT.md
git -C "$CUSTOMER_PROJECT" -c user.name='Customer Test' -c user.email='noreply@local.invalid' \
  commit -q -m '客户专属修改'

printf '\n基座公共维护说明。\n' >>"$BASE_REPO/NOTICE"
git -C "$BASE_REPO" add NOTICE
git -C "$BASE_REPO" commit -q -m '基座公共更新'
TARGET_COMMIT=$(git -C "$BASE_REPO" rev-parse HEAD)

UPGRADE_SCRIPT="$BASE_REPO/document/customer-project/scripts/upgrade-customer-project.sh"
PREVIEW_REPORT="$TEST_ROOT/preview.md"
"$UPGRADE_SCRIPT" --base-repo "$BASE_REPO" --project "$CUSTOMER_PROJECT" \
  --target-ref "$TARGET_COMMIT" --report "$PREVIEW_REPORT" >/dev/null
[ -z "$(git -C "$CUSTOMER_PROJECT" status --porcelain)" ]
grep -q '`NOTICE`' "$PREVIEW_REPORT"

"$UPGRADE_SCRIPT" --base-repo "$BASE_REPO" --project "$CUSTOMER_PROJECT" \
  --target-ref "$TARGET_COMMIT" --apply >/dev/null
grep -q '基座公共维护说明' "$CUSTOMER_PROJECT/NOTICE"
grep -q '客户专属版权补充' "$CUSTOMER_PROJECT/COPYRIGHT.md"
grep -q '客户项目基座升级报告' "$CUSTOMER_PROJECT/CUSTOMER_PROJECT_UPGRADE_REPORT.md"
python3 - "$CUSTOMER_PROJECT/CUSTOMER_PROJECT_ORIGIN.json" "$OLD_COMMIT" "$TARGET_COMMIT" <<'PY'
import json
import pathlib
import sys

manifest = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
assert manifest["previousBaseCommit"] == sys.argv[2]
assert manifest["baseCommit"] == sys.argv[3]
assert manifest["lastUpgradeAt"].endswith("Z")
PY

git -C "$CUSTOMER_PROJECT" add .
git -C "$CUSTOMER_PROJECT" -c user.name='Customer Test' -c user.email='noreply@local.invalid' \
  commit -q -m '升级客户基座'
"$UPGRADE_SCRIPT" --base-repo "$BASE_REPO" --project "$CUSTOMER_PROJECT" \
  --target-ref "$TARGET_COMMIT" --apply | grep -q '已经基于目标提交'

CONFLICT_PROJECT="$TEST_ROOT/customer-conflict"
"$BASE_REPO/document/customer-project/scripts/derive-customer-project.sh" \
  --customer-code customer_conflict \
  --customer-name 客户冲突测试 \
  --destination "$CONFLICT_PROJECT" \
  --source-ref "$OLD_COMMIT" >/dev/null
python3 - "$CONFLICT_PROJECT/README.md" <<'PY'
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
path.write_text(text.replace("# 灵启商城", "# 客户专属商城", 1), encoding="utf-8")
PY
git -C "$CONFLICT_PROJECT" add README.md
git -C "$CONFLICT_PROJECT" -c user.name='Customer Test' -c user.email='noreply@local.invalid' \
  commit -q -m '客户修改标题'

python3 - "$BASE_REPO/README.md" <<'PY'
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
path.write_text(text.replace("# 灵启商城", "# 灵启商城基座", 1), encoding="utf-8")
PY
git -C "$BASE_REPO" add README.md
git -C "$BASE_REPO" commit -q -m '基座修改标题'
CONFLICT_TARGET=$(git -C "$BASE_REPO" rev-parse HEAD)
BEFORE_STATUS=$(git -C "$CONFLICT_PROJECT" status --porcelain)
if "$UPGRADE_SCRIPT" --base-repo "$BASE_REPO" --project "$CONFLICT_PROJECT" \
  --target-ref "$CONFLICT_TARGET" --apply >/dev/null 2>&1; then
  echo "发生冲突时升级工具必须停止" >&2
  exit 1
fi
[ "$(git -C "$CONFLICT_PROJECT" status --porcelain)" = "$BEFORE_STATUS" ]
grep -q '^# 客户专属商城$' "$CONFLICT_PROJECT/README.md"
python3 - "$CONFLICT_PROJECT/CUSTOMER_PROJECT_ORIGIN.json" "$OLD_COMMIT" <<'PY'
import json
import pathlib
import sys

manifest = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
assert manifest["baseCommit"] == sys.argv[2]
PY

printf '\n未提交改动。\n' >>"$CONFLICT_PROJECT/NOTICE"
if "$UPGRADE_SCRIPT" --base-repo "$BASE_REPO" --project "$CONFLICT_PROJECT" \
  --target-ref "$CONFLICT_TARGET" >/dev/null 2>&1; then
  echo "工作区不干净时升级工具必须停止" >&2
  exit 1
fi

echo "customer project upgrade tests passed"
