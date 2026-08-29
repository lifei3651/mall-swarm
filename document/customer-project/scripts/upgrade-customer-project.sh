#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  cat <<'EOF'
用法：upgrade-customer-project.sh \
  --project 客户项目目录 \
  [--target-ref 新基座提交] \
  [--base-repo 商城基座仓库] \
  [--report 预检报告路径] \
  [--apply]

说明：
  - 默认只预检，不修改客户项目；确认报告无冲突后再使用 --apply。
  - 只允许从客户项目记录的旧基座提交向其后代提交升级，禁止降级或跨历史覆盖。
  - 基座未改、客户已改的文件保持原样；双方修改同一文件时尝试三方合并，冲突则整次停止。
  - --apply 不会自动提交、推送或发布，必须完成人工复核和项目全量测试后再提交。
EOF
}

PROJECT=""
TARGET_REF="HEAD"
BASE_REPO=""
REPORT_PATH=""
APPLY=0

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --project) PROJECT=${2:-}; shift 2 ;;
    --target-ref) TARGET_REF=${2:-}; shift 2 ;;
    --base-repo) BASE_REPO=${2:-}; shift 2 ;;
    --report) REPORT_PATH=${2:-}; shift 2 ;;
    --apply) APPLY=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
  esac
done

[[ -n "$PROJECT" ]] || { usage >&2; exit 2; }
command -v git >/dev/null 2>&1 || { echo "缺少 Git" >&2; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo "缺少 Python 3" >&2; exit 1; }

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DEFAULT_BASE_REPO=$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)
BASE_REPO=${BASE_REPO:-$DEFAULT_BASE_REPO}
BASE_REPO=$(git -C "$BASE_REPO" rev-parse --show-toplevel 2>/dev/null) \
  || { echo "商城基座目录不是 Git 仓库：$BASE_REPO" >&2; exit 1; }
PROJECT=$(git -C "$PROJECT" rev-parse --show-toplevel 2>/dev/null) \
  || { echo "客户项目目录不是 Git 仓库：$PROJECT" >&2; exit 1; }
[[ "$PROJECT" != "$BASE_REPO" ]] || { echo "不能把商城基座当作客户项目升级" >&2; exit 1; }
[[ -z "$(git -C "$BASE_REPO" status --porcelain)" ]] \
  || { echo "商城基座工作区不干净；请先提交或处理现有改动" >&2; exit 1; }
[[ -f "$PROJECT/CUSTOMER_PROJECT_ORIGIN.json" ]] \
  || { echo "客户项目缺少 CUSTOMER_PROJECT_ORIGIN.json" >&2; exit 1; }
[[ -z "$(git -C "$PROJECT" status --porcelain)" ]] \
  || { echo "客户项目工作区不干净；请先提交或处理现有改动" >&2; exit 1; }
if [[ -n "$REPORT_PATH" && "$APPLY" -eq 0 ]]; then
  python3 - "$PROJECT" "$REPORT_PATH" <<'PY'
import pathlib
import sys

project = pathlib.Path(sys.argv[1]).resolve()
report = pathlib.Path(sys.argv[2]).expanduser()
if not report.is_absolute():
    report = pathlib.Path.cwd() / report
try:
    report.resolve().relative_to(project)
except ValueError:
    raise SystemExit(0)
raise SystemExit("只读预检报告必须保存在客户项目目录之外")
PY
fi

BASE_REMOTE=$(git -C "$BASE_REPO" remote get-url origin 2>/dev/null || true)
PROJECT_REMOTE=$(git -C "$PROJECT" remote get-url origin 2>/dev/null || true)
normalize_remote() {
  python3 - "$1" <<'PY'
import pathlib
import re
import sys
import urllib.parse

value = sys.argv[1].strip()
scp = re.fullmatch(r"[^/@:]+@([^:]+):(.+)", value)
if scp:
    host, path = scp.groups()
    print(f"{host.lower()}/{path.strip('/').removesuffix('.git')}")
    raise SystemExit(0)

parsed = urllib.parse.urlparse(value)
if parsed.scheme == "file":
    print(pathlib.Path(urllib.parse.unquote(parsed.path)).resolve())
elif parsed.scheme and parsed.hostname:
    path = urllib.parse.unquote(parsed.path).strip("/").removesuffix(".git")
    print(f"{parsed.hostname.lower()}/{path}")
else:
    print(pathlib.Path(value).expanduser().resolve())
PY
}
BASE_REMOTE_ID=$(normalize_remote "$BASE_REMOTE")
PROJECT_REMOTE_ID=$(normalize_remote "$PROJECT_REMOTE")
if [[ -n "$BASE_REMOTE" && -n "$PROJECT_REMOTE" && "$PROJECT_REMOTE_ID" == "$BASE_REMOTE_ID" ]]; then
  echo "客户项目仍指向商城基座远程仓库，已停止以防误推送" >&2
  exit 1
