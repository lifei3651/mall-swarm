#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DEPLOY_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
ROOT_DIR=$(CDPATH= cd -- "$DEPLOY_DIR/../.." && pwd)
ENV_FILE="$DEPLOY_DIR/.env"
COMPOSE_FILE="$DEPLOY_DIR/docker-compose.private.yml"
MIGRATION_DIR="$ROOT_DIR/document/db/migrations"

compose() { docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"; }
mysql_cmd() {
  compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --batch --skip-column-names -u"$MYSQL_USER" "$MYSQL_DATABASE"' "$@"
}
checksum() {
  if command -v shasum >/dev/null 2>&1; then shasum -a 256 "$1" | awk '{print $1}'; else sha256sum "$1" | awk '{print $1}'; fi
}
env_get() { awk -v key="$1" 'index($0, key "=") == 1 { print substr($0, length(key)+2); exit }' "$ENV_FILE"; }
unquote() { value=$1; case "$value" in \"*\") value=${value#\"}; value=${value%\"} ;; \'*\') value=${value#\'}; value=${value%\'} ;; esac; printf '%s' "$value"; }
hex_value() { printf '%s' "$1" | od -An -tx1 | tr -d ' \n'; }

[ -d "$MIGRATION_DIR" ] || { echo "缺少迁移目录：$MIGRATION_DIR" >&2; exit 1; }
mysql_cmd <<'SQL'
CREATE TABLE IF NOT EXISTS dms_schema_migration_history (
  version VARCHAR(32) PRIMARY KEY,
  script VARCHAR(255) NOT NULL,
  checksum CHAR(64) NOT NULL,
  success TINYINT NOT NULL,
  execution_time_ms BIGINT NOT NULL DEFAULT 0,
  installed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
SQL

lock_dir="${TMPDIR:-/tmp}/mall-private-migration-$(env_get COMPOSE_PROJECT_NAME).lock"
mkdir "$lock_dir" 2>/dev/null || { echo "已有迁移任务正在执行" >&2; exit 1; }
trap 'rmdir "$lock_dir" 2>/dev/null || true' EXIT HUP INT TERM

# distribution.sql 已含 team_opt_in，但对应历史迁移使用不可重复的直接 ADD COLUMN。
# 只在明确的首次安装基线、字段真实存在且总账为空时登记这一项；已有客户仍按原总账升级。
baseline_marker_table=$(mysql_cmd <<'SQL'
SELECT COUNT(*) FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'dms_schema_baseline_marker';
SQL
)
baseline_marker=0
if [ "$baseline_marker_table" = "1" ]; then
  baseline_marker=$(mysql_cmd <<'SQL'
SELECT COUNT(*) FROM dms_schema_baseline_marker
WHERE baseline_key = 'distribution_20260813';
SQL
)
fi
history_count=$(mysql_cmd <<'SQL'
SELECT COUNT(*) FROM dms_schema_migration_history;
SQL
)
if [ "$baseline_marker" = "1" ] && [ "$history_count" = "0" ]; then
  absorbed_file="$MIGRATION_DIR/V202608170900__split_public_and_team_membership.sql"
  [ -f "$absorbed_file" ] || { echo "缺少商城基线已吸收的团队身份迁移" >&2; exit 1; }
  absorbed_column=$(mysql_cmd <<'SQL'
SELECT COUNT(*) FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'dms_shop_member' AND column_name = 'team_opt_in';
SQL
)
  [ "$absorbed_column" = "1" ] || { echo "商城基线标记与 team_opt_in 结构不一致" >&2; exit 1; }
  absorbed_hash=$(checksum "$absorbed_file")
  mysql_cmd <<SQL
INSERT INTO dms_schema_migration_history(version, script, checksum, success, execution_time_ms)
VALUES('202608170900', 'V202608170900__split_public_and_team_membership.sql', '$absorbed_hash', 1, 0);
SQL
  echo "已登记商城基线吸收的团队身份迁移"
fi

find "$MIGRATION_DIR" -maxdepth 1 -type f -name 'V*.sql' | LC_ALL=C sort | while IFS= read -r file; do
  base=$(basename "$file")
  version=$(printf '%s' "$base" | sed -n 's/^V\([0-9]\{12\}\)__[a-z0-9_]*\.sql$/\1/p')
  [ -n "$version" ] || { echo "迁移文件名不合法：$base" >&2; exit 1; }
  hash=$(checksum "$file")
  existing=$(mysql_cmd <<SQL
SELECT CONCAT(checksum, ':', success) FROM dms_schema_migration_history WHERE version = '$version' LIMIT 1;
SQL
)
  if [ -n "$existing" ]; then
    [ "$existing" = "$hash:1" ] || { echo "迁移记录冲突：$base" >&2; exit 1; }
    echo "已跳过 $base"
    continue
  fi
  start=$(date +%s)
  echo "正在执行 $base"
  if mysql_cmd < "$file"; then
    elapsed=$(( ($(date +%s) - start) * 1000 ))
    mysql_cmd <<SQL
INSERT INTO dms_schema_migration_history(version, script, checksum, success, execution_time_ms)
VALUES('$version', '$base', '$hash', 1, $elapsed);
SQL
  else
    echo "迁移失败：${base}；部署已停止，请使用升级前备份核对" >&2
    exit 1
  fi
done

# 早期私有部署初始化只创建了租户，没有落安全关闭的奖金版本。
# 仅在整张版本表为空时补齐；一旦客户项目已有任何版本，绝不自动启用、停用或替换其制度。
bonus_version_count=$(mysql_cmd <<'SQL'
SELECT COUNT(*) FROM dms_commission_rule_version WHERE tenant_id = 1;
SQL
)
if [ "$bonus_version_count" = "0" ]; then
  mysql_cmd <<'SQL'
INSERT INTO dms_commission_rule_version
  (tenant_id, version_no, version_name, status, effective_time, remark)
VALUES
  (1, 'CUSTOMER_BONUS_DISABLED', '客户奖金程序未接入', 1, NOW(),
   '商城基座安全默认值：正常交易不产生奖金，客户制度开发并验收后再替换');
SQL
  echo "已补齐新客户安全关闭的奖金程序"
fi
active_bonus_version_count=$(mysql_cmd <<'SQL'
SELECT COUNT(*) FROM dms_commission_rule_version WHERE tenant_id = 1 AND status = 1;
SQL
)
[ "$active_bonus_version_count" = "1" ] \
  || { echo "客户必须且只能启用一个奖金程序，当前为 ${active_bonus_version_count} 个" >&2; exit 1; }

# 客户资料是可重复应用的交付配置，不属于通用数据库结构迁移；使用十六进制避免SQL转义和注入。
customer_name=$(unquote "$(env_get CUSTOMER_NAME)")
brand_name=$(unquote "$(env_get CUSTOMER_BRAND_NAME)")
theme_color=$(unquote "$(env_get CUSTOMER_THEME_COLOR)")
product_template=$(unquote "$(env_get CUSTOMER_PRODUCT_TEMPLATE)")
customer_hex=$(hex_value "$customer_name")
brand_hex=$(hex_value "$brand_name")
theme_hex=$(hex_value "$theme_color")
template_hex=$(hex_value "$product_template")
tenant_count=$(mysql_cmd <<'SQL'
SELECT COUNT(*) FROM dms_tenant WHERE id = 1;
SQL
)
[ "$tenant_count" = "1" ] || { echo "缺少商城基座租户 id=1，已停止写入客户资料" >&2; exit 1; }
mysql_cmd <<SQL
UPDATE dms_tenant
SET tenant_name = CONVERT(0x${customer_hex} USING utf8mb4),
    brand_name = CONVERT(0x${brand_hex} USING utf8mb4),
    theme_color = CONVERT(0x${theme_hex} USING utf8mb4),
    product_template = CONVERT(0x${template_hex} USING utf8mb4)
WHERE id = 1;
SQL

echo "数据库版本化迁移完成"
