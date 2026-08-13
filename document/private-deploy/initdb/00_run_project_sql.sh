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
run_sql_file /init-sql/distribution/distribution.sql

# 增量迁移由统一部署入口在基线建库后登记并执行；避免首次初始化执行了 SQL 却没有迁移记录。