fi

IFS=$'\t' read -r SCHEMA_VERSION CUSTOMER_CODE CUSTOMER_NAME OLD_COMMIT OLD_VERSION < <(
  python3 - "$PROJECT/CUSTOMER_PROJECT_ORIGIN.json" <<'PY'
import json
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
try:
    manifest = json.loads(path.read_text(encoding="utf-8"))
except (OSError, ValueError) as exc:
    raise SystemExit(f"客户来源清单无法读取：{exc}")

required = ("schemaVersion", "customerCode", "customerName", "baseCommit", "baseVersion")
missing = [key for key in required if key not in manifest]
if missing:
    raise SystemExit("客户来源清单缺少字段：" + ", ".join(missing))
values = [str(manifest[key]) for key in required]
if any("\t" in value or "\n" in value or "\r" in value for value in values):
    raise SystemExit("客户来源清单包含不允许的控制字符")
print("\t".join(values))
PY
)
[[ "$SCHEMA_VERSION" == "1" ]] || { echo "暂不支持客户来源清单版本：$SCHEMA_VERSION" >&2; exit 1; }

OLD_COMMIT=$(git -C "$BASE_REPO" rev-parse --verify "${OLD_COMMIT}^{commit}" 2>/dev/null) \
  || { echo "基座仓库中找不到客户记录的来源提交，请先补齐完整历史：$OLD_COMMIT" >&2; exit 1; }
TARGET_COMMIT=$(git -C "$BASE_REPO" rev-parse --verify "${TARGET_REF}^{commit}" 2>/dev/null) \
  || { echo "目标不是有效基座提交：$TARGET_REF" >&2; exit 1; }
git -C "$BASE_REPO" merge-base --is-ancestor "$OLD_COMMIT" "$TARGET_COMMIT" \
  || { echo "目标提交不是客户当前基座的后代，禁止降级或跨历史升级" >&2; exit 1; }
TARGET_VERSION=$(git -C "$BASE_REPO" show "${TARGET_COMMIT}:VERSION" 2>/dev/null | tr -d '\r\n')
[[ -n "$TARGET_VERSION" ]] || { echo "目标基座提交缺少 VERSION" >&2; exit 1; }

if [[ "$OLD_COMMIT" == "$TARGET_COMMIT" ]]; then
  echo "客户项目已经基于目标提交：$TARGET_VERSION / $TARGET_COMMIT"
  exit 0
fi

DERIVE_SCRIPT="$BASE_REPO/document/customer-project/scripts/derive-customer-project.sh"
[[ -x "$DERIVE_SCRIPT" ]] || { echo "目标基座缺少可执行的客户项目派生工具" >&2; exit 1; }

TMP_ROOT=$(mktemp -d "${TMPDIR:-/tmp}/customer-project-upgrade.XXXXXX")
cleanup() {
  case "${TMP_ROOT:-}" in
    "${TMPDIR:-/tmp}/customer-project-upgrade."*) [[ ! -d "$TMP_ROOT" ]] || rm -rf -- "$TMP_ROOT" ;;
  esac
}
trap cleanup EXIT HUP INT TERM

"$DERIVE_SCRIPT" --customer-code "$CUSTOMER_CODE" --customer-name "$CUSTOMER_NAME" \
  --destination "$TMP_ROOT/old" --source-ref "$OLD_COMMIT" >/dev/null
"$DERIVE_SCRIPT" --customer-code "$CUSTOMER_CODE" --customer-name "$CUSTOMER_NAME" \
  --destination "$TMP_ROOT/new" --source-ref "$TARGET_COMMIT" >/dev/null

for snapshot in "$TMP_ROOT/old" "$TMP_ROOT/new"; do
  rm -rf -- "$snapshot/.git"
  rm -f -- "$snapshot/AGENTS.md" "$snapshot/CUSTOMER_PROJECT.md" \
    "$snapshot/CUSTOMER_PROJECT_ORIGIN.json" "$snapshot/CUSTOMER_PROJECT_UPGRADE_REPORT.md"
done

