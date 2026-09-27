#!/bin/sh
set -e

run_sql_file() {
  file="$1"
  [ -f "$file" ] || { echo "missing required init sql: $file" >&2; exit 1; }
  echo "running init sql: $file"
  MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE" < "$file"
}

# 新客户以当前分销基线建库；历史 document/sql 中包含旧商城的 DROP/示例数据，禁止自动执行。
# 基线不再创建任何默认管理员；部署完成后必须显式执行 deploy.sh bootstrap-admin。
run_sql_file /init-sql/distribution/distribution.sql
# 显式依赖顺序：退货流程依赖地址脚本建立的 return_address，不能按文件名排序。
# 只执行这份允许清单，不扫描含旧商城 DROP/示例数据的历史 SQL 目录。
run_sql_file /init-sql/prerequisites/20260714_erp_integration_upgrade.sql
run_sql_file /init-sql/prerequisites/20260808_add_shop_service_addresses.sql
run_sql_file /init-sql/prerequisites/20260808_add_after_sale_return_workflow.sql

# 仅首次创建数据卷时写入基础版本标记。统一迁移入口只会登记
# distribution.sql 已经吸收、且自身不可重复执行的指定迁移；其余迁移仍真实执行。
MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE" <<'SQL'
CREATE TABLE IF NOT EXISTS dms_schema_baseline_marker (
  baseline_key VARCHAR(64) PRIMARY KEY,
  installed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO dms_schema_baseline_marker(baseline_key)
VALUES('distribution_20260813')
ON DUPLICATE KEY UPDATE baseline_key = VALUES(baseline_key);
SQL
