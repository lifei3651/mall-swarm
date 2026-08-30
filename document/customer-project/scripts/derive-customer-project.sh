#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  cat <<'EOF'
用法：derive-customer-project.sh \
  --customer-code 客户项目标识 \
  --customer-name 客户名称 \
  --destination 新项目目录 \
  [--source-ref 已验收提交]

说明：
  - 只从一个明确 Git 提交派生，不包含当前未提交文件。
  - 目标目录必须不存在，且不能位于商城基座仓库内部。
  - 新项目会建立独立 Git 仓库，但不会配置远程或执行推送。
EOF
}

CUSTOMER_CODE=""
CUSTOMER_NAME=""
DESTINATION=""
SOURCE_REF="HEAD"

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --customer-code) CUSTOMER_CODE=${2:-}; shift 2 ;;
    --customer-name) CUSTOMER_NAME=${2:-}; shift 2 ;;
    --destination) DESTINATION=${2:-}; shift 2 ;;
    --source-ref) SOURCE_REF=${2:-}; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
  esac
done

[[ -n "$CUSTOMER_CODE" && -n "$CUSTOMER_NAME" && -n "$DESTINATION" ]] || { usage >&2; exit 2; }
printf '%s' "$CUSTOMER_CODE" | grep -Eq '^[a-z0-9][a-z0-9_-]{2,40}$' \
  || { echo "客户项目标识只能使用3至41位小写字母、数字、下划线和短横线" >&2; exit 1; }
command -v git >/dev/null 2>&1 || { echo "缺少 Git" >&2; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo "缺少 Python 3" >&2; exit 1; }
command -v tar >/dev/null 2>&1 || { echo "缺少 tar" >&2; exit 1; }

python3 - "$CUSTOMER_NAME" <<'PY'
import sys

name = sys.argv[1]
if not name or len(name) > 64 or any(ord(char) < 32 for char in name):
    raise SystemExit("客户名称不能为空、不能超过64个字符，也不能包含控制字符")
PY

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT_DIR=$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)
SOURCE_COMMIT=$(git -C "$ROOT_DIR" rev-parse --verify "${SOURCE_REF}^{commit}" 2>/dev/null) \
  || { echo "指定来源不是有效 Git 提交：$SOURCE_REF" >&2; exit 1; }
BASE_VERSION=$(git -C "$ROOT_DIR" show "${SOURCE_COMMIT}:VERSION" 2>/dev/null | tr -d '\r\n')
[[ -n "$BASE_VERSION" ]] || { echo "来源提交缺少 VERSION" >&2; exit 1; }
BASE_REMOTE=$(git -C "$ROOT_DIR" remote get-url origin 2>/dev/null || printf 'unconfigured')

DESTINATION_PARENT=$(dirname -- "$DESTINATION")
DESTINATION_NAME=$(basename -- "$DESTINATION")
[[ "$DESTINATION_NAME" != "." && "$DESTINATION_NAME" != ".." && "$DESTINATION_NAME" != "/" ]] \
  || { echo "目标目录不正确" >&2; exit 1; }