python3 - "$TMP_ROOT/old" "$TMP_ROOT/new" "$PROJECT" "$PROJECT/CUSTOMER_PROJECT_ORIGIN.json" \
  "$OLD_COMMIT" "$OLD_VERSION" "$TARGET_COMMIT" "$TARGET_VERSION" "$CUSTOMER_CODE" "$CUSTOMER_NAME" \
  "$APPLY" "$REPORT_PATH" "$TMP_ROOT" <<'PY'
import datetime as dt
import hashlib
import json
import os
import pathlib
import shutil
import stat
import subprocess
import sys

(
    old_root_arg, new_root_arg, project_arg, manifest_arg,
    old_commit, old_version, target_commit, target_version,
    customer_code, customer_name, apply_arg, report_arg, temp_arg,
) = sys.argv[1:]
old_root = pathlib.Path(old_root_arg)
new_root = pathlib.Path(new_root_arg)
project = pathlib.Path(project_arg)
manifest_path = pathlib.Path(manifest_arg)
temp_root = pathlib.Path(temp_arg)
apply_changes = apply_arg == "1"

generated_paths = {
    "AGENTS.md",
    "CUSTOMER_PROJECT.md",
    "CUSTOMER_PROJECT_ORIGIN.json",
    "CUSTOMER_PROJECT_UPGRADE_REPORT.md",
}

def collect(root: pathlib.Path):
    result = {}
    for path in root.rglob("*"):
        if path.is_dir():
            continue
        rel = path.relative_to(root).as_posix()
        if rel in generated_paths or rel.startswith(".git/"):
            continue
        if path.is_symlink() or not path.is_file():
            raise SystemExit(f"基座快照含不支持的文件类型：{rel}")
        data = path.read_bytes()
        result[rel] = {
            "path": path,
            "data": data,
            "hash": hashlib.sha256(data).hexdigest(),
            "mode": stat.S_IMODE(path.stat().st_mode),
        }
    return result

def current_entry(rel: str):
    path = project / rel
    cursor = path
    while cursor != project:
        if cursor.is_symlink():
            return {"path": path, "unsupported": True}
        cursor = cursor.parent
    if not path.exists() and not path.is_symlink():
        return None
    if path.is_symlink() or not path.is_file():
        return {"path": path, "unsupported": True}
    data = path.read_bytes()
    return {
        "path": path,
        "data": data,
        "hash": hashlib.sha256(data).hexdigest(),
        "mode": stat.S_IMODE(path.stat().st_mode),
    }

def same(left, right):
    return left is not None and right is not None \
        and not left.get("unsupported") and not right.get("unsupported") \
        and left["hash"] == right["hash"] and left["mode"] == right["mode"]

def text_file(entry):
    if entry is None or entry.get("unsupported") or b"\0" in entry["data"]:
        return False
    try:
        entry["data"].decode("utf-8")
        return True
    except UnicodeDecodeError:
        return False

old_files = collect(old_root)
new_files = collect(new_root)
actions = []
conflicts = []
already_applied = []

