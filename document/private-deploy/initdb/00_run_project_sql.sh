#!/bin/sh
set -e

run_sql_dir() {
  dir="$1"
  if [ ! -d "$dir" ]; then
    return
  fi
  find "$dir" -maxdepth 1 -type f -name '*.sql' | sort | while read -r file; do
    run_sql_file "$file"
  done
}

run_sql_file() {
  file="$1"
  if [ -f "$file" ]; then
    echo "running init sql: $file"
    MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE" < "$file"
  fi
}

# 新客户以当前分销基线建库；历史 document/sql 中包含旧商城的 DROP/示例数据，禁止自动执行。
# 基线不再创建任何默认管理员；部署完成后必须显式执行 deploy.sh bootstrap-admin。
run_sql_file /init-sql/distribution/distribution.sql
run_sql_dir /init-sql/prerequisites

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