[[ -d "$DESTINATION_PARENT" ]] || { echo "目标目录的父目录不存在：$DESTINATION_PARENT" >&2; exit 1; }
DESTINATION_PARENT_ABS=$(CDPATH= cd -- "$DESTINATION_PARENT" && pwd -P)
DESTINATION_ABS="${DESTINATION_PARENT_ABS}/${DESTINATION_NAME}"
[[ ! -e "$DESTINATION_ABS" ]] || { echo "目标目录已存在，为防止覆盖已停止：$DESTINATION_ABS" >&2; exit 1; }
case "$DESTINATION_ABS" in
  "$ROOT_DIR"|"$ROOT_DIR"/*) echo "客户项目不能创建在商城基座仓库内部" >&2; exit 1 ;;
esac

STAGING=$(mktemp -d "${DESTINATION_PARENT_ABS}/.${CUSTOMER_CODE}.derive.XXXXXX")
PROJECT_STAGE="$STAGING/project"
cleanup() {
  case "${STAGING:-}" in
    "${DESTINATION_PARENT_ABS}/.${CUSTOMER_CODE}.derive."*) [[ ! -d "$STAGING" ]] || rm -rf -- "$STAGING" ;;
  esac
}
trap cleanup EXIT HUP INT TERM
mkdir -p "$PROJECT_STAGE"

# 只导出冻结提交。基座生产脚本、旧发布物、历史验收截图和基座 AGENTS 不能进入客户仓库。
git -C "$ROOT_DIR" archive --format=tar "$SOURCE_COMMIT" -- . \
  ':(exclude)AGENTS.md' \
  ':(exclude).deploy/**' \
  ':(exclude)scripts/**' \
  ':(exclude)document/customer-project/**' \
  ':(exclude)document/audits/**' \
  ':(exclude)document/qa/**' \
  ':(exclude)document/mind/**' \
  ':(exclude)document/DAILY_LOG.md' \
  ':(exclude)document/RELEASE_NOTES.md' \
  ':(exclude)document/RELEASE_REPORT.md' \
  ':(exclude)document/RELEASE_CANDIDATE_*.md' \
  ':(exclude)document/handoff-*.md' \
  ':(exclude)document/release-*.md' \
  | tar -xf - -C "$PROJECT_STAGE"

# 只保留构建和本地安全验证仍依赖的通用脚本；所有基座服务器目标与历史远程发布脚本均不导出。
mkdir -p "$PROJECT_STAGE/scripts"
git -C "$ROOT_DIR" archive --format=tar "$SOURCE_COMMIT" -- \
  scripts/db-migrate.sh \
  scripts/export-openapi.sh \
  scripts/flash-sale-concurrency-check.py \
  scripts/nginx/lingqimall-security.conf \
  scripts/nginx/lingqimall.conf \
  scripts/nginx/ssl-params.conf \
  scripts/payload-encrypt.py \
  scripts/verify-active-module-boundary.sh \
  scripts/vite-version-manifest.mjs \
  | tar -xf - -C "$PROJECT_STAGE"

DERIVED_AT=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
python3 - "$PROJECT_STAGE" "$CUSTOMER_CODE" "$CUSTOMER_NAME" "$SOURCE_COMMIT" "$BASE_VERSION" "$BASE_REMOTE" "$DERIVED_AT" <<'PY'
import json
import pathlib
import re
import sys

project = pathlib.Path(sys.argv[1])
customer_code, customer_name, source_commit, base_version, base_remote, derived_at = sys.argv[2:]

android_env = project / "mall-shop-web/.env.android"
android_lines = android_env.read_text(encoding="utf-8").splitlines()
replacements = {
    "VITE_API_BASE_URL": "https://replace-with-customer-domain.invalid/api",
    "VITE_PUBLIC_WEB_ORIGIN": "https://replace-with-customer-domain.invalid",
}
updated_lines = []
for line in android_lines:
    key = line.split("=", 1)[0]
    updated_lines.append(f"{key}={replacements[key]}" if key in replacements else line)
android_env.write_text("\n".join(updated_lines) + "\n", encoding="utf-8")

mini_runtime = project / "mall-mini-program/config/runtime.js"
mini_program_included = mini_runtime.is_file()
if mini_program_included:
    mini_text = re.sub(
        r"API_BASE_URL: '[^']*'",
        "API_BASE_URL: 'https://replace-with-customer-domain.invalid/api'",
        mini_runtime.read_text(encoding="utf-8"),
        count=1,
    )
    mini_runtime.write_text(mini_text, encoding="utf-8")

application_yml = project / "mall-distribution/src/main/resources/application.yml"
application_text = application_yml.read_text(encoding="utf-8")
application_text = application_text.replace(
    "${ALIPAY_NOTIFY_URL:https://lingqimall.com/api/pay/alipay/notify}",
    "${ALIPAY_NOTIFY_URL:}",
).replace(
    "${ALIPAY_RETURN_URL:https://lingqimall.com/api/pay/alipay/return}",
    "${ALIPAY_RETURN_URL:}",
)
application_yml.write_text(application_text, encoding="utf-8")

alipay_config = project / "mall-distribution/src/main/java/com/macro/mall/distribution/config/AlipayConfig.java"
alipay_text = alipay_config.read_text(encoding="utf-8").replace(
    "https://lingqimall.com/api/pay/alipay/notify",
    "https://customer-domain.invalid/api/pay/alipay/notify",
).replace(
    "https://lingqimall.com/api/pay/alipay/return",
    "https://customer-domain.invalid/api/pay/alipay/return",
)
alipay_config.write_text(alipay_text, encoding="utf-8")

# 商城前端安全回归需要读取这份 CSP 参考配置，但客户项目不能继承基座正式域名。
# 只保留无执行能力的配置参考文件，不导出证书部署、主机连接或线上验收脚本。
nginx_config = project / "scripts/nginx/lingqimall.conf"
nginx_text = nginx_config.read_text(encoding="utf-8")
nginx_text = nginx_text.replace("play.lingqimall.com", "play.customer-domain.invalid")
nginx_text = nginx_text.replace("www.lingqimall.com", "team.customer-domain.invalid")
nginx_text = nginx_text.replace("lingqimall.com", "customer-domain.invalid")
nginx_config.write_text(nginx_text, encoding="utf-8")

manifest = {
    "schemaVersion": 1,
    "customerCode": customer_code,
    "customerName": customer_name,
    "baseRepository": base_remote,
    "baseCommit": source_commit,
    "baseVersion": base_version,
    "derivedAt": derived_at,
    "gitRemoteConfigured": False,
    "productionTargetConfigured": False,
    "defaultBonusPolicy": "CUSTOMER_BONUS_DISABLED",
    "miniProgramIncluded": mini_program_included,
}
(project / "CUSTOMER_PROJECT_ORIGIN.json").write_text(
    json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
    encoding="utf-8",
)

(project / "CUSTOMER_PROJECT.md").write_text(
    f"""# {customer_name}商城独立项目

项目标识：`{customer_code}`

商城基座版本：`{base_version}`

商城基座提交：`{source_commit}`

派生时间：`{derived_at}`

## 当前状态

- 这是独立客户项目，不与商城基座共用 Git 仓库、服务器、数据库、域名或密钥。
- 尚未配置 Git 远程仓库和正式服务器目标。
- 奖金程序默认为 `CUSTOMER_BONUS_DISABLED`；交易和售后可以验收，但不会产生奖金。
- Android 接口地址已替换为不可访问的安全占位域名，配置客户正式域名前不得发布 App。
{('- 微信小程序项目已包含，但仍使用不可访问的客户域名占位和游客 AppID；客户主体、隐私政策、合法域名和密钥未配置前不得提交审核。' if mini_program_included else '- 当前来源基座尚未包含微信小程序；如客户需要，应先升级到包含小程序的已验收基座版本。')}

## 接下来必须完成

1. 创建该客户自己的私有 Git 仓库，人工核对地址后再配置 `origin`。
2. 冻结客户确认的制度原文、流程图、异常口径和验收案例。
3. 在本项目实现客户专属 `CustomerBonusPolicy`，不得把制度反向写回商城基座。
4. 使用 `document/private-deploy/scripts/deploy.sh` 配置客户独立域名、服务器和受保护密钥。
5. 如交付微信小程序，使用客户自己的 AppID、AppSecret、合法域名和隐私政策完成真机登录；支付仍需客户自己的微信支付商户号单独联调。
6. 跑通注册邀请、购买支付、履约售后、奖金结算、退款追回、提现和备份恢复后再上线。
""",
    encoding="utf-8",
)

(project / "AGENTS.md").write_text(
    f"""# 客户独立项目自动执行规则

## 项目身份

- 客户项目：`{customer_code}`（{customer_name}）
- 来源基座版本：`{base_version}`
- 来源基座提交：`{source_commit}`
- 本仓库只维护该客户项目；不得把该客户的奖金制度、密钥、资料或专属 UI 合回商城基座。

## Git 与目录边界

- 每次操作前先执行 `git rev-parse --show-toplevel`，确认位于本客户仓库。
- 本项目初始状态没有远程仓库；只有人工确认客户私有仓库地址后才能配置 `origin`。
- 禁止把商城基座仓库设为本项目远程，禁止向商城基座分支推送客户改动。
- 修改前先检查工作区与远程；遇到冲突停止并保留双方改动，禁止 force push。
- 代码修改并验证后更新本客户的日志、提交并推送到客户私有远程；不得提交 `.env`、密钥、证书、验证码或客户隐私数据。

## 产品与发布边界

- 客户奖金名称、比例、层级、复购和资格规则只在本项目实现；通用订单、钱包、退款追回和审计边界不得绕开。
- 默认 `CUSTOMER_BONUS_DISABLED`，客户制度未冻结、未实现、未通过全流程验收前不得启用计奖。
- 使用 `document/private-deploy/scripts/deploy.sh` 作为唯一部署入口，不得复用商城基座正式服务器、域名或历史发布脚本。
- 发布必须取得用户针对本客户版本的明确授权；发布前后都要完成备份、版本身份、健康、数据库迁移、唯一启用奖金程序和核心数据不变量验收。
""",
    encoding="utf-8",
)
PY

# 客户真实密钥和基座生产目标都不允许出现在新仓库。
if find "$PROJECT_STAGE" -type f \( -name '.env' -o -name '*.pem' -o -name '*.key' -o -name 'id_rsa*' \) -print -quit | grep -q .; then
  echo "派生内容包含不允许进入客户仓库的密钥或真实环境文件" >&2
  exit 1
fi
[[ ! -e "$PROJECT_STAGE/.deploy" ]] || { echo "派生内容仍包含基座旧发布物" >&2; exit 1; }
[[ ! -e "$PROJECT_STAGE/scripts/production-targets.sh" ]] || { echo "派生内容仍包含基座生产目标" >&2; exit 1; }
if find "$PROJECT_STAGE/scripts" -maxdepth 1 -type f -name 'remote-*' -print -quit | grep -q .; then
  echo "派生内容仍包含基座远程发布脚本" >&2
  exit 1
fi
grep -q 'replace-with-customer-domain.invalid' "$PROJECT_STAGE/mall-shop-web/.env.android" \
  || { echo "Android 客户域名安全占位未生效" >&2; exit 1; }
if [[ -f "$PROJECT_STAGE/mall-mini-program/config/runtime.js" ]]; then
  grep -q 'replace-with-customer-domain.invalid/api' "$PROJECT_STAGE/mall-mini-program/config/runtime.js" \
    || { echo "微信小程序客户域名安全占位未生效" >&2; exit 1; }
fi
grep -Fq 'notifyUrl: ${ALIPAY_NOTIFY_URL:}' "$PROJECT_STAGE/mall-distribution/src/main/resources/application.yml" \
  || { echo "支付宝客户回调安全占位未生效" >&2; exit 1; }
grep -q "form-action 'self' https://openapi.alipay.com" "$PROJECT_STAGE/scripts/nginx/lingqimall.conf" \
  || { echo "客户 CSP 安全参考缺失" >&2; exit 1; }
if grep -q 'lingqimall\.com' "$PROJECT_STAGE/scripts/nginx/lingqimall.conf"; then
  echo "客户 CSP 安全参考仍包含基座正式域名" >&2
  exit 1
fi

git -C "$PROJECT_STAGE" init -q -b main
git -C "$PROJECT_STAGE" add .
git -C "$PROJECT_STAGE" -c user.name='Lingqi Customer Project' -c user.email='noreply@local.invalid' \
  commit -q -m '初始化客户独立项目'
[[ -z "$(git -C "$PROJECT_STAGE" status --porcelain)" ]] || { echo "客户项目初始提交后工作区不干净" >&2; exit 1; }
[[ -z "$(git -C "$PROJECT_STAGE" remote)" ]] || { echo "客户项目不得自动继承远程仓库" >&2; exit 1; }

mv -- "$PROJECT_STAGE" "$DESTINATION_ABS"
INITIAL_COMMIT=$(git -C "$DESTINATION_ABS" rev-parse HEAD)
echo "客户独立项目已创建：$DESTINATION_ABS"
echo "来源基座：$BASE_VERSION / $SOURCE_COMMIT"
echo "客户初始提交：$INITIAL_COMMIT"
echo "当前未配置远程和正式服务器；请先阅读 CUSTOMER_PROJECT.md。"