for rel in sorted(set(old_files) | set(new_files)):
    old = old_files.get(rel)
    new = new_files.get(rel)
    current = current_entry(rel)
    if same(old, new):
        continue
    if current is not None and current.get("unsupported"):
        conflicts.append((rel, "客户项目中的路径不是普通文件"))
        continue

    if old is None:
        if current is None:
            actions.append((rel, "add", "基座新增文件", new["data"], new["mode"]))
        elif same(current, new):
            already_applied.append((rel, "基座新增内容已存在"))
        else:
            conflicts.append((rel, "基座和客户分别新增了同一路径"))
        continue

    if new is None:
        if current is None:
            already_applied.append((rel, "基座删除内容已不存在"))
        elif same(current, old):
            actions.append((rel, "delete", "基座删除、客户未修改", None, None))
        else:
            conflicts.append((rel, "基座删除了客户已修改的文件"))
        continue

    if current is None:
        conflicts.append((rel, "基座修改了客户已删除的文件"))
    elif same(current, new):
        already_applied.append((rel, "目标基座内容已存在"))
    elif same(current, old):
        actions.append((rel, "update", "基座修改、客户未修改", new["data"], new["mode"]))
    elif text_file(old) and text_file(new) and text_file(current):
        merge_dir = temp_root / "merge"
        merge_dir.mkdir(parents=True, exist_ok=True)
        token = hashlib.sha256(rel.encode("utf-8")).hexdigest()
        current_file = merge_dir / f"{token}.current"
        old_file = merge_dir / f"{token}.old"
        new_file = merge_dir / f"{token}.new"
        current_file.write_bytes(current["data"])
        old_file.write_bytes(old["data"])
        new_file.write_bytes(new["data"])
        merged = subprocess.run(
            ["git", "merge-file", "-p", str(current_file), str(old_file), str(new_file)],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
        if merged.returncode == 0:
            mode = new["mode"] if current["mode"] == old["mode"] else current["mode"]
            actions.append((rel, "merge", "基座与客户修改已自动三方合并", merged.stdout, mode))
        elif merged.returncode == 1:
            conflicts.append((rel, "基座与客户修改发生内容冲突"))
        else:
            raise SystemExit(f"三方合并工具执行失败：{rel}: {merged.stderr.decode('utf-8', 'replace')}")
    else:
        conflicts.append((rel, "基座与客户同时修改了二进制或非UTF-8文件"))

mode_label = "正式应用" if apply_changes else "只读预检"
now = dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
lines = [
    "# 客户项目基座升级报告",
    "",
    f"- 客户项目：`{customer_code}`（{customer_name}）",
    f"- 执行方式：{mode_label}",
    f"- 原基座：`{old_version}` / `{old_commit}`",
    f"- 目标基座：`{target_version}` / `{target_commit}`",
    f"- 自动处理：{len(actions)} 项",
    f"- 已存在目标内容：{len(already_applied)} 项",
    f"- 冲突：{len(conflicts)} 项",
    "",
    "## 自动处理项",
    "",
]
if actions:
    lines.extend(f"- `{rel}`：{reason}" for rel, _, reason, _, _ in actions)
else:
    lines.append("- 无")
lines.extend(["", "## 已存在目标内容", ""])
if already_applied:
    lines.extend(f"- `{rel}`：{reason}" for rel, reason in already_applied)
else:
    lines.append("- 无")
lines.extend(["", "## 需要人工处理的冲突", ""])
if conflicts:
    lines.extend(f"- `{rel}`：{reason}" for rel, reason in conflicts)
else:
    lines.append("- 无")
lines.extend([
    "",
    "## 后续要求",
    "",
    "- 客户新增且基座不存在的专属文件不会被工具删除或覆盖。",
    "- 正式应用后必须人工查看 Git 差异，并完成客户项目后端、商城、团队 H5、一体化 H5 和管理后台回归。",
    "- 工具不会自动提交、推送、迁移数据库或发布服务器。",
    "",
])
report = "\n".join(lines)

if report_arg:
    report_path = pathlib.Path(report_arg).expanduser()
    if not report_path.is_absolute():
        report_path = pathlib.Path.cwd() / report_path
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(report, encoding="utf-8")

print(report)

if conflicts:
    if apply_changes:
        raise SystemExit("检测到冲突，客户项目未作任何修改")
    raise SystemExit(3)

if not apply_changes:
    raise SystemExit(0)

backup_root = temp_root / "rollback"
backup_root.mkdir(parents=True, exist_ok=True)
backups = []
try:
    for rel, action, _, data, mode in actions:
        destination = project / rel
        existed = destination.exists()
        backup = backup_root / rel
        if existed:
            backup.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(destination, backup)
        backups.append((destination, backup, existed))
        if action == "delete":
            destination.unlink()
        else:
            destination.parent.mkdir(parents=True, exist_ok=True)
            temporary = destination.with_name(destination.name + ".customer-upgrade-tmp")
            temporary.write_bytes(data)
            os.chmod(temporary, mode)
            os.replace(temporary, destination)

    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    manifest_backup = backup_root / "CUSTOMER_PROJECT_ORIGIN.json"
    shutil.copy2(manifest_path, manifest_backup)
    backups.append((manifest_path, manifest_backup, True))
    manifest["previousBaseCommit"] = old_commit
    manifest["baseCommit"] = target_commit
    manifest["baseVersion"] = target_version
    manifest["lastUpgradeAt"] = now
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    project_report = project / "CUSTOMER_PROJECT_UPGRADE_REPORT.md"
    existed = project_report.exists()
    report_backup = backup_root / "CUSTOMER_PROJECT_UPGRADE_REPORT.md"
    if existed:
        shutil.copy2(project_report, report_backup)
    backups.append((project_report, report_backup, existed))
    project_report.write_text(report, encoding="utf-8")
except Exception:
    for destination, backup, existed in reversed(backups):
        if existed:
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(backup, destination)
        elif destination.exists():
            destination.unlink()
    raise

print(f"客户项目已更新到基座 {target_version}；请先复核和测试，不要直接发布。")
PY
